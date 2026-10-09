package tech.kiasolutions.workoutcoach.core.experience
import tech.kiasolutions.workoutcoach.core.contracts.*
data class PoseLine(val start:Point2,val end:Point2)
/** Display-only geometry; the fit scale and optional preview mirror are native-owned. */
fun poseLines(frame:PoseFrame):List<PoseLine> {
    val edges=listOf(Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER,Joint.LEFT_HIP to Joint.RIGHT_HIP,
        Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW,Joint.LEFT_ELBOW to Joint.LEFT_WRIST,Joint.LEFT_SHOULDER to Joint.LEFT_HIP,Joint.LEFT_HIP to Joint.LEFT_KNEE,Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
        Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW,Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,Joint.RIGHT_HIP to Joint.RIGHT_KNEE,Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE)
    fun point(joint:Joint)=frame.joints[joint]?.takeIf{it.confidence>=.65 && it.point.x in 0.0..1.0 && it.point.y in 0.0..1.0}?.let{frame.transform.geometry(it.point)}
    return edges.mapNotNull{(a,b)->val start=point(a);val end=point(b);if(start!=null && end!=null)PoseLine(start,end)else null}
}
