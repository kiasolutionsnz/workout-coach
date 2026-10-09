package tech.kiasolutions.workoutcoach
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import tech.kiasolutions.workoutcoach.speech.AndroidSpeaker
import tech.kiasolutions.workoutcoach.core.speech.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class SpeechApiTest {
    @Test fun nativeSpeechInitializesOrReportsMissingOfflineVoiceAndCloses(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val ready=CountDownLatch(1)
        lateinit var speaker:AndroidSpeaker
        instrumentation.runOnMainSync{speaker=AndroidSpeaker(instrumentation.targetContext,{ready.countDown()})}
        Assert.assertTrue("TTS did not finish initialization",ready.await(15,TimeUnit.SECONDS))
        instrumentation.runOnMainSync{
            val accepted=speaker.speak(SpeechCue("qualification","Ready",CuePriority.TIMING,10000))
            if(!speaker.available)Assert.assertFalse(accepted)
            speaker.stop();speaker.close();speaker.close()
            Assert.assertFalse(speaker.speak(SpeechCue("closed","Ready",CuePriority.TIMING,10000)))
        }
    }
}
