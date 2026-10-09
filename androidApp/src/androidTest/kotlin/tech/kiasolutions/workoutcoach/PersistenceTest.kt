package tech.kiasolutions.workoutcoach

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tech.kiasolutions.workoutcoach.core.persistence.*

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun nativeAndroidDatabaseSurvivesReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = newRecordId()
        androidRepository(context, "guest").use { it.savePreference(key, "persisted") }
        androidRepository(context, "guest").use {
            assertEquals("persisted", it.preferences()[key])
            assertEquals(0, it.pendingUploads())
        }
    }
}
