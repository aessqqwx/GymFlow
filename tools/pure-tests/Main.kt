package com.aess.gymflow

import java.time.DayOfWeek

private fun checkThat(value: Boolean, message: String) {
    if (!value) error(message)
}

private fun simpleDay(): WorkoutDay = WorkoutDay(
    key = "test",
    dayOfWeek = DayOfWeek.MONDAY,
    shortDay = "ПН",
    title = "Test",
    compactTitle = "Test",
    exercises = listOf(
        Exercise("bench", "CHEST", "Bench", 2, 60, listOf("cue"), "10", "weight"),
        Exercise("pullup", "BACK", "Pull-up", 2, 90, listOf("cue"), "8", null)
    )
)

private fun workoutStateMachineTest() {
    val day = simpleDay()
    var t = 1_000_000L
    var s = createActiveWorkoutState(day, t)
    checkThat(s.currentExerciseIndex == 0 && s.currentSetIndex == 0, "initial indices")

    t += 1_000
    s = advanceAfterCompletedSet(day, s, t, weightKg = 80.0, reps = 10)
    checkThat(s.completedSets.size == 1, "first set recorded once")
    checkThat(s.restState == RestState.RESTING, "rest after first set")
    checkThat(s.currentSetIndex == 1, "next set index prepared")
    val duplicateAttempt = advanceAfterCompletedSet(day, s, t + 100, weightKg = 80.0, reps = 10)
    checkThat(duplicateAttempt == s, "double-complete blocked while resting")

    val originalRestEnd = s.restUntil
    val restoredDuringRest = normalizeActiveWorkoutState(day, s, originalRestEnd - 20_000)
    checkThat(restoredDuringRest.restState == RestState.RESTING, "rest survives recreation")
    checkThat(restoredDuringRest.restUntil == originalRestEnd, "absolute rest end survives recreation")
    val restoredAfterRest = normalizeActiveWorkoutState(day, s, originalRestEnd + 1)
    checkThat(restoredAfterRest.restState == RestState.IDLE && restoredAfterRest.restUntil == 0L, "expired rest is not restarted")

    s = finishWorkoutRest(s)
    t += 61_000
    s = advanceAfterCompletedSet(day, s, t, weightKg = 85.0, reps = 8)
    checkThat(s.currentExerciseIndex == 1 && s.currentSetIndex == 0, "moves to next exercise")
    checkThat(s.completedSets.size == 2, "second set recorded")
    s = finishWorkoutRest(s)
    t += 181_000
    s = advanceAfterCompletedSet(day, s, t, reps = 8)
    checkThat(s.currentExerciseIndex == 1 && s.currentSetIndex == 1 && s.restState == RestState.RESTING, "second exercise set transition")
    s = finishWorkoutRest(s)
    t += 91_000
    s = advanceAfterCompletedSet(day, s, t, reps = 9)
    checkThat(s.workoutCompleted, "workout completes after final set")
    checkThat(s.completedSets.size == 4, "all sets recorded")
    checkThat(s.completedSets.map { it.exerciseId to it.setNumber }.distinct().size == 4, "no duplicate completed sets")
}

private fun normalizationTest() {
    val day = simpleDay()
    val raw = ActiveWorkoutState(
        day.key, 99, 99,
        listOf(
            CompletedSet("bench", 1, 1),
            CompletedSet("bench", 1, 2),
            CompletedSet("missing", 1, 3),
            CompletedSet("pullup", 99, 4)
        ),
        restUntil = 0,
        restState = RestState.RESTING,
        workoutCompleted = false,
        startedAt = 1
    )
    val normalized = normalizeActiveWorkoutState(day, raw, 100)
    checkThat(normalized.currentExerciseIndex == 1 && normalized.currentSetIndex == 1, "indices clamped")
    checkThat(normalized.completedSets.size == 1, "invalid and duplicate completed sets removed")
    checkThat(normalized.restState == RestState.IDLE, "stale rest state cleared")
}

private fun historyIdempotencyTest() {
    val base = WorkoutLog(10, 1000, 2000, "test", "Test", 4, 1000, listOf("bench"), emptyList())
    val first = mergeCompletedWorkout(emptyList(), base)
    val again = mergeCompletedWorkout(first, base.copy(id = 11, finishedAt = 3000, durationMillis = 2000))
    checkThat(again.size == 1, "same session creates one history row")
    checkThat(again.single().finishedAt == 3000L, "re-finalization replaces same session")
    val other = mergeCompletedWorkout(again, base.copy(id = 12, startedAt = 5000, finishedAt = 6000))
    checkThat(other.size == 2, "different session remains separate")
}

