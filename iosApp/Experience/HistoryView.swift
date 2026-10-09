import SwiftUI
import UniformTypeIdentifiers
import WorkoutCore

struct WorkoutExportDocument:FileDocument {
    static var readableContentTypes:[UTType]{[.json]}
    var text:String
    init(text:String){self.text = text}
    init(configuration:ReadConfiguration)throws{text = String(data:configuration.file.regularFileContents ?? Data(),encoding:.utf8) ?? ""}
    func fileWrapper(configuration:WriteConfiguration)throws->FileWrapper{FileWrapper(regularFileWithContents:Data(text.utf8))}
}
struct HistoryView:View {
    @StateObject private var model = IOSHistoryModel()
    var body:some View {
        List {
            if model.loading {ProgressView("Loading history")}
            if let error = model.error {Text(error).foregroundStyle(.red);Button("Try again"){model.reload()}}
            if !model.loading && model.entries.isEmpty {Text("Your completed and interrupted workouts will appear here.")}
            ForEach(model.entries,id:\.checkpoint.id){entry in
                NavigationLink {HistoryDetailView(entry:entry,model:model)}label:{
                    VStack(alignment:.leading,spacing:6){Text(entry.checkpoint.routineName).font(.headline);Text(Date(timeIntervalSince1970:Double(entry.checkpoint.startedEpochMillis)/1000),style:.date);Text("\(entry.status) · \(entry.sets.count) recorded sets · \(entry.accepted) reps").font(.subheadline)}
                }
            }
        }.navigationTitle("History").onAppear{model.reload()}
    }
}
struct HistoryDetailView:View {
    let entry:HistoryEntry
    @ObservedObject var model:IOSHistoryModel
    @State private var deleting = false
    @State private var exporting = false
    @State private var document = WorkoutExportDocument(text:"")
    @Environment(\.dismiss) private var dismiss
    var body:some View {
        List {
            Section {Text(entry.status);Text("\(entry.accepted) accepted · \(entry.partial) partial reps · \(entry.activeMillis/1000)s recorded active time")}
            Section("Recorded sets") {
                ForEach(entry.sets,id:\.ordinal){set in
                    VStack(alignment:.leading,spacing:6){
                        Text("Set \(set.ordinal+1) · \(exerciseLabel(set.exercise.name))").font(.headline)
                        Text(set.reachedGoal ? "Goal reached" : "Ended incomplete")
                        Text("\(set.accepted) accepted · \(set.partial) partial · \(set.elapsedMillis/1000)s active")
                        if set.exercise == .plank {
                            Text("\(set.target.map { "\($0) seconds" } ?? "Target not recorded") · \(set.holdTiming?.name.lowercased().replacingOccurrences(of:"_",with:" ") ?? "Timing mode not recorded")")
                            Text("\(set.validHoldMillis/1000)s valid posture observed")
                        }
                    }
                }
            }
            if let error = model.error {Text(error).foregroundStyle(.red)}
            Section {
                Button("Export JSON"){model.export(entry.checkpoint.id){text in document = WorkoutExportDocument(text:text);exporting = true}}.disabled(model.busy)
                Button("Delete workout",role:.destructive){deleting = true}.disabled(model.busy)
            }
        }.navigationTitle(entry.checkpoint.routineName)
        .alert("Delete this workout?",isPresented:$deleting){Button("Delete",role:.destructive){model.delete(entry.checkpoint.id){dismiss()}};Button("Cancel",role:.cancel){} }message:{Text("Remove this workout from this phone. Your routines and other workouts stay saved.")}
        .fileExporter(isPresented:$exporting,document:document,contentType:.json,defaultFilename:"workout-\(entry.checkpoint.id)"){result in if case .failure(let error) = result {let failure = error as NSError;if failure.domain != NSCocoaErrorDomain || failure.code != NSUserCancelledError {model.exportFailed()}}}
    }
}
