package tech.kiasolutions.workoutcoach.core.persistence

import app.cash.sqldelight.db.SqlDriver
import tech.kiasolutions.workoutcoach.core.contracts.*
import tech.kiasolutions.workoutcoach.core.persistence.db.CoachDatabase
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun newRecordId(): String = Uuid.random().toString()
private fun checkId(id: String) = require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(id)) { "Invalid record ID" }

data class SessionCheckpoint(val id: String, val routineName: String, val startedEpochMillis: Long, val phase: WorkoutPhase, val blockIndex: Int, val setIndex: Int, val remainingMillis: Long) {
    init { checkId(id); require(routineName.isNotBlank() && startedEpochMillis >= 0 && blockIndex >= 0 && setIndex >= 0 && remainingMillis >= 0) }
}
data class StoredRep(val ordinal: Int, val side: Side, val durationMillis: Long, val full: Boolean) {
    init { require(ordinal >= 0 && durationMillis >= 0) }
}
data class StoredSet(val ordinal: Int, val exercise: ExerciseId, val accepted: Int, val partial: Int, val elapsedMillis: Long, val validHoldMillis: Long, val reachedGoal: Boolean, val reps: List<StoredRep> = emptyList(),val target:Int?=null,val holdTiming:HoldTiming?=null) {
    init {
        require(ordinal >= 0 && accepted >= 0 && partial >= 0 && elapsedMillis >= 0 && validHoldMillis in 0..elapsedMillis)
        require(target==null || target>0)
        require(reps.map { it.ordinal }.distinct().size == reps.size)
    }
}

/** Scope is fixed at construction. App adapters serialize access off the UI thread.
 * Never replace/delete a database on an open/query error; propagate for recovery UI. */
class WorkoutRepository(private val driver: SqlDriver, val namespace: String) : AutoCloseable {
    init { require(namespace == "guest" || namespace.startsWith("account:").also { if (it) checkId(namespace.removePrefix("account:")) }) }
    private val db = CoachDatabase(driver.also { it.execute(null, "PRAGMA foreign_keys = 1", 0) })
    private val q = db.coachQueries

