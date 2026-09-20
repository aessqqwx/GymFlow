package com.aess.gymflow

fun updatedRecordsAfterWorkout(
    existing: List<PersonalRecord>,
    day: WorkoutDay,
    log: WorkoutLog,
    language: String,
    now: Long = System.currentTimeMillis()
): List<PersonalRecord> {
    if (log.completedSetDetails.isEmpty()) return existing
    val byExercise = day.exercises.associateBy { it.id }
    val result = existing.toMutableList()

    log.completedSetDetails.groupBy { it.exerciseId }.forEach { (exerciseId, sets) ->
        val exercise = byExercise[exerciseId] ?: return@forEach
        val weightedBest = sets.mapNotNull { it.weightKg }.maxOrNull()?.takeIf { it > 0.0 }
        val repsBest = sets.mapNotNull { it.reps }.maxOrNull()?.takeIf { it > 0 }
        val timeBest = sets.mapNotNull { it.durationSec }.maxOrNull()?.takeIf { it > 0 }
        val candidateValue: Double
        val unit: String
        when {
            weightedBest != null -> {
                candidateValue = weightedBest
                unit = gs(language, R.string.kg)
            }
            repsBest != null -> {
                candidateValue = repsBest.toDouble()
                unit = gs(language, R.string.reps_unit)
            }
            timeBest != null -> {
                candidateValue = timeBest.toDouble()
                unit = gs(language, R.string.sec_unit)
            }
            else -> return@forEach
        }

        val id = stableRecordId(exerciseId)
        val old = result.firstOrNull { it.id == id }
        val oldValue = old?.value?.replace(',', '.')?.toDoubleOrNull()
        if (oldValue == null || candidateValue > oldValue) {
            result.removeAll { it.id == id }
            result += PersonalRecord(
                id = id,
                title = exerciseTitle(exercise, language),
                value = trimRecordNumber(candidateValue),
                unit = unit,
                updatedAt = now
            )
        }
    }
    return result.sortedByDescending { it.updatedAt }
}

private fun stableRecordId(exerciseId: String): Long = 0x47000000L + (exerciseId.hashCode().toLong() and 0x7fffffffL)
private fun trimRecordNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(java.util.Locale.US, value)
