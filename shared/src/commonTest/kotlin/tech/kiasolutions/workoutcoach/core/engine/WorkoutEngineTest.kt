package tech.kiasolutions.workoutcoach.core.engine
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.test.*

class WorkoutEngineTest {
    private fun engine(clock: WorkoutClock, blocks: List<ExerciseBlock>) = WorkoutEngine(WorkoutPlan("plan", "Test", blocks), "session", clock)
    private fun start(e: WorkoutEngine, clock: ManualWorkoutClock) { e.prepare(); e.startCountdown(); clock.advanceBy(3000); e.tick() }
    @Test fun elapsedDurationsFinishExactlyWithoutCameraCallbacks() {
        for (seconds in listOf(20,29,30,45,60)) {
            val clock = ManualWorkoutClock(); val e = engine(clock,listOf(ExerciseBlock(ExerciseId.PLANK,1,SetGoal.Duration(seconds))))
            start(e,clock); clock.advanceBy(seconds * 1000L - 1); e.tick()
            assertEquals(WorkoutPhase.ACTIVE_SET,e.state.phase); assertEquals(1L,e.state.remainingMillis)
            clock.advanceBy(1); e.tick(); e.tick()
            assertEquals(WorkoutPhase.SET_COMPLETE,e.state.phase)
            assertEquals(1,e.drainEvents().filterIsInstance<WorkoutEvent.SetCompleted>().size)
            e.continueAfterSet(); e.tick()
            assertEquals(WorkoutPhase.COMPLETED,e.state.phase)
            assertEquals(1,e.drainEvents().filterIsInstance<WorkoutEvent.WorkoutCompleted>().size)
        }
    }
    @Test fun partialRepsNeverReachGoalAndCompletedSetIgnoresLateCallbacks() {
        val c = ManualWorkoutClock(); val e = engine(c,listOf(ExerciseBlock(ExerciseId.SQUAT,1,SetGoal.Reps(2))))
        start(e,c); e.rep(false,Side.BOTH); e.rep(true,Side.LEFT)
        assertEquals(0,e.state.accepted); assertEquals(1,e.state.partial)
        e.rep(true,Side.BOTH); e.rep(true,Side.BOTH); e.rep(true,Side.BOTH)
        assertEquals(2,e.state.accepted)
        assertEquals(1,e.drainEvents().filterIsInstance<WorkoutEvent.SetCompleted>().size)
    }
    @Test fun pauseAndInterruptionFreezeEveryTimerAndKeepCounts() {
        val c = ManualWorkoutClock(); val e = engine(c,listOf(ExerciseBlock(ExerciseId.PLANK,2,SetGoal.Duration(20),restSeconds=29)))
        e.prepare(); e.startCountdown(); c.advanceBy(1000); e.pause()
        c.advanceBy(100000); e.tick(); assertEquals(2000L,e.state.remainingMillis)
        e.resume(); c.advanceBy(2000); e.tick(); c.advanceBy(5000); e.pause(true)
        c.advanceBy(100000); e.resume(); assertEquals(15000L,e.state.remainingMillis)
        c.advanceBy(15000); e.tick(); e.continueAfterSet(); c.advanceBy(1000); e.pause()
        c.advanceBy(100000); e.resume(); assertEquals(28000L,e.state.remainingMillis)
        c.advanceBy(28000); e.tick(); assertEquals(WorkoutPhase.PREPARING,e.state.phase); assertEquals(1,e.state.setIndex)
    }
    @Test fun validHoldNeverBridgesLossPauseOrNoFrameGaps() {
        val c = ManualWorkoutClock(); val e = engine(c,listOf(ExerciseBlock(ExerciseId.PLANK,1,SetGoal.Duration(1,HoldTiming.VALID_HOLD))))
        start(e,c); e.holdObservation(c.nowMillis(),true); c.advanceBy(200); e.holdObservation(c.nowMillis(),true)
        assertEquals(200L,e.state.validHoldMillis)
        c.advanceBy(10000); e.tick(); assertEquals(800L,e.state.remainingMillis)
        e.holdObservation(c.nowMillis(),true); c.advanceBy(100); e.trackingChanged(false); e.holdObservation(c.nowMillis(),true)
        assertEquals(200L,e.state.validHoldMillis)
        e.pause(); c.advanceBy(10000); e.resume(); e.holdObservation(c.nowMillis(),true)
        repeat(4) { c.advanceBy(200); e.holdObservation(c.nowMillis(),true) }
        assertEquals(WorkoutPhase.SET_COMPLETE,e.state.phase); assertEquals(1000L,e.state.validHoldMillis)
    }
    @Test fun earlyFinishSkipRestAndSequencePreserveEventScope() {
        val c = ManualWorkoutClock(); val e = engine(c,listOf(ExerciseBlock(ExerciseId.SQUAT,1,SetGoal.Reps(5),restSeconds=60),ExerciseBlock(ExerciseId.LUNGE,1,SetGoal.Reps(1))))
        start(e,c); e.finishEarly(); e.continueAfterSet(); e.skipRest(); e.startCountdown(); c.advanceBy(3000); e.tick(); e.rep(true,Side.LEFT); e.continueAfterSet()
        val events = e.drainEvents()
        assertFalse(events.filterIsInstance<WorkoutEvent.SetCompleted>().first().reachedGoal)
        assertEquals(1,events.filterIsInstance<WorkoutEvent.AcceptedRep>().single().scope.blockIndex)
        assertEquals(events.size,events.map{it.scope.id}.distinct().size)
        assertEquals(events.indices.map{it.toLong()},events.map{it.scope.sequence})
        assertEquals(WorkoutPhase.COMPLETED,e.state.phase)
    }
    @Test fun clockJumpDoesNotCreditUnstartedSetsAndCancelIsTerminal() {
        val c = ManualWorkoutClock(); val e = engine(c,listOf(ExerciseBlock(ExerciseId.PLANK,1,SetGoal.Duration(20))))
        e.prepare(); e.startCountdown(); c.advanceBy(100000); e.tick()
        assertEquals(0L,e.state.elapsedMillis); assertEquals(20000L,e.state.remainingMillis)
        e.cancel(); c.advanceBy(100000); e.tick(); e.finishEarly(); e.rep(true,Side.BOTH)
        assertEquals(WorkoutPhase.CANCELLED,e.state.phase)
        assertTrue(e.drainEvents().none{it is WorkoutEvent.SetCompleted})
        var time = 10L; val badClock = WorkoutClock { time }; val other = engine(badClock,listOf(ExerciseBlock(ExerciseId.SQUAT,1,SetGoal.Reps(1))))
        time = 9; assertFailsWith<IllegalStateException>{other.tick()}
    }
}
