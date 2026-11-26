package com.matthew.perfoverlay.monitor

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Parses the batched root dump produced by [RootMtkMonitor]. Pure so it is
 * unit-testable without a device.
 *
 * Dump format: sections start with "@@ <name>", followed by the raw file lines:
 *  - ged_freq:  "<opp> <kHz>"  from /sys/kernel/ged/hal/current_freqency
 *  - ged_util:  "<load%> <block%> <idle%>" from /sys/kernel/ged/hal/gpu_utilization
 *  - devfreq:<dir>: Hz from /sys/class/devfreq/<dir>/cur_freq
 */
object MtkRootParser {
    private const val DEVFREQ_PREFIX = "devfreq:"

    fun parse(dump: String): RootSample {
        val sections = sections(dump)
        val gpu = parseGedFreq(sections["ged_freq"]?.firstOrNull())
        val load = parseGedLoad(sections["ged_util"]?.firstOrNull())
        val devfreqs = sections.entries
            .filter { it.key.startsWith(DEVFREQ_PREFIX) }
            .mapNotNull { (key, lines) ->
                val hz = lines.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                DevfreqSample(shortDevfreqName(key.removePrefix(DEVFREQ_PREFIX)), hz / 1000)
            }
        return RootSample(
            gpuFreqKhz = gpu?.second,
            gpuOpp = gpu?.first,
            gpuLoadPct = load,
            devfreqs = devfreqs,
        )
    }

    /** "<opp> <kHz>" → (opp, kHz). */
    fun parseGedFreq(line: String?): Pair<Int, Long>? {
        val parts = line?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 2) return null
        val opp = parts[0].toIntOrNull() ?: return null
        val khz = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return opp to khz
    }

    /** First field of "<load> <block> <idle>" is the GPU busy percentage. */
    fun parseGedLoad(line: String?): Float? {
        val first = line?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: return null
        return first.toFloatOrNull()?.coerceIn(0f, 100f)
    }

    /** Splits the "@@ name" sectioned dump into name → following lines. */
    fun sections(dump: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in dump.lineSequence()) {
            if (line.startsWith("@@ ")) {
                current = mutableListOf()
                out[line.substring(3).trim()] = current
            } else {
                current?.add(line)
            }
        }
        return out
    }

    /** "13000000.mali" → "mali", "mtk-dvfsrc-devfreq" → "mtk-dvfsrc". */
    fun shortDevfreqName(dir: String): String {
        val afterAddr = Regex("""^[0-9a-fA-F]+\.(.+)$""").find(dir)?.groupValues?.get(1) ?: dir
        return afterAddr.removeSuffix("-devfreq")
    }
}

/**
 * Root-gated MediaTek sensors. Keeps one persistent `su` shell and sends a
 * single batched dump per sample, so there is one process for the whole
 * session instead of one `su` spawn per tick.
 *
 * Failure (no su, denied, nodes missing) yields [RootSample.EMPTY] — never
 * fake values. A failed shell is retried after [RETRY_MS] so a denied prompt
 * does not respawn `su` every tick.
 */
class RootMtkMonitor {
    private var shell: RootShell? = null
    private var retryAtMs = 0L

    fun sample(): RootSample {
        val now = System.currentTimeMillis()
        val sh = shell ?: run {
            if (now < retryAtMs) return RootSample.EMPTY
            val created = try {
                RootShell()
            } catch (e: IOException) {
                Log.d(TAG, "su unavailable: ${e.message}")
                retryAtMs = now + RETRY_MS
                return RootSample.EMPTY
            }
            shell = created
            created
        }
        val dump = sh.run(DUMP_SCRIPT)
        if (dump == null) {
            Log.d(TAG, "root dump failed; retrying in ${RETRY_MS}ms")
            close()
            retryAtMs = now + RETRY_MS
            return RootSample.EMPTY
        }
        return MtkRootParser.parse(dump)
    }

    fun close() {
        shell?.close()
        shell = null
    }

    private class RootShell {
        private val process = ProcessBuilder("su").redirectErrorStream(true).start()
        private val input = BufferedWriter(OutputStreamWriter(process.outputStream))
        private val output = BufferedReader(InputStreamReader(process.inputStream))

        /** Runs [script] and returns its stdout, or null if the shell died. */
        fun run(script: String): String? = try {
            input.write(script)
            input.write("\necho $END_MARKER\n")
            input.flush()
            val sb = StringBuilder()
            while (true) {
                val line = output.readLine() ?: return null
                if (line == END_MARKER) break
                sb.append(line).append('\n')
            }
            sb.toString()
        } catch (_: IOException) {
            null
        }

        fun close() {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "PerfOverlay"
        const val RETRY_MS = 10_000L
        const val END_MARKER = "__PERFOVERLAY_END__"

        // Every read is best-effort (2>/dev/null) so one missing node never
        // hides the others.
        const val DUMP_SCRIPT =
            "echo '@@ ged_freq'; cat /sys/kernel/ged/hal/current_freqency 2>/dev/null\n" +
                "echo '@@ ged_util'; cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null\n" +
                "for d in /sys/class/devfreq/*; do " +
                "echo \"@@ devfreq:\$(basename \"\$d\")\"; cat \"\$d/cur_freq\" 2>/dev/null; done"
    }
}
