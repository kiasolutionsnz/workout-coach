package tech.kiasolutions.workoutcoach.experience

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.kiasolutions.workoutcoach.camera.CameraActivity
import tech.kiasolutions.workoutcoach.core.contracts.ExerciseId
import tech.kiasolutions.workoutcoach.core.experience.*

fun exerciseLabel(id:String)=when(id){"SQUAT"->"Squat";"PUSH_UP"->"Push-up";"CURL"->"Bicep curl";"LUNGE"->"Lunge";"SHOULDER_PRESS"->"Shoulder press";else->"Plank"}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CoachApp(account:tech.kiasolutions.workoutcoach.account.AccountModel=viewModel()){
    val accountState by account.state.collectAsState()
    key(accountState.namespace){CoachContent(account,viewModel(key="app:"+accountState.namespace))}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CoachContent(account:tech.kiasolutions.workoutcoach.account.AccountModel,model:AppModel){
    val state by model.state.collectAsState()
    var screen by rememberSaveable{mutableStateOf("routines")}
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    val selected=state.routines.find{it.id==selectedId}
    var editing by rememberSaveable{mutableStateOf(false)}
    var discard by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    BackHandler(editing || selected!=null){if(editing)discard=true else selectedId=null}
    Scaffold(topBar={TopAppBar(title={Text(if(editing)"Edit routine"else selected?.name?:"Workout Coach")})}){padding->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(state.error!=null)Text(state.error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("form-error"))
            when{
                state.loading->CircularProgressIndicator(Modifier.padding(24.dp))
                editing && model.draft!=null->RoutineEditor(model.draft!!,state.saving,{model.updateDraft(it)},{model.save{editing=false;selectedId=null}},{discard=true})
                selected!=null->{
                    val routine=selected!!
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(routine.blocks){b->ListItem(headlineContent={Text(exerciseLabel(b.exercise))},supportingContent={Text("${b.sets} sets · ${b.target} ${if(b.exercise=="PLANK")"seconds"else "reps"} · ${b.restSeconds}s rest")})}}
                    Button(onClick={context.startActivity(Intent(context,CameraActivity::class.java).putExtra("routineId",routine.id).putExtra("frontCamera",state.settings.frontCamera))},modifier=Modifier.fillMaxWidth()){Text("Prepare workout")}
                    OutlinedButton(onClick={model.edit(routine);editing=true},modifier=Modifier.fillMaxWidth()){Text("Edit routine")}
                    TextButton(onClick={selectedId=null}){Text("Back to routines")}
                }
                screen=="history"->HistoryView(onBack={screen="routines"})
                screen=="settings"->{
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Settings",style=MaterialTheme.typography.headlineSmall)
                    ListItem(headlineContent={Text("Spoken cues")},supportingContent={Text("Hear countdowns and rep numbers.")},trailingContent={Switch(state.settings.voice,{model.voice(it)})})
                    ListItem(headlineContent={Text("Use front camera")},supportingContent={Text("Use the rear camera when this is off.")},trailingContent={Switch(state.settings.frontCamera,{model.frontCamera(it)})})
                    Text("Workouts and camera processing work on your phone. An account is optional.")
                    tech.kiasolutions.workoutcoach.account.AccountView(account)
                    TextButton(onClick={screen="routines"}){Text("Back to routines")}
                    }
                }
                else->{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Your routines",style=MaterialTheme.typography.headlineSmall);TextButton(onClick={screen="history"}){Text("History")}
                        TextButton(onClick={screen="settings"}){Text("Settings")}}
                    Text("Choose a routine or make one your own.")
                    LazyColumn(Modifier.weight(1f).testTag("routine-list"),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        items(state.routines,key={it.id}){routine->Card(onClick={selectedId=routine.id},modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(16.dp)){Text(routine.name,style=MaterialTheme.typography.titleMedium);Text("${routine.blocks.size} exercises · ${routine.blocks.sumOf{it.sets}} sets")}
                        }}
                    }
                    Button(onClick={model.edit(null);editing=true},enabled=state.error==null,modifier=Modifier.fillMaxWidth()){Text("New routine")}
                    if(state.error!=null)OutlinedButton(onClick={model.reload()}){Text("Try again")}
                }
            }
        }
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Discard changes?")},text={Text("Your saved routine will stay as it is.")},confirmButton={TextButton(onClick={model.discard();editing=false;discard=false}){Text("Discard")}},dismissButton={TextButton(onClick={discard=false}){Text("Keep editing")}})
}

