# TestFlight preparation

The simulator workflow does not create an installable iPhone app. Run the manual **iOS device archive preflight** workflow to compile the Kotlin release framework and Swift app for iPhoneOS and verify an unsigned archive. It uses no Apple credentials, performs no upload and invites no testers.

Before signing/uploading, identify the Apple Developer team and the App Store Connect app record for `tech.kiasolutions.workoutcoach`. Configure distribution signing and App Store Connect access through protected CI secrets, never source files, workflow inputs, logs or chat. A simulator signing identity or entitlement is not a distribution credential. The device spec references the arm64 release framework; simulator-only Keychain entitlements do not apply to iPhoneOS.

Upload preparation also needs the app icon, beta description/support contact, accurate encryption and privacy declarations, and a clear list of working/incomplete features. Public source defaults to a nonfunctional example backend; account-enabled beta builds need an explicitly selected HTTPS backend and completed account checks. A guest-only beta must clearly exclude unfinished account/sync functionality.

Follow the project testing sequence: preparation may proceed, but actual user testing starts after the agent acceptance and handoff milestone. No invitations are sent by the preflight workflow. External testers need TestFlight App Review before distribution; internal testers must have the appropriate App Store Connect access.

Apple references: [TestFlight](https://developer.apple.com/testflight/), [distribution](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases/), [app icons](https://developer.apple.com/help/app-store-connect/manage-app-information/add-an-app-icon/).
