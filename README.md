# <img src="https://github.com/franekk3/cubyy-android/media/cubyy-android-icon.png" width="36" height="36" valign="middle"> Cubyy

[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)](https://developer.android.com)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Open Source](https://img.shields.io/badge/Open%20Source-%E2%9D%A4-orange.svg)](https://github.com)

**Cubyy** is a native Android speedcubing timer built with Kotlin and Jetpack Compose, featuring complete **Material You (Material 3)** dynamic theming and seamless **Smart Cube** hardware integration.

[![Download APK](https://img.shields.io/badge/Download-APK-success?style=for-the-badge&logo=android)](https://github.com/franekk3/cubyy-android/releases/latest)

---

## 📸 Screenshots

<p align="center">
  <img src="https://github.com/franekk3/cubyy-android/media/phone1.png" width="19%" />
  <img src="https://github.com/franekk3/cubyy-android/media/phone2.png" width="19%" />
  <img src="https://github.com/franekk3/cubyy-android/media/phone3.png" width="19%" />
  <img src="https://github.com/franekk3/cubyy-android/media/phone4.png" width="19%" />
  <img src="https://github.com/franekk3/cubyy-android/media/phone5.png" width="19%" />
</p>

---

## ✨ Features

### ⏱️ Speedcubing Timer
- **Precision Timing**: Millisecond-accurate timer running at 60 FPS using monotonic hardware clocks.
- **WCA Inspection**: Optional 15-second countdown with standard WCA haptic cues at 8s and 12s, plus automatic `+2` (>15s) and `DNF` (>17s) penalties.
- **Two-Finger / Full-Screen Touch Pad**: Hold anywhere to arm (Red -> Green `READY`), release to solve, tap to stop.
- **WCA Scramble Generator**: Compliant random-state 3x3 scrambler avoiding consecutive face turns and parallel face cancellations.
- **Post-Solve Actions**: Quick `+2`, `DNF`, and delete buttons.

### 🧊 Smart Cube Hardware Support
Cubyy reverse-engineers and natively implements Bluetooth Low Energy (BLE) protocols for all major speedcube brands:
- **GAN OG & Gen 1**: GAN 356 i, GAN 356 i Play, GAN 356 i 2 (AES-128-ECB decryption via SystemID / MAC salt mixing).
- **GAN Gen 2 / Gen 3 / Gen 4**: GAN 356 i Carry, i Carry S, i Carry 2, GAN 12 ui, GAN 12 ui FreeDoc, Monster Go 3Ai, AiCube (Nordic UART service `6e400001`, `28be4cb6` notify, AES-128 cipher chaining).
- **MoYu**: MoYu WeiLong AI, MoYu AI 2023 (`00001000` service, `00001003` turn notifications).
- **GiiKER**: Super Cube i3s, i2 (`0000aadb` service, `0000aadc` telemetry).

#### Smart Cube Automations:
- **Auto-Start**: Starts on the first solve turn after the scramble target has been reached.
- **Auto-Stop**: Uses real-time virtual cube state simulation to detect when all 6 faces are solved and stops immediately.
- **Live Virtual Cube Visualizer**: A frameless, draggable 3D preview appears on the Timer screen while a cube is connected.
- **Scramble Mistake Recovery**: If a turn deviates from the scramble, a two-phase solver shows a short route back to the cube state before the first mistake; the original scramble and completed move colors are then restored.
- **TPS & Reconstruction**: Calculates Turns Per Second (TPS) and saves full move notation sequences (e.g., `R U R' U' ...`).
- **Battery & Signal Monitoring**: Live battery percentage indicator and RSSI signal strength.

The recovery solver uses `org.worldcubeassociation.tnoodle:scrambler-min2phase` (GPL-3.0). It returns a practical short two-phase route, not a mathematically guaranteed globally shortest route. Distributing this app requires complying with that dependency's license.

### 📊 Solve History & Statistics
- Comprehensive stats: Best Single, Worst Single, Current Ao5, Best Ao5, Current Ao12, Best Ao12, Session Mean, and Average TPS.
- Multi-session management: Create and switch between sessions (e.g. `Default`, `3x3`, `Smart Cube`, custom sessions).
- Detailed solve dialog: Scramble viewer with one-click copy, move reconstruction logs, and solve sharing formatted for Discord and speedcubing forums.

### 🎨 Material You Design
- Full support for Android 12+ Dynamic Theming (`dynamicDarkColorScheme` / `dynamicLightColorScheme`), adapting color palettes to your system wallpaper.
- Elegant fallback light & dark high-contrast themes optimized for cubers.
- Edge-to-edge layout with expressive Material 3 typography and rounded surfaces.

---

## 🛠️ Project Structure

```
cubyy-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/cubyy/timer/
│   │   │   │   ├── MainActivity.kt               # Main entry point & bottom navigation
│   │   │   │   ├── smartcube/
│   │   │   │   │   ├── SmartCubeManager.kt       # BLE scanner, connection & protocol router
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── CubeModel.kt          # Face, Color, Move data classes
│   │   │   │   │   │   ├── VirtualCube.kt        # 3x3 Rubik's Cube state engine
│   │   │   │   │   │   └── SmartCubeDevice.kt    # Device info & event types
│   │   │   │   │   └── protocols/
│   │   │   │   │       ├── SmartCubeProtocol.kt  # Protocol driver interface
│   │   │   │   │       ├── GanGen1Protocol.kt    # GAN 356 i, i Play, i2 driver
│   │   │   │   │       ├── GanGen2Protocol.kt    # GAN i Carry, 12 ui, Monster Go driver
│   │   │   │   │       ├── MoyuProtocol.kt       # MoYu WeiLong AI driver
│   │   │   │   │       └── GiikerProtocol.kt     # GiiKER Super Cube driver
│   │   │   │   ├── scramble/
│   │   │   │   │   └── ScrambleGenerator.kt      # WCA 3x3 scramble generation
│   │   │   │   ├── timer/
│   │   │   │   │   └── TimerEngine.kt            # High-precision timer & smart cube sync
│   │   │   │   ├── data/
│   │   │   │   │   ├── Solve.kt                  # Solve model & Ao5/Ao12 calculator
│   │   │   │   │   ├── SolveRepository.kt        # Persistent solve & session storage
│   │   │   │   │   └── SettingsRepository.kt     # User preferences storage
│   │   │   │   └── ui/
│   │   │   │       ├── components/
│   │   │   │       │   └── VirtualCubeView.kt    # 3D isometric & 2D net cube renderer
│   │   │   │       ├── screens/
│   │   │   │       │   ├── TimerScreen.kt        # Main timer interface
│   │   │   │       │   ├── SmartCubeScreen.kt    # BLE device scanner & live cube hub
│   │   │   │       │   ├── HistoryScreen.kt      # Solves history, stats & details
│   │   │   │       │   └── SettingsScreen.kt     # Preferences & hardware config
│   │   │   │       └── theme/
│   │   │   │           ├── Color.kt              # Material 3 color definitions
│   │   │   │           ├── Type.kt               # Monospace & display typography
│   │   │   │           └── Theme.kt              # Material You dynamic theme
│   │   │   ├── res/                              # Drawables, layouts, strings, themes
│   │   │   └── AndroidManifest.xml               # Permissions & BLE configurations
│   │   └── test/                                 # Unit tests for cube math, stats & scrambles
│   └── build.gradle.kts                          # Module dependencies & compile SDK 35
├── gradle/
│   └── libs.versions.toml                        # Version catalog (AGP 9.4, Kotlin 2.2, Compose)
├── build.gradle.kts                              # Root buildscript
├── settings.gradle.kts                           # Module registration
└── gradle.properties                             # Build optimization & JVM arguments
```

---

## 🚀 Building & Running

### Option 1: Android Studio (Recommended)
1. Launch **Android Studio**.
2. Select **Open** and choose `/home/frank/Documents/projects/cubyy-android`.
3. Wait for Gradle Sync to finish.
4. Select your connected Android device or emulator (Android 8.0+ / API 26+) and click **Run (Shift + F10)**.

### Option 2: Command Line (Gradle)
```bash
# Verify tasks
./gradlew tasks

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```
The compiled APK will be output at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Permissions Used
- `BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT` (Android 12+): Required to discover and connect to smart cubes.
- `ACCESS_FINE_LOCATION` (Android 11 and earlier): Required by legacy Android OS for BLE peripheral scanning.
- `VIBRATE`: Provides haptic cues during inspection (8s, 12s) and solve finish.

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for full details.
