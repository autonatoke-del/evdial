# Build EVDial from source

1. Install OpenJDK 17, Android SDK platform 35, Android Build Tools 35.0.0 and Gradle 8.13 (the archived verified toolchain). Verify `JAVA_HOME`, `ANDROID_HOME` or `ANDROID_SDK_ROOT`, and `gradle --version`.
2. Clone this repo. In the repository root run `gradle assembleDebug`. The build resolves the Linphone SDK from `https://download.linphone.org/maven_repository`.
3. Run `gradle lintDebug` and source/JVM tests using the documented Python scripts. No real phone calls occur during source or JVM checks.
4. Debug APK path: `build/outputs/apk/debug/`. The debug keystore is local to the builder and MUST NOT be committed.
5. The official preview APK is signed with a separately stored owner key. A self-built APK cannot replace the official installed EVDial because Android requires the same signing certificate for updates. Do not uninstall the application or lose its local settings just to try a debug build.

## Variants and distributions

The application is a **website-distributed preview**, not a Google Play/RuStore publication. The manifest contains `REQUEST_INSTALL_PACKAGES` solely for the manual, verified website-update flow. A store release may require a separate flavor without that permission and must follow store rules.

`applicationId=ru.autonatoke.evdial.design`, `minSdk=26`, `targetSdk=33` in the historical releases. Those settings should only be changed with device and store-policy testing. The APK bundles Linphone native libraries for ARM32, ARM64, and x86_64.

## Tests

```bash
python3 tests/ThemeSourceChecks.py
python3 tests/ModernUxSourceChecks.py
python3 tests/run_jvm_regressions.py
# Available from v0.3.2-rc1:
python3 tests/CompactUiSourceChecks.py
python3 tests/SmartDialUiSourceChecks.py
gradle assembleRelease lintRelease # unsigned release build, do not publish it
# Native Android keypad layout, widths 320/360/393dp and font scales up to 200%:
gradle testDebugUnitTest --tests ru.autonatoke.evdial.DialpadKeyLayoutTest
# Actual Dialer: every key and call action visible, including small portrait screens:
gradle testDebugUnitTest --tests ru.autonatoke.evdial.DialerTypingLayoutTest
```

The keypad layout suite uses Robolectric Native Graphics on Android API 34 and saves renderings under `build/reports/keypad/`. It creates the real keypad component with a plain Android Application, without starting SIP or placing calls.

The typing layout suite runs the actual Dialer Activity with synthetic local contacts. Its 12 configurations cover portrait screens from 320×568dp through 393×873dp, system fonts up to 200%, app scale up to 150%, and EV/Rubina/Minimal themes. Every scenario checks that the number field, all 12 keys and the call button are fully visible above navigation with at least 48dp touch targets. It also verifies stable positions during edits and asynchronous search, independent upper-area scrolling, line-selection and EV-services panels, and upgrading from the old expanded-services preference. A test application prevents telephony and service activation; stale result actions must be unavailable while a new search is pending.

The source, JVM and keypad layout tests do NOT prove functional telephony or physical device UI. Test Android default dialer role, SIM/SIP incoming and outgoing calls, double-SIM routing, lock-screen, audio routes, contact permission handling, vCard import/export and updater behavior on Xiaomi/Samsung before a production rollout.

No `local.properties` or actual signing secret is committed. Use your own local build configuration.
