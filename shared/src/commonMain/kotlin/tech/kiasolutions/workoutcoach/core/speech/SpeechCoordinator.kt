package tech.kiasolutions.workoutcoach.core.speech
import tech.kiasolutions.workoutcoach.core.contracts.*

enum class CuePriority { FEEDBACK, REP, TIMING }
data class SpeechCue(val id:String,val text:String,val priority:CuePriority,val expiresAtMillis:Long)
interface CueSink { fun speak(cue:SpeechCue):Boolean;fun stop() }

/** One active and one pending cue. Native sinks report completion by ID; obsolete
 * callbacks cannot release a newer cue. No microphone or network is needed by this layer. */
class SpeechCoordinator(private val workoutId:String,private val clock:WorkoutClock,private val sink:CueSink) {
    private var lastSequence=-1L
    private var floor=-1L
    private var enabled=true
    private var active:SpeechCue?=null;private var pending:SpeechCue?=null
    var available=true;private set
    private var exerciseLabel:String?=null
    fun configureExercise(label:String){require(label.isNotBlank() && label.length<=120);exerciseLabel=label}
    private fun expires(now:Long,ttl:Long)=if(now>Long.MAX_VALUE-ttl)Long.MAX_VALUE else now+ttl
    fun accept(event:WorkoutEvent){
        val scope=event.scope;val now=clock.nowMillis()
        if(scope.workoutId!=workoutId || scope.sequence<=lastSequence || scope.atMillis>now || scope.atMillis<floor || now-scope.atMillis>2000)return
        lastSequence=scope.sequence
        if(!enabled)return
        val text=when(event){
            is WorkoutEvent.Ready->(if(scope.blockIndex>0 && scope.setIndex==0)"Stop. Next exercise. "else if(scope.setIndex>0)"Stop. Next set. "else "")+(exerciseLabel?.let{"$it. "}?:"")+"Get ready."
            is WorkoutEvent.Countdown->event.remaining.toString()
            is WorkoutEvent.SetStarted->"Start."
            is WorkoutEvent.AcceptedRep->event.count.toString()
            is WorkoutEvent.SetCompleted->"Stop. Set complete."
            is WorkoutEvent.RestStarted->"Stop. Rest for ${event.seconds} seconds."
            is WorkoutEvent.WorkoutCompleted->"Stop. Workout complete."
            is WorkoutEvent.TrackingChanged->if(!event.reliable)"Move back into view."else return
            else->return
        }
        val priority=if(event is WorkoutEvent.AcceptedRep)CuePriority.REP else CuePriority.TIMING
        enqueue(SpeechCue(scope.id,text,priority,expires(scope.atMillis,if(priority==CuePriority.REP)1500 else 2000)))
    }
    private fun enqueue(cue:SpeechCue){
        poll()
        if(clock.nowMillis()>=cue.expiresAtMillis)return
        val current=active
        if(current==null){play(cue);return}
        if(cue.priority==CuePriority.TIMING){active=null;pending=null;sink.stop();play(cue)}
        else if(pending==null || cue.priority.ordinal>=pending!!.priority.ordinal)pending=cue
    }
    private fun play(cue:SpeechCue){
        active=cue
        available=sink.speak(cue)
        if(!available)active=null
    }
    fun completed(id:String){
        if(active?.id!=id)return
        active=null
        val next=pending;pending=null
        if(enabled && next!=null && clock.nowMillis()<next.expiresAtMillis)play(next)
    }
    fun poll(){
        val now=clock.nowMillis()
        if(pending?.let{now>=it.expiresAtMillis}==true)pending=null
        if(active?.let{now>=it.expiresAtMillis}==true){active=null;sink.stop();val next=pending;pending=null;if(enabled && next!=null)play(next)}
    }
    fun pause(){enabled=false;floor=clock.nowMillis();active=null;pending=null;sink.stop()}
    fun resume(){floor=clock.nowMillis();enabled=true}
}
