package tech.kiasolutions.workoutcoach.core.experience

import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.detectors.*
import tech.kiasolutions.workoutcoach.core.engine.*
import tech.kiasolutions.workoutcoach.core.persistence.*
import tech.kiasolutions.workoutcoach.core.tracking.*

/** Serialized by the native main thread. Storage is asynchronous: acknowledge only
 * after the immutable set has committed. Camera delivery never drives the clock. */
class WorkoutSession(val plan:WorkoutPlan,val id:String,val startedEpochMillis:Long,private val clock:WorkoutClock) {
    private val engine=WorkoutEngine(plan,id,clock)
    private val tracker=PoseTracker()
    private val selector=StableSideSelector()
    private var analyzer:ExerciseAnalyzer?=null
    private var configuredBlock=-1
    private var configuredSet=-1
    private var automatic=false
    private var savedProgressionPending=false
    private var preparingSince=clock.nowMillis()
    private val events=mutableListOf<WorkoutEvent>()
    private val reps=mutableListOf<StoredRep>()
    private val saved=mutableListOf<StoredSet>()
    val savedSets:List<StoredSet> get()=saved.toList()
    var pendingSet:StoredSet?=null;private set
    var reliable=false;private set
    var cue="Move back until your whole body is visible.";private set
    var movement="UNKNOWN";private set
    val state:WorkoutState get()=engine.state
    val block:ExerciseBlock get()=plan.blocks[state.blockIndex]
    val exerciseName:String get()=when(block.exercise){ExerciseId.SQUAT->"Squat";ExerciseId.PUSH_UP->"Push-up";ExerciseId.CURL->"Bicep curl";ExerciseId.LUNGE->"Lunge";ExerciseId.SHOULDER_PRESS->"Shoulder press";ExerciseId.PLANK->"Plank"}
    val nextLabel:String get(){
        val nextIndex=if(state.setIndex+1<block.sets)state.blockIndex else (state.blockIndex+1).coerceAtMost(plan.blocks.lastIndex)
        val next=plan.blocks[nextIndex];val number=if(nextIndex==state.blockIndex)state.setIndex+2 else 1
        val name=when(next.exercise){ExerciseId.SQUAT->"Squat";ExerciseId.PUSH_UP->"Push-up";ExerciseId.CURL->"Bicep curl";ExerciseId.LUNGE->"Lunge";ExerciseId.SHOULDER_PRESS->"Shoulder press";ExerciseId.PLANK->"Plank"}
        return "$name · set $number of ${next.sets}"
    }
    val placement:String get()=when(block.exercise){ExerciseId.CURL,ExerciseId.SHOULDER_PRESS->"Face the camera with both arms visible.";else->"Place the camera side-on, with shoulders, hips and feet visible."}
    val progressText:String get()=when(val goal=block.goal){is SetGoal.Reps->"${state.accepted} / ${goal.count} reps";is SetGoal.Duration->"${(state.remainingMillis+999)/1000} seconds · ${if(goal.timing==HoldTiming.ELAPSED)"elapsed time" else "valid hold time"}"}
    init { engine.prepare();collect() }
    fun checkpoint()=SessionCheckpoint(id,plan.name,startedEpochMillis,state.phase,state.blockIndex,state.setIndex,state.remainingMillis)
    fun drainEvents():List<WorkoutEvent> = events.toList().also{events.clear()}
    private fun collect(){
        engine.drainEvents().forEach{event->
            events+=event
            if(event is WorkoutEvent.Ready)preparingSince=clock.nowMillis()
            if(event is WorkoutEvent.SetStarted){resetDetection();reps.clear()}
            if(event is WorkoutEvent.SetCompleted){
                val ordinal=plan.blocks.take(state.blockIndex).sumOf{it.sets}+state.setIndex
                pendingSet=StoredSet(ordinal,block.exercise,state.accepted,state.partial,state.elapsedMillis,state.validHoldMillis.coerceAtMost(state.elapsedMillis),event.reachedGoal,reps.toList(),when(val goal=block.goal){is SetGoal.Reps->goal.count;is SetGoal.Duration->goal.seconds},(block.goal as? SetGoal.Duration)?.timing)
            }
        }
        if(configuredBlock!=state.blockIndex || configuredSet!=state.setIndex){configuredBlock=state.blockIndex;configuredSet=state.setIndex;resetDetection()}
    }
    private fun resetDetection(){tracker.reset();selector.reset();analyzer=null;reliable=false;movement="UNKNOWN";cue=placement}
    private fun makeAnalyzer(side:Side)=when(block.exercise){
        ExerciseId.SQUAT->SquatAnalyzer(side=side)
        ExerciseId.PUSH_UP->PushUpAnalyzer(side=side)
        ExerciseId.CURL->CurlAnalyzer(block.limbMode)
        ExerciseId.LUNGE->LungeAnalyzer(mode=block.limbMode)
        ExerciseId.SHOULDER_PRESS->ShoulderPressAnalyzer()
        ExerciseId.PLANK->PlankAnalyzer(side)
    }
    fun observe(frame:PoseFrame){
        if(state.phase !in listOf(WorkoutPhase.PREPARING,WorkoutPhase.COUNTDOWN,WorkoutPhase.ACTIVE_SET))return
        val pose=tracker.observe(frame,clock.nowMillis())?:return
        fun visible(side:Side):Boolean {
            val prefix=side.name+"_"
            val names=when(block.exercise){ExerciseId.SQUAT,ExerciseId.LUNGE->listOf("SHOULDER","HIP","KNEE","ANKLE");ExerciseId.CURL,ExerciseId.SHOULDER_PRESS->listOf("SHOULDER","ELBOW","WRIST","HIP");else->listOf("SHOULDER","ELBOW","WRIST","HIP","ANKLE")}
            return names.all{pose.points.containsKey(Joint.valueOf(prefix+it))}
        }
        val changed=selector.observe(visible(Side.LEFT),visible(Side.RIGHT))
        val side=selector.selected
        if(side==null){reliable=false;engine.trackingChanged(false);collect();return}
        if(changed || analyzer==null)analyzer=makeAnalyzer(side)
        val result=analyzer!!.analyze(pose)
        reliable=result.reliable;movement=result.phase.name;cue=result.cue ?: if(reliable)"Position visible" else placement
        engine.trackingChanged(reliable)
        if(state.phase==WorkoutPhase.ACTIVE_SET){
            result.reps.forEach{rep->
                if(state.phase==WorkoutPhase.ACTIVE_SET){
                    // Squat observes one visible leg as a proxy for a whole-body rep.
                    val countedSide=if(block.exercise==ExerciseId.SQUAT)Side.BOTH else rep.side
                    reps+=StoredRep(reps.size,countedSide,rep.durationMillis,rep.full);engine.rep(rep.full,countedSide)
                }
            }
            engine.holdObservation(pose.atMillis,result.holdValid)
        }
        collect()
        if(automatic && state.phase==WorkoutPhase.PREPARING && reliable && clock.nowMillis()-preparingSince>=2000)begin()
    }
    fun tick(){
        if(tracker.expire(clock.nowMillis())){reliable=false;cue=placement;analyzer?.reset();engine.trackingChanged(false)}
        engine.tick();collect()
        if(automatic && state.phase==WorkoutPhase.PREPARING && reliable && clock.nowMillis()-preparingSince>=2000)begin()
    }
    fun begin(){if(state.phase==WorkoutPhase.PREPARING && reliable){automatic=true;engine.startCountdown();collect()}}
    fun pause(interrupted:Boolean){engine.pause(interrupted);resetDetection();collect()}
    fun resume(){if(state.phase in listOf(WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED) && reliable){
        engine.resume();resetDetection();collect()
        if(savedProgressionPending && state.phase==WorkoutPhase.SET_COMPLETE){savedProgressionPending=false;engine.continueAfterSet();collect()}
    }}
    /** Paused camera frames can establish framing without updating a movement cycle. */
    fun framing(frame:PoseFrame){
        if(state.phase !in listOf(WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED)){observe(frame);return}
        val pose=tracker.observe(frame,clock.nowMillis())?:return
        reliable=pose.points.keys.containsAll(listOf(Joint.LEFT_SHOULDER,Joint.LEFT_HIP,Joint.LEFT_ANKLE)) || pose.points.keys.containsAll(listOf(Joint.RIGHT_SHOULDER,Joint.RIGHT_HIP,Joint.RIGHT_ANKLE))
        cue=if(reliable)"Position visible. Resume when ready." else placement
    }
    fun endSet(){if(state.phase in listOf(WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED)){engine.resume()};engine.finishEarly();collect()}
    fun skipRest(){engine.skipRest();collect()}
    fun cancel(){engine.cancel();savedProgressionPending=false;resetDetection();collect()}
    fun acknowledgeSet(){if(pendingSet!=null){
        saved+=pendingSet!!;pendingSet=null
        if(state.phase==WorkoutPhase.SET_COMPLETE){engine.continueAfterSet();collect()}
        else if(state.phase in listOf(WorkoutPhase.PAUSED,WorkoutPhase.INTERRUPTED))savedProgressionPending=true
    }}
}
