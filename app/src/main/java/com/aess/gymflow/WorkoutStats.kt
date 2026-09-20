package com.aess.gymflow

private const val FOUR_WEEKS_MS = 28L * 24L * 60L * 60L * 1000L

fun computeMuscleLoadCounts(
    logs: List<WorkoutLog>,
    profile: UserProfile,
    language: String,
    now: Long = System.currentTimeMillis()
): List<Pair<String, Int>> {
    val recent = logs.filter { it.finishedAt >= now - FOUR_WEEKS_MS }
    if (recent.isEmpty()) return emptyList()
    val catalog = (workoutsFor(profile) + AllWorkouts).flatMap { it.exercises }.associateBy { it.id }
    val counts = mutableMapOf<String, Int>()
    recent.forEach { log ->
        log.completedSetDetails.forEach { set ->
            catalog[set.exerciseId]?.let { exercise ->
                val muscle = exerciseMuscle(exercise, language)
                counts[muscle] = (counts[muscle] ?: 0) + 1
            }
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}
