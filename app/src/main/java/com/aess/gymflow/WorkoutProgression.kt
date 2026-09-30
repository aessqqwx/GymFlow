package com.aess.gymflow

fun createActiveWorkoutState(day: WorkoutDay, startedAt: Long): ActiveWorkoutState = ActiveWorkoutState(
    dayKey = day.key,
    currentExerciseIndex = 0,
    currentSetIndex = 0,
    completedSets = emptyList(),
    restUntil = 0L,
    restState = RestState.IDLE,
    workoutCompleted = false,
    startedAt = startedAt,
    workoutDay = day
)

fun normalizeActiveWorkoutState(day: WorkoutDay, state: ActiveWorkoutState, now: Long): ActiveWorkoutState {
    if (day.exercises.isEmpty()) {
        return state.copy(
            dayKey = day.key,
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            completedSets = emptyList(),
            restUntil = 0L,
            restState = RestState.IDLE,
            workoutCompleted = false
        )
    }
    val exerciseIndex = state.currentExerciseIndex.coerceIn(0, day.exercises.lastIndex)
    val setIndex = state.currentSetIndex.coerceIn(0, day.exercises[exerciseIndex].sets - 1)
    val validExerciseSets = day.exercises.associate { it.id to it.sets }
    val validCompleted = state.completedSets
        .filter { completed ->
            val setCount = validExerciseSets[completed.exerciseId] ?: return@filter false
            completed.setNumber in 1..setCount
        }
        .distinctBy { it.exerciseId to it.setNumber }

    val migratedCompleted = if (validCompleted.isEmpty() && (exerciseIndex > 0 || setIndex > 0)) {
        buildList {
            day.exercises.take(exerciseIndex).forEach { exercise ->
                repeat(exercise.sets) { set -> add(CompletedSet(exercise.id, set + 1, state.startedAt)) }
            }
            repeat(setIndex) { set -> add(CompletedSet(day.exercises[exerciseIndex].id, set + 1, state.startedAt)) }
        }
    } else validCompleted

    val restIsActive = state.restUntil > now && !state.workoutCompleted && !state.isPaused
    val restPaused = state.restState == RestState.PAUSED && state.restRemainingMs > 0L && !state.workoutCompleted
    return state.copy(
        dayKey = day.key,
        currentExerciseIndex = exerciseIndex,
        currentSetIndex = setIndex,
        completedSets = migratedCompleted,
        restUntil = if (restIsActive) state.restUntil else 0L,
        restState = when {
            restIsActive -> RestState.RESTING
            restPaused -> RestState.PAUSED
            else -> RestState.IDLE
        },
        restRemainingMs = if (restPaused) state.restRemainingMs else 0L,
        isPaused = state.isPaused && !state.workoutCompleted,
        exerciseNotes = state.exerciseNotes,
        workoutDay = day
    )
}

fun advanceAfterCompletedSet(
    day: WorkoutDay,
    rawState: ActiveWorkoutState,
    completedAt: Long,
    betweenExercisesRestSec: Int = 180,
    weightKg: Double? = null,
    reps: Int? = null,
    durationSec: Int? = null
): ActiveWorkoutState {
    val state = normalizeActiveWorkoutState(day, rawState, completedAt)
    if (day.exercises.isEmpty() || state.workoutCompleted || state.isPaused || state.restState == RestState.RESTING || state.restState == RestState.PAUSED) return state

    val exercise = day.exercises[state.currentExerciseIndex]
    val setNumber = state.currentSetIndex + 1
    val completed = CompletedSet(
        exerciseId = exercise.id,
        setNumber = setNumber,
        completedAt = completedAt,
        weightKg = weightKg?.takeIf { it >= 0.0 },
        reps = reps?.takeIf { it >= 0 },
        durationSec = durationSec?.takeIf { it >= 0 }
    )
    val completedSets = if (state.completedSets.any {
            it.exerciseId == completed.exerciseId && it.setNumber == completed.setNumber
        }
    ) state.completedSets else state.completedSets + completed

    return when {
        state.currentSetIndex < exercise.sets - 1 -> state.copy(
            currentSetIndex = state.currentSetIndex + 1,
            completedSets = completedSets,
            restUntil = completedAt + exercise.restSec * 1000L,
            restState = RestState.RESTING,
            restRemainingMs = 0L
        )

        state.currentExerciseIndex < day.exercises.lastIndex -> state.copy(
            currentExerciseIndex = state.currentExerciseIndex + 1,
            currentSetIndex = 0,
            completedSets = completedSets,
            restUntil = completedAt + betweenExercisesRestSec * 1000L,
            restState = RestState.RESTING,
            restRemainingMs = 0L
        )

        else -> state.copy(
            completedSets = completedSets,
            restUntil = 0L,
            restState = RestState.IDLE,
            workoutCompleted = true
        )
    }
}

