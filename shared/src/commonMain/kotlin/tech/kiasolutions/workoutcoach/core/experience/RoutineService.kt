package tech.kiasolutions.workoutcoach.core.experience

import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.persistence.*

data class EditableBlock(val exercise:String,val sets:Int,val target:Int,val restSeconds:Int,val limbMode:String="BILATERAL",val holdTiming:String="ELAPSED")
data class EditableRoutine(val id:String,val name:String,val blocks:List<EditableBlock>)
data class CoachSettings(val voice:Boolean,val frontCamera:Boolean)

/** Native screens retain drafts locally until Save. Validation is shared, then the entire
 * routine commits atomically. Call from the same serialized storage scope as the repository. */
class RoutineService(private val repository:WorkoutRepository) {
    fun validationMessage(routine:EditableRoutine):String? {
        if(routine.name.isBlank() || routine.name.length>120)return "Enter a routine name of 1 to 120 characters."
        if(routine.blocks.isEmpty() || routine.blocks.size>50)return "Choose between 1 and 50 exercises."
        for(block in routine.blocks){
            if(ExerciseId.entries.none{it.name==block.exercise})return "Choose a supported exercise."
            if(block.sets !in 1..20)return "Sets must be between 1 and 20."
            if(block.target !in 1..(if(block.exercise=="PLANK")3600 else 100))return if(block.exercise=="PLANK")"Duration must be between 1 and 3600 seconds."else "Reps must be between 1 and 100."
            if(block.restSeconds !in 0..3600)return "Rest must be between 0 and 3600 seconds."
            if(LimbMode.entries.none{it.name==block.limbMode})return "Choose a supported side mode."
            if(block.limbMode!="BILATERAL" && block.exercise !in listOf("CURL","LUNGE"))return "Choose both sides for this exercise."
            if(block.exercise=="PLANK" && HoldTiming.entries.none{it.name==block.holdTiming})return "Choose elapsed time or valid hold time."
        }
        return null
    }
    @Throws(Exception::class)
    fun routines():List<EditableRoutine> = repository.plans().map { plan->EditableRoutine(plan.id,plan.name,plan.blocks.map { block->
        EditableBlock(block.exercise.name,block.sets,when(val goal=block.goal){is SetGoal.Reps->goal.count;is SetGoal.Duration->goal.seconds},block.restSeconds,block.limbMode.name,(block.goal as? SetGoal.Duration)?.timing?.name ?: "ELAPSED")
    }) }
    @Throws(Exception::class)
    fun workoutPlan(routine:EditableRoutine):WorkoutPlan = WorkoutPlan(routine.id,routine.name.trim(),routine.blocks.map {block->
        val exercise=ExerciseId.valueOf(block.exercise)
        ExerciseBlock(exercise,block.sets,if(exercise==ExerciseId.PLANK)SetGoal.Duration(block.target,HoldTiming.valueOf(block.holdTiming))else SetGoal.Reps(block.target),block.restSeconds,LimbMode.valueOf(block.limbMode))
    })
    @Throws(Exception::class)
    fun save(routine:EditableRoutine){repository.savePlan(workoutPlan(routine))}
    @Throws(Exception::class)
    fun settings():CoachSettings {
        val values=repository.preferences()
        return CoachSettings(values["voice"]!="false",values["frontCamera"]!="false")
    }
    @Throws(Exception::class)
    fun saveSettings(settings:CoachSettings){repository.savePreferences(mapOf("voice" to settings.voice.toString(),"frontCamera" to settings.frontCamera.toString()))}
    @Throws(Exception::class)
    fun seedIfEmpty(){
        if(repository.plans().isNotEmpty())return
        save(EditableRoutine(newRecordId(),"Full body",listOf(
            EditableBlock("SQUAT",2,10,30),EditableBlock("PUSH_UP",2,8,30),EditableBlock("CURL",2,10,30),EditableBlock("LUNGE",2,8,30,"ALTERNATING"),EditableBlock("SHOULDER_PRESS",2,10,30),EditableBlock("PLANK",1,30,30))))
        save(EditableRoutine(newRecordId(),"Core and legs",listOf(EditableBlock("SQUAT",2,10,30),EditableBlock("LUNGE",2,8,30,"ALTERNATING"),EditableBlock("PLANK",1,29,30))))
    }
    @Throws(Exception::class)
    fun close(){repository.close()}
}
