package tech.kiasolutions.workoutcoach.core.persistence
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import tech.kiasolutions.workoutcoach.core.experience.*
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase
import java.util.Properties
import kotlin.test.*

class RoutineServiceTest {
    @Test fun presetsCoverSixExercisesAndCustomDurationsAndSettingsRoundTrip(){
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,Properties(),CoachDatabase.Schema).use{d->
            val service=RoutineService(WorkoutRepository(d,"guest"));service.seedIfEmpty();service.seedIfEmpty()
            assertEquals(2,service.routines().size)
            assertEquals(6,service.routines().flatMap{it.blocks}.map{it.exercise}.distinct().size)
            val routine=EditableRoutine(newRecordId(),"Custom",listOf(EditableBlock("PLANK",3,45,60,holdTiming="VALID_HOLD")))
            for(seconds in listOf(20,29,30,45,60)){
                val exact=routine.copy(blocks=listOf(routine.blocks.single().copy(target=seconds)))
                service.save(exact);assertEquals(exact,service.routines().find{it.id==routine.id})
            }
            service.saveSettings(CoachSettings(false,false));assertEquals(CoachSettings(false,false),service.settings())
        }
    }
    @Test fun invalidDraftCannotOverwriteExistingPlanAndEditsIncreaseRevision(){
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,Properties(),CoachDatabase.Schema).use{d->
            val service=RoutineService(WorkoutRepository(d,"guest"));service.seedIfEmpty();val original=service.routines().first()
            assertFailsWith<IllegalArgumentException>{service.save(original.copy(blocks=listOf(EditableBlock("PLANK",1,0,30))))}
            assertEquals(original,service.routines().first())
            service.save(original.copy(name="Edited"))
            assertEquals(2L,CoachDatabase(d).coachQueries.plans("guest").executeAsList().find{it.id==original.id}!!.revision)
        }
    }
}
