package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

enum class PushUpVariant { STANDARD, KNEE, INCLINE }
class PushUpAnalyzer(private val variant: PushUpVariant = PushUpVariant.STANDARD, config: CycleConfig = CycleConfig(depthAngle=95.0), side: Side = Side.LEFT) : ExerciseAnalyzer {
    init { require(side != Side.BOTH) }
    private val body=bodySide(side)
    private val cycle=AngleCycle(config)
    private val repSide=Side.BOTH
    override fun reset() = cycle.reset()
    override fun analyze(pose:TrackedPose):Analysis {
        if(pose.cycleReset) reset()
        val endJoint=if(variant==PushUpVariant.KNEE) body.knee else body.ankle
        if(!pose.has(body.shoulder,body.elbow,body.wrist,body.hip,endJoint)) {reset();return Analysis(MovementPhase.UNKNOWN,false)}
        val s=pose.points.getValue(body.shoulder);val e=pose.points.getValue(body.elbow);val w=pose.points.getValue(body.wrist)
        val h=pose.points.getValue(body.hip);val end=pose.points.getValue(endJoint)
        val dx=abs(end.x-s.x);val dy=abs(end.y-s.y)
        val view=dx>.12 && dy < dx*(if(variant==PushUpVariant.INCLINE) 1.5 else .65)
        val alignment=angleDegrees(s,h,end)?.let{it>=155} == true
        val support=w.y>s.y && distance(s,w)>.04
        val valid=view && alignment && support
        val angle=angleDegrees(s,e,w)
        val rep=cycle.update(pose.atMillis,angle,valid,repSide)
        return Analysis(cycle.phase,valid && angle!=null,rep?.let{listOf(it)}?:emptyList(),cue=if(view && !alignment) "Keep your hips aligned with your body" else null)
    }
}
