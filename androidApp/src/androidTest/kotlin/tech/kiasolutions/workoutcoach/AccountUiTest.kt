package tech.kiasolutions.workoutcoach
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
class AccountUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun failedSignInKeepsGuestAvailableAndFieldsAreReachable(){
        compose.waitUntil(15000){compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Email").performScrollTo().performTextInput("invalid-agent@qualification.invalid")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("invalid-synthetic-password")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithText("Couldn’t sign in.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Back to routines").performScrollTo().performClick()
        compose.onNodeWithText("New routine").assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,java.io.FileOutputStream(java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"account-guest.png")))
    }
    @Test fun realNativeSignInAndSignOutReturnToGuest(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.filesDir,"auth-fixture.json")
        Assume.assumeTrue("Private fixture supplied for local qualification only",file.exists())
        val account=org.json.JSONObject(file.readText()).getJSONObject("A")
        compose.waitUntil(15000){compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Email").performScrollTo().performTextInput(account.getString("email"))
        compose.onNodeWithText("Password").performScrollTo().performTextInput(account.getString("password"))
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithText("New routine").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Signed in. Your workouts on this phone belong to this account.").performScrollTo().assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"account-settings.png")))
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("New routine").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals("guest",tech.kiasolutions.workoutcoach.account.AccountScope.namespace)
        file.delete()
    }
}
