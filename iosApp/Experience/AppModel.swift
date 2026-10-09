import Foundation
import Combine
import WorkoutCore
enum AppStorageError: Error { case unavailable }

final class IOSAppModel: ObservableObject {
    @Published private(set) var routines: [EditableRoutine] = []
    @Published private(set) var loading = true
    @Published private(set) var saving = false
    @Published private(set) var voice = true
    @Published private(set) var frontCamera = true
    @Published var error: String?
    private let namespace = IOSAccountScope.namespace
    private let storage = DispatchQueue(label:"workout.storage")
    private var service: RoutineService?
    init() { load() }
    func load() {
        storage.async { [self] in
            do {
                if service == nil { service = try IosDatabaseKt.iosRoutineService(namespace:namespace) }
                let service = service!
                try service.seedIfEmpty()
                let routines = try service.routines();let settings = try service.settings()
                DispatchQueue.main.async { [self] in self.routines = routines;voice = settings.voice;frontCamera = settings.frontCamera;loading = false;error = nil }
            } catch { DispatchQueue.main.async { [self] in loading = false;self.error = "Couldn’t open saved workouts. Your data has been kept. Try again." } }
        }
    }
    func save(_ routine:EditableRoutine, onSuccess:@escaping ()->Void) {
        guard !saving else { return }
        if let message = service?.validationMessage(routine:routine) { error = message;return }
        saving = true;error = nil
        storage.async { [self] in
            do {
                guard let service else { throw AppStorageError.unavailable }
                try service.save(routine:routine)
                let routines = try service.routines()
                DispatchQueue.main.async { [self] in self.routines = routines;saving = false;error = nil;onSuccess() }
            } catch { DispatchQueue.main.async { [self] in saving = false;self.error = "Couldn’t save this routine. Your changes are still here. Try again." } }
        }
    }
    func saveSettings(voice:Bool,frontCamera:Bool) {
        self.voice = voice;self.frontCamera = frontCamera;error = nil
        storage.async { [self] in
            do {
                guard let service else { throw AppStorageError.unavailable }
                try service.saveSettings(settings:CoachSettings(voice:voice,frontCamera:frontCamera))
            } catch { DispatchQueue.main.async { self.error = "Couldn’t save settings. Try again." } }
        }
    }
    deinit { if let service { storage.async { try? service.close() } } }
}

struct RoutineBlockDraft: Identifiable {
    var id = UUID()
    var exercise = "SQUAT"
    var sets = 1
    var target = 10
    var rest = 30
    var limb = "BILATERAL"
    var timing = "ELAPSED"
    init() {}
    init(_ block:EditableBlock) { exercise = block.exercise;sets = Int(block.sets);target = Int(block.target);rest = Int(block.restSeconds);limb = block.limbMode;timing = block.holdTiming }
    var editable:EditableBlock { EditableBlock(exercise:exercise,sets:Int32(sets),target:Int32(target),restSeconds:Int32(rest),limbMode:limb,holdTiming:timing) }
}
func exerciseLabel(_ id:String)->String {
    switch id {case "SQUAT":return "Squat";case "PUSH_UP":return "Push-up";case "CURL":return "Bicep curl";case "LUNGE":return "Lunge";case "SHOULDER_PRESS":return "Shoulder press";default:return "Plank"}
}
