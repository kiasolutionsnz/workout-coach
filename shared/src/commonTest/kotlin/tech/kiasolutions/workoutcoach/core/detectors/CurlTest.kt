package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*
import kotlin.test.*

class CurlTest {
    private fun pose(time:Long,left:Double,right:Double=left,swing:Boolean=false):TrackedPose {
        val points=mutableMapOf<Joint,Point2>()
        for((side,angle) in listOf(Side.LEFT to left,Side.RIGHT to right)){
            val b=bodySide(side);val x=if(side==Side.LEFT).3 else .7;val elbowX=if(swing)x+.3 else x
            points[b.shoulder]=Point2(x,.25);points[b.elbow]=Point2(elbowX,.45);points[b.hip]=Point2(x,.65)
            val r=angle*PI/180;points[b.wrist]=Point2(elbowX+.18*sin(r),.45-.18*cos(r))
        }
        return TrackedPose(time,points,false)
    }
    private val sequence=listOf(175.0,175.0,130.0,45.0,45.0,175.0,175.0)
    @Test fun bilateralPairsTwoArmsOnceAndIndividualModesCountPerArm() {
        val bilateral=CurlAnalyzer();val paired=sequence.flatMapIndexed{i,a->bilateral.analyze(pose(i*200L,a)).reps}
        assertEquals(Side.BOTH,paired.single().side);assertTrue(paired.single().full)
        val alternating=CurlAnalyzer(LimbMode.ALTERNATING)
        assertEquals(2,sequence.flatMapIndexed{i,a->alternating.analyze(pose(i*200L,a)).reps}.size)
        val left=CurlAnalyzer(LimbMode.LEFT)
        assertEquals(Side.LEFT,sequence.flatMapIndexed{i,a->left.analyze(pose(i*200L,a,175.0)).reps}.single().side)
    }
    @Test fun asynchronousArmCannotCountTwiceAndSwingOrWristLossCannotFinish() {
        val bilateral=CurlAnalyzer()
        assertTrue(sequence.flatMapIndexed{i,a->bilateral.analyze(pose(i*200L,a,175.0)).reps}.isEmpty())
        repeat(5){assertTrue(bilateral.analyze(pose(1400+it*200L,175.0)).reps.isEmpty())}
        val swing=CurlAnalyzer();assertTrue(sequence.flatMapIndexed{i,a->swing.analyze(pose(i*200L,a,swing=true)).reps}.isEmpty())
        val missing=CurlAnalyzer();missing.analyze(pose(0,175.0));missing.analyze(pose(200,175.0));missing.analyze(pose(400,45.0))
        val loss=pose(600,45.0);missing.analyze(TrackedPose(600,loss.points-Joint.LEFT_WRIST,false))
        assertTrue(missing.analyze(pose(800,175.0)).reps.isEmpty());assertTrue(missing.analyze(pose(1000,175.0)).reps.isEmpty())
    }
    @Test fun delayedPairAndPartialPairHaveExplicitAggregation() {
        val a=CurlAnalyzer();val reps=mutableListOf<RepResult>()
        for(i in 0..8) reps+=a.analyze(pose(i*200L,sequence[i.coerceAtMost(6)],sequence[(i-2).coerceIn(0,6)])).reps
        assertEquals(1,reps.size);assertTrue(reps.single().full)
        val partial=CurlAnalyzer()
        assertFalse(sequence.map{if(it==45.0)90.0 else it}.flatMapIndexed{i,angle->partial.analyze(pose(i*200L,angle)).reps}.single().full)
    }
}
