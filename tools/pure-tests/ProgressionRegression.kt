package com.aess.gymflow

/** JVM checks against the compiled application's actual models and functions. */
fun main() {
    val thresholds = listOf(1000, 2500, 4500, 7000, 10000, 13500)
    thresholds.forEachIndexed { index, xp ->
        check(computeLevel(xp - 1).level == index + 1)
        check(computeLevel(xp).level == index + 2)
        check(computeLevel(xp).xpIntoLevel == 0)
        check(computeLevel(xp).progress == 0f)
    }
    check(computeLevel(-1).level == 1)
    check(computeLevel(Int.MAX_VALUE).progress in 0f..1f)
    check(UserProfile().trainingDays.isEmpty())
    val automatic = UserProfile(trainingDays = setOf("MONDAY"), sessionDurationMinutes = 20)
    val catalog = workoutCandidatesFor(automatic, java.time.DayOfWeek.MONDAY, 0)
    val ids = catalog.map { it.id }.distinct().reversed()
    val manual = automatic.copy(selectedProgram = "CUSTOM", customWorkoutName = "My workout", selectedExerciseIds = mapOf("MONDAY" to ids))
    val plan = workoutsFor(manual)
    check(plan.single().title == "My workout")
    check(plan.single().exercises.map { it.id } == ids)
    check(plan.map { it.key }.distinct().size == plan.size)
    check(workoutPlanSignature(manual) != workoutPlanSignature(manual.copy(customWorkoutName = "Renamed")))
    check(computeTotalXp(emptyList(), emptyList()) == 0)
    check(!validProteinTarget(null) && !validProteinTarget(0) && !validProteinTarget(501))
    check(validProteinTarget(1) && validProteinTarget(500))
    val today = java.time.LocalDate.of(2026, 9, 30)
    val person = UserProfile(birthDate = "1995-01-01", trainingExperience = "BEGINNER", fitnessGoals = setOf("GENERAL"))
    val measurements = listOf(ProgressEntry(1, 1, 80.0, null, ""), ProgressEntry(2, 2, null, 180.0, ""))
    val recommendation = recommendProtein(person, measurements, today)
    check(recommendation.minimum == 112 && recommendation.maximum == 160)
    check(recommendProtein(person, emptyList(), today).missing.containsAll(listOf("weight", "height")))
    check(!recommendProtein(person.copy(birthDate = "2015-01-01"), measurements, today).adult)
    check(recommendProtein(person.copy(trainingExperience = "UNSPECIFIED"), measurements, today).missing == listOf("experience"))
    println("PROGRESSION_REGRESSION_OK: XP boundaries, empty days, custom order/volume/name/cache, protein validation/range/missing inputs")
}
