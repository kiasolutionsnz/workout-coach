package tech.kiasolutions.workoutcoach.core.engine

import tech.kiasolutions.workoutcoach.core.contracts.*

data class WorkoutState(val phase: WorkoutPhase, val blockIndex: Int, val setIndex: Int, val remainingMillis: Long, val accepted: Int, val partial: Int, val elapsedMillis: Long, val validHoldMillis: Long)

/** Native adapters call tick from a monotonic scheduler independent of camera delivery.
 * All calls are serialized. No callback means no valid-hold credit. */
class WorkoutEngine(val plan: WorkoutPlan, val workoutId: String, private val clock: WorkoutClock, private val countdownSeconds: Int = 3) {
    init { require(workoutId.isNotBlank() && countdownSeconds in 1..10) }
    private var phase = WorkoutPhase.IDLE
    private var pausedPhase: WorkoutPhase? = null
    private var block = 0; private var set = 0; private var sequence = 0L
    private var lastTick = clock.nowMillis()
    private var remaining = 0L
    private var accepted = 0; private var partial = 0
    private var elapsed = 0L; private var validHold = 0L
    private var lastHoldObservation: Long? = null
    private var observationFloor = 0L
    private var lastCountdown = 0
    private var tracking: Boolean? = null
    private val events = mutableListOf<WorkoutEvent>()
    val state: WorkoutState get() = WorkoutState(phase, block, set, remaining, accepted, partial, elapsed, validHold)
    private val current get() = plan.blocks[block]
    private fun scope() = EventScope(workoutId, block, set, sequence++, clock.nowMillis())
    fun drainEvents(): List<WorkoutEvent> = events.toList().also { events.clear() }

