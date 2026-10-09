package tech.kiasolutions.workoutcoach.experience

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tech.kiasolutions.workoutcoach.core.experience.*
import tech.kiasolutions.workoutcoach.core.persistence.*
import java.util.concurrent.Executors

data class AppState(val loading:Boolean=true,val routines:List<EditableRoutine> = emptyList(),val settings:CoachSettings=CoachSettings(true,true),val error:String?=null,val saving:Boolean=false)
class AppModel(application:Application):AndroidViewModel(application){
    private val namespace=tech.kiasolutions.workoutcoach.account.AccountScope.namespace
    private val executor=Executors.newSingleThreadExecutor()
    private val storage=executor.asCoroutineDispatcher()
    private val repository by lazy{androidRepository(application,namespace)}
    private val service by lazy{RoutineService(repository)}
    private val mutable=MutableStateFlow(AppState())
    val state:StateFlow<AppState> = mutable.asStateFlow()
    var draft:EditableRoutine?=null;private set
    init{reload()}
    fun reload(){viewModelScope.launch{
        try{
            val loaded=withContext(storage){service.seedIfEmpty();service.routines() to service.settings()}
            mutable.value=AppState(false,loaded.first,loaded.second)
        }catch(_:Exception){mutable.value=mutable.value.copy(loading=false,error="Couldn’t open saved workouts. Your data has been kept. Try again.")}
    }}
    fun edit(routine:EditableRoutine?){draft=routine?:EditableRoutine(newRecordId(),"",listOf(EditableBlock("SQUAT",1,10,30)));mutable.value=mutable.value.copy(error=null)}
    fun updateDraft(value:EditableRoutine){draft=value;mutable.value=mutable.value.copy(error=null)}
    fun discard(){draft=null;mutable.value=mutable.value.copy(error=null)}
    fun save(onSuccess:()->Unit){
        val value=draft?:return
        val message=service.validationMessage(value)
        if(message!=null){mutable.value=mutable.value.copy(error=message);return}
        if(mutable.value.saving)return
        mutable.value=mutable.value.copy(saving=true,error=null)
        viewModelScope.launch{
            try{val routines=withContext(storage){service.save(value);service.routines()};mutable.value=mutable.value.copy(routines=routines,saving=false);draft=null;onSuccess()}
            catch(_:Exception){mutable.value=mutable.value.copy(saving=false,error="Couldn’t save this routine. Your changes are still here. Try again.")}
        }
    }
    fun voice(enabled:Boolean)=settings(mutable.value.settings.copy(voice=enabled))
    fun frontCamera(enabled:Boolean)=settings(mutable.value.settings.copy(frontCamera=enabled))
    private fun settings(value:CoachSettings){
        mutable.value=mutable.value.copy(settings=value,error=null)
        viewModelScope.launch{
        try{withContext(storage){service.saveSettings(value)}}
        catch(_:Exception){mutable.value=mutable.value.copy(error="Couldn’t save settings. Try again.")}
    }}
    override fun onCleared(){executor.execute{repository.close()};executor.shutdown();super.onCleared()}
}
