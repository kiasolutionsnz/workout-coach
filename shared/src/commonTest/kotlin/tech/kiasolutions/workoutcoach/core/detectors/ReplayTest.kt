package tech.kiasolutions.workoutcoach.core.detectors
import tech.kiasolutions.workoutcoach.core.contracts.*
import kotlin.math.*
import kotlin.test.*

class ReplayTest {
    private fun fixture(angles: List<Double>, confidence: Double = 1.0): List<PoseFrame> = angles.mapIndexed { index, angle ->
        val radians = angle * PI / 180
        PoseFrame(index.toLong(), index * 200L, ImageTransform(100,100), mapOf(
            Joint.LEFT_SHOULDER to Landmark(Point2(.7,.5),confidence),
            Joint.LEFT_ELBOW to Landmark(Point2(.5,.5),confidence),
            Joint.LEFT_WRIST to Landmark(Point2(.5+.2*cos(radians),.5+.2*sin(radians)),confidence)))
    }
    @Test fun labeledSyntheticReplayIsDeterministicAndUnknownPostureSuppressesReps() {
        val analyzer = JointAngleAnalyzer(Triple(Joint.LEFT_SHOULDER,Joint.LEFT_ELBOW,Joint.LEFT_WRIST),Side.LEFT,{true})
        val frames = fixture(listOf(175.0,175.0,130.0,80.0,175.0,175.0,175.0))
        assertTrue(replaySynthetic("synthetic:full-cycle",frames,analyzer,1,0).matches)
        assertTrue(replaySynthetic("synthetic:repeat",frames,analyzer,1,0).matches)
        assertTrue(replaySynthetic("synthetic:occluded",fixture(listOf(175.0,175.0,130.0,80.0,175.0,175.0),.1),analyzer,0,0).matches)
        val invalid = JointAngleAnalyzer(Triple(Joint.LEFT_SHOULDER,Joint.LEFT_ELBOW,Joint.LEFT_WRIST),Side.LEFT,{false})
        assertTrue(replaySynthetic("synthetic:unrelated-posture",frames,invalid,0,0).matches)
        assertFailsWith<IllegalArgumentException>{replaySynthetic("human accuracy",frames,analyzer,1,0)}
    }
}
