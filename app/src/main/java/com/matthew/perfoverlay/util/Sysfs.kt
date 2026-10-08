package com.matthew.perfoverlay.util

import android.util.Log
import java.io.File

/**
 * Best-effort sysfs/proc readers. Everything returns null/empty on denial.
 * Denials log once per path at debug level so `adb logcat` shows exactly
 * which nodes SELinux/vendor policy blocks on a given device.
 */
object Sysfs {
    private const val TAG = "PerfOverlay"
    private val loggedPaths = mutableSetOf<String>()

    private fun logOnce(path: String, e: Exception) {
        if (loggedPaths.add(path)) {
            Log.d(TAG, "unreadable: $path (${e.message})")
        }
    }

    fun readFirstLine(path: String): String? =
        try {
            File(path).bufferedReader().use { it.readLine() }?.trim()
        } catch (e: Exception) {
            logOnce(path, e)
            null
        }

    fun readLong(path: String): Long? = readFirstLine(path)?.toLongOrNull()

    fun readLines(path: String): List<String> =
        try {
            File(path).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            logOnce(path, e)
            emptyList()
        }

    fun listDirs(path: String): List<File> =
        try {
            File(path).listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
        } catch (e: Exception) {
            logOnce(path, e)
            emptyList()
        }

    fun listNames(path: String): List<String> =
        try {
            File(path).list()?.toList() ?: emptyList()
        } catch (e: Exception) {
            logOnce(path, e)
            emptyList()
        }

    fun exists(path: String): Boolean =
        try {
            File(path).exists()
        } catch (_: Exception) {
            false
        }
}
