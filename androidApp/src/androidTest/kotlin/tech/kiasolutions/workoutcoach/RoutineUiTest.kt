package tech.kiasolutions.workoutcoach
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun invalidDraftStaysOpenAndValidRoutinePersistsAfterRecreate(){
        waitFor("New routine");compose.onNodeWithText("New routine").performClick()
        compose.onNodeWithText("Save routine").performClick()
        compose.onNodeWithTag("form-error").assertTextContains("Enter a routine name",substring=true)
        val name="Agent routine ${System.nanoTime()}"
        compose.onNodeWithTag("routine-name").performTextInput(name)
        compose.onNodeWithText("Squat").performClick();compose.onNodeWithText("Plank").performClick()
        compose.onNodeWithTag("target-0").performTextClearance();compose.onNodeWithTag("target-0").performTextInput("45")
        compose.onNodeWithText("Save routine").performClick();waitFor("New routine")
        compose.activityRule.scenario.recreate();waitFor("New routine")
        compose.onNodeWithTag("routine-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithText("1 sets · 45 seconds · 30s rest").assertExists()
    }
    @Test fun cancelRequiresDiscardAndDoesNotSaveDraft(){
        waitFor("New routine");compose.onNodeWithText("New routine").performClick()
        compose.onNodeWithTag("routine-name").performTextInput("Discarded draft")
        compose.onNodeWithText("Cancel").performClick();compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("routine-name").assertTextContains("Discarded draft")
        compose.onNodeWithText("Cancel").performClick();compose.onNodeWithText("Discard",useUnmergedTree=true).performClick()
        waitFor("New routine");compose.onNodeWithText("Discarded draft").assertDoesNotExist()
    }
}
