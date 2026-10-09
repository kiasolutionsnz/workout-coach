package tech.kiasolutions.workoutcoach.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import tech.kiasolutions.workoutcoach.core.contracts.*

/** Owned by one inference executor. Images and results remain in process memory. */
class PoseProcessor(context: Context) : AutoCloseable {
    private val model=PoseLandmarker.createFromOptions(context,PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").setDelegate(Delegate.CPU).build())
        .setRunningMode(RunningMode.VIDEO).setNumPoses(1)
        .setMinPoseDetectionConfidence(.65f).setMinPosePresenceConfidence(.65f).setMinTrackingConfidence(.65f).build())
    private var lastTimestamp=-1L;private var sequence=0L
    /** Ownership transfers to MPImage; closing it recycles this bitmap. */
    fun process(uprightBitmap:Bitmap,capturedAtMillis:Long):PoseFrame? {
        if(capturedAtMillis<=lastTimestamp)return null
        require(!uprightBitmap.isRecycled && uprightBitmap.config==Bitmap.Config.ARGB_8888) { "Invalid inference image" }
        lastTimestamp=capturedAtMillis
        val image=BitmapImageBuilder(uprightBitmap).build()
        try {
            val result=model.detectForVideo(image,capturedAtMillis)
            val landmarks=result.landmarks().firstOrNull()
            val points=mutableMapOf<Joint,Landmark>()
            for((joint,index) in indices){
                val l=landmarks?.getOrNull(index)?:continue
                val x=l.x().toDouble();val y=l.y().toDouble()
                val confidence=minOf(l.visibility().orElse(0f),l.presence().orElse(0f)).toDouble()
                if(x.isFinite() && y.isFinite() && confidence.isFinite())points[joint]=Landmark(Point2(x,y),confidence.coerceIn(0.0,1.0))
            }
            return PoseFrame(sequence++,capturedAtMillis,ImageTransform(uprightBitmap.width,uprightBitmap.height),points)
        } finally {image.close()}
    }
    override fun close(){model.close()}
    companion object {
        val indices=mapOf(Joint.NOSE to 0,Joint.LEFT_EAR to 7,Joint.RIGHT_EAR to 8,Joint.LEFT_SHOULDER to 11,Joint.RIGHT_SHOULDER to 12,Joint.LEFT_ELBOW to 13,Joint.RIGHT_ELBOW to 14,Joint.LEFT_WRIST to 15,Joint.RIGHT_WRIST to 16,Joint.LEFT_HIP to 23,Joint.RIGHT_HIP to 24,Joint.LEFT_KNEE to 25,Joint.RIGHT_KNEE to 26,Joint.LEFT_ANKLE to 27,Joint.RIGHT_ANKLE to 28)
    }
}
