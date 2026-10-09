package tech.kiasolutions.workoutcoach.core.contracts

enum class ExerciseId { SQUAT, PUSH_UP, CURL, LUNGE, SHOULDER_PRESS, PLANK }
enum class LimbMode { BILATERAL, ALTERNATING, LEFT, RIGHT }
enum class HoldTiming { ELAPSED, VALID_HOLD }
sealed interface SetGoal {
    data class Reps(val count: Int) : SetGoal { init { require(count in 1..100) } }
    data class Duration(val seconds: Int, val timing: HoldTiming = HoldTiming.ELAPSED) : SetGoal { init { require(seconds in 1..3600) } }
}
data class ExerciseBlock(val exercise: ExerciseId, val sets: Int, val goal: SetGoal, val restSeconds: Int = 30, val limbMode: LimbMode = LimbMode.BILATERAL) {
    init {
        require(sets in 1..20 && restSeconds in 0..3600)
        require(exercise != ExerciseId.PLANK || goal is SetGoal.Duration)
        require(limbMode == LimbMode.BILATERAL || exercise == ExerciseId.CURL || exercise == ExerciseId.LUNGE)
    }
}
class WorkoutPlan(val id: String, val name: String, blocks: List<ExerciseBlock>) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(name.isNotBlank() && name.length <= 120)
        require(blocks.isNotEmpty() && blocks.size <= 50)
    }
    val blocks: List<ExerciseBlock> = blocks.toList()
}
enum class WorkoutPhase { IDLE, PREPARING, COUNTDOWN, ACTIVE_SET, SET_COMPLETE, RESTING, COMPLETED, PAUSED, INTERRUPTED, CANCELLED }
enum class RepPhase { READY, LOWERING, DEPTH, RETURNING }
enum class Side { LEFT, RIGHT, BOTH }
data class EventScope(val workoutId: String, val blockIndex: Int, val setIndex: Int, val sequence: Long, val atMillis: Long) {
    init { require(workoutId.isNotBlank() && blockIndex >= 0 && setIndex >= 0 && sequence >= 0 && atMillis >= 0) }
    val id: String get() = "$workoutId:$sequence"
}
sealed interface WorkoutEvent {
    val scope: EventScope
    data class Ready(override val scope: EventScope) : WorkoutEvent
    data class Countdown(override val scope: EventScope, val remaining: Int) : WorkoutEvent { init { require(remaining in 1..10) } }
    data class SetStarted(override val scope: EventScope) : WorkoutEvent
    data class AcceptedRep(override val scope: EventScope, val count: Int, val side: Side) : WorkoutEvent { init { require(count > 0) } }
    data class PartialRep(override val scope: EventScope, val side: Side) : WorkoutEvent
    data class HoldProgress(override val scope: EventScope, val validMillis: Long) : WorkoutEvent { init { require(validMillis >= 0) } }
    data class TrackingChanged(override val scope: EventScope, val reliable: Boolean) : WorkoutEvent
    data class SetCompleted(override val scope: EventScope, val reachedGoal: Boolean) : WorkoutEvent
    data class RestStarted(override val scope: EventScope, val seconds: Int) : WorkoutEvent { init { require(seconds >= 0) } }
    data class WorkoutCompleted(override val scope: EventScope) : WorkoutEvent
}

