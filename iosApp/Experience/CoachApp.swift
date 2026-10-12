import SwiftUI
import WorkoutCore

private enum VoiceRoute:Hashable { case history, settings, routine(String), workout(String) }

struct CoachHomeView:View {
    @StateObject private var account = IOSAccountModel()
    var body:some View {CoachHomeContent(account:account).id(account.namespace)}
}
struct CoachHomeContent: View {
    @ObservedObject var account:IOSAccountModel
    @StateObject private var model = IOSAppModel()
    @State private var newRoutine = false
    @StateObject private var voiceControl = IOSVoiceControl()
    @State private var path:[VoiceRoute] = []
    @State private var voiceNotice = ""
    @Environment(\.scenePhase) private var scenePhase
    var body:some View {
        NavigationStack(path:$path) {
            Group {
                if model.loading { ProgressView("Loading routines") }
                else { List {
                    Section { Text("Choose a routine or make one your own.") }
                    if let error = model.error { Section { Text(error).foregroundStyle(.red);Button("Try again"){model.load()} } }
                    Section("Your routines") {
                        ForEach(model.routines,id:\.id) { routine in
                            NavigationLink(value:VoiceRoute.routine(routine.id)) {
                                VStack(alignment:.leading,spacing:6) { Text(routine.name).font(.headline);Text("\(routine.blocks.count) exercises").font(.subheadline).foregroundStyle(.secondary) }
                            }
                        }
                    }
                } }
            }.navigationTitle("Workout Coach")
                .toolbar { ToolbarItem(placement:.navigationBarLeading) { Button("History"){path.append(.history)} };ToolbarItem(placement:.navigationBarTrailing) { Button("Settings"){path.append(.settings)} } }
                .safeAreaInset(edge:.bottom) {
                    if !model.loading { VStack { VoiceControlPanel(voice:voiceControl);Text("Navigation: Coach open routines, open history, open settings, back. Select a routine, then say Coach start workout.").font(.caption);if !voiceNotice.isEmpty { Text(voiceNotice) };Button("New routine"){model.error = nil;newRoutine = true}.buttonStyle(.borderedProminent).frame(maxWidth:.infinity).padding().background(.regularMaterial).disabled(model.error != nil) } }
                }
                .sheet(isPresented:$newRoutine) { RoutineEditorView(routine:nil,model:model) }
                .navigationDestination(for:VoiceRoute.self){route in
                    switch route {
                    case .history: HistoryView().safeAreaInset(edge:.bottom){VoiceControlPanel(voice:voiceControl).padding()}
                    case .settings: CoachSettingsView(account:account,model:model).safeAreaInset(edge:.bottom){VoiceControlPanel(voice:voiceControl).padding()}
                    case .routine(let id): if let routine = model.routines.first(where:{$0.id == id}) { RoutineDetailView(routine:routine,model:model,onLeaveForWorkout:{voiceControl.disable()}).safeAreaInset(edge:.bottom){VoiceControlPanel(voice:voiceControl).padding()} }
                    case .workout(let id): if let routine = model.routines.first(where:{$0.id == id}) { WorkoutView(routine:routine,voice:model.voice,frontCamera:model.frontCamera) }
                    }
                }
                .onAppear { voiceControl.onCommand = navigate }
                .onChange(of:newRoutine){if $0 { voiceControl.disable() }}
                .onChange(of:scenePhase){if $0 != .active { voiceControl.disable() }}
                .onChange(of:path){_ in if case .workout(_)? = path.last { voiceControl.disable() } else { voiceControl.onCommand = navigate } }
        }
    }
    private func navigate(_ action:VoiceAction) {
        guard !newRoutine, !model.loading else { return }
        switch action {
        case .history: path = [.history]
        case .settings: path = [.settings]
        case .routines: path = []
        case .back: if !path.isEmpty { path.removeLast() }
        case .start: if case .routine(let id)? = path.last { path.append(.workout(id)) } else { voiceNotice = "Select a routine before starting." }
        default: voiceNotice = "Workout commands are available inside a workout."
        }
    }

}

struct RoutineDetailView: View {
    let routine:EditableRoutine
    @ObservedObject var model:IOSAppModel
    var onLeaveForWorkout:()->Void = {}
    @State private var editing = false
    var body:some View {
        List {
            Section { ForEach(Array(routine.blocks.enumerated()),id:\.offset) { _, block in
                VStack(alignment:.leading) { Text(exerciseLabel(block.exercise)).font(.headline);Text("\(block.sets) sets · \(block.target) \(block.exercise == "PLANK" ? "seconds" : "reps") · \(block.restSeconds)s rest").font(.subheadline).foregroundStyle(.secondary) }
            } }
            Section { NavigationLink("Prepare workout"){WorkoutView(routine:routine,voice:model.voice,frontCamera:model.frontCamera)}.buttonStyle(.borderedProminent).simultaneousGesture(TapGesture().onEnded{onLeaveForWorkout()});Button("Edit routine"){onLeaveForWorkout();model.error = nil;editing = true} }
        }.navigationTitle(routine.name)
            .sheet(isPresented:$editing){RoutineEditorView(routine:routine,model:model)}
    }
}

