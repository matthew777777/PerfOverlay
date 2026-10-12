# PerfOverlay

Floating system profiler for Android: a transparent overlay drawn over other
apps while you profile them. No root required.

Shows, live:

- **Power**: current (mA), voltage (V), watts (W)
- **Switchable plot**: tap the graph to cycle mA, W, V, CPU total, every
  CPU core, CPU/GPU freq, GPU load, RAM, battery/CPU/GPU/skin temps,
  network ↓/↑ — each series keeps full history with a peak label;
  default series (mA/W) in the Plot settings on the main screen
- **Session energy**: **average mA** + elapsed time (resettable)
- **Battery**: level %, temp °C, remaining Wh/mAh, charge direction (`+`/`-`)
- **CPU**: per-core freq for all cores; total + per-core load % via
  HardwarePropertiesManager with `/proc/stat` fallback
- **Temps**: CPU/GPU/skin °C (color-coded), `HOT` flag on thermal throttling
- **GPU**: load % + freq (best-effort, `N/A` where the vendor blocks sysfs)
- **Network**: device-wide ↓/↑ KB/s
- **Memory**: system used/total GB + %

## Build

```sh
cd AndroidStudioProjects/PerfOverlay
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/release/PerfOverlay-1.1.0-release.apk`
(signed with the debug key for on-device profiling; override via
`PERFOVERLAY_KEYSTORE*` env for store builds).

Toolchain mirrors RawLens: AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35,
minSdk 29, Java 17. Zero app dependencies (platform APIs only).

## Install & run

```sh
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/PerfOverlay-1.0.0-release.apk
```

1. Open **PerfOverlay**.
2. Grant **“Display over other apps”** (opens system settings).
3. Pick a sample interval (0.5s / 1s / 2s), tap **Start overlay**.
4. Switch to the app you profile — the overlay floats on top:
   - drag it anywhere by the header row,
   - tap the graph to switch the plotted metric,
   - `▼` collapses to a thin strip (`W mA V batt% temp` + `▲ R X`),
     `▲` expands back, `R` resets session stats, `X` closes it.

## Recorded sessions

`●` (overlay) or **Start recording** (main screen) saves every sample to
CSV — all overlay readouts: power, battery, CPU total + per-core
loads/freqs, GPU load/freq, CPU/GPU/skin temps, memory, network.
**Sessions** lists recordings; each opens a detail screen with a replay
graph + avg/min/max per metric, a per-core table, and a per-app
breakdown (avg mA/mWh/W/CPU). Pre-1.1 9-column files still open.

## Data sources & caveats

| Metric | Source | Notes |
|---|---|---|
| Current | `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` | Signed `+`/`-` by plug state; 0 counts only while plugged in |
| Voltage | sticky `ACTION_BATTERY_CHANGED` `EXTRA_VOLTAGE` | |
| Avg mA | charge integral / elapsed hours | Session average since reset |
| Batt Lvl/Temp | sticky battery intent level + temp | Tenths °C → °C |
| Remaining | `BATTERY_PROPERTY_CHARGE/ENERGY_COUNTER` | mAh / Wh left, `N/A` if unsupported |
| CPU load | `HardwarePropertiesManager.cpuUsages` → `/proc/stat` deltas | No permission needed |
| CPU freq | `…/cpuN/cpufreq/scaling_cur_freq` → `cpuinfo_cur_freq` | Per-core, `N/A` if blocked |
| Temps | `HardwarePropertiesManager.deviceTemperatures` → thermal_zone scan | CPU/GPU/skin; hottest wins |
| Throttle | `PowerManager.currentThermalStatus` | `HOT` at moderate+ |
| GPU load | kgsl direct % / `gpubusy` / devfreq `load` / Mali `utilization` | Vendor-dependent |
| GPU freq | kgsl `gpuclk` / devfreq `cur_freq` / Mali `cur_freq` | Hz → normalized to kHz |
| Network | `TrafficStats` total Rx/Tx deltas | KB/s, no permission needed |
| Memory | `ActivityManager.MemoryInfo` | System-wide |

GPU nodes vary per SoC (Adreno/Mali/etc.) and newer Android versions restrict
some sysfs paths — the overlay shows `N/A` instead of fake data. Denied paths
log once at debug level (`adb logcat -s PerfOverlay:D`) for diagnosis.
