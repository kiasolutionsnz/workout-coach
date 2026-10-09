import SwiftUI
import WorkoutCore

@main
struct WorkoutCoachApp: App {
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--reset-sdk-consent") { IOSSDKConsent.shared.withdraw() }
        #endif
    }
    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--workout-fixture") {
                NavigationStack { WorkoutView(routine:EditableRoutine(id:"6767256b-8d3a-4702-8f1f-712a19960348",name:"Synthetic two holds",blocks:[EditableBlock(exercise:"PLANK",sets:2,target:1,restSeconds:1,limbMode:"BILATERAL",holdTiming:"ELAPSED")]),voice:false,frontCamera:true) }
            }else{CoachHomeView()}
#else
            CoachHomeView()
#endif
        }
    }
}
