package tech.kiasolutions.workoutcoach.core.speech
import kotlin.test.*

class VoiceCommandsTest {
    @Test fun confirmationExpiresAndCannotBeReplayed(){
        val gate=VoiceEndConfirmation();assertFalse(gate.confirm(0))
        gate.request(100);assertTrue(gate.confirm(200));assertFalse(gate.confirm(201))
        gate.request(300);assertFalse(gate.confirm(8301))
        gate.request(9000);gate.cancel();assertFalse(gate.confirm(9001))
    }
    @Test fun requiresExplicitPrefixAndEntireCommand(){
        assertEquals(VoiceAction.START,parseVoiceCommand("Coach, start workout!"))
        assertEquals(VoiceAction.PAUSE,parseVoiceCommand("coach stop reps"))
        assertEquals(VoiceAction.HISTORY,parseVoiceCommand("Coach open history"))
        listOf("Start", "Stop. Set complete.", "please coach pause", "coach start and end workout", "coach", "coach delete history").forEach{assertNull(parseVoiceCommand(it))}
    }
    @Test fun endingRequiresSeparateConfirmationPhrase(){
        assertEquals(VoiceAction.REQUEST_END,parseVoiceCommand("coach end workout"))
        assertEquals(VoiceAction.CONFIRM_END,parseVoiceCommand("coach confirm end"))
        assertEquals(VoiceAction.CANCEL_END,parseVoiceCommand("coach keep going"))
    }
}
