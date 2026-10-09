package tech.kiasolutions.workoutcoach.experience

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import androidx.compose.runtime.*
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.experience.*
import tech.kiasolutions.workoutcoach.core.persistence.*
import tech.kiasolutions.workoutcoach.core.speech.*
import tech.kiasolutions.workoutcoach.speech.AndroidSpeaker
import java.util.concurrent.Executors

class WorkoutModel(application:Application):AndroidViewModel(application){
    var clock:WorkoutClock=MonotonicWorkoutClock();private set
    var fixture=false;private set
    private var fixtureSequence=0L
    private val executor=Executors.newSingleThreadExecutor()
    private val storage=executor.asCoroutineDispatcher()
    private var repository:WorkoutRepository?=null
    var session:WorkoutSession? by mutableStateOf(null);private set
    var revision by mutableIntStateOf(0);private set
    var error:String? by mutableStateOf(null);private set
    var saving by mutableStateOf(false);private set
    var voiceAvailable by mutableStateOf(true);private set
    var voiceEnabled by mutableStateOf(true);private set
    private var speaker:AndroidSpeaker?=null
    private var speech:SpeechCoordinator?=null
    private var lastPhase:WorkoutPhase?=null
    private var loading=false
    private var interruptedBeforeLoad=false
    private var requestedId=""
    private var requestedFixture=false
    fun load(routineId:String,debugFixture:Boolean=false){
        if(session!=null || loading)return
        requestedId=routineId;requestedFixture=debugFixture;loading=true;error=null
        fixture=debugFixture && (getApplication<Application>().applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        if(fixture)clock=ManualWorkoutClock()
        viewModelScope.launch{
            try{
                val pair=withContext(storage){
                    val repo=repository ?: androidRepository(getApplication(),tech.kiasolutions.workoutcoach.account.AccountScope.namespace).also{repository=it}
                    (if(fixture)WorkoutPlan(newRecordId(),"Synthetic two holds",listOf(ExerciseBlock(ExerciseId.PLANK,2,SetGoal.Duration(1),1)))else repo.plans().first{it.id==routineId}) to RoutineService(repo).settings()
                }
                voiceEnabled=pair.second.voice
                val run=WorkoutSession(pair.first,newRecordId(),System.currentTimeMillis(),clock);session=run;loading=false
                if(interruptedBeforeLoad)run.pause(true)
                speaker=AndroidSpeaker(getApplication(),{voiceAvailable=it},{speech?.completed(it)},{if(it)pause(true)})
                speech=SpeechCoordinator(run.id,clock,speaker!!).also{if(!voiceEnabled || interruptedBeforeLoad)it.pause()}
                update()
                while(isActive){delay(50)
                    if(fixture){(clock as ManualWorkoutClock).advanceBy(100);pose(PoseFrame(fixtureSequence++,clock.nowMillis(),ImageTransform(100,100),mapOf(Joint.LEFT_SHOULDER to Landmark(Point2(.2,.4),1.0),Joint.LEFT_ELBOW to Landmark(Point2(.2,.55),1.0),Joint.LEFT_WRIST to Landmark(Point2(.35,.55),1.0),Joint.LEFT_HIP to Landmark(Point2(.5,.4),1.0),Joint.LEFT_ANKLE to Landmark(Point2(.9,.4),1.0))))}
                    run.tick();speech?.poll();update()}
            }catch(_:Exception){loading=false;error="Couldn’t open this workout. Your saved data has been kept."}
        }
    }
    fun pose(frame:PoseFrame){session?.framing(frame);update()}
    fun begin(){session?.begin();update()}
    fun pause(interrupted:Boolean=false){if(session==null && interrupted)interruptedBeforeLoad=true;session?.pause(interrupted);speech?.pause();update()}
    fun resume(){session?.resume();if(voiceEnabled)speech?.resume();update()}
    fun endSet(){session?.endSet();update()}
    fun skipRest(){session?.skipRest();update()}
    fun cancel(){session?.cancel();speech?.pause();update()}
    fun retry(){if(session==null){load(requestedId,requestedFixture);return};error=null;lastPhase=null;update()}
    private fun update(){
        val run=session?:return
        speech?.configureExercise(run.exerciseName)
        run.drainEvents().forEach{speech?.accept(it)}
        val phase=run.state.phase
        if(!saving && error==null && (run.pendingSet!=null || phase!=lastPhase)){
            saving=true;val set=run.pendingSet;val checkpoint=run.checkpoint()
            viewModelScope.launch{
                try{withContext(storage){val repo=checkNotNull(repository);if(set!=null)repo.completeSet(checkpoint,set)else repo.checkpoint(checkpoint)}
                    lastPhase=checkpoint.phase;saving=false
                    if(set!=null)run.acknowledgeSet()
                    update()
                }catch(_:Exception){saving=false;error="Couldn’t save workout progress. Keep this screen open and retry.";session?.pause(true);speech?.pause()}
            }
        }
        revision++
    }
    override fun onCleared(){speaker?.close();executor.execute{repository?.close()};executor.shutdown();super.onCleared()}
}
