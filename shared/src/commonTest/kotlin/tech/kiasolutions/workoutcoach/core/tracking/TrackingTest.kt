package tech.kiasolutions.workoutcoach.core.tracking
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.test.*

class TrackingTest {
    private fun frame(seq: Long, time: Long, confidence: Double = 1.0, transform: ImageTransform = ImageTransform(100, 200)) =
        PoseFrame(seq, time, transform, mapOf(Joint.LEFT_KNEE to Landmark(Point2(.5, .5), confidence)))
    @Test fun occlusionAndStoppedCallbacksCannotReuseLandmarks() {
        val tracker = PoseTracker()
        assertEquals(1, tracker.observe(frame(0, 0), 0)!!.points.size)
        assertTrue(tracker.observe(frame(1, 30, .1), 30)!!.points.isEmpty())
        assertEquals(1, tracker.observe(frame(2, 60), 60)!!.points.size)
        assertTrue(tracker.expire(800))
        assertNull(tracker.observe(frame(3, 60), 800))
        assertTrue(tracker.observe(frame(4, 810), 810)!!.cycleReset)
        assertNull(tracker.observe(frame(4, 820), 820))
        assertNull(tracker.observe(frame(5, 1000), 900))
    }
    @Test fun anglesSurviveAspectRotationAndMirrorAndRejectDegenerateSegments() {
        val a = Point2(.25, .5); val v = Point2(.5, .5); val b = Point2(.5, .75)
        for (rotation in Rotation.entries) for (mirror in listOf(false, true)) {
            val transform = ImageTransform(1920, 1080, rotation, mirror)
            assertEquals(90.0, angleDegrees(transform.geometry(a), transform.geometry(v), transform.geometry(b))!!, 1e-8)
        }
        assertNull(angleDegrees(a, a, b))
        assertNull(angleDegrees(Point2(1e308, 0.0), Point2(0.0, 0.0), Point2(0.0, 1e308)))
    }
    @Test fun transformChangeResetsAndMissingJointDoesNotSmoothAcrossOcclusion() {
        val tracker = PoseTracker()
        tracker.observe(frame(0, 0), 0)
        assertTrue(tracker.observe(frame(1, 30, transform = ImageTransform(200, 100)), 30)!!.cycleReset)
        tracker.observe(frame(2, 60, .1), 60)
        val fresh = PoseFrame(3, 90, ImageTransform(200, 100), mapOf(Joint.LEFT_KNEE to Landmark(Point2(.9,.9),1.0)))
        assertEquals(Point2(.9,.45), tracker.observe(fresh,90)!!.points[Joint.LEFT_KNEE])
    }
    @Test fun sideSelectionHysteresisRequiresConfirmationAndKeepsReliableSide() {
        val selector = StableSideSelector()
        repeat(2) { assertFalse(selector.observe(true, true)) }
        assertTrue(selector.observe(true,true)); assertEquals(Side.LEFT,selector.selected)
        repeat(5) { assertFalse(selector.observe(true,true)) }
        assertFalse(selector.observe(false,true)); assertFalse(selector.observe(false,false))
        repeat(2) { assertFalse(selector.observe(false,true)) }
        assertTrue(selector.observe(false,true)); assertEquals(Side.RIGHT,selector.selected)
    }
}
