package tech.kiasolutions.workoutcoach.core.speech
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.test.*
class SpeechTest {
    private class Sink: CueSink {val messages=mutableListOf<SpeechCue>();var stopped=0;var working=true;override fun speak(cue:SpeechCue):Boolean{if(working)messages+=cue;return working};override fun stop(){stopped++}}
    private fun scope(sequence:Long,time:Long=0)=EventScope("session",0,0,sequence,time)
    @Test fun timingPreemptsRepQueueAndCompletionIsDeduplicated(){
        val clock=ManualWorkoutClock();val sink=Sink();val speech=SpeechCoordinator("session",clock,sink)
        speech.accept(WorkoutEvent.AcceptedRep(scope(0),1,Side.BOTH));speech.accept(WorkoutEvent.AcceptedRep(scope(1),2,Side.BOTH));speech.accept(WorkoutEvent.AcceptedRep(scope(2),3,Side.BOTH))
        assertEquals(listOf("1"),sink.messages.map{it.text});speech.completed("session:0")
        assertEquals(listOf("1","3"),sink.messages.map{it.text})
        speech.accept(WorkoutEvent.SetCompleted(scope(3),true));speech.accept(WorkoutEvent.SetCompleted(scope(3),true))
        assertEquals("Stop. Set complete.",sink.messages.last().text);assertEquals(1,sink.stopped)
        speech.completed("session:2");assertEquals(3,sink.messages.size)
    }
    @Test fun pauseResumeExpiryAndOldScopeNeverSpeakStaleCountdown(){
        val clock=ManualWorkoutClock();val sink=Sink();val speech=SpeechCoordinator("session",clock,sink)
        speech.accept(WorkoutEvent.Countdown(scope(0),3));clock.advanceBy(1000);speech.accept(WorkoutEvent.Countdown(scope(1,1000),2))
        speech.pause();speech.accept(WorkoutEvent.Countdown(scope(2,1000),1));clock.advanceBy(1000);speech.resume()
        speech.accept(WorkoutEvent.Countdown(scope(3,1000),1));assertEquals(listOf("3","2"),sink.messages.map{it.text})
        speech.accept(WorkoutEvent.SetStarted(scope(4,2000)));clock.advanceBy(2000);speech.poll()
        assertEquals("Start.",sink.messages.last().text);assertEquals(3,sink.stopped)
        speech.accept(WorkoutEvent.Ready(EventScope("old",0,0,99,4000)));assertEquals(3,sink.messages.size)
    }
    @Test fun missingSpeechDoesNotAffectWorkoutEventsAndNeverBuildsQueue(){
        val clock=ManualWorkoutClock();val sink=Sink().also{it.working=false};val speech=SpeechCoordinator("session",clock,sink)
        repeat(100){speech.accept(WorkoutEvent.AcceptedRep(scope(it.toLong()),it+1,Side.BOTH))}
        assertFalse(speech.available);assertTrue(sink.messages.isEmpty())
        sink.working=true;speech.accept(WorkoutEvent.WorkoutCompleted(scope(100)));assertTrue(speech.available);assertEquals("Stop. Workout complete.",sink.messages.single().text)
    }
}
