package com.aess.gymflow

/**
 * Inserts or replaces a completed workout by its stable session identity.
 * Re-running post-workout finalization is therefore idempotent after process recreation.
 */
fun mergeCompletedWorkout(existing: List<WorkoutLog>, completed: WorkoutLog): List<WorkoutLog> =
    (listOf(completed) + existing.filterNot {
        it.dayKey == completed.dayKey && it.startedAt == completed.startedAt
    }).sortedByDescending { it.finishedAt }
