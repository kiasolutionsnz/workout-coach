import Foundation
import Combine
import WorkoutCore

final class IOSWorkoutModel:ObservableObject {
    let clock:WorkoutClock
    let fixture:Bool
    private var fixtureSequence:Int64 = 0
    @Published private(set) var frame:PoseFrame?
    @Published private(set) var run:WorkoutSession?
    @Published private(set) var revision = 0
    @Published private(set) var saving = false
    @Published private(set) var error:String?
    @Published private(set) var voiceAvailable = true
    @Published private(set) var voiceEnabled = true
    private let namespace = IOSAccountScope.namespace
    private let storage = DispatchQueue(label:"workout.session.storage")
    private var repository:WorkoutRepository?
    private var timer:Timer?
    private var speaker:IOSSpeaker?
    private var speech:SpeechCoordinator?
    private var lastPhase:WorkoutPhase?
    private var closed = false
    init(routine:EditableRoutine,voice:Bool) {
        #if DEBUG
        fixture = ProcessInfo.processInfo.arguments.contains("--workout-fixture")
#else
        fixture = false
#endif
        clock = fixture ? ManualWorkoutClock(initialMillis:0) : MonotonicWorkoutClock()
        voiceEnabled = voice
        storage.async { [weak self] in
            guard let self else { return }
            do {
                let repo = try IosDatabaseKt.iosRepository(namespace:namespace);self.repository = repo
                let plan = try RoutineService(repository:repo).workoutPlan(routine:routine)
                DispatchQueue.main.async { [weak self] in
                    guard let self,!self.closed else { return }
                    let run = WorkoutSession(plan:plan,id:UUID().uuidString.lowercased(),startedEpochMillis:Int64(Date().timeIntervalSince1970*1000),clock:self.clock)
                    self.run = run
                    let speaker = IOSSpeaker();self.speaker = speaker;self.voiceAvailable = speaker.available
                    self.speech = SpeechCoordinator(workoutId:run.id,clock:self.clock,sink:speaker)
                    speaker.onFinished = {[weak self] id in self?.speech?.completed(id:id)}
                    speaker.onInterruption = {[weak self] interrupted in if interrupted { self?.pause(true) }}
                    if !voice { self.speech?.pause() }
                    self.update()
                    self.timer = Timer.scheduledTimer(withTimeInterval:0.05,repeats:true){[weak self] _ in self?.pulse()}
                }
            }catch { DispatchQueue.main.async { self.error = "Couldn’t open this workout. Your saved data has been kept." } }
        }
    }
    private func pulse() {
        if fixture, let clock = clock as? ManualWorkoutClock {
            clock.advanceBy(millis:100)
            let joints:[Joint:Landmark] = [.leftShoulder:Landmark(point:Point2(x:0.2,y:0.4),confidence:1),.leftElbow:Landmark(point:Point2(x:0.2,y:0.55),confidence:1),.leftWrist:Landmark(point:Point2(x:0.35,y:0.55),confidence:1),.leftHip:Landmark(point:Point2(x:0.5,y:0.4),confidence:1),.leftAnkle:Landmark(point:Point2(x:0.9,y:0.4),confidence:1)]
            pose(PoseFrame(sequence:fixtureSequence,capturedAtMillis:clock.nowMillis(),transform:ImageTransform(width:100,height:100,rotation:.none,mirrored:false),joints:joints));fixtureSequence += 1
        }
        run?.tick();speech?.poll();update()
    }
    func pose(_ frame:PoseFrame) { self.frame = frame;run?.framing(frame:frame);update() }
    func begin() { run?.begin();update() }
    func pause(_ interrupted:Bool = false) { run?.pause(interrupted:interrupted);speech?.pause();update() }
    func resume() { run?.resume();if voiceEnabled { speech?.resume() };update() }
    func endSet() { run?.endSet();update() }
    func skipRest() { run?.skipRest();update() }
    func cancel() { run?.endSet();run?.cancel();speech?.pause();update() }
    func retry() { error = nil;lastPhase = nil;update() }
    private func update() {
        guard let run,!closed else { return }
        speech?.configureExercise(label:run.exerciseName)
        run.drainEvents().forEach { speech?.accept(event:$0) }
        let phase = run.state.phase
        if !saving && error == nil && (run.pendingSet != nil || phase != lastPhase) {
            saving = true;let set = run.pendingSet;let checkpoint = run.checkpoint()
            storage.async { [weak self] in
                guard let self else { return }
                do {
                    guard let repo = self.repository else { throw AppStorageError.unavailable }
                    if let set { try repo.completeSet(session:checkpoint,set:set) }else{try repo.checkpoint(session:checkpoint)}
                    DispatchQueue.main.async { [weak self] in
                        guard let self else { return };self.lastPhase = checkpoint.phase;self.saving = false
                        if set != nil { run.acknowledgeSet() };self.update()
                    }
                }catch { DispatchQueue.main.async { self.saving = false;self.error = "Couldn’t save workout progress. Keep this screen open and retry.";self.run?.pause(interrupted:true);self.speech?.pause();self.revision += 1 } }
            }
        }
        voiceAvailable = (speaker?.available ?? false) && (speech?.available ?? true)
        revision += 1
    }
    func close() { guard !closed else { return };pause(true);closed = true;timer?.invalidate();timer = nil;speaker?.close();storage.async { [self] in try? repository?.closeStorage();repository = nil } }
    deinit { timer?.invalidate();if let repo = repository {storage.async {try? repo.closeStorage()}} }
}

