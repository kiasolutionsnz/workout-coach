package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.test.*

class PlankTest {
    private fun pose(time:Long,sag:Boolean=false,standing:Boolean=false)=TrackedPose(time,mapOf(Joint.LEFT_SHOULDER to Point2(.2,.4),Joint.LEFT_ELBOW to Point2(.2,.55),Joint.LEFT_WRIST to Point2(.35,.55),Joint.LEFT_HIP to (if(sag)Point2(.5,.7)else if(standing)Point2(.2,.65)else Point2(.5,.4)),Joint.LEFT_ANKLE to (if(standing)Point2(.2,.9)else Point2(.9,.4))),false)
    @Test fun holdsCountObservedIntervalsAndNeverCreditThirtySecondGap() {
        val a=PlankAnalyzer();assertTrue(a.analyze(pose(0)).holdValid)
        a.analyze(pose(200));assertEquals(200L,a.holdState.validMillis)
        assertFalse(a.expire(30200).reliable);assertEquals(200L,a.holdState.validMillis);assertEquals(0L,a.holdState.streakMillis)
        a.analyze(pose(30400));assertEquals(200L,a.holdState.validMillis)
        a.analyze(pose(30600));assertEquals(400L,a.holdState.validMillis)
    }
    @Test fun standingSagAndMissingSupportsImmediatelyBreakStreak() {
        val a=PlankAnalyzer();assertFalse(a.analyze(pose(0,standing=true)).holdValid)
        a.analyze(pose(200));a.analyze(pose(400));assertEquals(200L,a.holdState.streakMillis)
        assertFalse(a.analyze(pose(600,sag=true)).holdValid);assertEquals(0L,a.holdState.streakMillis)
        val p=pose(800);assertFalse(a.analyze(TrackedPose(800,p.points-Joint.LEFT_ELBOW,false)).holdValid)
        assertEquals(200L,a.holdState.validMillis)
    }
}
