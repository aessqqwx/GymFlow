package com.aess.gymflow

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val FOUR_WEEKS_MS = 28L * 24L * 60L * 60L * 1000L

data class WorkoutStreak(val current: Int, val best: Int)

/**
 * Derives the workout streak straight from [WorkoutLog] history — no separate
 * stored counter, so it can never drift out of sync with the actual logs.
 * A "streak day" is any calendar day with at least one finished workout.
 * The current streak counts back from today (or yesterday, so a day that
 * hasn't happened yet doesn't break it) through consecutive workout days.
 */
fun computeWorkoutStreak(logs: List<WorkoutLog>, today: LocalDate = LocalDate.now()): WorkoutStreak {
    if (logs.isEmpty()) return WorkoutStreak(0, 0)
    val zone = ZoneId.systemDefault()
    val workoutDays = logs.map { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }.toSortedSet()

    var current = 0
    var cursor = if (today in workoutDays) today else today.minusDays(1)
    while (cursor in workoutDays) {
        current++
        cursor = cursor.minusDays(1)
    }

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in workoutDays) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
        previous = day
    }
    return WorkoutStreak(current, maxOf(best, current))
}

/** Total lifted volume (kg × reps, summed across all logged sets) across all workout history. */
fun computeTotalVolumeKg(logs: List<WorkoutLog>): Double =
    logs.sumOf { log -> log.completedSetDetails.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0).toDouble() } }

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

// ── XP / Level / Achievements (derived from logs + records, never stored) ──

private const val XP_PER_WORKOUT = 80
private const val XP_PER_SET = 8
private const val XP_STREAK_DAY = 12
private const val XP_PER_RECORD = 40

