# Notra

Notra is a local-first Android notes and personal knowledge workspace built around a tactile spatial Board and a structured Library.

## Start here

Coding agents must read these files before making changes:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`

Implementation proceeds one packet at a time. Do not attempt to build the full product in one pass.

## Android build

Install JDK 17 and Android SDK platform 37.0 with build tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, then run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` (or `gradlew.bat` on Windows). The Gradle wrapper downloads Gradle 9.6.0. The debug APK is under `app/build/outputs/apk/debug/`.

I-001 supplies a four-destination shell and local persistence foundations. Screen interactions for notes and the spatial Board arrive in later packets.
