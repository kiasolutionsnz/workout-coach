package tech.kiasolutions.workoutcoach.core

/** Build integration probe only; no workout functionality is claimed. */
class BuildProbe {
    fun supportedExercises(): List<String> = listOf("squat", "push-up", "curl", "lunge", "shoulder press", "plank")
    fun validDuration(seconds: Int): Boolean = seconds in 1..3600
}

