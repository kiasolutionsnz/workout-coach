package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*
import kotlin.math.*

/** Zero credit/grace for missing or invalid posture. Only consecutive observed valid
 * intervals <=250ms contribute. Elapsed set timing is independently configured. */
class PlankAnalyzer(side:Side=Side.LEFT):ExerciseAnalyzer {
    init {require(side!=Side.BOTH)}
    private val body=bodySide(side);private val hold=ObservedHold()
    private var latest:Long?=null
    var holdState=HoldState(0,0,false);private set
    override fun reset(){hold.reset();latest=null;holdState=HoldState(0,0,false)}
    override fun analyze(pose:TrackedPose):Analysis {
        if(pose.cycleReset)reset()
        latest=pose.atMillis
        if(!pose.has(body.shoulder,body.elbow,body.wrist,body.hip,body.ankle)) {
            holdState=hold.update(pose.atMillis,false);return Analysis(MovementPhase.UNKNOWN,false)
        }
        val s=pose.points.getValue(body.shoulder);val e=pose.points.getValue(body.elbow);val w=pose.points.getValue(body.wrist);val h=pose.points.getValue(body.hip);val a=pose.points.getValue(body.ankle)
        val dx=abs(a.x-s.x);val view=dx>.12 && abs(a.y-s.y)<dx*.4
        val alignment=angleDegrees(s,h,a)?.let{it>=160}==true
        val support=e.y>s.y+.03 && w.y>s.y+.03 && abs(e.x-s.x)<distance(s,h)*.6
        val valid=view && alignment && support
        holdState=hold.update(pose.atMillis,valid)
        return Analysis(if(valid)MovementPhase.READY else MovementPhase.UNKNOWN,valid,holdValid=valid,cue=if(view && !alignment)"Keep your hips aligned with your body"else null)
    }
    fun expire(nowMillis:Long):HoldState {
        require(nowMillis>=0)
        if(latest?.let{nowMillis-it>250}!=false) holdState=hold.update(nowMillis,false)
        return holdState
    }
}
