package tech.kiasolutions.workoutcoach.core.contracts
import kotlin.time.TimeSource

fun interface WorkoutClock { fun nowMillis(): Long }
/** Each workout uses an elapsed monotonic origin; wall clock is history metadata only. */
class MonotonicWorkoutClock : WorkoutClock {
    private val origin = TimeSource.Monotonic.markNow()
    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}
class ManualWorkoutClock(initialMillis: Long = 0) : WorkoutClock {
    init { require(initialMillis >= 0) }
    private var value = initialMillis
    override fun nowMillis(): Long = value
    fun advanceBy(millis: Long) {
        require(millis >= 0 && value <= Long.MAX_VALUE - millis)
        value += millis
    }
}

