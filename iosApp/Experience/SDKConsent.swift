import SwiftUI

final class IOSSDKConsent: ObservableObject {
    static let shared = IOSSDKConsent()
    static let changed = Notification.Name("workout.cameraSDKConsentChanged")
    private static let key = "workout.cameraSDKMetricsConsent.v1"
    private let defaults: UserDefaults
    @Published private(set) var allowed: Bool
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        allowed = defaults.bool(forKey: Self.key)
    }
    // Read the stored value on inference queues without touching SwiftUI state.
    static var permitted: Bool { UserDefaults.standard.bool(forKey: key) }
    func accept() { update(true) }
    func withdraw() { update(false) }
    private func update(_ value: Bool) {
        defaults.set(value, forKey: Self.key)
        allowed = value
        NotificationCenter.default.post(name: Self.changed, object: self)
    }
}

struct CameraSDKConsentView: View {
    @ObservedObject var consent = IOSSDKConsent.shared
    var onAccept: () -> Void = {}
    @State private var declined = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Camera counting and SDK metrics").font(.headline)
            Text("Camera counting uses Google MediaPipe. Images and pose processing stay on your phone. Google's SDK sends API performance and usage metrics to Google. Allow this to enable camera counting.")
            Text("This is optional. Without consent, you can still edit routines and view, export or delete history. You can withdraw consent in Settings to stop further camera SDK use. Withdrawal cannot retract metrics already sent to Google.")
            Link("Google MediaPipe privacy notice", destination: URL(string: "https://github.com/google-ai-edge/mediapipe#privacy-notice")!)
            Link("Workout Coach privacy policy", destination: URL(string: "https://github.com/kiasolutionsnz/workout-coach/blob/main/PRIVACY.md")!)
            Button("Allow SDK metrics and camera counting") { consent.accept(); declined = false; onAccept() }.buttonStyle(.borderedProminent)
            Button("Not now") { declined = true }.buttonStyle(.borderless)
            if declined { Text("Camera counting remains off. You can go back to your routines or history.").accessibilityIdentifier("sdk-consent-declined") }
        }.accessibilityIdentifier("sdk-consent-disclosure")
    }
}

struct CameraSDKPrivacyView: View {
    @ObservedObject private var consent = IOSSDKConsent.shared
    var body: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:16) {
                if consent.allowed {
                    Text("Camera SDK metrics consent is on.").font(.headline)
                    Text("Google MediaPipe sends API usage and performance metrics to Google. Images and pose processing stay on your phone.")
                    Button("Withdraw camera SDK consent",role:.destructive){consent.withdraw()}.buttonStyle(.bordered)
                    Text("Withdrawal stops further camera SDK use. It cannot retract metrics already sent to Google or guarantee cancellation of reporting already queued by the SDK.")
                    Link("Workout Coach privacy policy",destination:URL(string:"https://github.com/kiasolutionsnz/workout-coach/blob/main/PRIVACY.md")!)
                } else { CameraSDKConsentView() }
            }.frame(maxWidth:.infinity,alignment:.leading).padding()
        }.navigationTitle("Camera SDK privacy")
    }
}
