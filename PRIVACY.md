# Workout Coach offline beta privacy

Updated 9 October 2026. This policy covers the early iOS offline beta provided by Kia Solutions Ltd.

Routines, settings, exercise counts, hold durations and workout history are stored locally on your phone. The camera is used to estimate exercise movement on the device. Camera frames and raw pose landmarks are not uploaded to a Workout Coach server. The beta has no account sign-in, cloud synchronization or advertising features.

Camera access is requested when you use camera tracking. Spoken cues use Apple's speech-synthesis service; the app does not request microphone access. The bundled movement model runs on the device. Timing APIs measure workout intervals; local file APIs support app-owned assets and saved data.

The current MediaPipe Tasks SDK is supplied by Google. [Google's MediaPipe privacy notice](https://github.com/google-ai-edge/mediapipe#privacy-notice) states that its APIs send performance and utilization metrics to Google while input processing stays on-device. Before the app initializes the camera-counting SDK, it asks you to allow these metrics with a disclosure and links to this policy and Google's notice. Consent is off by default. Declining keeps camera counting off; routines and local history remain available. Camera counting works without a Workout Coach server connection, but the Google SDK may communicate with Google when a connection is available. This beta is not telemetry-free.

You can withdraw consent in Settings using **Withdraw camera SDK consent**. This stops camera capture and further SDK inference and drops pending pose delivery. It cannot retract metrics already sent to Google or guarantee cancellation of reporting already queued inside the SDK. Google handles its received metrics under its privacy policies. We do not claim those metrics are anonymous or promise to delete data held by Google.

The app privacy manifest declares SDK performance data and product-interaction metrics for analytics, without advertising tracking. It conservatively declares these metrics as linked because the SDK notice does not establish that they are anonymous. Routines, workout history, camera images and raw pose data are not included in these app-declared analytics categories.

You can export a workout through the iOS share sheet. Any recipient or external service you choose receives that export under your direction. You can delete workouts from History. Uninstalling the app removes its local app data; device backups and shared exports are managed separately through your device and chosen destination.

TestFlight is operated by Apple. Beta diagnostics, crash reports, screenshots and feedback submitted through TestFlight are handled under Apple's TestFlight terms and privacy information. If you contact us or submit feedback, we use the information you provide to investigate and improve the beta. Avoid including information you do not want to share in screenshots or feedback.

For privacy questions or requests relating to feedback you sent us, contact contact@kiasolutions.co.nz. Accounts and cloud features planned for later releases will require updated privacy information before they are enabled.
