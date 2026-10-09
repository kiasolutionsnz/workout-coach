# Workout Coach — development source

Native SwiftUI iOS and Compose Android with a Kotlin Multiplatform workout core. Offline routines, six exercise analyzers, full/partial reps, elapsed/valid hold timers, local history/export/deletion and native camera/speech adapters. Raw camera frames and pose landmarks stay on the device.

This is prerelease source, not an approved store or production release. Public hosted verification passes Android shared/build tests and native instrumentation, Kotlin iOS simulator tests, and native SwiftUI/Keychain tests. See the [verified run](https://github.com/kiasolutionsnz/workout-coach/actions/runs/37875235958) at source revision 9adb2a3. Valid staging-account network verification remains separate; private fixtures are excluded from public CI. Synthetic tests do not establish real-person accuracy, physical camera/audio behavior, battery performance or accessibility usability.

Android/shared: use Java 17+, Android SDK36 and `./gradlew :shared:jvmTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`. iOS: use macOS/Xcode, build the shared simulator framework, generate the project using XcodeGen, install the locked CocoaPods dependencies, then run the simulator tests as shown in the workflow.

Accounts are optional. The default backend is a nonfunctional example HTTPS origin; guest workouts work without it. Android accepts a public `WORKOUT_API_BASE_URL` environment variable at build time. iOS accepts an Xcode build setting with the same name. Never supply credentials or tokens through these settings. No server keys, deployment identities, private platform inventory, historical CI logs or account fixtures are included.

The bundled Google Pose Landmarker Lite model is Apache-2.0 licensed; see [NOTICE.md](NOTICE.md). Source publication grants no additional license to original app code. See [SECURITY.md](SECURITY.md) for disclosure guidance.
