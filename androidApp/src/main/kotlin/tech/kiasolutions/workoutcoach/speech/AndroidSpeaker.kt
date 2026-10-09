package tech.kiasolutions.workoutcoach.speech
import android.content.Context
import android.media.*
import android.os.*
import android.speech.tts.*
import tech.kiasolutions.workoutcoach.core.speech.*

class AndroidSpeaker(context:Context,private val onAvailability:(Boolean)->Unit={},private val onFinished:(String)->Unit={},private val onInterruption:(Boolean)->Unit={}):CueSink,AutoCloseable {
    private val main=Handler(Looper.getMainLooper())
    private val audio=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).setOnAudioFocusChangeListener({change->
        if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){stop();onInterruption(true)}
        else if(change==AudioManager.AUDIOFOCUS_GAIN)onInterruption(false)
    },main).build()
    private var closed=false
    private var currentId:String?=null
    var available=false;private set
    private var tts:TextToSpeech?=null
    init {
        tts=TextToSpeech(context){result->
            if(closed)return@TextToSpeech
            val engine=tts
            val voice=if(result==TextToSpeech.SUCCESS)engine?.voices?.filter{it.locale.language=="en" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features}?.sortedBy{if(it.locale.toLanguageTag()=="en-NZ")0 else if(it.locale.toLanguageTag()=="en-US")1 else 2}?.firstOrNull()else null
            available=voice!=null
            if(voice!=null){available=engine!!.setVoice(voice)==TextToSpeech.SUCCESS;engine.setAudioAttributes(attributes);engine.setSpeechRate(1f)}
            engine?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                override fun onStart(id:String){}
                override fun onDone(id:String){main.post{if(!closed){if(id==currentId){currentId=null;audio.abandonAudioFocusRequest(focus)};onFinished(id)}}}
                @Deprecated("Platform legacy callback") override fun onError(id:String){main.post{if(!closed && id==currentId){currentId=null;available=false;audio.abandonAudioFocusRequest(focus);onAvailability(false);onFinished(id)}}}
            })
            onAvailability(available)
        }
    }
    override fun speak(cue:SpeechCue):Boolean {
        if(closed || !available)return false
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false
        currentId=cue.id
        val accepted=tts?.speak(cue.text,TextToSpeech.QUEUE_FLUSH,Bundle(),cue.id)==TextToSpeech.SUCCESS
        if(!accepted){currentId=null;audio.abandonAudioFocusRequest(focus)}
        return accepted
    }
    override fun stop(){currentId=null;tts?.stop();audio.abandonAudioFocusRequest(focus)}
    override fun close(){if(closed)return;closed=true;stop();tts?.shutdown();tts=null;available=false}
}
