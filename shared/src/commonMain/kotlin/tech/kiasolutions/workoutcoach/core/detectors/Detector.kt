package tech.kiasolutions.workoutcoach.core.detectors

import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.tracking.*

enum class MovementPhase { UNKNOWN, READY, MOVING, DEPTH, RETURNING }
data class RepResult(val full: Boolean, val side: Side, val durationMillis: Long)
data class Analysis(val phase: MovementPhase, val reliable: Boolean, val reps: List<RepResult> = emptyList(), val holdValid: Boolean = false, val cue: String? = null)
interface ExerciseAnalyzer {
    fun analyze(pose: TrackedPose): Analysis
    fun reset()
}
data class CycleConfig(val readyAngle: Double = 160.0, val departureAngle: Double = 145.0, val depthAngle: Double = 100.0, val stableMillis: Long = 150, val minimumCycleMillis: Long = 400, val maximumCycleMillis: Long = 10000) {
    init {
        require(listOf(readyAngle,departureAngle,depthAngle).all { it.isFinite() && it in 0.0..180.0 })
        require(depthAngle < departureAngle && departureAngle < readyAngle)
        require(stableMillis in 1..1000 && minimumCycleMillis >= stableMillis && maximumCycleMillis > minimumCycleMillis)
    }
}
/** Complete high-low-high cycles only. The first posture must establish a stable ready
 * baseline. A gap, changed side or invalid posture must reset before further updates. */
class AngleCycle(private val config: CycleConfig = CycleConfig()) {
    var phase = MovementPhase.UNKNOWN; private set
    private var previousTime: Long? = null
    private var readySince: Long? = null
    private var started: Long? = null
    private var reachedDepth = false
    private var previousSide: Side? = null
    fun reset() { phase = MovementPhase.UNKNOWN; previousTime = null; readySince = null; started = null; reachedDepth = false; previousSide = null }
    fun update(atMillis: Long, angle: Double?, postureValid: Boolean, side: Side): RepResult? {
        require(atMillis >= 0)
        if (previousTime?.let { atMillis <= it } == true) return null
        if (previousSide?.let { it != side } == true) reset()
        previousSide = side
        val gap = previousTime?.let { atMillis - it > 700 } == true
        previousTime = atMillis
        if (gap || !postureValid || angle == null || !angle.isFinite() || angle !in 0.0..180.0) {
            reset(); previousTime = atMillis; return null
        }
        val start = started
        if (start != null && atMillis - start > config.maximumCycleMillis) {
            reset(); previousTime = atMillis; return null
        }
        when (phase) {
            MovementPhase.UNKNOWN -> {
                if (angle >= config.readyAngle) {
                    if (readySince == null) readySince = atMillis
                    if (atMillis - readySince!! >= config.stableMillis) phase = MovementPhase.READY
                } else readySince = null
            }
            MovementPhase.READY -> if (angle <= config.departureAngle) {
                started = atMillis; readySince = null; reachedDepth = angle <= config.depthAngle
                phase = if (reachedDepth) MovementPhase.DEPTH else MovementPhase.MOVING
            }
            else -> {
                if (angle <= config.depthAngle) { reachedDepth = true; phase = MovementPhase.DEPTH }
                if (angle >= config.readyAngle) {
                    phase = MovementPhase.RETURNING
                    if (readySince == null) readySince = atMillis
                    if (atMillis - readySince!! >= config.stableMillis) {
                        val duration = atMillis - checkNotNull(started)
                        val result = if (duration >= config.minimumCycleMillis) RepResult(reachedDepth,side,duration) else null
                        started = null; reachedDepth = false; phase = MovementPhase.READY
                        return result
                    }
                } else readySince = null
            }
        }
        return null
    }
}

class JointAngleAnalyzer(private val joints: Triple<Joint,Joint,Joint>, private val side: Side, private val posture: (TrackedPose) -> Boolean, config: CycleConfig = CycleConfig()) : ExerciseAnalyzer {
    private val cycle = AngleCycle(config)
    override fun reset() = cycle.reset()
    override fun analyze(pose: TrackedPose): Analysis {
        if (pose.cycleReset) cycle.reset()
        val a = pose.points[joints.first]; val v = pose.points[joints.second]; val b = pose.points[joints.third]
        val angle = if (a != null && v != null && b != null) angleDegrees(a,v,b) else null
        val reliable = angle != null && posture(pose)
        val result = cycle.update(pose.atMillis,angle,reliable,side)
        return Analysis(cycle.phase,reliable,result?.let { listOf(it) } ?: emptyList())
    }
}

data class HoldState(val validMillis: Long, val streakMillis: Long, val reliable: Boolean)
class ObservedHold(private val maximumObservationGapMillis: Long = 250) {
    init { require(maximumObservationGapMillis in 1..1000) }
    private var previous: Long? = null
    private var lastTime: Long? = null
    private var validMillis = 0L; private var streakMillis = 0L
    fun update(atMillis: Long, valid: Boolean): HoldState {
        require(atMillis >= 0)
        val old = previous
        if (lastTime?.let { atMillis <= it } == true) return HoldState(validMillis,streakMillis,false)
        lastTime = atMillis
        val delta = if (old == null) 0 else atMillis - old
        if (valid && old != null && delta <= maximumObservationGapMillis) { validMillis += delta; streakMillis += delta }
        else streakMillis = 0
        previous = if (valid) atMillis else null
        return HoldState(validMillis,streakMillis,valid)
    }
    fun reset() { previous = null; lastTime = null; validMillis = 0; streakMillis = 0 }
}

data class SyntheticReplayReport(val label: String, val accepted: Int, val partial: Int, val expectedAccepted: Int, val expectedPartial: Int, val observations: Int) {
    val matches: Boolean get() = accepted == expectedAccepted && partial == expectedPartial
}
/** Only synthetic/consented inputs. Reports describe fixture behavior, never human accuracy. */
fun replaySynthetic(label: String, frames: List<PoseFrame>, analyzer: ExerciseAnalyzer, expectedAccepted: Int, expectedPartial: Int): SyntheticReplayReport {
    require(label.startsWith("synthetic:") && expectedAccepted >= 0 && expectedPartial >= 0)
    analyzer.reset()
    val tracker = PoseTracker(); var accepted = 0; var partial = 0
    for (frame in frames) {
        val pose = tracker.observe(frame,frame.capturedAtMillis) ?: continue
        val result = analyzer.analyze(pose)
        result.reps.forEach { if (it.full) accepted++ else partial++ }
    }
    return SyntheticReplayReport(label,accepted,partial,expectedAccepted,expectedPartial,frames.size)
}
