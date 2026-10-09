package tech.kiasolutions.workoutcoach.core.contracts
import kotlin.test.*

class ContractTest {
    @Test fun replayFixtureDecodeRejectsMalformedAndDuplicateJoints() {
        val prefix = "1\t100\t900\t1600\tNONE\tfalse\t"
        val frame = PoseFixtureCodec.decode(prefix + "LEFT_KNEE,0.5,0.8,0.9")
        assertEquals(100, frame.capturedAtMillis)
        assertEquals(0.9, frame.joints.getValue(Joint.LEFT_KNEE).confidence)
        assertFailsWith<IllegalArgumentException> { PoseFixtureCodec.decode(prefix + "LEFT_KNEE,0,0,1;LEFT_KNEE,0,0,1") }
        assertFailsWith<IllegalArgumentException> { PoseFixtureCodec.decode(prefix + "LEFT_KNEE,NaN,0,1") }
        assertFailsWith<IllegalArgumentException> { PoseFixtureCodec.decode("1\t100") }
    }
    @Test fun portraitGeometryIsIsotropic() {
        val tr = ImageTransform(900, 1600)
        val p = tr.geometry(Point2(1.0, 1.0))
        assertEquals(1.0, p.x)
        assertEquals(1600.0 / 900, p.y)
    }
    @Test fun rotationThenMirroringIsExplicit() {
        val tr = ImageTransform(900, 1600, Rotation.CLOCKWISE_90, mirrored = true)
        val p = tr.geometry(Point2(0.25, 0.75))
        assertEquals(0.75, p.x)
        assertEquals(225.0 / 1600, p.y)
        assertEquals(1600, tr.orientedWidth)
    }
    @Test fun nonfiniteAndInvalidConfidenceRejected() {
        assertFailsWith<IllegalArgumentException> { Point2(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { Landmark(Point2(0.0, 0.0), 1.1) }
        assertFailsWith<IllegalArgumentException> { ImageTransform(0, 1600) }
    }
    @Test fun frameInputIsSnapshottedAndOrderingStrict() {
        val data = mutableMapOf(Joint.NOSE to Landmark(Point2(0.5, 0.2), 0.9))
        val tr = ImageTransform(900, 1600)
        val f = PoseFrame(1, 100, tr, data)
        data.clear()
        assertEquals(1, f.joints.size)
        val guard = FrameOrderGuard()
        assertTrue(guard.accept(f))
        assertFalse(guard.accept(f))
        assertFalse(guard.accept(PoseFrame(2, 99, tr, emptyMap())))
        assertTrue(guard.accept(PoseFrame(3, 101, tr, emptyMap())))
    }
    @Test fun clockCannotGoBackOrOverflow() {
        val c = ManualWorkoutClock(100)
        c.advanceBy(29_000)
        assertEquals(29_100, c.nowMillis())
        assertFailsWith<IllegalArgumentException> { c.advanceBy(-1) }
        assertFailsWith<IllegalArgumentException> { ManualWorkoutClock(Long.MAX_VALUE).advanceBy(1) }
    }
    @Test fun durationAndLimbContractsAreUnambiguous() {
        for (s in listOf(20, 29, 30, 45, 60)) assertEquals(s, SetGoal.Duration(s).seconds)
        assertFailsWith<IllegalArgumentException> { ExerciseBlock(ExerciseId.PLANK, 1, SetGoal.Reps(10)) }
        assertFailsWith<IllegalArgumentException> { ExerciseBlock(ExerciseId.SQUAT, 1, SetGoal.Reps(10), limbMode = LimbMode.LEFT) }
        val scope = EventScope("session-a", 0, 0, 3, 100)
        assertEquals("session-a:3", scope.id)
        assertFailsWith<IllegalArgumentException> { WorkoutEvent.AcceptedRep(scope, 0, Side.LEFT) }
    }
}
