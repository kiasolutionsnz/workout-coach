package tech.kiasolutions.workoutcoach
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import tech.kiasolutions.workoutcoach.camera.CameraActivity
import tech.kiasolutions.workoutcoach.core.persistence.androidRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class WorkoutUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun syntheticPoseAndVirtualClockRunTwoSetsHandsFreeAndPersist(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<CameraActivity>(Intent(context,CameraActivity::class.java).putExtra("debugFixture",true)).use{scenario->
            compose.waitUntil(15000){compose.onAllNodesWithText("Begin countdown").fetchSemanticsNodes().isNotEmpty()}
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(5000){compose.onAllNodes(hasText("Resume") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Resume").performClick()
            compose.waitUntil(5000){compose.onAllNodes(hasText("Begin countdown") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Begin countdown").performClick()
            compose.waitUntil(15000){compose.onAllNodesWithText("Workout complete").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(5000){compose.onAllNodesWithText("Completed sets saved on this phone.").fetchSemanticsNodes().isNotEmpty()}
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"workout-complete.png")))
            val repo=androidRepository(context,"guest")
            val workout=repo.sessions().first{it.routineName=="Synthetic two holds"}
            val sets=repo.sets(workout.id);assertEquals(2,sets.size);assertTrue(sets.all{it.reachedGoal && it.elapsedMillis==1000L});repo.close()
        }
    }
}


