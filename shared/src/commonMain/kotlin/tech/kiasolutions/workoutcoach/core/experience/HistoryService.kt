package tech.kiasolutions.workoutcoach.core.experience
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.persistence.*

data class HistoryEntry(val checkpoint:SessionCheckpoint,val sets:List<StoredSet>){
    val accepted:Int get()=sets.sumOf{it.accepted}
    val partial:Int get()=sets.sumOf{it.partial}
    val activeMillis:Long get()=sets.sumOf{it.elapsedMillis}
    val status:String get()=when(checkpoint.phase){WorkoutPhase.COMPLETED->"Completed";WorkoutPhase.CANCELLED->"Ended incomplete";else->"Interrupted · recorded sets kept"}
}
class HistoryService(private val repository:WorkoutRepository){
    @Throws(Exception::class)
    fun entries():List<HistoryEntry> = repository.sessions().map{HistoryEntry(it,repository.sets(it.id))}
    @Throws(Exception::class)
    fun entry(id:String):HistoryEntry? = repository.sessions().find{it.id==id}?.let{HistoryEntry(it,repository.sets(id))}
    @Throws(Exception::class)
    fun delete(id:String)=repository.deleteSession(id)
    @Throws(Exception::class)
    fun export(id:String):String {
        val entry=checkNotNull(entry(id));val session=entry.checkpoint
        return "{\"schemaVersion\":1,\"workout\":{\"id\":${jsonString(session.id)},\"routine\":${jsonString(session.routineName)},\"startedEpochMillis\":${session.startedEpochMillis},\"status\":${jsonString(entry.status)},\"sets\":["+entry.sets.joinToString(","){s->
            "{\"ordinal\":${s.ordinal},\"exercise\":${jsonString(s.exercise.name)},\"target\":${s.target ?: "null"},\"holdTiming\":${s.holdTiming?.name?.let{jsonString(it)}?:"null"},\"accepted\":${s.accepted},\"partial\":${s.partial},\"activeMillis\":${s.elapsedMillis},\"validHoldMillis\":${s.validHoldMillis},\"reachedGoal\":${s.reachedGoal},\"reps\":["+s.reps.joinToString(","){r->"{\"ordinal\":${r.ordinal},\"side\":${jsonString(r.side.name)},\"durationMillis\":${r.durationMillis},\"full\":${r.full}}"}+"]}"}+"]}}"
    }
    @Throws(Exception::class)
    fun close()=repository.close()
}
private fun jsonString(value:String):String=buildString {
    append('"')
    value.forEach{c->when(c){'"'->append("\\\"");'\\'->append("\\\\");'\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t");else->if(c.code<32)append("\\u"+c.code.toString(16).padStart(4,'0'))else append(c)}}
    append('"')
}