@Composable private fun RoutineEditor(initial:EditableRoutine,saving:Boolean,onChange:(EditableRoutine)->Unit,onSave:()->Unit,onCancel:()->Unit){
    var draft by remember(initial.id){mutableStateOf(initial)}
    fun change(value:EditableRoutine){draft=value;onChange(value)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(draft.name,{change(draft.copy(name=it))},label={Text("Routine name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("routine-name"))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
            items(draft.blocks.size){index->
                val block=draft.blocks[index]
                fun update(value:EditableBlock){change(draft.copy(blocks=draft.blocks.toMutableList().also{it[index]=value}))}
                ElevatedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        var menu by remember{mutableStateOf(false)}
                        Box{OutlinedButton(onClick={menu=true}){Text(exerciseLabel(block.exercise))};DropdownMenu(menu,{menu=false}){ExerciseId.entries.forEach{exercise->DropdownMenuItem(text={Text(exerciseLabel(exercise.name))},onClick={update(block.copy(exercise=exercise.name,target=if(exercise==ExerciseId.PLANK)30 else 10,limbMode=if(exercise==ExerciseId.LUNGE)"ALTERNATING"else "BILATERAL"));menu=false})}}}
                        NumberField("Sets",block.sets,"sets-$index"){update(block.copy(sets=it))}
                        NumberField(if(block.exercise=="PLANK")"Seconds"else "Reps",block.target,"target-$index"){update(block.copy(target=it))}
                        NumberField("Rest seconds",block.restSeconds,"rest-$index"){update(block.copy(restSeconds=it))}
                        if(block.exercise=="CURL" || block.exercise=="LUNGE"){
                            var modes by remember{mutableStateOf(false)}
                            val choices=if(block.exercise=="LUNGE")listOf("ALTERNATING","LEFT","RIGHT")else listOf("BILATERAL","ALTERNATING","LEFT","RIGHT")
                            Box{TextButton(onClick={modes=true}){Text("Count: ${block.limbMode.lowercase().replaceFirstChar{it.uppercase()}}")};DropdownMenu(modes,{modes=false}){choices.forEach{mode->DropdownMenuItem(text={Text(mode.lowercase().replaceFirstChar{it.uppercase()})},onClick={update(block.copy(limbMode=mode));modes=false})}}}
                            Text(if(block.exercise=="LUNGE")"Each completed leg movement counts as one rep."else "Bilateral pairs both arms. Alternating counts each arm separately.",style=MaterialTheme.typography.bodySmall)
                        }
                        if(block.exercise=="PLANK")ListItem(headlineContent={Text("Count valid hold time")},supportingContent={Text("When off, the timer uses elapsed time.")},trailingContent={Switch(block.holdTiming=="VALID_HOLD",{update(block.copy(holdTiming=if(it)"VALID_HOLD"else "ELAPSED"))})})
                        TextButton(onClick={change(draft.copy(blocks=draft.blocks.filterIndexed{i,_->i!=index}))},enabled=draft.blocks.size>1){Text("Remove exercise")}
                    }
                }
            }
            item{OutlinedButton(onClick={change(draft.copy(blocks=draft.blocks+EditableBlock("SQUAT",1,10,30)))},enabled=draft.blocks.size<50){Text("Add exercise")}}
        }
        Button(onClick=onSave,enabled=!saving,modifier=Modifier.fillMaxWidth()){Text(if(saving)"Saving…"else "Save routine")}
        TextButton(onClick=onCancel,enabled=!saving){Text("Cancel")}
    }
}
@Composable private fun NumberField(label:String,value:Int,tag:String,onValue:(Int)->Unit){
    var text by remember(tag){mutableStateOf(if(value<0)""else value.toString())}
    LaunchedEffect(value){if(value>=0 && text.toIntOrNull()!=value)text=value.toString()}
    OutlinedTextField(text,{text=it;onValue(it.toIntOrNull()?:-1)},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag(tag))
}
