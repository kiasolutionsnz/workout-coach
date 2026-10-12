package tech.kiasolutions.workoutcoach.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import tech.kiasolutions.workoutcoach.core.speech.*

class VoiceControl(private val context:Context,private val command:(VoiceAction)->Unit):RecognitionListener {
    var enabled by mutableStateOf(false);private set
    var status by mutableStateOf("Voice control is off");private set
    private val main=Handler(Looper.getMainLooper())
    private var recognizer:SpeechRecognizer?=null
    private var generation=0
    private var closed=false
    fun enable(){
        if(enabled || closed)return
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)){status="On-device English voice control unavailable. Use the buttons.";return}
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){status="Microphone permission is off. Use the buttons.";return}
        try{generation++;val token=generation;recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also{it.setRecognitionListener(listener(token))};enabled=true;listen()}catch(_:Exception){disable();status="On-device voice control unavailable. Use the buttons."}
    }
    private fun listen(){
        if(!enabled)return
        try{recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false));status="Listening on-device. Begin commands with Coach."}catch(_:Exception){disable();status="Voice listening stopped. Enable voice control to retry."}
    }
    private fun listener(token:Int)=object:RecognitionListener {
        override fun onResults(results:Bundle?){if(token==generation)this@VoiceControl.onResults(results)}
        override fun onError(error:Int){if(token==generation)this@VoiceControl.onError(error)}
        override fun onReadyForSpeech(params:Bundle?){}
        override fun onBeginningOfSpeech(){}
        override fun onRmsChanged(rmsdB:Float){}
        override fun onBufferReceived(buffer:ByteArray?){}
        override fun onEndOfSpeech(){}
        override fun onPartialResults(partialResults:Bundle?){}
        override fun onEvent(eventType:Int,params:Bundle?){}
    }
    fun close(){closed=true;disable()}
    fun disable(){enabled=false;generation++;main.removeCallbacksAndMessages(null);recognizer?.cancel();recognizer?.destroy();recognizer=null;status="Voice control is off"}
    private fun restart(){val token=generation;main.postDelayed({if(enabled && token==generation)listen()},500)}
    override fun onResults(results:Bundle?){if(!enabled)return;results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{parseVoiceCommand(it)?.let(command)};restart()}
    override fun onError(error:Int){if(!enabled)return;if(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)restart()else{disable();status="On-device voice listening stopped. Enable voice control to retry."}}
    override fun onReadyForSpeech(params:Bundle?){}
    override fun onBeginningOfSpeech(){}
    override fun onRmsChanged(rmsdB:Float){}
    override fun onBufferReceived(buffer:ByteArray?){}
    override fun onEndOfSpeech(){}
    override fun onPartialResults(partialResults:Bundle?){}
    override fun onEvent(eventType:Int,params:Bundle?){}
}

@Composable fun VoiceControlPanel(onCommand:(VoiceAction)->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val current by rememberUpdatedState(onCommand)
    val control=remember{VoiceControl(context){current(it)}}
    val owner=LocalLifecycleOwner.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))control.enable()}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE)control.disable()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);control.close()}}
    TextButton(onClick={if(control.enabled)control.disable()else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)control.enable()else permission.launch(Manifest.permission.RECORD_AUDIO)}){Text(if(control.enabled)"Disable voice control"else "Enable voice control")}
    Text(control.status,style=MaterialTheme.typography.bodySmall)
    Text("Optional microphone access. On-device English commands; audio and transcripts are not saved. Say Coach before each command.",style=MaterialTheme.typography.bodySmall)
}
