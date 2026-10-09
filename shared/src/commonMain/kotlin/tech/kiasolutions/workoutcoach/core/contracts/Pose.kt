package tech.kiasolutions.workoutcoach.core.contracts

enum class Joint { NOSE, LEFT_EAR, RIGHT_EAR, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE }
enum class Rotation { NONE, CLOCKWISE_90, HALF_TURN, CLOCKWISE_270 }
data class Point2(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) { "Invalid pose coordinate" } }
}
data class Landmark(val point: Point2, val confidence: Double) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) { "Invalid confidence" } }
}
data class ImageTransform(val width: Int, val height: Int, val rotation: Rotation = Rotation.NONE, val mirrored: Boolean = false) {
    init { require(width > 0 && height > 0) }
    val orientedWidth: Int get() = if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_270) height else width
    val orientedHeight: Int get() = if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_270) width else height

    /** Input is normalized top-left, unrotated sensor space; mirror follows rotation.
     * Geometry uses pixels / oriented width: both axes have the same scale. */
    fun geometry(point: Point2): Point2 {
        val pixel = Point2(point.x * width, point.y * height)
        val rotated = when (rotation) {
            Rotation.NONE -> pixel
            Rotation.CLOCKWISE_90 -> Point2(height - pixel.y, pixel.x)
            Rotation.HALF_TURN -> Point2(width - pixel.x, height - pixel.y)
            Rotation.CLOCKWISE_270 -> Point2(pixel.y, width - pixel.x)
        }
        return Point2((if (mirrored) orientedWidth - rotated.x else rotated.x) / orientedWidth, rotated.y / orientedWidth)
    }
}
class PoseFrame(
    val sequence: Long,
    val capturedAtMillis: Long,
    val transform: ImageTransform,
    joints: Map<Joint, Landmark>,
) {
    init { require(sequence >= 0 && capturedAtMillis >= 0) }
    // Snapshot input so later adapter mutation cannot alter a frame already being analyzed.
    val joints: Map<Joint, Landmark> = joints.toMap()
    fun geometry(joint: Joint): Point2? = joints[joint]?.let { transform.geometry(it.point) }
}

/** Reject duplicate/reordered observations before any exercise state is advanced. */
class FrameOrderGuard {
    private var sequence: Long? = null
    private var time: Long? = null
    fun accept(frame: PoseFrame): Boolean {
        if (sequence?.let { frame.sequence <= it } == true || time?.let { frame.capturedAtMillis <= it } == true) return false
        sequence = frame.sequence
        time = frame.capturedAtMillis
        return true
    }
    fun reset() { sequence = null; time = null }
}

