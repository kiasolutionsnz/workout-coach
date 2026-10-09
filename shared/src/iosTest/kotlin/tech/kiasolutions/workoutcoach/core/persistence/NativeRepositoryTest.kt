package tech.kiasolutions.workoutcoach.core.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase
import kotlin.test.*

class NativeRepositoryTest {
    @Test fun nativeSqlitePersistsAcrossConnections() {
        val filename = "qualification-${newRecordId()}.db"
        WorkoutRepository(NativeSqliteDriver(CoachDatabase.Schema, filename), "guest").use { repo ->
            repo.savePreference("voice", "false")
        }
        WorkoutRepository(NativeSqliteDriver(CoachDatabase.Schema, filename), "guest").use { repo ->
            assertEquals("false", repo.preferences()["voice"])
            assertEquals(0, repo.pendingUploads())
        }
    }
}
