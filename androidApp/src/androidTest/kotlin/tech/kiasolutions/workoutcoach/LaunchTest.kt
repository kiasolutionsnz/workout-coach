package tech.kiasolutions.workoutcoach
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @Test fun applicationIdentity() {
        assertEquals("tech.kiasolutions.workoutcoach", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertEquals(false, activity.isFinishing) }
        }
    }
}
