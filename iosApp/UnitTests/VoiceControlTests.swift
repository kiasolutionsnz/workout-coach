import XCTest
import WorkoutCore
@testable import WorkoutCoach

final class VoiceControlTests:XCTestCase {
    func testVoiceControlIsOptInAndDisablingDoesNotStartRecording(){
        let voice = IOSVoiceControl()
        XCTAssertFalse(voice.enabled)
        XCTAssertFalse(IOSVoiceControl.capturing)
        voice.disable()
        XCTAssertEqual(voice.status,"Voice control is off")
        XCTAssertFalse(voice.enabled)
    }
    func testSharedParserRejectsSpokenCuesAndConfirmationExpires(){
        XCTAssertNil(VoiceCommandsKt.parseVoiceCommand(text:"Stop. Set complete."))
        XCTAssertEqual(VoiceCommandsKt.parseVoiceCommand(text:"Coach pause"),VoiceAction.pause)
        let confirmation = VoiceEndConfirmation()
        XCTAssertFalse(confirmation.confirm(nowMillis:0))
        confirmation.request(nowMillis:100)
        XCTAssertFalse(confirmation.confirm(nowMillis:8101))
    }
}