struct CoachSettingsView:View {
    @ObservedObject private var consent = IOSSDKConsent.shared
    @ObservedObject var account:IOSAccountModel
    @ObservedObject var model:IOSAppModel
    var body:some View {
        Form {
            Section {
                Toggle("Spoken cues",isOn:Binding(get:{model.voice},set:{model.saveSettings(voice:$0,frontCamera:model.frontCamera)}))
                Toggle("Use front camera",isOn:Binding(get:{model.frontCamera},set:{model.saveSettings(voice:model.voice,frontCamera:$0)}))
            }
            Section { Text(account.accountsEnabled ? "Workouts and camera processing work on your phone. An account is optional." : "Offline beta: workouts and camera processing stay on your phone. Accounts and cloud sync are not included.") }
            if account.accountsEnabled { AccountView(model:account) }
            Section("Camera SDK privacy") {
                Text(consent.allowed ? "Camera SDK metrics consent is on." : "Camera SDK metrics consent is off.")
                NavigationLink("Camera SDK privacy and consent"){CameraSDKPrivacyView()}
                if consent.allowed { Button("Withdraw camera SDK consent",role:.destructive){consent.withdraw()} }
            }
            if let error = model.error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("Settings")
    }
}

struct RoutineEditorView:View {
    @ObservedObject var model:IOSAppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name:String
    @State private var blocks:[RoutineBlockDraft]
    @State private var discard = false
    @FocusState private var nameFocused:Bool
    private let id:String
    init(routine:EditableRoutine?,model:IOSAppModel) { self.model = model;id = routine?.id ?? UUID().uuidString.lowercased();_name = State(initialValue:routine?.name ?? "");_blocks = State(initialValue:routine?.blocks.map(RoutineBlockDraft.init) ?? [RoutineBlockDraft()]) }
    var body:some View {
        NavigationStack {
            Form {
                Section { TextField("Routine name",text:$name).accessibilityIdentifier("routine-name").focused($nameFocused).submitLabel(.done).onSubmit{nameFocused = false}.onChange(of:name){_ in model.error = nil} }
                if let error = model.error { Section { Text(error).foregroundStyle(.red).accessibilityIdentifier("form-error") } }
                ForEach($blocks) { $block in
                    Section {
                        Picker("Exercise",selection:$block.exercise) { ForEach(["SQUAT","PUSH_UP","CURL","LUNGE","SHOULDER_PRESS","PLANK"],id:\.self){Text(exerciseLabel($0)).tag($0)} }
                            .onChange(of:block.exercise){exercise in block.target = exercise == "PLANK" ? 30:10;block.limb = exercise == "LUNGE" ? "ALTERNATING":"BILATERAL"}
                        Stepper("Sets: \(block.sets)",value:$block.sets,in:1...20)
                        Stepper("\(block.exercise == "PLANK" ? "Seconds" : "Reps"): \(block.target)",value:$block.target,in:1...(block.exercise == "PLANK" ? 3600:100))
                        Stepper("Rest: \(block.rest)s",value:$block.rest,in:0...3600)
                        if block.exercise == "CURL" || block.exercise == "LUNGE" {
                            Picker("Count",selection:$block.limb) { ForEach(block.exercise == "LUNGE" ? ["ALTERNATING","LEFT","RIGHT"] : ["BILATERAL","ALTERNATING","LEFT","RIGHT"],id:\.self){Text($0.capitalized).tag($0)} }
                            Text(block.exercise == "LUNGE" ? "Each completed leg movement counts as one rep." : "Bilateral pairs both arms. Alternating counts each arm separately.").font(.caption)
                        }
                        if block.exercise == "PLANK" {
                            Toggle("Count valid hold time",isOn:Binding(get:{block.timing == "VALID_HOLD"},set:{block.timing = $0 ? "VALID_HOLD":"ELAPSED"}))
                            Text("When off, the timer uses elapsed time.").font(.caption).foregroundStyle(.secondary)
                        }
                        Button("Remove exercise",role:.destructive){blocks.removeAll{$0.id == block.id}}.disabled(blocks.count == 1)
                    }
                }
                Section { Button("Add exercise"){blocks.append(RoutineBlockDraft())}.disabled(blocks.count >= 50) }
            }.navigationTitle("Edit routine")
                .safeAreaInset(edge:.bottom) { Button(model.saving ? "Saving…":"Save routine") { nameFocused = false;model.save(EditableRoutine(id:id,name:name,blocks:blocks.map(\.editable))){dismiss()} }.buttonStyle(.borderedProminent).frame(maxWidth:.infinity).padding().background(.regularMaterial).disabled(model.saving) }
                .toolbar { ToolbarItem(placement:.cancellationAction){Button("Cancel"){discard = true}.disabled(model.saving)} }
                .interactiveDismissDisabled()
                .confirmationDialog("Discard changes?",isPresented:$discard,titleVisibility:.visible){Button("Discard",role:.destructive){model.error = nil;dismiss()};Button("Keep editing",role:.cancel){}} message:{Text("Your saved routine will stay as it is.")}
        }
    }
}

