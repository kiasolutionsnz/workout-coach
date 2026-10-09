package tech.kiasolutions.workoutcoach.core
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class BuildProbeTest {
    @Test fun arbitraryDurationAcrossTargets() {
        val probe = BuildProbe()
        for (duration in listOf(20, 29, 30, 45, 60)) assertTrue(probe.validDuration(duration))
        assertFalse(probe.validDuration(0))
        assertFalse(probe.validDuration(3601))
        assertEquals(6, probe.supportedExercises().size)
    }
}

