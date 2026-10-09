package tech.kiasolutions.workoutcoach.core.experience
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.test.*

class WorkoutSessionTest {
    private val id="f0250c63-7f09-4e96-bdb9-81279106c317"
    private fun plank(clock:ManualWorkoutClock,sequence:Long)=PoseFrame(sequence,clock.nowMillis(),ImageTransform(100,100),mapOf(
        Joint.LEFT_SHOULDER to Landmark(Point2(.2,.4),1.0),Joint.LEFT_ELBOW to Landmark(Point2(.2,.55),1.0),Joint.LEFT_WRIST to Landmark(Point2(.35,.55),1.0),Joint.LEFT_HIP to Landmark(Point2(.5,.4),1.0),Joint.LEFT_ANKLE to Landmark(Point2(.9,.4),1.0)))
    @Test fun saveAcknowledgementDuringInterruptionContinuesExactlyOnceAfterResume(){
        val clock=ManualWorkoutClock();val run=WorkoutSession(WorkoutPlan(id,"Hold",listOf(ExerciseBlock(ExerciseId.PLANK,1,SetGoal.Duration(1)))),id,0,clock);var seq=0L
        repeat(3){clock.advanceBy(50);run.observe(plank(clock,seq++))};run.begin();clock.advanceBy(3000);run.tick();clock.advanceBy(1000);run.tick()
        assertNotNull(run.pendingSet);run.pause(true);run.acknowledgeSet();run.acknowledgeSet();assertEquals(1,run.savedSets.size)
        run.framing(plank(clock,seq++));run.resume();assertEquals(WorkoutPhase.COMPLETED,run.state.phase)
        assertEquals(1,run.drainEvents().filterIsInstance<WorkoutEvent.WorkoutCompleted>().size)
    }
    @Test fun selectedLegSquatCountsOneWholeBodyRepThroughRealSessionRouter(){
        val clock=ManualWorkoutClock();val run=WorkoutSession(WorkoutPlan(id,"Squat",listOf(ExerciseBlock(ExerciseId.SQUAT,1,SetGoal.Reps(1)))),id,0,clock);var seq=0L
        fun frame(deep:Boolean=false){clock.advanceBy(200);val hip=if(deep)Point2(.25,.65)else Point2(.5,.4)
            run.observe(PoseFrame(seq++,clock.nowMillis(),ImageTransform(100,100),mapOf(Joint.LEFT_SHOULDER to Landmark(Point2(hip.x,hip.y-.2),1.0),Joint.LEFT_HIP to Landmark(hip,1.0),Joint.LEFT_KNEE to Landmark(Point2(.5,.65),1.0),Joint.LEFT_ANKLE to Landmark(Point2(.5,.9),1.0))))}
        repeat(4){frame()};run.begin();clock.advanceBy(3000);run.tick()
        repeat(5){frame()};repeat(5){frame(true)};repeat(5){frame()}
        assertEquals(1,run.state.accepted);assertEquals(WorkoutPhase.SET_COMPLETE,run.state.phase)
        assertEquals(Side.BOTH,assertNotNull(run.pendingSet).reps.single().side)
    }
    @Test fun automaticTimedSetsWaitForSaveAndReacquireBeforeNextCountdown(){
        val clock=ManualWorkoutClock();val plan=WorkoutPlan(id,"Two holds",listOf(ExerciseBlock(ExerciseId.PLANK,2,SetGoal.Duration(1),1)))
        val run=WorkoutSession(plan,id,0,clock);var seq=0L
        repeat(3){clock.advanceBy(50);run.observe(plank(clock,seq++))};assertTrue(run.reliable)
        run.begin();clock.advanceBy(3000);run.tick();assertEquals(WorkoutPhase.ACTIVE_SET,run.state.phase)
        clock.advanceBy(1000);run.tick();assertEquals(WorkoutPhase.SET_COMPLETE,run.state.phase)
        val pending=assertNotNull(run.pendingSet);assertTrue(pending.reachedGoal);assertEquals(1000L,pending.elapsedMillis)
        clock.advanceBy(10000);run.tick();assertEquals(pending,run.pendingSet);assertEquals(WorkoutPhase.SET_COMPLETE,run.state.phase)
        run.acknowledgeSet();assertEquals(WorkoutPhase.RESTING,run.state.phase)
        clock.advanceBy(1000);run.tick();assertEquals(WorkoutPhase.PREPARING,run.state.phase)
        repeat(3){clock.advanceBy(50);run.observe(plank(clock,seq++))};assertEquals(WorkoutPhase.PREPARING,run.state.phase)
        clock.advanceBy(2000);run.observe(plank(clock,seq++));assertEquals(WorkoutPhase.COUNTDOWN,run.state.phase)
        clock.advanceBy(3000);run.tick();clock.advanceBy(1000);run.tick();run.acknowledgeSet();assertEquals(WorkoutPhase.COMPLETED,run.state.phase)
        val events=run.drainEvents();assertEquals(events.size,events.map{it.scope.id}.distinct().size);assertEquals(2,events.filterIsInstance<WorkoutEvent.SetCompleted>().size)
    }
    @Test fun interruptedHoldDoesNotCreditGapAndCancellationAcknowledgesPendingSave(){
        val clock=ManualWorkoutClock();val run=WorkoutSession(WorkoutPlan(id,"Valid hold",listOf(ExerciseBlock(ExerciseId.PLANK,1,SetGoal.Duration(2,HoldTiming.VALID_HOLD)))),id,0,clock);var seq=0L
        repeat(3){clock.advanceBy(50);run.observe(plank(clock,seq++))};run.begin();clock.advanceBy(3000);run.tick()
        repeat(6){clock.advanceBy(50);run.observe(plank(clock,seq++))}
        val before=run.state.validHoldMillis;assertTrue(before>0)
        run.pause(true);clock.advanceBy(30000);run.tick();assertEquals(before,run.state.validHoldMillis)
        run.resume();assertEquals(WorkoutPhase.INTERRUPTED,run.state.phase)
        run.framing(plank(clock,seq++));run.resume();assertEquals(WorkoutPhase.ACTIVE_SET,run.state.phase)
        clock.advanceBy(50);run.observe(plank(clock,seq++));assertEquals(before,run.state.validHoldMillis)
        run.endSet();assertFalse(assertNotNull(run.pendingSet).reachedGoal);run.cancel();run.acknowledgeSet();assertNull(run.pendingSet);assertEquals(WorkoutPhase.CANCELLED,run.state.phase)
    }
}
