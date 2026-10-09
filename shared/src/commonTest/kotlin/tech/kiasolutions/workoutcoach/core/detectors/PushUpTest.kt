package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*
import kotlin.test.*

class PushUpTest {
    private fun pose(time:Long,angle:Double=175.0,sag:Boolean=false,standing:Boolean=false):TrackedPose {
        val r=angle*PI/180
        return TrackedPose(time,mapOf(Joint.LEFT_SHOULDER to Point2(.25,.4),Joint.LEFT_ELBOW to Point2(.25,.6),Joint.LEFT_WRIST to Point2(.25+.2*sin(r),.6-.2*cos(r)),Joint.LEFT_HIP to (if(sag) Point2(.55,.7) else if(standing) Point2(.25,.6) else Point2(.55,.4)),Joint.LEFT_KNEE to (if(standing) Point2(.25,.75) else Point2(.7,.4)),Joint.LEFT_ANKLE to (if(standing) Point2(.25,.9) else Point2(.9,.4))),false)
    }
    private fun replay(analyzer:PushUpAnalyzer,depth:Double=80.0,sag:Boolean=false,standing:Boolean=false) = listOf(175.0,175.0,140.0,depth,depth,175.0,175.0).flatMapIndexed {i,a->analyzer.analyze(pose(i*200L,a,sag,standing)).reps}
    @Test fun declaredVariantsFullAndPartialCycles() {
        for(variant in PushUpVariant.entries) assertTrue(replay(PushUpAnalyzer(variant)).single().full)
        assertFalse(replay(PushUpAnalyzer(),120.0).single().full)
    }
    @Test fun standingSagAndOcclusionSuppressCounts() {
        assertTrue(replay(PushUpAnalyzer(),standing=true).isEmpty())
        assertTrue(replay(PushUpAnalyzer(),sag=true).isEmpty())
        val a=PushUpAnalyzer();a.analyze(pose(0));a.analyze(pose(200));a.analyze(pose(400,80.0))
        assertFalse(a.analyze(TrackedPose(600,emptyMap(),false)).reliable)
        assertTrue(a.analyze(pose(800)).reps.isEmpty()); assertTrue(a.analyze(pose(1000)).reps.isEmpty())
    }
}
