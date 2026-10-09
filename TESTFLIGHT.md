# TestFlight preparation

The simulator workflow does not create an installable iPhone app. Run the manual **iOS device archive preflight** workflow to compile the Kotlin release framework and Swift app for iPhoneOS and verify an unsigned archive. It uses no Apple credentials, performs no upload and invites no testers.

Before signing/uploading, identify the Apple Developer team and the App Store Connect app record for `tech.kiasolutions.workoutcoach`. Configure distribution signing and App Store Connect access through protected CI secrets, never source files, workflow inputs, logs or chat. A simulator signing identity or entitlement is not a distribution credential. The device spec references the arm64 release framework; simulator-only Keychain entitlements do not apply to iPhoneOS.

Upload preparation also needs the app icon, beta description/support contact, accurate encryption and privacy declarations, and a clear list of working/incomplete features. Public source defaults to a nonfunctional example backend; account-enabled beta builds need an explicitly selected HTTPS backend and completed account checks. A guest-only beta must clearly exclude unfinished account/sync functionality.

The owner explicitly selected an early offline beta, bringing TestFlight user testing forward from the full MVP handoff milestone. The device build sets WORKOUT_OFFLINE_BETA=YES and hides account/sync controls without accessing account credentials. Camera counting requires explicit MediaPipe metrics consent; withdrawal is available in Settings. Agent checks must pass before upload. No invitations are sent by the preflight workflow. External testers need TestFlight App Review before distribution; internal testers must have the appropriate App Store Connect access.

Apple references: [TestFlight](https://developer.apple.com/testflight/), [distribution](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases/), [app icons](https://developer.apple.com/help/app-store-connect/manage-app-information/add-an-app-icon/).

## Signed upload workflow

The manual `Upload offline beta to TestFlight` workflow is restricted to main and the `testflight` GitHub environment. It requires passing mobile verification and unsigned device preflight for the exact source revision, plus environment variable `TESTFLIGHT_PRIVACY_READY=true` after consent and disclosure verification.

Configure environment secrets `APPLE_TEAM_ID`, `IOS_CERT_P12_BASE64`, `IOS_CERT_PASSWORD`, `IOS_PROFILE_BASE64`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, and `ASC_PRIVATE_KEY`. The certificate/private key must match an unexpired App Store distribution profile for this bundle. The helper checks team, bundle, distribution type and certificate match before installing temporary signing material. Credentials stay out of source, workflow inputs and public artifacts. Protect the environment with a required reviewer where supported; never run signing on pull requests.

A Developer-role **team** App Store Connect API key can upload builds, but its role applies across team apps; it is not restricted to this app. New credentials require explicit owner approval for this scope. Prefer a dedicated key and do not reuse or revoke unrelated keys/certificates.

The workflow archives, exports and validates the signed offline app, uploads through Apple's command-line tools, and removes temporary credentials. It publishes no IPA artifacts and sends no invitations. Successful upload still requires Apple processing and any required TestFlight review. No public App Store release is performed.
