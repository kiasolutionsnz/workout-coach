import XCTest
import WorkoutCore
@testable import WorkoutCoach
final class SpeechApiTests:XCTestCase {
    func testNativeSpeechAPIAndClosureWithoutMicrophone() {
        let speaker = IOSSpeaker()
        let accepted = speaker.speak(cue:SpeechCue(id:"qualification",text:"Ready",priority:.timing,expiresAtMillis:10000))
        if !speaker.available { XCTAssertFalse(accepted) }
        speaker.stop();speaker.close();speaker.close()
        XCTAssertFalse(speaker.speak(cue:SpeechCue(id:"closed",text:"Ready",priority:.timing,expiresAtMillis:10000)))
    }
}
