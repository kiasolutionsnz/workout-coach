package tech.kiasolutions.workoutcoach
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tech.kiasolutions.workoutcoach.camera.*
import tech.kiasolutions.workoutcoach.core.contracts.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CameraTest {
    @Test fun bundledModelRunsOfflineAndRejectsRepeatedTimestamp(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val hash=context.assets.open("pose_landmarker_lite.task").use{MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString(""){b->"%02x".format(b)}}
        Assert.assertEquals("59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a",hash)
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        try{PoseProcessor(context).use{p->
            val frame=p.process(bitmap,0)!!
            Assert.assertTrue(frame.joints.isEmpty());Assert.assertEquals(64,frame.transform.width)
            Assert.assertNull(p.process(bitmap,0))
            Assert.assertTrue(bitmap.isRecycled)
            try {p.process(bitmap,1);Assert.fail("Recycled image accepted")}catch(_:IllegalArgumentException){}
            Assert.assertNotNull(p.process(Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888),1))
        }}finally{bitmap.recycle()}
    }
    @Test fun deniedPermissionDoesNotStartCaptureAndCloseIsIdempotent(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
            Assert.assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,activity.checkSelfPermission(android.Manifest.permission.CAMERA))
            val controller=CameraController(activity,ManualWorkoutClock(),{Assert.fail("Denied capture emitted a frame")},{})
            controller.start(activity,PreviewView(activity));Assert.assertEquals(CameraStatus.PERMISSION_REQUIRED,controller.status)
            controller.close();controller.close();Assert.assertEquals(CameraStatus.STOPPED,controller.status)
        }}
    }
    @Test fun grantedVirtualCameraDeliversFrameAndReleasesOwnedUseCases(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName,android.Manifest.permission.CAMERA)
        val frameReceived=CountDownLatch(1);var controller:CameraController?=null
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->
                val view=PreviewView(activity);activity.setContentView(view)
                controller=CameraController(activity,MonotonicWorkoutClock(),{frameReceived.countDown()},{})
                controller!!.start(activity,view)
            }
            try{Assert.assertTrue("Virtual camera frame not delivered",frameReceived.await(30,TimeUnit.SECONDS))}
            finally{scenario.onActivity{controller!!.close();Assert.assertEquals(CameraStatus.STOPPED,controller!!.status)}}
        }
    }
}
