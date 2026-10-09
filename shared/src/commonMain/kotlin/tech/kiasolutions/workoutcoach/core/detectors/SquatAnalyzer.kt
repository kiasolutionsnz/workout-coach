package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

internal data class BodySide(val shoulder: Joint, val elbow: Joint, val wrist: Joint, val hip: Joint, val knee: Joint, val ankle: Joint)
internal fun bodySide(side: Side) = if (side == Side.LEFT) BodySide(Joint.LEFT_SHOULDER,Joint.LEFT_ELBOW,Joint.LEFT_WRIST,Joint.LEFT_HIP,Joint.LEFT_KNEE,Joint.LEFT_ANKLE) else BodySide(Joint.RIGHT_SHOULDER,Joint.RIGHT_ELBOW,Joint.RIGHT_WRIST,Joint.RIGHT_HIP,Joint.RIGHT_KNEE,Joint.RIGHT_ANKLE)
internal fun distance(a: Point2,b: Point2) = hypot(a.x-b.x,a.y-b.y)
internal fun TrackedPose.has(vararg joints: Joint) = joints.all { points.containsKey(it) }

class SquatAnalyzer(private val config: CycleConfig = CycleConfig(), private val side: Side = Side.LEFT) : ExerciseAnalyzer {
    init { require(side != Side.BOTH) }
    private val body = bodySide(side)
    private val cycle = AngleCycle(config)
    private var readyHip: Double? = null
    private var maximumHipDrop = 0.0
    private var legLength = 0.0
    override fun reset() { cycle.reset(); readyHip = null; maximumHipDrop = 0.0; legLength = 0.0 }
    override fun analyze(pose: TrackedPose): Analysis {
        if (pose.cycleReset) reset()
        if (!pose.has(body.shoulder,body.hip,body.knee,body.ankle)) { reset(); return Analysis(MovementPhase.UNKNOWN,false) }
        val shoulder=pose.points.getValue(body.shoulder); val hip=pose.points.getValue(body.hip)
        val knee=pose.points.getValue(body.knee); val ankle=pose.points.getValue(body.ankle)
        val kneeAngle=angleDegrees(hip,knee,ankle)
        val length=distance(hip,knee)+distance(knee,ankle)
        val upright=shoulder.y < hip.y && hip.y < ankle.y && knee.y < ankle.y && distance(shoulder,hip) > .04 && length > .08
        if (cycle.phase == MovementPhase.READY && kneeAngle != null && kneeAngle >= config.readyAngle) { readyHip=hip.y; legLength=length; maximumHipDrop=0.0 }
        readyHip?.let { maximumHipDrop=max(maximumHipDrop,hip.y-it) }
        val rep=cycle.update(pose.atMillis,kneeAngle,upright,side)
        if (!upright) { readyHip=null; maximumHipDrop=0.0 }
        val qualified=rep?.copy(full=rep.full && maximumHipDrop >= legLength*.08)
        if (rep != null) { readyHip=hip.y; maximumHipDrop=0.0 }
        val other=bodySide(if(side==Side.LEFT)Side.RIGHT else Side.LEFT)
        val sideView=pose.points[other.hip]?.let{otherHip->pose.points[other.shoulder]?.let{otherShoulder->
            val trunk=distance(shoulder,hip)
            abs(otherHip.x-hip.x)<trunk*.3 && abs(otherShoulder.x-shoulder.x)<trunk*.3
        }}==true
        return Analysis(cycle.phase,upright && kneeAngle != null,qualified?.let{listOf(it)} ?: emptyList(),cue=if(qualified?.full == false && sideView) "Return to standing after a deeper squat" else null)
    }
}
