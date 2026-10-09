import Foundation

enum IOSBuildFeatures {
    static var offlineBeta: Bool {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--offline-beta") { return true }
        #endif
        let value = Bundle.main.object(forInfoDictionaryKey: "WORKOUT_OFFLINE_BETA")
        return (value as? Bool) == true || ["YES", "true", "1"].contains(value as? String ?? "")
    }
}
