package tech.kiasolutions.workoutcoach.core.persistence
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase

@Throws(Exception::class)
fun iosRepository(namespace: String): WorkoutRepository =
    WorkoutRepository(NativeSqliteDriver(CoachDatabase.Schema, "workout-coach.db"), namespace)

@Throws(Exception::class)
fun iosRoutineService(namespace: String): tech.kiasolutions.workoutcoach.core.experience.RoutineService =
    tech.kiasolutions.workoutcoach.core.experience.RoutineService(iosRepository(namespace))
