package tech.kiasolutions.workoutcoach.experience
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@Composable fun HistoryView(onBack:()->Unit,model:HistoryModel=viewModel(key="history:"+tech.kiasolutions.workoutcoach.account.AccountScope.namespace)){
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var deleting by rememberSaveable{mutableStateOf(false)}
    val entry=model.entries.find{it.checkpoint.id==selected}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null && selected!=null)model.export(selected!!,uri)}
    LaunchedEffect(Unit){model.reload()}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("History",style=MaterialTheme.typography.headlineMedium)
        model.error?.let{Text(it,color=MaterialTheme.colorScheme.error);Button(onClick={model.reload()}){Text("Try again")}}
        if(model.loading)CircularProgressIndicator()
        else if(entry==null){
            if(model.entries.isEmpty())Text("Your completed and interrupted workouts will appear here.")
            LazyColumn(Modifier.weight(1f)){items(model.entries,key={it.checkpoint.id}){e->OutlinedButton(onClick={selected=e.checkpoint.id},modifier=Modifier.fillMaxWidth()){
                Column(Modifier.fillMaxWidth()){Text(e.checkpoint.routineName);Text(DateFormat.getDateTimeInstance().format(Date(e.checkpoint.startedEpochMillis)));Text("${e.status} · ${e.sets.size} recorded sets · ${e.accepted} reps")}
            }}}
            TextButton(onClick=onBack){Text("Back to routines")}
        }else{
            Text(entry.checkpoint.routineName,style=MaterialTheme.typography.headlineSmall)
            Text(entry.status);Text("${entry.accepted} accepted · ${entry.partial} partial reps · ${entry.activeMillis/1000}s recorded active time")
            LazyColumn(Modifier.weight(1f)){items(entry.sets,key={it.ordinal}){set->
                ListItem(headlineContent={Text("Set ${set.ordinal+1} · ${exerciseLabel(set.exercise.name)}")},supportingContent={Column{
                    Text(if(set.reachedGoal)"Goal reached"else "Ended incomplete")
                    Text("${set.accepted} accepted · ${set.partial} partial · ${set.elapsedMillis/1000}s active")
                    if(set.exercise.name=="PLANK")Text("${set.target?.let{"$it seconds"}?:"Target not recorded"} · ${set.holdTiming?.name?.lowercase()?.replace('_',' ')?:"Timing mode not recorded"} · ${set.validHoldMillis/1000}s valid posture observed")
                }})
            }}
            Button(onClick={exporter.launch("workout-${entry.checkpoint.id}.json")},enabled=!model.busy){Text("Export JSON")}
            OutlinedButton(onClick={deleting=true},enabled=!model.busy){Text("Delete workout")}
            TextButton(onClick={selected=null}){Text("Back to history")}
        }
    }
    if(deleting && entry!=null)AlertDialog(onDismissRequest={deleting=false},title={Text("Delete this workout?")},text={Text("Remove this workout from this phone. Your routines and other workouts stay saved.")},confirmButton={TextButton(onClick={model.delete(entry.checkpoint.id){selected=null};deleting=false}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})
}
