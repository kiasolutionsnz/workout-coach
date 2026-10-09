package tech.kiasolutions.workoutcoach.core.contracts
import kotlin.test.*
class CaptureClockTest {
    @Test fun captureEpochIsMappedAndExpiredOrFutureFramesCannotPoisonOrdering(){
        val clock=ManualWorkoutClock(100);val mapper=CaptureClockMapper(clock)
        assertEquals(100L,mapper.map(5000));assertNull(mapper.map(5000))
        assertNull(mapper.map(5200));clock.advanceBy(200);assertEquals(300L,mapper.map(5200))
        clock.advanceBy(1000);assertNull(mapper.map(5300));assertEquals(1300L,mapper.map(6200))
        assertNull(mapper.map(-1));mapper.reset();assertEquals(1300L,mapper.map(10))
    }
}
