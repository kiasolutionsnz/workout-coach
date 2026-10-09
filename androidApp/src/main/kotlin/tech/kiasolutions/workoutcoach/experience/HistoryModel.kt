package tech.kiasolutions.workoutcoach.experience
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import tech.kiasolutions.workoutcoach.core.experience.*
import tech.kiasolutions.workoutcoach.core.persistence.*

class HistoryModel(application:Application):AndroidViewModel(application){
    private val namespace=tech.kiasolutions.workoutcoach.account.AccountScope.namespace
    private val executor=Executors.newSingleThreadExecutor()
    private val storage=executor.asCoroutineDispatcher()
    private val service by lazy{HistoryService(androidRepository(application,namespace))}
    var entries:List<HistoryEntry> by mutableStateOf(emptyList());private set
    var error:String? by mutableStateOf(null);private set
    var loading by mutableStateOf(true);private set
    var busy by mutableStateOf(false);private set
    init{reload()}
    fun reload(){viewModelScope.launch{try{entries=withContext(storage){service.entries()};loading=false;error=null}catch(_:Exception){loading=false;error="Couldn’t read history. Your data has been kept."}}}
    fun delete(id:String,onDone:()->Unit){if(busy)return;busy=true;viewModelScope.launch{try{withContext(storage){service.delete(id)};busy=false;reload();onDone()}catch(_:Exception){busy=false;error="Couldn’t delete this workout. Try again."}}}
    fun export(id:String,uri:Uri){if(busy)return;busy=true;viewModelScope.launch{try{withContext(storage){val text=service.export(id);getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray(Charsets.UTF_8))}?:error("No destination")};busy=false;error=null}catch(_:Exception){busy=false;error="Couldn’t export this workout. Choose a destination and try again."}}}
    override fun onCleared(){executor.execute{service.close()};executor.shutdown();super.onCleared()}
}
