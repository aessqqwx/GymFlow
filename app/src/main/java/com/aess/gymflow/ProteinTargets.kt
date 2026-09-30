package com.aess.gymflow

import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

internal fun validProteinTarget(value: Int?): Boolean = value != null && value in 1..500

internal data class ProteinRecommendation(val minimum: Int? = null, val maximum: Int? = null,
    val missing: List<String> = emptyList(), val adult: Boolean = true, val weight: Double? = null)

/** Adult exercise guidance: ISSN position stand, doi:10.1186/s12970-017-0177-8.
 * Height and age establish input completeness; no invented height/age multiplier. */
internal fun recommendProtein(profile: UserProfile, progress: List<ProgressEntry>, today: LocalDate = LocalDate.now()): ProteinRecommendation {
    val latest = progress.sortedByDescending { it.createdAt }
    val weight = latest.firstNotNullOfOrNull { it.weightKg?.takeIf { v -> v.isFinite() && v > 0 } }
    val height = latest.firstNotNullOfOrNull { it.heightCm?.takeIf { v -> v.isFinite() && v > 0 } }
    val birth = parseBirthDate(profile.birthDate)
    val missing = buildList {
        if (birth == null || birth > today) add("age")
        if (weight == null) add("weight")
        if (height == null) add("height")
        if (profile.activityLevel.isBlank() || profile.activityLevel == "UNSPECIFIED") add("activity")
        if (profile.trainingExperience.isBlank() || profile.trainingExperience == "UNSPECIFIED") add("experience")
        if (profile.fitnessGoals.isEmpty()) add("goal")
    }
    if (missing.isNotEmpty()) return ProteinRecommendation(missing = missing, weight = weight)
    if (Period.between(birth, today).years < 18) return ProteinRecommendation(adult = false, weight = weight)
    // Goals and training experience choose the lower end within the published active-adult range.
    val focused = profile.fitnessGoals.any { it in setOf("MUSCLE", "HYPERTROPHY", "STRENGTH", "FAT_LOSS") }
    val trained = profile.trainingExperience !in setOf("BEGINNER", "NEW", "NONE")
    val lower = if (focused && trained && profile.activityLevel != "LOW") 1.6 else 1.4
    val min = (weight!! * lower).roundToInt()
    val max = (weight * 2.0).roundToInt()
    return if (validProteinTarget(min) && validProteinTarget(max)) ProteinRecommendation(min, max, weight = weight)
        else ProteinRecommendation(missing = listOf("range"), weight = weight)
}
