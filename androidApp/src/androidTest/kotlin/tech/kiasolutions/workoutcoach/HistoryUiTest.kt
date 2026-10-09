package tech.kiasolutions.workoutcoach
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.persistence.*
class HistoryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun historyReloadAndCancelledDeletePreserveDataAndConfirmedDeleteIsScoped(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=androidRepository(context,"guest");val id=newRecordId();val other=newRecordId()
        repo.completeSet(SessionCheckpoint(id,"History qualification",System.currentTimeMillis(),WorkoutPhase.COMPLETED,0,0,0),StoredSet(0,ExerciseId.PLANK,0,0,30000,29000,true,target=29,holdTiming=HoldTiming.VALID_HOLD))
        repo.checkpoint(SessionCheckpoint(other,"Preserved history",1000,WorkoutPhase.INTERRUPTED,0,0,0))
        compose.waitUntil(15000){compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("History qualification").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("History qualification").performClick()
        val destination=java.io.File(context.getExternalFilesDir(null),"agent-export.json")
        lateinit var exporter:tech.kiasolutions.workoutcoach.experience.HistoryModel
        destination.delete()
        val holder=androidx.lifecycle.ViewModelStore()
        InstrumentationRegistry.getInstrumentation().runOnMainSync{exporter=tech.kiasolutions.workoutcoach.experience.HistoryModel(context.applicationContext as android.app.Application);holder.put("export",exporter);exporter.export(id,android.net.Uri.fromFile(destination))}
        compose.waitUntil(10000){!exporter.busy && destination.exists()}
        val json=org.json.JSONObject(destination.readText());org.junit.Assert.assertNull(exporter.error);org.junit.Assert.assertEquals(id,json.getJSONObject("workout").getString("id"));org.junit.Assert.assertEquals(1,json.getInt("schemaVersion"));org.junit.Assert.assertEquals(29,json.getJSONObject("workout").getJSONArray("sets").getJSONObject(0).getInt("target"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync{holder.clear()}

        compose.onNodeWithText("Export JSON").assertExists();InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"history-detail.png")));compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Cancel").performClick();compose.onNodeWithText("Export JSON").assertExists()
        compose.onNodeWithText("Delete workout").performClick();compose.onNodeWithText("Delete",useUnmergedTree=true).performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("History qualification").fetchSemanticsNodes().isEmpty()}
        org.junit.Assert.assertTrue(repo.sessions().any{it.id==other});org.junit.Assert.assertTrue(repo.sessions().none{it.id==id});repo.deleteSession(other);repo.close()
    }
}
