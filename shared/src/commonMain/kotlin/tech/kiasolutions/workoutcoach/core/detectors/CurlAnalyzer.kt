package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

class CurlAnalyzer(private val mode:LimbMode=LimbMode.BILATERAL,config:CycleConfig=CycleConfig(depthAngle=60.0),private val pairingWindowMillis:Long=2000):ExerciseAnalyzer {
    init {require(pairingWindowMillis in 200..5000)}
    private val cycles=mapOf(Side.LEFT to AngleCycle(config),Side.RIGHT to AngleCycle(config))
    private val pending=mutableMapOf<Side,Pair<Long,RepResult>>()
    override fun reset(){cycles.values.forEach{it.reset()};pending.clear()}
    override fun analyze(pose:TrackedPose):Analysis {
        if(pose.cycleReset) reset()
        val required=when(mode){LimbMode.LEFT->listOf(Side.LEFT);LimbMode.RIGHT->listOf(Side.RIGHT);else->listOf(Side.LEFT,Side.RIGHT)}
        val output=mutableListOf<RepResult>();var reliable=0
        pending.entries.removeAll {pose.atMillis-it.value.first>pairingWindowMillis}
        for(side in required){
            val body=bodySide(side);val cycle=cycles.getValue(side)
            if(!pose.has(body.shoulder,body.elbow,body.wrist,body.hip)){cycle.reset();pending.remove(side);continue}
            val s=pose.points.getValue(body.shoulder);val e=pose.points.getValue(body.elbow);val w=pose.points.getValue(body.wrist);val h=pose.points.getValue(body.hip)
            val trunk=distance(s,h)
            val posture=s.y<h.y && e.y>s.y && trunk>.06 && abs(e.x-s.x)<trunk*.5 && distance(s,e)<trunk*1.2
            val angle=angleDegrees(s,e,w)
            if(posture && angle!=null) reliable++ else pending.remove(side)
            val rep=cycle.update(pose.atMillis,angle,posture,side) ?: continue
            if(mode==LimbMode.BILATERAL) pending[side]=pose.atMillis to rep else output+=rep
        }
        if(mode==LimbMode.BILATERAL){
            if(reliable<2){pending.clear();cycles.values.forEach{it.reset()}}
            else if(pending.size==2){
                val left=pending.getValue(Side.LEFT).second;val right=pending.getValue(Side.RIGHT).second
                output+=RepResult(left.full && right.full,Side.BOTH,max(left.durationMillis,right.durationMillis));pending.clear()
            }
        }
        return Analysis(cycles.getValue(required.first()).phase,if(mode==LimbMode.BILATERAL)reliable==2 else reliable>0,output)
    }
}