    fun prepare() {
        check(phase == WorkoutPhase.IDLE)
        phase = WorkoutPhase.PREPARING; lastTick = clock.nowMillis()
        remaining = (current.goal as? SetGoal.Duration)?.seconds?.times(1000L) ?: 0
        events += WorkoutEvent.Ready(scope())
    }
    fun startCountdown() {
        check(phase == WorkoutPhase.PREPARING)
        phase = WorkoutPhase.COUNTDOWN; remaining = countdownSeconds * 1000L
        lastTick = clock.nowMillis(); lastCountdown = countdownSeconds
        events += WorkoutEvent.Countdown(scope(), countdownSeconds)
    }
    fun tick() {
        val now = clock.nowMillis(); check(now >= lastTick) { "Clock moved backwards" }
        val delta = now - lastTick; lastTick = now
        when (phase) {
            WorkoutPhase.COUNTDOWN -> {
                remaining = (remaining - delta).coerceAtLeast(0)
                if (remaining == 0L) startSet() else {
                    val seconds = ((remaining + 999) / 1000).toInt()
                    if (seconds != lastCountdown) { lastCountdown = seconds; events += WorkoutEvent.Countdown(scope(), seconds) }
                }
            }
            WorkoutPhase.ACTIVE_SET -> {
                elapsed = if (delta > Long.MAX_VALUE - elapsed) Long.MAX_VALUE else elapsed + delta
                val goal = current.goal
                if (goal is SetGoal.Duration) {
                    val credited = if (goal.timing == HoldTiming.ELAPSED) elapsed else validHold
                    remaining = (goal.seconds * 1000L - credited).coerceAtLeast(0)
                    if (remaining == 0L) finishSet(true)
                }
            }
            WorkoutPhase.RESTING -> {
                remaining = (remaining - delta).coerceAtLeast(0)
                if (remaining == 0L) advance()
            }
            else -> Unit
        }
    }
    private fun startSet() {
        phase = WorkoutPhase.ACTIVE_SET; accepted = 0; partial = 0; elapsed = 0; validHold = 0
        lastHoldObservation = null; tracking = null
        observationFloor = clock.nowMillis()
        remaining = (current.goal as? SetGoal.Duration)?.seconds?.times(1000L) ?: 0
        events += WorkoutEvent.SetStarted(scope())
    }
    fun rep(full: Boolean, side: Side) {
        tick()
        if (phase != WorkoutPhase.ACTIVE_SET || current.goal !is SetGoal.Reps) return
        val mode = current.limbMode
        if (mode == LimbMode.LEFT && side != Side.LEFT || mode == LimbMode.RIGHT && side != Side.RIGHT || mode == LimbMode.BILATERAL && current.exercise != ExerciseId.LUNGE && side != Side.BOTH) return
        if (full) {
            accepted++; events += WorkoutEvent.AcceptedRep(scope(), accepted, side)
            if (accepted >= (current.goal as SetGoal.Reps).count) finishSet(true)
        } else { partial++; events += WorkoutEvent.PartialRep(scope(), side) }
    }
    /** Valid intervals need two valid observations no more than 250ms apart.
     * Observations are capture times, current and monotonic, never timer samples. */
    fun holdObservation(capturedAtMillis: Long, valid: Boolean) {
        tick()
        if (phase != WorkoutPhase.ACTIVE_SET || current.goal !is SetGoal.Duration) return
        val now = clock.nowMillis()
        if (capturedAtMillis > now || capturedAtMillis < observationFloor || now - capturedAtMillis > 250) { lastHoldObservation = null; return }
        val previous = lastHoldObservation
        if (previous != null && capturedAtMillis <= previous) return
        if (valid && previous != null && capturedAtMillis - previous <= 250) {
            validHold += capturedAtMillis - previous
            events += WorkoutEvent.HoldProgress(scope(), validHold)
        }
        lastHoldObservation = if (valid) capturedAtMillis else null
        if ((current.goal as SetGoal.Duration).timing == HoldTiming.VALID_HOLD) {
            remaining = ((current.goal as SetGoal.Duration).seconds * 1000L - validHold).coerceAtLeast(0)
            if (remaining == 0L) finishSet(true)
        }
    }
    fun trackingChanged(reliable: Boolean) {
        if (!reliable) lastHoldObservation = null
        if (phase == WorkoutPhase.ACTIVE_SET && reliable != tracking) { tracking = reliable; events += WorkoutEvent.TrackingChanged(scope(), reliable) }
    }
    fun finishEarly() { tick(); if (phase == WorkoutPhase.ACTIVE_SET) finishSet(false) }
    private fun finishSet(reached: Boolean) {
        phase = WorkoutPhase.SET_COMPLETE; remaining = 0; lastHoldObservation = null
        events += WorkoutEvent.SetCompleted(scope(), reached)
    }
    fun continueAfterSet() {
        check(phase == WorkoutPhase.SET_COMPLETE)
        lastTick = clock.nowMillis()
        if (block == plan.blocks.lastIndex && set == current.sets - 1) complete()
        else if (current.restSeconds == 0) advance()
        else { phase = WorkoutPhase.RESTING; remaining = current.restSeconds * 1000L; events += WorkoutEvent.RestStarted(scope(), current.restSeconds) }
    }
    fun skipRest() { tick(); if (phase == WorkoutPhase.RESTING) advance() }
    private fun advance() {
        if (set + 1 < current.sets) set++ else { block++; set = 0 }
        if (block >= plan.blocks.size) { block = plan.blocks.lastIndex; complete() } else {
            phase = WorkoutPhase.PREPARING
            accepted = 0; partial = 0; elapsed = 0; validHold = 0; lastHoldObservation = null; tracking = null
            remaining = (current.goal as? SetGoal.Duration)?.seconds?.times(1000L) ?: 0
            events += WorkoutEvent.Ready(scope())
        }
    }
    private fun complete() { phase = WorkoutPhase.COMPLETED; remaining = 0; events += WorkoutEvent.WorkoutCompleted(scope()) }
    fun pause(interrupted: Boolean = false) {
        tick()
        if (phase !in listOf(WorkoutPhase.PREPARING, WorkoutPhase.COUNTDOWN, WorkoutPhase.ACTIVE_SET, WorkoutPhase.SET_COMPLETE, WorkoutPhase.RESTING)) return
        pausedPhase = phase; phase = if (interrupted) WorkoutPhase.INTERRUPTED else WorkoutPhase.PAUSED
        lastHoldObservation = null
    }
    fun resume() {
        check(phase == WorkoutPhase.PAUSED || phase == WorkoutPhase.INTERRUPTED)
        phase = checkNotNull(pausedPhase); pausedPhase = null; lastTick = clock.nowMillis(); observationFloor = lastTick; lastHoldObservation = null
    }
    fun cancel() {
        if (phase == WorkoutPhase.COMPLETED || phase == WorkoutPhase.CANCELLED) return
        phase = WorkoutPhase.CANCELLED; remaining = 0; lastHoldObservation = null
    }
}