fun finishWorkoutRest(state: ActiveWorkoutState): ActiveWorkoutState = state.copy(
    restUntil = 0L,
    restState = RestState.IDLE,
    restRemainingMs = 0L
)

fun pauseWorkout(state: ActiveWorkoutState, now: Long = System.currentTimeMillis()): ActiveWorkoutState {
    if (state.workoutCompleted || state.isPaused) return state
    val remaining = when {
        state.restState == RestState.RESTING && state.restUntil > now -> state.restUntil - now
        state.restState == RestState.PAUSED -> state.restRemainingMs
        else -> 0L
    }
    return state.copy(
        isPaused = true,
        restUntil = 0L,
        restState = if (remaining > 0L) RestState.PAUSED else RestState.IDLE,
        restRemainingMs = remaining.coerceAtLeast(0L)
    )
}

fun resumeWorkout(state: ActiveWorkoutState, now: Long = System.currentTimeMillis()): ActiveWorkoutState {
    if (!state.isPaused || state.workoutCompleted) return state
    val remaining = state.restRemainingMs
    return if (remaining > 0L) {
        state.copy(
            isPaused = false,
            restUntil = now + remaining,
            restState = RestState.RESTING,
            restRemainingMs = 0L
        )
    } else {
        state.copy(isPaused = false, restUntil = 0L, restState = RestState.IDLE, restRemainingMs = 0L)
    }
}

fun pauseRestTimer(state: ActiveWorkoutState, now: Long = System.currentTimeMillis()): ActiveWorkoutState {
    if (state.restState != RestState.RESTING || state.restUntil <= now) return state
    return state.copy(
        restRemainingMs = state.restUntil - now,
        restUntil = 0L,
        restState = RestState.PAUSED
    )
}

fun resumeRestTimer(state: ActiveWorkoutState, now: Long = System.currentTimeMillis()): ActiveWorkoutState {
    if (state.restState != RestState.PAUSED || state.restRemainingMs <= 0L) return state
    return state.copy(
        restUntil = now + state.restRemainingMs,
        restState = RestState.RESTING,
        restRemainingMs = 0L
    )
}

fun setRestDuration(state: ActiveWorkoutState, durationSec: Int, now: Long = System.currentTimeMillis()): ActiveWorkoutState {
    val ms = durationSec.coerceAtLeast(0) * 1000L
    if (ms == 0L) return finishWorkoutRest(state)
    return state.copy(
        restUntil = now + ms,
        restState = RestState.RESTING,
        restRemainingMs = 0L,
        isPaused = false
    )
}

/** Prefer the saved session over a plan that may have changed since it started. */
fun resolveActiveWorkoutDay(state: ActiveWorkoutState, plan: List<WorkoutDay>): WorkoutDay? =
    state.workoutDay?.takeIf { it.key == state.dayKey && it.exercises.isNotEmpty() }
        ?: plan.firstOrNull { it.key == state.dayKey }
        ?: workoutByKey(state.dayKey)
