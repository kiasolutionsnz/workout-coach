package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.test.*

class ShoulderPressTest {
    private fun pose(time:Long,up:Boolean=false,partial:Boolean=false,otherArmReady:Boolean=false):TrackedPose {
        val points=mutableMapOf<Joint,Point2>()
        for(side in listOf(Side.LEFT,Side.RIGHT)){
            val b=bodySide(side);val x=if(side==Side.LEFT).3 else .7;val sign=if(side==Side.LEFT)-1 else 1
            val raised=up && !(side==Side.RIGHT && otherArmReady)
            points[b.shoulder]=Point2(x,.5);points[b.hip]=Point2(x,.85)
            points[b.elbow]=if(raised)Point2(x,.3)else if(partial)Point2(x+sign*.12,.38)else Point2(x+sign*.12,.65)
            points[b.wrist]=if(raised)Point2(x,.1)else if(partial)Point2(x+sign*.18,.18)else Point2(x+sign*.27,.53)
        }
        return TrackedPose(time,points,false)
    }
    @Test fun overheadBilateralCyclesRequireReturnAndDoNotRepeat() {
        val a=ShoulderPressAnalyzer();val reps=listOf(false,false,false,true,true,false,false,false).flatMapIndexed{i,up->a.analyze(pose(i*200L,up)).reps}
        assertTrue(reps.single().full);assertEquals(Side.BOTH,reps.single().side)
    }
    @Test fun missingWristsAsymmetryAndCurlPostureDoNotCount() {
        val a=ShoulderPressAnalyzer()
        assertTrue(listOf(false,false,false,true,true,false,false).flatMapIndexed{i,up->a.analyze(pose(i*200L,up,otherArmReady=true)).reps}.isEmpty())
        a.reset();a.analyze(pose(0));a.analyze(pose(200));a.analyze(pose(400,true))
        val missing=pose(600,true);assertFalse(a.analyze(TrackedPose(600,missing.points-Joint.LEFT_WRIST,false)).reliable)
        assertTrue(a.analyze(pose(800)).reps.isEmpty());assertTrue(a.analyze(pose(1000)).reps.isEmpty())
        val curl=pose(1200).points.toMutableMap();curl[Joint.LEFT_WRIST]=Point2(.3,.9);curl[Joint.RIGHT_WRIST]=Point2(.7,.9)
        assertFalse(a.analyze(TrackedPose(1200,curl,false)).reliable)
    }
    @Test fun partialPressAndOverheadFirstFrameDoNotClaimFullCycle() {
        val a=ShoulderPressAnalyzer()
        val partial=listOf(false,false,false,true,true,false,false).flatMapIndexed {i,moved->a.analyze(pose(i*200L,partial=moved)).reps}
        assertFalse(partial.single().full)
        a.reset();assertTrue(a.analyze(pose(0,true)).reps.isEmpty())
        assertTrue(a.analyze(pose(200,true)).reps.isEmpty())
        assertTrue(a.analyze(pose(400)).reps.isEmpty());assertTrue(a.analyze(pose(600)).reps.isEmpty())
    }
    @Test fun pullUpLikeFixedOverheadHandsCannotEstablishPressReadyPosture() {
        val a=ShoulderPressAnalyzer()
        val reps=(0..6).flatMap{i->
            val points=pose(i*200L,up=true).points.toMutableMap()
            if(i in 2..4){points[Joint.LEFT_ELBOW]=Point2(.1,.3);points[Joint.RIGHT_ELBOW]=Point2(.9,.3)}
            a.analyze(TrackedPose(i*200L,points,false)).reps
        }
        assertTrue(reps.isEmpty())
    }
}
