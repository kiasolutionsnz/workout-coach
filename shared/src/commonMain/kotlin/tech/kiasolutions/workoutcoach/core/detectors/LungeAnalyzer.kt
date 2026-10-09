package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

enum class LungeVariant { FORWARD, REVERSE }
/** Side-on forward/reverse lunges. One front-leg down/return cycle is one rep.
 * Lateral and walking variants are outside the supported configuration. */
class LungeAnalyzer(val variant:LungeVariant=LungeVariant.FORWARD,private val mode:LimbMode=LimbMode.ALTERNATING,config:CycleConfig=CycleConfig()):ExerciseAnalyzer {
    private val cycles=mapOf(Side.LEFT to AngleCycle(config),Side.RIGHT to AngleCycle(config))
    private val readyAngle=config.readyAngle
    override fun reset()=cycles.values.forEach{it.reset()}
    override fun analyze(pose:TrackedPose):Analysis {
        if(pose.cycleReset) reset()
        val left=bodySide(Side.LEFT);val right=bodySide(Side.RIGHT)
        if(!pose.has(left.shoulder,left.hip,left.knee,left.ankle,right.hip,right.knee,right.ankle)){reset();return Analysis(MovementPhase.UNKNOWN,false)}
        val ls=pose.points.getValue(left.shoulder);val lh=pose.points.getValue(left.hip);val rh=pose.points.getValue(right.hip)
        val la=pose.points.getValue(left.ankle);val ra=pose.points.getValue(right.ankle)
        val torso=distance(ls,lh)
        val sideView=torso>.06 && abs(lh.x-rh.x)<torso*.3 && ls.y<lh.y
        val split=abs(la.x-ra.x)>torso*.7
        val output=mutableListOf<RepResult>();var reliable=false
        for(side in listOf(Side.LEFT,Side.RIGHT)){
            val b=bodySide(side);val h=pose.points.getValue(b.hip);val k=pose.points.getValue(b.knee);val a=pose.points.getValue(b.ankle)
            val angle=angleDegrees(h,k,a)
            val standing=angle!=null && angle>=readyAngle
            val front=split && abs(k.x-a.x)<distance(h,k)*.4 && h.y<a.y && k.y<a.y
            val valid=sideView && (standing || front)
            reliable=reliable || valid
            val rep=cycles.getValue(side).update(pose.atMillis,angle,valid,side)
            if(rep!=null && (mode!=LimbMode.LEFT || side==Side.LEFT) && (mode!=LimbMode.RIGHT || side==Side.RIGHT)) output+=rep
        }
        return Analysis(cycles.getValue(Side.LEFT).phase,reliable,output)
    }
}
