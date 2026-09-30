package com.aess.gymflow

/**
 * Inserts or replaces a completed workout by its stable session identity.
 * Re-running post-workout finalization is therefore idempotent after process recreation.
 */
fun mergeCompletedWorkout(existing: List<WorkoutLog>, completed: WorkoutLog): List<WorkoutLog> =
    (listOf(completed) + existing.filterNot {
        it.dayKey == completed.dayKey && it.startedAt == completed.startedAt
    }).sortedByDescending { it.finishedAt }


fun exerciseCatalog(profile: UserProfile): Map<String, Exercise> =
    (workoutsFor(profile) + AllWorkouts).flatMap { it.exercises }.associateBy { it.id }

/** Build a fresh WorkoutDay from a past log without mutating the log. Missing exercises are skipped. */
fun workoutDayFromLog(log: WorkoutLog, profile: UserProfile, language: String): WorkoutDay? {
    val catalog = exerciseCatalog(profile)
    val ids = log.exerciseIds.ifEmpty { log.completedSetDetails.map { it.exerciseId }.distinct() }
    val exercises = ids.mapNotNull { catalog[it] }
    if (exercises.isEmpty()) return null
    val title = log.title.ifBlank { gs(language, R.string.workout) }
    return WorkoutDay(
        key = "repeat_${log.id}",
        dayOfWeek = java.time.LocalDate.now().dayOfWeek,
        shortDay = dayShort(java.time.LocalDate.now().dayOfWeek, language),
        title = title,
        compactTitle = title,
        exercises = exercises
    )
}

fun workoutDayFromTemplate(template: WorkoutTemplate, profile: UserProfile, language: String): WorkoutDay? {
    val catalog = exerciseCatalog(profile)
    val exercises = template.exerciseIds.mapNotNull { catalog[it] }
    if (exercises.isEmpty()) return null
    val title = template.name.ifBlank { gs(language, R.string.workout) }
    return WorkoutDay(
        key = "template_${template.id}",
        dayOfWeek = java.time.LocalDate.now().dayOfWeek,
        shortDay = dayShort(java.time.LocalDate.now().dayOfWeek, language),
        title = title,
        compactTitle = title,
        exercises = exercises
    )
}

fun templateFromLog(log: WorkoutLog, name: String): WorkoutTemplate {
    val ids = log.exerciseIds.ifEmpty { log.completedSetDetails.map { it.exerciseId }.distinct() }
    return WorkoutTemplate(
        id = System.currentTimeMillis(),
        name = name.ifBlank { log.title }.ifBlank { "Template" },
        exerciseIds = ids.filter { it.isNotBlank() },
        createdAt = System.currentTimeMillis()
    )
}
