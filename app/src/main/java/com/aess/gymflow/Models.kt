package com.aess.gymflow

import java.time.DayOfWeek

data class Exercise(
    val id: String,
    val muscle: String,
    val title: String,
    val sets: Int,
    val restSec: Int = 90,
    val cues: List<String>,
    val reps: String = "8–12",
    val weightLabel: String? = "Рабочий вес"
)

data class WorkoutDay(
    val key: String,
    val dayOfWeek: DayOfWeek,
    val shortDay: String,
    val title: String,
    val compactTitle: String,
    val exercises: List<Exercise>
)

data class ProgressEntry(
    val id: Long,
    val createdAt: Long,
    val weightKg: Double?,
    val heightCm: Double?,
    val note: String,
    val upperArmCm: Double? = null,
    val forearmCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null
)

data class NutritionEntry(
    val id: Long,
    val createdAt: Long,
    val calories: Int,
    val proteinG: Double,
    val waterMl: Int = 0,
    val note: String = ""
)

data class GoalEntry(
    val id: Long,
    val title: String,
    val value: String,
    val unit: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PersonalRecord(
    val id: Long,
    val title: String,
    val value: String,
    val unit: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CompletedSet(
    val exerciseId: String,
    val setNumber: Int,
    val completedAt: Long,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null
)

data class WorkoutLog(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val dayKey: String,
    val title: String,
    val completedSets: Int,
    val durationMillis: Long = (finishedAt - startedAt).coerceAtLeast(0L),
    val exerciseIds: List<String> = emptyList(),
    val completedSetDetails: List<CompletedSet> = emptyList(),
    /** 1..5 rating from the post-workout "how did it feel" prompt; null if not answered. */
    val moodRating: Int? = null,
    /** Per-exercise notes captured during this session (exerciseId → text). */
    val exerciseNotes: Map<String, String> = emptyMap()
)

enum class RestState { IDLE, RESTING, PAUSED }

data class ActiveWorkoutState(
    val dayKey: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val completedSets: List<CompletedSet>,
    val restUntil: Long,
    val restState: RestState,
    val workoutCompleted: Boolean,
    val startedAt: Long,
    /** Whole-workout pause (not completed; no XP until finish after resume). */
    val isPaused: Boolean = false,
    /** Remaining rest ms when [restState] is PAUSED. */
    val restRemainingMs: Long = 0L,
    /** Session notes keyed by exerciseId. */
    val exerciseNotes: Map<String, String> = emptyMap(),
    /** Saved plan for restoring the session after edits to the current program. */
    val workoutDay: WorkoutDay? = null
)

data class UserProfile(
    val proteinGoal: Int = 0,
    val calorieGoal: Int = 0,
    val waterGoalMl: Int = 2000,
    val nutritionAutoTargets: Boolean = false,
    val activityLevel: String = "ACTIVE",
    val reminderEnabled: Boolean = true,
    val themeMode: String = "DARK",
    val colorStyle: String = "MONO",
    val appLanguage: String = "RU",
    val birthDate: String? = null,
    val sex: String = "UNSPECIFIED",
    val trainingExperience: String = "UNSPECIFIED",
    val profilePromptDismissedAt: Long = 0L,
    val onboardingCompleted: Boolean = false,
    val monthlyCheckInEnabled: Boolean = true,
    val nextMonthlyCheckInAt: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val profileDescription: String = "",
    val avatarUri: String = "",
    val trainingPlace: String = "GYM",
    val homeEquipment: Set<String> = emptySet(),
    val trainingDays: Set<String> = emptySet(),
    val selectedProgram: String = "FULL_BODY",
    val customWorkoutName: String = "",
    val dayFocus: Map<String, String> = emptyMap(),
    val selectedExerciseIds: Map<String, List<String>> = emptyMap(),
    val fontScale: Float = 1f,
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val workoutNotifications: Boolean = true,
    val proteinNotifications: Boolean = true,
    val motivationNotifications: Boolean = true,
    val measurementNotifications: Boolean = true,
    /** Primary training goals (e.g. STRENGTH, HYPERTROPHY, FAT_LOSS, ENDURANCE, MOBILITY). */
    val fitnessGoals: Set<String> = emptySet(),
    /** Preferred session length in minutes used to shape plan volume. */
    val sessionDurationMinutes: Int = 45,
    /** Local hour (0-23) for workout reminder. */
    val reminderHour: Int = 16,
    /** Local minute (0-59) for workout reminder. */
    val reminderMinute: Int = 0
)

data class MusicTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val artworkUri: String = ""
)

data class MusicPlaylist(
    val id: Long,
    val name: String,
    val trackUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class PlayerPreferences(
    val queueUris: List<String> = emptyList(),
    val currentUri: String = "",
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0
)

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val exerciseIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class GymFlowBackup(
    val profile: UserProfile,
    val progress: List<ProgressEntry>,
    val workouts: List<WorkoutLog>,
    val nutrition: List<NutritionEntry>,
    val goals: List<GoalEntry> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    val activeWorkout: ActiveWorkoutState?,
    val trainingPlan: List<WorkoutDay> = emptyList(),
    val musicLibrary: List<MusicTrack> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val playerPreferences: PlayerPreferences = PlayerPreferences(),
    val templates: List<WorkoutTemplate> = emptyList()
)