data class GymFlowLevel(
    val level: Int,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int
) {
    val progress: Float get() = if (xpForNextLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
}

/** XP needed to go from [level] to the next level, derived without persisted counters. */
fun xpRequiredForLevel(level: Int): Int {
    return (1000L + (level.coerceAtLeast(1).toLong() - 1L) * 500L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

fun computeTotalXp(logs: List<WorkoutLog>, records: List<PersonalRecord> = emptyList()): Int {
    if (logs.isEmpty() && records.isEmpty()) return 0
    val workoutXp = logs.size * XP_PER_WORKOUT
    val setXp = logs.sumOf { it.completedSets.coerceAtLeast(0) } * XP_PER_SET
    val bestStreak = computeWorkoutStreak(logs).best
    val streakXp = bestStreak * XP_STREAK_DAY
    val recordXp = records.size * XP_PER_RECORD
    return (workoutXp + setXp + streakXp + recordXp).coerceAtLeast(0)
}

fun computeLevel(totalXp: Int): GymFlowLevel {
    var remaining = totalXp.coerceAtLeast(0)
    var level = 1
    while (true) {
        val need = xpRequiredForLevel(level)
        if (remaining < need) {
            return GymFlowLevel(level, totalXp.coerceAtLeast(0), remaining, need)
        }
        remaining -= need
        level++
    }
}

fun xpGainedForWorkout(
    priorLogs: List<WorkoutLog>,
    newLog: WorkoutLog,
    priorRecords: List<PersonalRecord> = emptyList(),
    newRecords: List<PersonalRecord> = priorRecords
): Int {
    val before = computeTotalXp(priorLogs, priorRecords)
    val after = computeTotalXp(priorLogs + newLog, newRecords)
    return (after - before).coerceAtLeast(0)
}

data class GymFlowAchievement(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: String,
    val unlocked: Boolean,
    /** Epoch ms when threshold was first met; null if locked. Derived from logs/records. */
    val unlockedAt: Long? = null
)

fun computeAchievements(
    logs: List<WorkoutLog>,
    records: List<PersonalRecord> = emptyList()
): List<GymFlowAchievement> {
    val sorted = logs.sortedBy { it.finishedAt }
    val count = sorted.size
    val bestStreak = computeWorkoutStreak(logs).best
    val recSorted = records.sortedBy { it.updatedAt }
    val recCount = recSorted.size
    fun workoutAt(n: Int): Long? = sorted.getOrNull(n - 1)?.finishedAt
    fun recordAt(n: Int): Long? = recSorted.getOrNull(n - 1)?.updatedAt
    // Approximate streak unlock: day when best streak first reached n (from sorted unique days).
    fun streakUnlockAt(n: Int): Long? {
        if (bestStreak < n) return null
        val zone = ZoneId.systemDefault()
        val days = sorted.map { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }.toSortedSet()
        var run = 0
        var prev: LocalDate? = null
        for (day in days) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            if (run >= n) {
                return day.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            prev = day
        }
        return sorted.lastOrNull()?.finishedAt
    }
    return listOf(
        GymFlowAchievement("first_workout", R.string.ach_first_workout_title, R.string.ach_first_workout_desc, "🏁", count >= 1, workoutAt(1)),
        GymFlowAchievement("workouts_3", R.string.ach_workouts_3_title, R.string.ach_workouts_3_desc, "3️⃣", count >= 3, workoutAt(3)),
        GymFlowAchievement("workouts_10", R.string.ach_workouts_10_title, R.string.ach_workouts_10_desc, "🔟", count >= 10, workoutAt(10)),
        GymFlowAchievement("workouts_25", R.string.ach_workouts_25_title, R.string.ach_workouts_25_desc, "💪", count >= 25, workoutAt(25)),
        GymFlowAchievement("workouts_50", R.string.ach_workouts_50_title, R.string.ach_workouts_50_desc, "🏆", count >= 50, workoutAt(50)),
        GymFlowAchievement("streak_7", R.string.ach_streak_7_title, R.string.ach_streak_7_desc, "🔥", bestStreak >= 7, streakUnlockAt(7)),
        GymFlowAchievement("streak_14", R.string.ach_streak_14_title, R.string.ach_streak_14_desc, "⚡", bestStreak >= 14, streakUnlockAt(14)),
        GymFlowAchievement("streak_30", R.string.ach_streak_30_title, R.string.ach_streak_30_desc, "🌟", bestStreak >= 30, streakUnlockAt(30)),
        GymFlowAchievement("record_1", R.string.ach_record_1_title, R.string.ach_record_1_desc, "🥇", recCount >= 1, recordAt(1)),
        GymFlowAchievement("record_5", R.string.ach_record_5_title, R.string.ach_record_5_desc, "🎖️", recCount >= 5, recordAt(5)),
        GymFlowAchievement("record_10", R.string.ach_record_10_title, R.string.ach_record_10_desc, "👑", recCount >= 10, recordAt(10))
    )
}

fun newlyUnlockedAchievements(
    beforeLogs: List<WorkoutLog>,
    afterLogs: List<WorkoutLog>,
    beforeRecords: List<PersonalRecord> = emptyList(),
    afterRecords: List<PersonalRecord> = beforeRecords
): List<GymFlowAchievement> {
    val before = computeAchievements(beforeLogs, beforeRecords).filter { it.unlocked }.map { it.id }.toSet()
    return computeAchievements(afterLogs, afterRecords).filter { it.unlocked && it.id !in before }
}

data class MonthlyRecap(
    val yearMonth: java.time.YearMonth,
    val workoutCount: Int,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val bestStreakInMonth: Int,
    val prCount: Int,
    val xpGained: Int,
    val activeDays: Int,
    val topExerciseId: String?,
    val topExerciseTitle: String?
)

fun computeMonthlyRecap(
    logs: List<WorkoutLog>,
    records: List<PersonalRecord>,
    profile: UserProfile,
    language: String,
    yearMonth: java.time.YearMonth = java.time.YearMonth.now()
): MonthlyRecap {
    val zone = ZoneId.systemDefault()
    val monthLogs = logs.filter {
        val d = Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate()
        java.time.YearMonth.from(d) == yearMonth
    }
    val start = yearMonth.atDay(1)
    val end = yearMonth.atEndOfMonth()
    val monthRecords = records.filter {
        val d = Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate()
        !d.isBefore(start) && !d.isAfter(end)
    }
    val activeDays = monthLogs.map {
        Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate()
    }.toSet().size
    val totalSets = monthLogs.sumOf { it.completedSets.coerceAtLeast(0) }
    val volume = computeTotalVolumeKg(monthLogs)
    // Streak within month: longest consecutive workout days in this month only.
    val days = monthLogs.map { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }.toSortedSet()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (day in days) {
        run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
        prev = day
    }
    val beforeLogs = logs.filter {
        Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate().isBefore(start)
    }
    val beforeRecords = records.filter {
        Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate().isBefore(start)
    }
    val xpBefore = computeTotalXp(beforeLogs, beforeRecords)
    val xpAfter = computeTotalXp(beforeLogs + monthLogs, beforeRecords + monthRecords)
    val xpGained = (xpAfter - xpBefore).coerceAtLeast(0)
    val exerciseCounts = mutableMapOf<String, Int>()
    monthLogs.forEach { log ->
        log.completedSetDetails.forEach { set ->
            if (set.exerciseId.isNotBlank()) {
                exerciseCounts[set.exerciseId] = (exerciseCounts[set.exerciseId] ?: 0) + 1
            }
        }
    }
    val topId = exerciseCounts.maxByOrNull { it.value }?.key
    val topTitle = topId?.let { exerciseDisplayTitle(it, monthLogs, profile, language) }
    return MonthlyRecap(
        yearMonth = yearMonth,
        workoutCount = monthLogs.size,
        totalSets = totalSets,
        totalVolumeKg = volume,
        bestStreakInMonth = best,
        prCount = monthRecords.size,
        xpGained = xpGained,
        activeDays = activeDays,
        topExerciseId = topId,
        topExerciseTitle = topTitle
    )
}

fun monthlyRecapSummary(recap: MonthlyRecap, language: String): String {
    if (recap.workoutCount == 0) return gs(language, R.string.monthly_recap_empty_summary)
    return gs(
        language,
        R.string.monthly_recap_summary,
        recap.workoutCount,
        recap.activeDays,
        recap.xpGained
    )
}
