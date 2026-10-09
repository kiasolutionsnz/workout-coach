import XCTest
import Foundation
import WorkoutCore
final class HistoryBridgeTests:XCTestCase {
    func testNativeExportIsValidJSONAndDeletionIsScoped()throws {
        let scope = "account:\(UUID().uuidString.lowercased())"
        let repo = try IosDatabaseKt.iosRepository(namespace:scope)
        defer {try? repo.closeStorage()}
        let id = UUID().uuidString.lowercased()
        let checkpoint = SessionCheckpoint(id:id,routineName:"Māori \"quoted\"\nline",startedEpochMillis:1000,phase:.completed,blockIndex:0,setIndex:0,remainingMillis:0)
        let record = StoredSet(ordinal:0,exercise:.plank,accepted:0,partial:0,elapsedMillis:30000,validHoldMillis:29000,reachedGoal:true,reps:[],target:KotlinInt(int:29),holdTiming:.validHold)
        try repo.completeSet(session:checkpoint,set:record)
        let history = HistoryService(repository:repo)
        let text = try history.export(id:id)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with:Data(text.utf8)) as? [String:Any])
        XCTAssertEqual(json["schemaVersion"] as? Int,1)
        let workout = try XCTUnwrap(json["workout"] as? [String:Any])
        XCTAssertEqual(workout["routine"] as? String,checkpoint.routineName)
        let sets = try XCTUnwrap(workout["sets"] as? [[String:Any]])
        XCTAssertEqual(sets[0]["target"] as? Int,29);XCTAssertEqual(sets[0]["holdTiming"] as? String,"VALID_HOLD")
        try history.delete(id:id);XCTAssertTrue(try history.entries().isEmpty)
    }
}
