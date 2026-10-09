package tech.kiasolutions.workoutcoach.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import tech.kiasolutions.workoutcoach.core.contracts.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class CameraStatus { STOPPED, PERMISSION_REQUIRED, STARTING, RUNNING, UNAVAILABLE }
/** CameraX owns one latest pending frame; this single worker performs synchronous inference.
 * Main-thread lifecycle ownership; callbacks are delivered on main. Preview mirrors the front
 * lens, inference remains unmirrored and upright, preserving anatomical joint identities. */
class CameraController(private val context:Context,private val clock:WorkoutClock,private val onPose:(PoseFrame)->Unit,private val onStatus:(CameraStatus)->Unit,private val permissionAllowed:()->Boolean={ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED}):AutoCloseable {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=ContextCompat.getMainExecutor(context)
    private val closed=AtomicBoolean(false)
    private val failed=AtomicBoolean(false)
    private val latestPose=AtomicReference<PoseFrame?>(null)
    private val deliveryScheduled=AtomicBoolean(false)
    private var provider:ProcessCameraProvider?=null
    private var preview:Preview?=null;private var analysis:ImageAnalysis?=null
    private var processor:PoseProcessor?=null
    private val captureClock=CaptureClockMapper(clock)
    var status=CameraStatus.STOPPED;private set
    var previewMirrored=false;private set
    private fun status(value:CameraStatus){status=value;onStatus(value)}
    private fun deliverLatest(){
        if(!deliveryScheduled.compareAndSet(false,true))return
        main.execute{
            val frame=latestPose.getAndSet(null)
            if(frame!=null && !closed.get())onPose(frame)
            deliveryScheduled.set(false)
            if(latestPose.get()!=null && !closed.get())deliverLatest()
        }
    }
    fun start(owner:LifecycleOwner,view:PreviewView,front:Boolean=true) {
        check(!closed.get());check(status==CameraStatus.STOPPED || status==CameraStatus.PERMISSION_REQUIRED)
        if(!permissionAllowed()){status(CameraStatus.PERMISSION_REQUIRED);return}
        status(CameraStatus.STARTING)
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            if(closed.get())return@addListener
            try {
                val cameraProvider=future.get();provider=cameraProvider
                var selector=if(front)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if(!cameraProvider.hasCamera(selector))selector=if(front)CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                if(!cameraProvider.hasCamera(selector)){status(CameraStatus.UNAVAILABLE);return@addListener}
                previewMirrored=selector==CameraSelector.DEFAULT_FRONT_CAMERA
                val resolution=ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                val p=Preview.Builder().setResolutionSelector(resolution).build();p.surfaceProvider=view.surfaceProvider;preview=p
                val a=ImageAnalysis.Builder().setResolutionSelector(resolution).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();analysis=a
                a.setAnalyzer(worker){image->
                    if(closed.get() || failed.get()){image.close();return@setAnalyzer}
                    val captured=captureClock.map(image.imageInfo.timestamp/1_000_000)
                    if(captured==null){image.close();return@setAnalyzer}
                    var raw:Bitmap?=null;var upright:Bitmap?=null
                    try {
                        if(processor==null)processor=PoseProcessor(context)
                        raw=image.toBitmap()
                        upright=if(image.imageInfo.rotationDegrees==0)raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply{postRotate(image.imageInfo.rotationDegrees.toFloat())},true)
                        val frame=processor!!.process(upright,captured)
                        if(frame!=null){latestPose.set(frame);deliverLatest()}
                    }catch(_:Exception){
                        if(failed.compareAndSet(false,true))main.execute{if(!closed.get()){a.clearAnalyzer();cameraProvider.unbind(a);status(CameraStatus.UNAVAILABLE)}}
                    }
                    finally{if(upright!==raw && upright?.isRecycled==false)upright.recycle();if(raw?.isRecycled==false)raw.recycle();image.close()}
                }
                cameraProvider.bindToLifecycle(owner,selector,p,a);status(CameraStatus.RUNNING)
            }catch(_:Exception){status(CameraStatus.UNAVAILABLE)}
        },main)
    }
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        latestPose.set(null)
        analysis?.clearAnalyzer()
        val useCases=listOfNotNull(preview,analysis).toTypedArray()
        if(useCases.isNotEmpty())provider?.unbind(*useCases)
        worker.execute{processor?.close();processor=null};worker.shutdown()
        status(CameraStatus.STOPPED)
    }
}
