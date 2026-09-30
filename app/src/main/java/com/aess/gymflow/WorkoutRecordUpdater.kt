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

data class ExerciseSetSnapshot(
    val finishedAt: Long,
    val logId: Long,
    val workoutTitle: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val setNumber: Int
)

data class ExerciseHistorySummary(
    val exerciseId: String,
    val displayTitle: String,
    val sessions: List<ExerciseSessionSummary>,
    val bestWeightKg: Double?,
    val bestReps: Int?,
    val bestDurationSec: Int?,
    val bestVolume: Double?
)

data class ExerciseSessionSummary(
    val finishedAt: Long,
    val logId: Long,
    val workoutTitle: String,
    val sets: Int,
    val bestWeightKg: Double?,
    val bestReps: Int?,
    val bestDurationSec: Int?,
    val volume: Double?
)

/** All distinct exercise ids seen in workout history, newest activity first. */
fun knownExerciseIdsFromLogs(logs: List<WorkoutLog>): List<String> {
    val order = linkedMapOf<String, Long>()
    logs.forEach { log ->
        log.completedSetDetails.forEach { set ->
            if (set.exerciseId.isNotBlank()) {
                val prev = order[set.exerciseId]
                if (prev == null || log.finishedAt > prev) order[set.exerciseId] = log.finishedAt
            }
        }
    }
    return order.entries.sortedByDescending { it.value }.map { it.key }
}

fun exerciseDisplayTitle(
    exerciseId: String,
    logs: List<WorkoutLog>,
    profile: UserProfile,
    language: String
): String {
    val fromPlan = workoutsFor(profile).flatMap { it.exercises }.firstOrNull { it.id == exerciseId }
    if (fromPlan != null) return exerciseTitle(fromPlan, language)
    // Fall back to any cached title-like id or the raw id.
    return exerciseId.removePrefix("custom_").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun exerciseHistorySummary(
    logs: List<WorkoutLog>,
    exerciseId: String,
    profile: UserProfile,
    language: String
): ExerciseHistorySummary {
    val sessions = mutableListOf<ExerciseSessionSummary>()
    var bestWeight: Double? = null
    var bestReps: Int? = null
    var bestDur: Int? = null
    var bestVolume: Double? = null

    logs.sortedByDescending { it.finishedAt }.forEach { log ->
        val sets = log.completedSetDetails.filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) return@forEach
        val w = sets.mapNotNull { it.weightKg }.maxOrNull()
        val r = sets.mapNotNull { it.reps }.maxOrNull()
        val d = sets.mapNotNull { it.durationSec }.maxOrNull()
        val vol = sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0).toDouble() }.takeIf { it > 0.0 }
        if (w != null) bestWeight = maxOf(bestWeight ?: w, w)
        if (r != null) bestReps = maxOf(bestReps ?: r, r)
        if (d != null) bestDur = maxOf(bestDur ?: d, d)
        if (vol != null) bestVolume = maxOf(bestVolume ?: vol, vol)
        sessions += ExerciseSessionSummary(
            finishedAt = log.finishedAt,
            logId = log.id,
            workoutTitle = log.title,
            sets = sets.size,
            bestWeightKg = w,
            bestReps = r,
            bestDurationSec = d,
            volume = vol
        )
    }

    return ExerciseHistorySummary(
        exerciseId = exerciseId,
        displayTitle = exerciseDisplayTitle(exerciseId, logs, profile, language),
        sessions = sessions,
        bestWeightKg = bestWeight,
        bestReps = bestReps,
        bestDurationSec = bestDur,
        bestVolume = bestVolume
    )
}

/** Best result per exercise derived purely from logs (for display consistency). */
fun recordsDerivedFromLogs(
    logs: List<WorkoutLog>,
    profile: UserProfile,
    language: String,
    now: Long = System.currentTimeMillis()
): List<PersonalRecord> {
    data class Best(val exerciseId: String, val value: Double, val unit: String, val finishedAt: Long)
    val best = linkedMapOf<String, Best>()
    logs.forEach { log ->
        log.completedSetDetails.groupBy { it.exerciseId }.forEach { (exerciseId, sets) ->
            if (exerciseId.isBlank()) return@forEach
            val weighted = sets.mapNotNull { it.weightKg }.maxOrNull()?.takeIf { it > 0 }
            val reps = sets.mapNotNull { it.reps }.maxOrNull()?.takeIf { it > 0 }
            val timed = sets.mapNotNull { it.durationSec }.maxOrNull()?.takeIf { it > 0 }
            val (value, unit) = when {
                weighted != null -> weighted to gs(language, R.string.kg)
                reps != null -> reps.toDouble() to gs(language, R.string.reps_unit)
                timed != null -> timed.toDouble() to gs(language, R.string.sec_unit)
                else -> return@forEach
            }
            val idKey = exerciseId
            val prev = best[idKey]
            if (prev == null || value > prev.value) {
                best[idKey] = Best(exerciseId, value, unit, log.finishedAt)
            }
        }
    }
    return best.values.map { b ->
        PersonalRecord(
            id = stableRecordId(b.exerciseId),
            title = exerciseDisplayTitle(b.exerciseId, logs, profile, language),
            value = if (b.value % 1.0 == 0.0) b.value.toLong().toString() else "%.1f".format(java.util.Locale.US, b.value),
            unit = b.unit,
            updatedAt = b.finishedAt.coerceAtMost(now)
        )
    }.sortedByDescending { it.updatedAt }
}

/** Titles of exercises that set a new best in [newLog] vs [priorLogs]. */
fun newPersonalRecordTitles(
    priorLogs: List<WorkoutLog>,
    newLog: WorkoutLog,
    day: WorkoutDay,
    language: String
): List<String> {
    val byExercise = day.exercises.associateBy { it.id }
    val priorBest = mutableMapOf<String, Double>()
    priorLogs.forEach { log ->
        log.completedSetDetails.groupBy { it.exerciseId }.forEach { (id, sets) ->
            val v = sets.mapNotNull { it.weightKg }.maxOrNull()
                ?: sets.mapNotNull { it.reps?.toDouble() }.maxOrNull()
                ?: sets.mapNotNull { it.durationSec?.toDouble() }.maxOrNull()
                ?: return@forEach
            priorBest[id] = maxOf(priorBest[id] ?: v, v)
        }
    }
    val titles = mutableListOf<String>()
    newLog.completedSetDetails.groupBy { it.exerciseId }.forEach { (id, sets) ->
        val v = sets.mapNotNull { it.weightKg }.maxOrNull()
            ?: sets.mapNotNull { it.reps?.toDouble() }.maxOrNull()
            ?: sets.mapNotNull { it.durationSec?.toDouble() }.maxOrNull()
            ?: return@forEach
        val old = priorBest[id]
        if (old == null || v > old) {
            val title = byExercise[id]?.let { exerciseTitle(it, language) } ?: id
            titles += title
        }
    }
    return titles.distinct()
}
