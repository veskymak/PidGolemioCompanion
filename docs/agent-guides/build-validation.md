# Build and validation

Run these commands from the repository root with the checked-in Gradle wrapper:

| Goal | Windows | Other platforms |
| --- | --- | --- |
| Assemble the debug app | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Run debug unit tests | `.\gradlew.bat testDebugUnitTest` | `./gradlew testDebugUnitTest` |
| Run debug instrumentation tests on a connected device | `.\gradlew.bat connectedDebugAndroidTest` | `./gradlew connectedDebugAndroidTest` |
| Run configured project checks | `.\gradlew.bat check` | `./gradlew check` |
