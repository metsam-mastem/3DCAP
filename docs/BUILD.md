# Build 3DCAP

## Android Studio
1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Clone the repository and check out `feat/android-guided-capture-mvp`.
3. Open the repository root as an Android Studio project.
4. Allow Gradle sync (Android Gradle Plugin 8.7.3 / Gradle 8.9).
5. Run the `app` configuration on an Android 8.0+ device with a rear camera.

## Command line
With Gradle 8.9 and Android SDK 35 installed:

```bash
gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## CI artifact
The GitHub Actions workflow runs unit tests, lint and APK assembly. On a successful run, download the `3DCAP-debug-apk` artifact from the workflow run's Artifacts section.

## Device validation still required
The CI build cannot validate camera sensor axes, exposure/sharpness thresholds, autofocus behaviour, thermal performance or UI smoothness on a physical phone. Test on the target device before treating the build as a release.
