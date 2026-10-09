import Foundation
import Combine
import WorkoutCore

final class IOSHistoryModel:ObservableObject {
    @Published private(set) var entries:[HistoryEntry] = []
    @Published private(set) var loading = true
    @Published private(set) var busy = false
    @Published private(set) var error:String?
    private let namespace = IOSAccountScope.namespace
    private let storage = DispatchQueue(label:"workout.history.storage")
    private var service:HistoryService?
    init(){reload()}
    func reload(){storage.async {[weak self] in
        guard let self else{return}
        do {
            if service == nil {service = HistoryService(repository:try IosDatabaseKt.iosRepository(namespace:namespace))}
            let entries = try service!.entries()
            DispatchQueue.main.async {self.entries = entries;self.loading = false;self.error = nil}
        }catch {DispatchQueue.main.async {self.loading = false;self.error = "Couldn’t read history. Your data has been kept."}}
    }}
    func delete(_ id:String,onDone:@escaping ()->Void){guard !busy else{return};busy = true
        storage.async {[weak self] in
            guard let self else{return}
            do {guard let service else{throw AppStorageError.unavailable};try service.delete(id:id)
                let entries = try service.entries();DispatchQueue.main.async {self.entries = entries;self.busy = false;self.error = nil;onDone()}
            }catch {DispatchQueue.main.async {self.busy = false;self.error = "Couldn’t delete this workout. Try again."}}
        }
    }
    func export(_ id:String,onReady:@escaping (String)->Void){guard !busy else{return};busy = true
        storage.async {[weak self] in
            guard let self else{return}
            do {guard let service else{throw AppStorageError.unavailable};let json = try service.export(id:id)
                DispatchQueue.main.async {self.busy = false;self.error = nil;onReady(json)}
            }catch {DispatchQueue.main.async {self.busy = false;self.error = "Couldn’t export this workout. Try again."}}
        }
    }
    func exportFailed(){error = "Couldn’t export this workout. Choose a destination and try again."}
    deinit {if let service {storage.async {try? service.close()}}}
}
