package tech.kiasolutions.workoutcoach.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.experience.WorkoutModel
import tech.kiasolutions.workoutcoach.core.experience.poseLines

class CameraActivity:ComponentActivity(){
    private val model:WorkoutModel by viewModels()
    private var permission by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private val request=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->permission=granted;denied=!granted}
    override fun onResume(){super.onResume();permission=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED}
    override fun onPause(){model.pause(true);super.onPause()}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.load(intent.getStringExtra("routineId")?:"",intent.getBooleanExtra("debugFixture",false))
        setContent{MaterialTheme{Surface(Modifier.fillMaxSize()){
            val run=model.session;val refresh=model.revision
            var ending by remember{mutableStateOf(false)}
            BackHandler{if(run==null)finish()else if(run.state.phase in listOf(WorkoutPhase.COMPLETED,WorkoutPhase.CANCELLED)){if(!model.saving && model.error==null)finish()}else ending=true}
            if(ending)AlertDialog(onDismissRequest={ending=false},title={Text("End workout?")},text={Text("Completed sets stay saved. The current set will end incomplete.")},confirmButton={TextButton(onClick={model.endSet();model.cancel();ending=false}){Text("End workout")}},dismissButton={TextButton(onClick={ending=false}){Text("Keep going")}})
            Column(Modifier.safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(run==null){Text(if(model.error==null)"Preparing workout…"else "Workout unavailable");OutlinedButton(onClick={finish()}){Text("Back to routines")}}else{
                    val state=run.state
                    Text(run.exerciseName,style=MaterialTheme.typography.headlineMedium)
                    Text("Set ${state.setIndex+1} of ${run.block.sets} · ${state.phase.name.lowercase().replace('_',' ')}")
                    Text(when(state.phase){WorkoutPhase.COUNTDOWN->"${(state.remainingMillis+999)/1000}";WorkoutPhase.RESTING->"Rest · ${(state.remainingMillis+999)/1000} seconds";WorkoutPhase.COMPLETED->"Workout complete";WorkoutPhase.CANCELLED->"Workout ended";else->run.progressText},style=MaterialTheme.typography.headlineLarge)
                    if(state.phase==WorkoutPhase.RESTING)Text("Next: ${run.nextLabel}")
                    Text("${state.partial} partial reps · ${run.movement.lowercase()}")
                    if(state.phase !in listOf(WorkoutPhase.COMPLETED,WorkoutPhase.CANCELLED)){
                        Text(run.placement)
                        if(model.fixture){Text("Synthetic pose stream · agent verification")}else if(!permission){
                            Text(if(denied)"Camera access is off. Enable it in Settings."else "Enable camera access to check your position.")
                            Button(onClick={if(denied)startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))else request.launch(Manifest.permission.CAMERA)}){Text(if(denied)"Open Settings"else "Enable camera")}
                        }else{
                            var status by remember{mutableStateOf(CameraStatus.STARTING)}
                            var frame by remember{mutableStateOf<PoseFrame?>(null)}
                            var mirrored by remember{mutableStateOf(false)}
                            val preview=remember{PreviewView(this@CameraActivity).apply{scaleType=PreviewView.ScaleType.FIT_CENTER}}
                            DisposableEffect(preview){
                                lateinit var controller:CameraController
                                controller=CameraController(this@CameraActivity,model.clock,{mirrored=controller.previewMirrored;frame=it;model.pose(it)},{status=it;if(it==CameraStatus.UNAVAILABLE)model.pause(true)})
                                controller.start(this@CameraActivity,preview,front=intent.getBooleanExtra("frontCamera",true))
                                mirrored=controller.previewMirrored
                                onDispose{controller.close()}
                            }
                            Box(Modifier.height(200.dp).fillMaxWidth()){
                                AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
                                Canvas(Modifier.fillMaxSize()){
                                    frame?.takeIf{model.clock.nowMillis()-it.capturedAtMillis<=250}?.let{f->val scale=minOf(size.width/f.transform.orientedWidth,size.height/f.transform.orientedHeight);val w=f.transform.orientedWidth*scale;val h=f.transform.orientedHeight*scale
                                        fun screen(p:Point2)=Offset((size.width-w)/2+(if(mirrored)1-p.x else p.x).toFloat()*w,(size.height-h)/2+p.y.toFloat()*w)
                                        poseLines(f).forEach{drawLine(Color.Green,screen(it.start),screen(it.end),3f)}
                                        f.joints.values.filter{it.confidence>=.65}.forEach{l->val p=f.transform.geometry(l.point);val x=if(mirrored)1-p.x else p.x;drawCircle(Color.Green,5f,Offset((size.width-w)/2+x.toFloat()*w,(size.height-h)/2+p.y.toFloat()*w))}}
                                }
                            }
                            Text(if(status==CameraStatus.UNAVAILABLE)"Camera unavailable. Return and try again."else if(run.reliable)run.cue else "Tracking paused. ${run.cue}")
                        }
                        if(model.voiceEnabled && !model.voiceAvailable)Text("Spoken cues unavailable. Follow the on-screen cues.")

                    }else{Text(if(model.saving)"Saving…"else if(model.error==null)"Completed sets saved on this phone."else "Saving needs attention.");run.savedSets.forEach{set->Text("Set ${set.ordinal+1} · ${set.accepted} accepted · ${set.partial} partial · ${set.elapsedMillis/1000}s active · ${if(set.reachedGoal)"goal reached" else "incomplete"}")}}
                }
                }
                model.error?.let{Text(it);Button(onClick={model.retry()}){Text(if(run==null)"Try again"else "Retry save")}}
                if(run!=null){
                    val state=run.state
                    when(state.phase){
                        WorkoutPhase.PREPARING->Button(onClick={model.begin()},enabled=run.reliable && !model.saving){Text("Begin countdown")}
                        WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED->Button(onClick={model.resume()},enabled=run.reliable){Text("Resume")}
                        WorkoutPhase.RESTING->Button(onClick={model.skipRest()}){Text("Skip rest")}
                        WorkoutPhase.SET_COMPLETE->Text(if(model.saving)"Saving set…"else "Set complete")
                        WorkoutPhase.COMPLETED,WorkoutPhase.CANCELLED->Button(onClick={finish()},enabled=!model.saving && model.error==null){Text("Done")}
                        else->Button(onClick={model.pause()}){Text("Pause")}
                    }
                    if(state.phase !in listOf(WorkoutPhase.COMPLETED,WorkoutPhase.CANCELLED)){
                        if(state.phase in listOf(WorkoutPhase.ACTIVE_SET,WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED))OutlinedButton(onClick={model.endSet()}){Text("End set incomplete")}
                        TextButton(onClick={ending=true}){Text("End workout")}
                    }
                }
            }
        }}}
    }
}
