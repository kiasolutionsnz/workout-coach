package tech.kiasolutions.workoutcoach.core.persistence
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.*

class RepositoryTest {
    private fun driver(url: String) = JdbcSqliteDriver(url, Properties(), CoachDatabase.Schema)
    @Test fun restartPreservesPlansRecordsAndPreferencesAndScopesAccounts() {
        val file = Files.createTempDirectory("wco-db").resolve("coach.db")
        val url = "jdbc:sqlite:$file"
        val plan = WorkoutPlan(newRecordId(), "Squat and plank", listOf(ExerciseBlock(ExerciseId.SQUAT, 2, SetGoal.Reps(10)), ExerciseBlock(ExerciseId.PLANK, 1, SetGoal.Duration(29))))
        val id = newRecordId()
        driver(url).use { d ->
            val guest = WorkoutRepository(d, "guest")
            guest.savePlan(plan); guest.savePreference("voice", "true")
            guest.completeSet(SessionCheckpoint(id, "Squat and plank", 1000, WorkoutPhase.RESTING, 0, 0, 30000), StoredSet(0, ExerciseId.SQUAT, 1, 0, 3000, 0, false, listOf(StoredRep(0, Side.BOTH, 3000, true))))
            assertEquals(0, guest.pendingUploads())
            assertTrue(WorkoutRepository(d, "account:" + newRecordId()).sessions().isEmpty())
        }
        driver(url).use { d ->
            val guest = WorkoutRepository(d, "guest")
            assertEquals(29, (guest.plans().single().blocks.last().goal as SetGoal.Duration).seconds)
            assertEquals("true", guest.preferences()["voice"])
            assertEquals(id, guest.sessions().single().id)
            assertEquals(1, guest.sets(id).single().accepted)
        }
    }
    @Test fun transactionRollbackAndRetriesDoNotLoseOrDuplicateHistory() {
        driver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            val db = CoachDatabase(d)
            val repo = WorkoutRepository(d, "account:" + newRecordId())
            val session = SessionCheckpoint(newRecordId(), "Squat", 1000, WorkoutPhase.RESTING, 0, 0, 30000)
            val set = StoredSet(0, ExerciseId.SQUAT, 1, 0, 1000, 0, true)
            repo.completeSet(session, set); repo.completeSet(session, set)
            assertEquals(1, repo.sessions().size); assertEquals(1, repo.sets(session.id).size); assertEquals(1, repo.pendingUploads())
            assertFailsWith<IllegalStateException> {
                db.transaction { repo.checkpoint(session.copy(id = newRecordId())); error("simulated interrupted transaction") }
            }
            assertEquals(1, repo.sessions().size)
        }
    }
    @Test fun migrationAndMalformedDatabasePreserveData() {
        driver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            d.execute(null, "DROP INDEX workout_namespace_started", 0)
            CoachDatabase.Schema.migrate(d, 1, 2)
            CoachDatabase.Schema.migrate(d, 1, 2)
            assertEquals(3L, CoachDatabase.Schema.version)
        }
        val file = Files.createTempDirectory("wco-corrupt").resolve("coach.db")
        val content = "not a sqlite database".toByteArray()
        Files.write(file, content)
        assertFails { driver("jdbc:sqlite:$file").close() }
        assertContentEquals(content, Files.readAllBytes(file))
    }
    @Test fun updatingPlansAndRejectingConflictsPreservesSavedHistory() {
        driver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            val repo = WorkoutRepository(d, "guest")
            val planId = newRecordId()
            repo.savePlan(WorkoutPlan(planId, "Original", listOf(ExerciseBlock(ExerciseId.SQUAT, 1, SetGoal.Reps(5)))))
            repo.savePlan(WorkoutPlan(planId, "Updated", listOf(ExerciseBlock(ExerciseId.PLANK, 1, SetGoal.Duration(45)))), 2)
            assertEquals("Updated", repo.plans().single().name)
            assertEquals(ExerciseId.PLANK, repo.plans().single().blocks.single().exercise)
            val session = SessionCheckpoint(newRecordId(), "Original", 1000, WorkoutPhase.RESTING, 0, 0, 0)
            val saved = StoredSet(0, ExerciseId.SQUAT, 5, 0, 10000, 0, true)
            repo.completeSet(session, saved)
            assertFailsWith<IllegalArgumentException> { repo.completeSet(session.copy(phase = WorkoutPhase.COMPLETED), saved.copy(accepted = 6)) }
            assertEquals(saved, repo.sets(session.id).single())
            assertEquals(WorkoutPhase.RESTING, repo.sessions().single().phase)
            assertFails { CoachDatabase(d).coachQueries.putSet("guest", newRecordId(), 0, "SQUAT", 0, 0, 0, 0, 0, null, null) }
        }
    }
}
