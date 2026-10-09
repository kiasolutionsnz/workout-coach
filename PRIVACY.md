# Workout Coach offline beta privacy

Draft dated 9 October 2026. The early iOS beta has not been distributed. SDK metrics behavior and the associated consent/declarations must be resolved before distribution by Kia Solutions Ltd.

Routines, settings, exercise counts, hold durations and workout history are stored locally on your phone. The camera is used to estimate exercise movement on the device. Camera frames and raw pose landmarks are not uploaded to a Workout Coach server. The beta has no account sign-in, cloud synchronization or advertising features.

Camera access is requested when you use camera tracking. Spoken cues use Apple's speech-synthesis service; the app does not request microphone access. The bundled movement model runs on the device. Timing APIs measure workout intervals; local file APIs support app-owned assets and saved data.

The current MediaPipe Tasks SDK is supplied by Google. [Google's MediaPipe privacy notice](https://github.com/google-ai-edge/mediapipe#privacy-notice) states that its APIs send performance and utilization metrics to Google while input processing stays on-device. This reporting must be disabled/replaced, or handled through explicit informed consent and accurate disclosure, before this beta is released. Until then, the current SDK build is not claimed to be telemetry-free and this privacy document remains a draft.

You can export a workout through the iOS share sheet. Any recipient or external service you choose receives that export under your direction. You can delete workouts from History. Uninstalling the app removes its local app data; device backups and shared exports are managed separately through your device and chosen destination.

TestFlight is operated by Apple. Beta diagnostics, crash reports, screenshots and feedback submitted through TestFlight are handled under Apple's TestFlight terms and privacy information. If you contact us or submit feedback, we use the information you provide to investigate and improve the beta. Avoid including information you do not want to share in screenshots or feedback.

For privacy questions or requests relating to feedback you sent us, contact contact@kiasolutions.co.nz. Accounts and cloud features planned for later releases will require updated privacy information before they are enabled.
