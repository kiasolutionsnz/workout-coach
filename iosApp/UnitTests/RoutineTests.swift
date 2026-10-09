import XCTest
import WorkoutCore
@testable import WorkoutCoach
final class RoutineBridgeTests:XCTestCase {
    func testInvalidRoutineIsBridgedAsErrorAndDoesNotAbortSwift() throws {
        let service = try IosDatabaseKt.iosRoutineService(namespace:"guest")
        defer {try? service.close()}
        let block = EditableBlock(exercise:"PLANK",sets:1,target:0,restSeconds:30,limbMode:"BILATERAL",holdTiming:"ELAPSED")
        let draft = EditableRoutine(id:UUID().uuidString.lowercased(),name:"Invalid",blocks:[block])
        XCTAssertNotNil(service.validationMessage(routine:draft))
        XCTAssertThrowsError(try service.save(routine:draft))
    }
}
