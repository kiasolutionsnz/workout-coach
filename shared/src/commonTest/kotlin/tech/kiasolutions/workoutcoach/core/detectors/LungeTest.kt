package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.test.*

class LungeTest {
    private fun pose(time:Long,deep:Boolean=false,side:Side=Side.LEFT,squat:Boolean=false):TrackedPose {
        val points=mutableMapOf<Joint,Point2>();val hip=if(deep)Point2(.25,.65)else Point2(.5,.4)
        for(s in listOf(Side.LEFT,Side.RIGHT)){
            val b=bodySide(s);points[b.shoulder]=Point2(hip.x,hip.y-.25);points[b.hip]=hip
            points[b.knee]=if(deep && s!=Side.LEFT && !squat)Point2(.15,.8)else Point2(.5,.65)
            points[b.ankle]=if(deep && s!=Side.LEFT && !squat)Point2(.05,.9)else Point2(.5,.9)
        }
        return TrackedPose(time,if(side==Side.RIGHT)points.mapKeys{(j,_)->Joint.valueOf(j.name.replace("LEFT","TEMP").replace("RIGHT","LEFT").replace("TEMP","RIGHT"))}.mapValues{(_,p)->Point2(1-p.x,p.y)}else points,false)
    }
    @Test fun alternatingAndRepeatedLegCyclesCountOncePerLeg() {
        val a=LungeAnalyzer();val reps=mutableListOf<RepResult>()
        for((n,side) in listOf(Side.LEFT,Side.RIGHT,Side.RIGHT).withIndex()) for((i,deep) in listOf(false,false,false,true,true,false,false).withIndex()) reps+=a.analyze(pose(n*1600L+i*200L,deep,side)).reps
        assertEquals(listOf(Side.LEFT,Side.RIGHT,Side.RIGHT),reps.map{it.side});assertTrue(reps.all{it.full})
    }
    @Test fun squatAndOccludedLungeCannotCount() {
        val a=LungeAnalyzer()
        assertTrue(listOf(false,false,false,true,true,false,false).flatMapIndexed{i,d->a.analyze(pose(i*200L,d,squat=true)).reps}.isEmpty())
        a.reset();a.analyze(pose(0));a.analyze(pose(200));a.analyze(pose(400,true))
        assertFalse(a.analyze(TrackedPose(600,emptyMap(),false)).reliable)
        assertTrue(a.analyze(pose(800)).reps.isEmpty());assertTrue(a.analyze(pose(1000)).reps.isEmpty())
    }
    @Test fun declaredReverseAndShallowLungeRespectLegCounts() {
        val a=LungeAnalyzer(LungeVariant.REVERSE)
        val results=listOf(false,false,false,true,true,false,false).flatMapIndexed {i,deep->
            val p=pose(i*200L,deep)
            val adjusted=if(deep)p.points.toMutableMap().also{it[Joint.LEFT_HIP]=Point2(.35,.5);it[Joint.RIGHT_HIP]=Point2(.35,.5);it[Joint.LEFT_SHOULDER]=Point2(.35,.25)} else p.points
            a.analyze(TrackedPose(p.atMillis,adjusted,false)).reps
        }
        assertFalse(results.single().full);assertEquals(Side.LEFT,results.single().side)
    }
}
