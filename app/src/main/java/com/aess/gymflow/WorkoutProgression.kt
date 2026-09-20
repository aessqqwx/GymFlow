package com.aess.gymflow

fun createActiveWorkoutState(day: WorkoutDay, startedAt: Long): ActiveWorkoutState = ActiveWorkoutState(
    dayKey = day.key,
    currentExerciseIndex = 0,
    currentSetIndex = 0,
    completedSets = emptyList(),
    restUntil = 0L,
    restState = RestState.IDLE,
    workoutCompleted = false,
    startedAt = startedAt
)

fun normalizeActiveWorkoutState(day: WorkoutDay, state: ActiveWorkoutState, now: Long): ActiveWorkoutState {
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

    val restIsActive = state.restUntil > now && !state.workoutCompleted
    return state.copy(
        dayKey = day.key,
        currentExerciseIndex = exerciseIndex,
        currentSetIndex = setIndex,
        completedSets = migratedCompleted,
        restUntil = if (restIsActive) state.restUntil else 0L,
        restState = if (restIsActive) RestState.RESTING else RestState.IDLE
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
    if (state.workoutCompleted || state.restState == RestState.RESTING) return state

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
            restState = RestState.RESTING
        )

        state.currentExerciseIndex < day.exercises.lastIndex -> state.copy(
            currentExerciseIndex = state.currentExerciseIndex + 1,
            currentSetIndex = 0,
            completedSets = completedSets,
            restUntil = completedAt + betweenExercisesRestSec * 1000L,
            restState = RestState.RESTING
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
    restState = RestState.IDLE
)
