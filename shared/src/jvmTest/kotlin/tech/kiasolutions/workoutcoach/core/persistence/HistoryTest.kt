package tech.kiasolutions.workoutcoach.core.persistence
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.experience.*
import java.util.Properties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
class HistoryTest {
    @Test fun exportAndDeleteAreScopedAndRetainOriginalHoldSemantics(){
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,Properties(),CoachDatabase.Schema).use{d->
            val guest=WorkoutRepository(d,"guest");val account=WorkoutRepository(d,"account:"+newRecordId())
            val id=newRecordId();val name="Routine \"quoted\"\nMāori\t\\path"
            val session=SessionCheckpoint(id,name,1000,WorkoutPhase.COMPLETED,0,0,0)
            val set=StoredSet(0,ExerciseId.PLANK,0,0,30000,29000,true,target=29,holdTiming=HoldTiming.VALID_HOLD)
            guest.completeSet(session,set);account.completeSet(session,set)
            val service=HistoryService(guest);val export=service.export(id)
            assertTrue(export.contains("\\\"quoted\\\"\\nMāori\\t\\\\path"));assertTrue(export.contains("\"holdTiming\":\"VALID_HOLD\""));assertTrue(export.contains("\"target\":29"))
            val fixture=Path.of("build/test-fixtures/workout.json");Files.createDirectories(fixture.parent);Files.writeString(fixture,export)
            val plan=WorkoutPlan(newRecordId(),"Preserved",listOf(ExerciseBlock(ExerciseId.SQUAT,1,SetGoal.Reps(3))));guest.savePlan(plan)
            service.delete(id);service.delete(id);assertTrue(service.entries().isEmpty());assertEquals(1,account.sessions().size);assertEquals(plan.id,guest.plans().single().id);assertEquals(1,account.pendingUploads())
            HistoryService(account).delete(id);assertTrue(account.sessions().isEmpty());assertEquals(1,account.pendingUploads()) // tombstone retained
        }
    }
    @Test fun schemaTwoMigrationPreservesLegacyRowsWithoutInventingTargets(){
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use{d->
            d.execute(null,"CREATE TABLE workout_set(namespace TEXT,session_id TEXT,ordinal INTEGER,exercise TEXT,accepted INTEGER,partial INTEGER,elapsed_ms INTEGER,valid_hold_ms INTEGER,reached_goal INTEGER)",0)
            d.execute(null,"INSERT INTO workout_set VALUES('guest','legacy',0,'PLANK',0,0,30000,10000,1)",0)
            CoachDatabase.Schema.migrate(d,2,3)
            val old=CoachDatabase(d).coachQueries.sets("guest","legacy").executeAsOne()
            assertEquals(30000L,old.elapsed_ms);assertNull(old.target);assertNull(old.hold_timing)
        }
    }
}