    @Throws(Exception::class)
    fun savePlan(plan: WorkoutPlan, revision: Long? = null) {
        checkId(plan.id); require(revision == null || revision > 0)
        db.transaction {
            val previous = q.plans(namespace).executeAsList().find { it.id == plan.id }?.revision ?: 0
            require(previous < Long.MAX_VALUE)
            val nextRevision = revision ?: (previous + 1)
            q.putPlan(namespace, plan.id, plan.name, nextRevision)
            q.updatePlan(plan.name, nextRevision, namespace, plan.id)
            q.deleteBlocks(namespace, plan.id)
            plan.blocks.forEachIndexed { i, b ->
                val target = when (val g = b.goal) { is SetGoal.Reps -> g.count; is SetGoal.Duration -> g.seconds }
                q.putBlock(namespace, plan.id, i.toLong(), b.exercise.name, b.sets.toLong(), if (b.goal is SetGoal.Reps) "reps" else "duration", target.toLong(), (b.goal as? SetGoal.Duration)?.timing?.name ?: HoldTiming.ELAPSED.name, b.restSeconds.toLong(), b.limbMode.name)
            }
            if (namespace != "guest") { q.putOutbox(namespace, plan.id, "plan", nextRevision); q.updateOutbox(nextRevision, namespace, plan.id, "plan") }
        }
    }
    @Throws(Exception::class)
    fun plans(): List<WorkoutPlan> = q.plans(namespace).executeAsList().map { p ->
        WorkoutPlan(p.id, p.name, q.blocks(namespace, p.id).executeAsList().map { b ->
            ExerciseBlock(ExerciseId.valueOf(b.exercise), b.sets.toInt(), if (b.goal_kind == "reps") SetGoal.Reps(b.target.toInt()) else SetGoal.Duration(b.target.toInt(), HoldTiming.valueOf(b.hold_timing)), b.rest_seconds.toInt(), LimbMode.valueOf(b.limb_mode))
        })
    }
    @Throws(Exception::class)
    fun checkpoint(session: SessionCheckpoint) {
        db.transaction {
            q.putWorkout(namespace, session.id, session.routineName, session.startedEpochMillis, session.phase.name, session.blockIndex.toLong(), session.setIndex.toLong(), session.remainingMillis)
            q.updateWorkout(session.phase.name, session.blockIndex.toLong(), session.setIndex.toLong(), session.remainingMillis, namespace, session.id)
        }
    }
    @Throws(Exception::class)
    fun sessions(): List<SessionCheckpoint> = q.workouts(namespace).executeAsList().map {
        SessionCheckpoint(it.id, it.routine_name, it.started_epoch_ms, WorkoutPhase.valueOf(it.phase), it.block_index.toInt(), it.set_index.toInt(), it.remaining_ms)
    }
    /** Set, reps, checkpoint and upload intent commit atomically. Retry uses the same IDs. */
    @Throws(Exception::class)
    fun completeSet(session: SessionCheckpoint, set: StoredSet) {
        db.transaction {
            val existing = sets(session.id).find { it.ordinal == set.ordinal }
            require(existing == null || existing == set) { "Completed set differs from saved record" }
            checkpoint(session)
            q.putSet(namespace, session.id, set.ordinal.toLong(), set.exercise.name, set.accepted.toLong(), set.partial.toLong(), set.elapsedMillis, set.validHoldMillis, if (set.reachedGoal) 1 else 0,set.target?.toLong(),set.holdTiming?.name)
            set.reps.forEach { rep -> q.putRep(namespace, session.id, set.ordinal.toLong(), rep.ordinal.toLong(), rep.side.name, rep.durationMillis, if (rep.full) 1 else 0) }
            if (namespace != "guest") q.putOutbox(namespace, session.id, "workout", 1)
        }
    }
    @Throws(Exception::class)
    fun sets(sessionId: String): List<StoredSet> {
        checkId(sessionId)
        return q.sets(namespace, sessionId).executeAsList().map { set ->
            StoredSet(set.ordinal.toInt(), ExerciseId.valueOf(set.exercise), set.accepted.toInt(), set.partial.toInt(), set.elapsed_ms, set.valid_hold_ms, set.reached_goal != 0L,
                q.reps(namespace, sessionId, set.ordinal).executeAsList().map { rep -> StoredRep(rep.ordinal.toInt(), Side.valueOf(rep.side), rep.duration_ms, rep.is_full != 0L) },set.target?.toInt(),set.hold_timing?.let{HoldTiming.valueOf(it)})
        }
    }
    @Throws(Exception::class)
    fun savePreference(key: String, value: String) = savePreferences(mapOf(key to value))
    @Throws(Exception::class)
    fun savePreferences(values:Map<String,String>) {
        require(values.all{(key,value)->key.isNotBlank() && key.length<=100 && value.length<=1000})
        db.transaction{values.forEach{(key,value)->q.putPreference(namespace,key,value);q.updatePreference(value,namespace,key)}}
    }
    @Throws(Exception::class)
    fun preferences(): Map<String, String> = q.preferences(namespace).executeAsList().associate { it.key to it.value_ }
    @Throws(Exception::class)
    fun pendingUploads(): Int = q.pending(namespace).executeAsList().size
    @Throws(Exception::class)
    fun deleteSession(sessionId:String){
        checkId(sessionId)
        db.transaction {
            q.deleteSession(namespace,sessionId)
            q.removeUpload(namespace,sessionId)
            if(namespace!="guest")q.putOutbox(namespace,sessionId,"delete",1)
        }
    }
    override fun close() { driver.close() }
    @Throws(Exception::class)
    fun closeStorage() { close() }
}