private fun recordUpgradeTest() {
    val day = simpleDay()
    val first = WorkoutLog(
        id = 1, startedAt = 1, finishedAt = 2, dayKey = day.key, title = day.title, completedSets = 2,
        completedSetDetails = listOf(
            CompletedSet("bench", 1, 1, weightKg = 80.0, reps = 10),
            CompletedSet("bench", 2, 2, weightKg = 85.0, reps = 8),
            CompletedSet("pullup", 1, 2, reps = 12)
        )
    )
    val r1 = updatedRecordsAfterWorkout(emptyList(), day, first, "EN", now = 100)
    checkThat(r1.any { it.title == "Bench" && it.value == "85" && it.unit == "kg" }, "weight PR uses max weight")
    checkThat(r1.any { it.title == "Pull-up" && it.value == "12" && it.unit == "reps" }, "rep PR uses max reps")

    val worse = first.copy(id = 2, startedAt = 3, finishedAt = 4, completedSetDetails = listOf(CompletedSet("bench", 1, 4, weightKg = 70.0, reps = 20)))
    val r2 = updatedRecordsAfterWorkout(r1, day, worse, "EN", now = 200)
    checkThat(r2.first { it.title == "Bench" }.value == "85", "PR never decreases")

    val better = first.copy(id = 3, startedAt = 5, finishedAt = 6, completedSetDetails = listOf(CompletedSet("bench", 1, 6, weightKg = 90.0, reps = 5)))
    val r3 = updatedRecordsAfterWorkout(r2, day, better, "EN", now = 300)
    checkThat(r3.first { it.title == "Bench" }.value == "90", "PR upgrades")
}

private fun generatedPlanTest() {
    val profiles = listOf(
        UserProfile(trainingPlace = "GYM", trainingDays = setOf("TUESDAY", "FRIDAY", "SUNDAY"), selectedProgram = "PPL"),
        UserProfile(trainingPlace = "HOME", homeEquipment = setOf("DUMBBELLS", "PULLUP_BAR"), trainingDays = setOf("MONDAY", "THURSDAY"), selectedProgram = "UPPER_LOWER"),
        UserProfile(trainingPlace = "HOME", homeEquipment = setOf("BODYWEIGHT"), trainingDays = setOf("SATURDAY"), selectedProgram = "MOBILITY")
    )
    profiles.forEachIndexed { idx, profile ->
        val plan = workoutsFor(profile)
        checkThat(plan.isNotEmpty(), "generated plan $idx not empty")
        plan.forEach { day ->
            checkThat(day.exercises.isNotEmpty(), "generated day exercises not empty")
            var state = createActiveWorkoutState(day, 1000)
            var now = 1000L
            var guard = 0
            while (!state.workoutCompleted && guard++ < 500) {
                if (state.restState == RestState.RESTING) state = finishWorkoutRest(state)
                else state = advanceAfterCompletedSet(day, state, ++now)
            }
            val expected = day.exercises.sumOf { it.sets }
            checkThat(state.workoutCompleted, "generated plan day completes")
            checkThat(state.completedSets.size == expected, "generated plan records all sets exactly once")
        }
    }
}

private fun birthDateTest() {
    val today = java.time.LocalDate.of(2026, 9, 20)
    checkThat(normalizeBirthDate("2010-05-16") == "2010-05-16", "ISO birth date round-trip")
    checkThat(normalizeBirthDate("2023-02-29") == null, "invalid leap date rejected")
    checkThat(maxBirthDay(2024, 2, today) == 29, "leap February has 29 days")
    checkThat(maxBirthDay(2025, 2, today) == 28, "non-leap February has 28 days")
    checkThat(clampBirthDate(2010, 3, 31, today).toString() == "2010-03-31", "valid 31-day date preserved")
    checkThat(clampBirthDate(2010, 4, 31, today).toString() == "2010-04-30", "31 April clamps to 30 April")
    checkThat(clampBirthDate(2026, 12, 31, today) == today, "future date clamps to today")
    checkThat(parseBirthDate("2999-01-01") == null, "future stored birth date rejected")
    checkThat(normalizeBirthDate(null) == null, "old backup without birthDate remains optional")
    val legacy = java.time.LocalDate.of(2010, 5, 16).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    checkThat(birthDateFromLegacyMillis(legacy) == "2010-05-16", "legacy millis migrate to ISO birthDate")
}

fun main() {
    workoutStateMachineTest()
    normalizationTest()
    historyIdempotencyTest()
    recordUpgradeTest()
    generatedPlanTest()
    birthDateTest()
    println("PURE_TEST_OK")
}
