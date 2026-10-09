package tech.kiasolutions.workoutcoach.core.persistence
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase

fun androidRepository(context: Context, namespace: String): WorkoutRepository =
    WorkoutRepository(AndroidSqliteDriver(CoachDatabase.Schema, context, "workout-coach.db"), namespace)

