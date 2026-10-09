package tech.kiasolutions.workoutcoach.core.tracking

import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.math.*

fun angleDegrees(a: Point2, vertex: Point2, b: Point2): Double? {
    val ax = a.x - vertex.x; val ay = a.y - vertex.y
    val bx = b.x - vertex.x; val by = b.y - vertex.y
    val length = hypot(ax, ay) * hypot(bx, by)
    if (!length.isFinite() || length < 1e-10) return null
    val cosine = (ax * bx + ay * by) / length
    if (!cosine.isFinite()) return null
    return acos(cosine.coerceIn(-1.0, 1.0)) * 180 / PI
}

data class TrackedPose(val atMillis: Long, val points: Map<Joint, Point2>, val cycleReset: Boolean)

/** Missing/low-confidence landmarks are absent immediately. Filters never create observations.
 * The caller uses expire() even when camera callbacks stop. */
class PoseTracker(
    private val confidence: Double = 0.65,
    private val expiryMillis: Long = 250,
    private val resetGapMillis: Long = 700,
    private val smoothingTimeMillis: Double = 80.0,
) {
    init { require(confidence in 0.0..1.0 && expiryMillis > 0 && resetGapMillis >= expiryMillis && smoothingTimeMillis > 0 && smoothingTimeMillis.isFinite()) }
    private val order = FrameOrderGuard()
    private var lastFrame: Long? = null
    private var lastTransform: ImageTransform? = null
    private val previous = mutableMapOf<Joint, Pair<Long, Point2>>()
    private var resetPending = false

    fun observe(frame: PoseFrame, nowMillis: Long): TrackedPose? {
        require(nowMillis >= 0)
        if (frame.capturedAtMillis > nowMillis || nowMillis - frame.capturedAtMillis > expiryMillis) return null
        if (!order.accept(frame)) return null
        val reset = resetPending || lastFrame?.let { frame.capturedAtMillis - it > resetGapMillis } == true || lastTransform?.let { it != frame.transform } == true
        if (reset) previous.clear()
        val current = mutableMapOf<Joint, Point2>()
        for ((joint, landmark) in frame.joints) {
            if (landmark.confidence < confidence || landmark.point.x !in 0.0..1.0 || landmark.point.y !in 0.0..1.0) continue
            val point = frame.transform.geometry(landmark.point)
            val old = previous[joint]
            val filtered = if (old == null || frame.capturedAtMillis - old.first > expiryMillis) point else {
                val alpha = 1 - exp(-(frame.capturedAtMillis - old.first) / smoothingTimeMillis)
                Point2(old.second.x + alpha * (point.x - old.second.x), old.second.y + alpha * (point.y - old.second.y))
            }
            current[joint] = filtered
        }
        previous.clear()
        current.forEach { (joint, point) -> previous[joint] = frame.capturedAtMillis to point }
        lastFrame = frame.capturedAtMillis; lastTransform = frame.transform; resetPending = false
        return TrackedPose(frame.capturedAtMillis, current.toMap(), reset)
    }
    fun expire(nowMillis: Long): Boolean {
        require(nowMillis >= 0)
        val time = lastFrame ?: return true
        require(nowMillis >= time)
        if (nowMillis - time > expiryMillis) previous.clear()
        if (nowMillis - time > resetGapMillis) resetPending = true
        return nowMillis - time > expiryMillis
    }
    fun reset() { previous.clear(); order.reset(); lastFrame = null; lastTransform = null; resetPending = true }
}

/** Keep the selected side while reliable. A replacement needs consecutive observations;
 * every switch invalidates an in-progress movement cycle in the detector. */
class StableSideSelector(private val confirmationFrames: Int = 3) {
    init { require(confirmationFrames > 0) }
    var selected: Side? = null; private set
    private var candidate: Side? = null
    private var count = 0
    fun observe(leftReliable: Boolean, rightReliable: Boolean): Boolean {
        if ((selected == Side.LEFT && leftReliable) || (selected == Side.RIGHT && rightReliable)) { candidate = null; count = 0; return false }
        val next = when { leftReliable -> Side.LEFT; rightReliable -> Side.RIGHT; else -> null }
        if (next == null) { candidate = null; count = 0; return false }
        if (next == candidate) count++ else { candidate = next; count = 1 }
        if (count < confirmationFrames) return false
        selected = next; candidate = null; count = 0
        return true
    }
    fun reset() { selected = null; candidate = null; count = 0 }
}
