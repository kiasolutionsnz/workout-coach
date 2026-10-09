import SwiftUI
import AVFoundation
import WorkoutCore

struct WorkoutView:View {
    @ObservedObject private var consent = IOSSDKConsent.shared
    @StateObject private var model:IOSWorkoutModel
    @StateObject private var camera:IOSCameraController
    @State private var permission = AVCaptureDevice.authorizationStatus(for:.video)
    @State private var ending = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    init(routine:EditableRoutine,voice:Bool,frontCamera:Bool) {
        let model = IOSWorkoutModel(routine:routine,voice:voice)
        _model = StateObject(wrappedValue:model)
        _camera = StateObject(wrappedValue:IOSCameraController(clock:{model.clock.nowMillis()},onPose:{[weak model] frame in model?.pose(frame)},onInterruption:{[weak model] in model?.pause(true)},front:frontCamera))
    }
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:16) {
                if let run = model.run {
                    let state = run.state
                    Text(run.exerciseName).font(.title)
                    Text("Set \(state.setIndex+1) of \(run.block.sets) · \(state.phase.name.lowercased().replacingOccurrences(of:"_",with:" "))")
                    Text(progress(run)).font(.largeTitle).accessibilityIdentifier("workout-progress")
                    if state.phase == .resting {Text("Next: \(run.nextLabel)")}
                    Text("\(state.partial) partial reps · \(run.movement.lowercased())")
                    if state.phase != .completed && state.phase != .cancelled {
                        Text(run.placement)
                        if model.fixture { Text("Synthetic pose stream · agent verification") }else if !consent.allowed {
                            CameraSDKConsentView(onAccept:refreshPermission)
                        }else if permission == .authorized {
                            NativeCameraPreview(session:camera.session,mirrored:camera.previewMirrored).frame(height:200).overlay {
                                Canvas { context,size in
                                    if let frame = model.frame, model.clock.nowMillis()-frame.capturedAtMillis <= 250 {
                                        let width = Double(frame.transform.orientedWidth),height = Double(frame.transform.orientedHeight)
                                        let scale = min(size.width/width,size.height/height),w = width*scale,h = height*scale
                                        func screen(_ p:Point2)->CGPoint{CGPoint(x:(camera.previewMirrored ? 1-p.x : p.x)*w+(size.width-w)/2,y:p.y*w+(size.height-h)/2)}
                                        for line in PoseOverlayKt.poseLines(frame:frame) {var path = Path();path.move(to:screen(line.start));path.addLine(to:screen(line.end));context.stroke(path,with:.color(.green),lineWidth:2)}
                                        for (_,landmark) in frame.joints where landmark.confidence >= 0.65 {
                                            let p = frame.transform.geometry(point:landmark.point)
                                            let x = (camera.previewMirrored ? 1-p.x : p.x)*w+(size.width-w)/2,y = p.y*w+(size.height-h)/2
                                            context.fill(Path(ellipseIn:CGRect(x:x-4,y:y-4,width:8,height:8)),with:.color(.green))
                                        }
                                    }
                                }.accessibilityHidden(true)
                            }
                            Text(camera.status == .unavailable ? "Camera unavailable. Return and try again." : run.reliable ? run.cue : "Tracking paused. \(run.cue)")
                        }else {
                            Text("Enable camera access to check your position.")
                            Button(permission == .notDetermined ? "Enable camera" : "Open Settings") {
                                if permission == .notDetermined { AVCaptureDevice.requestAccess(for:.video){_ in DispatchQueue.main.async { refreshPermission() } } }
                                else if let url = URL(string:UIApplication.openSettingsURLString){UIApplication.shared.open(url)}
                            }.buttonStyle(.borderedProminent)
                        }
                        if model.voiceEnabled && !model.voiceAvailable { Text("Spoken cues unavailable. Follow the on-screen cues.") }
                    }else { Text(model.saving ? "Saving…" : model.error == nil ? "Completed sets saved on this phone." : "Saving needs attention.")
                        ForEach(run.savedSets,id:\.ordinal){set in Text("Set \(set.ordinal+1) · \(set.accepted) accepted · \(set.partial) partial · \(set.elapsedMillis/1000)s active · \(set.reachedGoal ? "goal reached" : "incomplete")")}
                    }
                    if let error = model.error { Text(error).foregroundStyle(.red);Button("Retry save"){model.retry()} }
                }else { ProgressView("Preparing workout") }
            }.frame(maxWidth:.infinity,alignment:.leading).padding()
        }.navigationTitle("Workout").navigationBarBackButtonHidden()
        .safeAreaInset(edge:.bottom) {
            if let run = model.run {
                VStack(spacing:8) {
                    switch run.state.phase {
                    case .preparing:Button("Begin countdown"){model.begin()}.disabled(!run.reliable || model.saving)
                    case .paused,.interrupted:Button("Resume"){model.resume()}.disabled(!run.reliable)
                    case .resting:Button("Skip rest"){model.skipRest()}
                    case .setComplete:Text(model.saving ? "Saving set…" : "Set complete")
                    case .completed,.cancelled:Button("Done"){dismiss()}.disabled(model.saving || model.error != nil)
                    default:Button("Pause"){model.pause()}
                    }
                    if run.state.phase != .completed && run.state.phase != .cancelled {
                        if run.state.phase == .activeSet || run.state.phase == .paused || run.state.phase == .interrupted { Button("End set incomplete"){model.endSet()}.buttonStyle(.bordered) }
                        Button("End workout",role:.destructive){ending = true}.buttonStyle(.borderless)
                    }
                }.buttonStyle(.borderedProminent).frame(maxWidth:.infinity).padding().background(.regularMaterial)
            }else { Button("Back"){dismiss()}.padding() }
        }
        .confirmationDialog("End workout?",isPresented:$ending,titleVisibility:.visible){Button("End workout",role:.destructive){model.cancel()};Button("Cancel",role:.cancel){} }message:{Text("Completed sets stay saved. The current set will end incomplete.")}
        .onAppear { refreshPermission() }.onDisappear { camera.close();model.close() }
        .onChange(of:consent.allowed){allowed in if allowed { refreshPermission() }else if !model.fixture { camera.stopForConsent();model.pause(true) } }
        .onChange(of:scenePhase){phase in if phase == .active { refreshPermission() }else { if !model.fixture {camera.suspend()};model.pause(true) } }
    }
    private func progress(_ run:WorkoutSession)->String {
        switch run.state.phase {
        case .countdown:return "\((run.state.remainingMillis+999)/1000)"
        case .resting:return "Rest · \((run.state.remainingMillis+999)/1000) seconds"
        case .completed:return "Workout complete"
        case .cancelled:return "Workout ended"
        default:return run.progressText
        }
    }
    private func refreshPermission(){permission = AVCaptureDevice.authorizationStatus(for:.video);if permission == .authorized && consent.allowed && !model.fixture {camera.start()}}
}
