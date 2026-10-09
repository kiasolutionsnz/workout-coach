package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.test.*

class SquatTest {
    private fun pose(time:Long,deep:Boolean=false,shallow:Boolean=false):TrackedPose {
        val hip=when {deep -> Point2(.25,.65); shallow -> Point2(.37,.48); else -> Point2(.5,.4)}
        return TrackedPose(time,mapOf(Joint.LEFT_SHOULDER to Point2(hip.x,hip.y-.2),Joint.LEFT_HIP to hip,Joint.LEFT_KNEE to Point2(.5,.65),Joint.LEFT_ANKLE to Point2(.5,.9)),false)
    }
    private fun movement(analyzer:SquatAnalyzer,shallow:Boolean=false):List<RepResult> = listOf(pose(0),pose(200),pose(400),pose(600,!shallow,shallow),pose(800,!shallow,shallow),pose(1000),pose(1200)).flatMap{analyzer.analyze(it).reps}
    @Test fun hipAndKneeFullCycleAndShallowCycleRemainDistinct() {
        assertTrue(movement(SquatAnalyzer()).single().full)
        assertFalse(movement(SquatAnalyzer(),true).single().full)
    }
    @Test fun flexedStartOcclusionGapAndRepeatedStandingCannotCount() {
        val analyzer=SquatAnalyzer()
        assertTrue(analyzer.analyze(pose(0,true)).reps.isEmpty())
        analyzer.analyze(pose(200)); analyzer.analyze(pose(400)); analyzer.analyze(pose(600,true))
        assertFalse(analyzer.analyze(TrackedPose(800,emptyMap(),false)).reliable)
        repeat(10){assertTrue(analyzer.analyze(pose(1000+it*200L)).reps.isEmpty())}
        analyzer.analyze(pose(4000,true)); assertTrue(analyzer.analyze(pose(6000)).reps.isEmpty())
    }
}
