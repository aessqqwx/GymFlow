package com.aess.gymflow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GymFlowStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("gymflow_v1", Context.MODE_PRIVATE)
    private val activeWorkoutWriteLock = Any()
    private val activeWorkoutWriteEpoch = java.util.concurrent.atomic.AtomicLong(0)
    private val activeWorkoutExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "gymflow-active-workout-write").apply { isDaemon = true }
    }

    fun loadProgress(): List<ProgressEntry> {
        val saved = runCatching {
            val arr = JSONArray(prefs.getString("progress_entries", "[]") ?: "[]")
            buildList { for (i in 0 until arr.length()) add(progressFromJson(arr.getJSONObject(i))) }
        }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
        if (saved.isNotEmpty()) return saved

        val legacy = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val oldWeight = legacy.getString("weight", "")?.replace(',', '.')?.toDoubleOrNull()
        val oldHeight = legacy.getString("height", "")?.replace(',', '.')?.toDoubleOrNull()
        if (oldWeight != null || oldHeight != null) {
            val now = System.currentTimeMillis()
            val migrated = listOf(ProgressEntry(now, now, oldWeight, oldHeight, gs(prefs.getString("app_language", "RU") ?: "RU", R.string.migrated_previous_version)))
            saveProgress(migrated)
            return migrated
        }
        return emptyList()
    }

    fun saveProgress(entries: List<ProgressEntry>) = putArray("progress_entries", entries.map(::progressToJson))

    fun loadWorkoutLogs(): List<WorkoutLog> = readArray("workout_logs", ::workoutFromJson).sortedByDescending { it.finishedAt }
    fun saveWorkoutLogs(logs: List<WorkoutLog>) = putArray("workout_logs", logs.map(::workoutToJson))

    fun loadNutrition(): List<NutritionEntry> = readArray("nutrition_entries", ::nutritionFromJson).sortedByDescending { it.createdAt }
    fun saveNutrition(entries: List<NutritionEntry>) = putArray("nutrition_entries", entries.map(::nutritionToJson))

    fun loadGoals(): List<GoalEntry> = readArray("goals", ::goalFromJson).sortedByDescending { it.createdAt }
    fun saveGoals(entries: List<GoalEntry>) = putArray("goals", entries.map(::goalToJson))

    fun loadRecords(): List<PersonalRecord> = readArray("records", ::recordFromJson).sortedByDescending { it.updatedAt }
    fun saveRecords(entries: List<PersonalRecord>) = putArray("records", entries.map(::recordToJson))

    fun loadTemplates(): List<WorkoutTemplate> = readArray("workout_templates", ::templateFromJson).sortedByDescending { it.createdAt }
    fun saveTemplates(entries: List<WorkoutTemplate>) = putArray("workout_templates", entries.map(::templateToJson))

    fun loadMusicLibrary(): List<MusicTrack> = readArray("music_library", ::musicTrackFromJson).sortedByDescending { it.addedAt }
    fun saveMusicLibrary(entries: List<MusicTrack>) = putArray("music_library", entries.map(::musicTrackToJson))

    fun loadPlaylists(): List<MusicPlaylist> = readArray("music_playlists", ::playlistFromJson).sortedBy { it.createdAt }
    fun savePlaylists(entries: List<MusicPlaylist>) = putArray("music_playlists", entries.map(::playlistToJson))

    fun loadPlayerPreferences(): PlayerPreferences = runCatching {
        val raw = prefs.getString("player_preferences", null) ?: return PlayerPreferences()
        playerPreferencesFromJson(JSONObject(raw))
    }.getOrDefault(PlayerPreferences())

    fun savePlayerPreferences(value: PlayerPreferences) {
        prefs.edit().putString("player_preferences", playerPreferencesToJson(value).toString()).apply()
    }

    fun loadCachedWorkoutPlan(profile: UserProfile): List<WorkoutDay>? = runCatching {
        val raw = prefs.getString("workout_plan_cache", null) ?: return null
        val root = JSONObject(raw)
        if (root.optString("signature") != workoutPlanSignature(profile)) return null
        root.optJSONArray("days").mapObjects(::workoutDayCacheFromJson).takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun saveCachedWorkoutPlan(profile: UserProfile, plan: List<WorkoutDay>) {
        if (plan.isEmpty()) return
        val root = JSONObject()
            .put("signature", workoutPlanSignature(profile))
            .put("days", JSONArray().apply { plan.forEach { put(workoutDayCacheToJson(it)) } })
        prefs.edit().putString("workout_plan_cache", root.toString()).apply()
        primeWorkoutPlanCache(profile, plan)
    }

    fun clearCachedWorkoutPlan() {
        prefs.edit().remove("workout_plan_cache").apply()
        invalidateWorkoutPlanCache()
    }

    fun loadActiveWorkout(): ActiveWorkoutState? = runCatching {
        val raw = prefs.getString("active_workout", null) ?: return null
        activeWorkoutFromJson(JSONObject(raw))
    }.getOrNull()

    fun saveActiveWorkout(state: ActiveWorkoutState?) {
        val payload = if (state == null) null else activeWorkoutToJson(state).toString()
        val epoch = activeWorkoutWriteEpoch.incrementAndGet()
        // JSON on caller thread; SharedPreferences write on a single background thread.
        activeWorkoutExecutor.execute {
            synchronized(activeWorkoutWriteLock) {
                if (epoch != activeWorkoutWriteEpoch.get()) return@execute
                if (payload == null) prefs.edit().remove("active_workout").apply()
                else prefs.edit().putString("active_workout", payload).apply()
            }
        }
    }

    fun loadProfile(): UserProfile {
        val legacyTheme = context.getSharedPreferences("gymflow_ui", Context.MODE_PRIVATE).getString("theme_mode", "DARK") ?: "DARK"
        val birthDate = normalizeBirthDate(prefs.getString("birth_date", null))
            ?: prefs.getLong("birth_date_millis", Long.MIN_VALUE)
                .let { legacy -> if (legacy == Long.MIN_VALUE) null else birthDateFromLegacyMillis(legacy) }
        return UserProfile(
            proteinGoal = prefs.getInt("protein_goal", 0),
            calorieGoal = prefs.getInt("calorie_goal", 0),
            waterGoalMl = prefs.getInt("water_goal_ml", 2000),
            nutritionAutoTargets = prefs.getBoolean("nutrition_auto_targets", false),
            activityLevel = prefs.getString("activity_level", "ACTIVE") ?: "ACTIVE",
            reminderEnabled = prefs.getBoolean("reminder_enabled", true),
            themeMode = prefs.getString("theme_mode", legacyTheme) ?: legacyTheme,
            colorStyle = prefs.getString("color_style", "MONO") ?: "MONO",
            appLanguage = prefs.getString("app_language", "RU") ?: "RU",
            birthDate = birthDate,
            sex = prefs.getString("sex", "UNSPECIFIED") ?: "UNSPECIFIED",
            trainingExperience = prefs.getString("training_experience", "UNSPECIFIED") ?: "UNSPECIFIED",
            profilePromptDismissedAt = prefs.getLong("profile_prompt_dismissed_at", 0L),
            onboardingCompleted = prefs.getBoolean("onboarding_completed", false),
            monthlyCheckInEnabled = prefs.getBoolean("monthly_checkin_enabled", true),
            nextMonthlyCheckInAt = prefs.getLong("next_monthly_checkin_at", 0L),
            firstName = prefs.getString("first_name", "") ?: "",
            lastName = prefs.getString("last_name", "") ?: "",
            profileDescription = prefs.getString("profile_description", "") ?: "",
            avatarUri = prefs.getString("avatar_uri", "") ?: "",
            trainingPlace = prefs.getString("training_place", "GYM") ?: "GYM",
            homeEquipment = prefs.getStringSet("home_equipment", null)?.toSet() ?: emptySet(),
            trainingDays = prefs.getStringSet("training_days", null)?.toSet()
                ?: if (prefs.getBoolean("onboarding_completed", false)) setOf("TUESDAY", "FRIDAY", "SUNDAY") else emptySet(),
            selectedProgram = prefs.getString("selected_program", "FULL_BODY") ?: "FULL_BODY",
            customWorkoutName = prefs.getString("custom_workout_name", "").orEmpty(),
            dayFocus = decodeDayFocus(prefs.getString("day_focus", "") ?: ""),
            selectedExerciseIds = decodeExerciseMap(prefs.getString("selected_exercises", "") ?: ""),
            fontScale = prefs.getFloat("font_scale", 1f),
            termsAccepted = prefs.getBoolean("terms_accepted", false),
            privacyAccepted = prefs.getBoolean("privacy_accepted", false),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", prefs.getBoolean("reminder_enabled", true)),
            workoutNotifications = prefs.getBoolean("workout_notifications", true),
            proteinNotifications = prefs.getBoolean("protein_notifications", true),
            motivationNotifications = prefs.getBoolean("motivation_notifications", true),
            measurementNotifications = prefs.getBoolean("measurement_notifications", true),
            fitnessGoals = prefs.getStringSet("fitness_goals", null)?.toSet() ?: emptySet(),
            sessionDurationMinutes = prefs.getInt("session_duration_minutes", 45).coerceIn(15, 180),
            reminderHour = prefs.getInt("reminder_hour", 16).coerceIn(0, 23),
            reminderMinute = prefs.getInt("reminder_minute", 0).coerceIn(0, 59)
        )
    }

    fun saveProfile(profile: UserProfile) {
        val editor = prefs.edit()
            .putInt("protein_goal", profile.proteinGoal)
            .putInt("calorie_goal", profile.calorieGoal)
            .putInt("water_goal_ml", profile.waterGoalMl)
            .putBoolean("nutrition_auto_targets", profile.nutritionAutoTargets)
            .putString("activity_level", profile.activityLevel)
            .putBoolean("reminder_enabled", profile.reminderEnabled)
            .putString("theme_mode", profile.themeMode)
            .putString("color_style", profile.colorStyle)
            .putString("app_language", profile.appLanguage)
            .putString("sex", profile.sex)
            .putString("training_experience", profile.trainingExperience)
            .putLong("profile_prompt_dismissed_at", profile.profilePromptDismissedAt)
            .putBoolean("onboarding_completed", profile.onboardingCompleted)
            .putBoolean("monthly_checkin_enabled", profile.monthlyCheckInEnabled)
            .putLong("next_monthly_checkin_at", profile.nextMonthlyCheckInAt)
            .putString("first_name", profile.firstName)
            .putString("last_name", profile.lastName)
            .putString("profile_description", profile.profileDescription)
            .putString("avatar_uri", profile.avatarUri)
            .putString("training_place", profile.trainingPlace)
            .putStringSet("home_equipment", profile.homeEquipment.toSet())
            .putStringSet("training_days", profile.trainingDays.toSet())
            .putString("selected_program", profile.selectedProgram)
            .putString("custom_workout_name", profile.customWorkoutName)
            .putString("day_focus", encodeDayFocus(profile.dayFocus))
            .putString("selected_exercises", encodeExerciseMap(profile.selectedExerciseIds))
            .putFloat("font_scale", profile.fontScale)
            .putBoolean("terms_accepted", profile.termsAccepted)
            .putBoolean("privacy_accepted", profile.privacyAccepted)
            .putBoolean("notifications_enabled", profile.notificationsEnabled)
            .putBoolean("workout_notifications", profile.workoutNotifications)
            .putBoolean("protein_notifications", profile.proteinNotifications)
            .putBoolean("motivation_notifications", profile.motivationNotifications)
            .putBoolean("measurement_notifications", profile.measurementNotifications)
            .putStringSet("fitness_goals", profile.fitnessGoals.toSet())
            .putInt("session_duration_minutes", profile.sessionDurationMinutes.coerceIn(15, 180))
            .putInt("reminder_hour", profile.reminderHour.coerceIn(0, 23))
            .putInt("reminder_minute", profile.reminderMinute.coerceIn(0, 59))
        val normalizedBirthDate = normalizeBirthDate(profile.birthDate)
        if (normalizedBirthDate == null) editor.remove("birth_date") else editor.putString("birth_date", normalizedBirthDate)
        editor.remove("birth_date_millis")
        editor.apply()
    }

    fun exportJson(): String {
        val profile = loadProfile()
        val trainingPlan = loadCachedWorkoutPlan(profile) ?: workoutsFor(profile).also { saveCachedWorkoutPlan(profile, it) }
        val root = JSONObject()
            .put("format", "GymFlowBackup")
            .put("schemaVersion", 8)
            .put("backupVersion", "1.0")
            .put("exportedAt", System.currentTimeMillis())
            .put("profile", profileToJson(profile))
        root.put("progress", JSONArray().apply { loadProgress().forEach { put(progressToJson(it)) } })
        root.put("workouts", JSONArray().apply { loadWorkoutLogs().forEach { put(workoutToJson(it)) } })
        root.put("nutrition", JSONArray().apply { loadNutrition().forEach { put(nutritionToJson(it)) } })
        root.put("goals", JSONArray().apply { loadGoals().forEach { put(goalToJson(it)) } })
        root.put("records", JSONArray().apply { loadRecords().forEach { put(recordToJson(it)) } })
        root.put("activeWorkout", loadActiveWorkout()?.let(::activeWorkoutToJson) ?: JSONObject.NULL)
        root.put("trainingPlan", JSONArray().apply { trainingPlan.forEach { put(workoutDayCacheToJson(it)) } })
        root.put("musicLibrary", JSONArray().apply { loadMusicLibrary().forEach { put(musicTrackToJson(it)) } })
        root.put("playlists", JSONArray().apply { loadPlaylists().forEach { put(playlistToJson(it)) } })
        root.put("playerPreferences", playerPreferencesToJson(loadPlayerPreferences()))
        root.put("templates", JSONArray().apply { loadTemplates().forEach { put(templateToJson(it)) } })
        avatarBackupData(context)?.let { root.put("avatarDataBase64", it) }
        return root.toString(2)
    }

    fun importJson(raw: String): GymFlowBackup {
        val root = JSONObject(raw)
        val language = loadProfile().appLanguage
        require(root.optString("format") == "GymFlowBackup") { gs(language, R.string.unsupported_gymflow_backup_file) }
        val schema = root.optInt("schemaVersion", 1)
        require(schema in 1..8) { gs(language, R.string.unsupported_backup_version) }
        require(root.has("profile")) { gs(language, R.string.backup_profile_missing) }
        var profile = profileFromJson(root.getJSONObject("profile"))
        val avatarData = root.optString("avatarDataBase64", "")
        if (avatarData.isNotBlank()) {
            val restoredAvatar = restoreAvatarFromBackup(context, avatarData)
            if (restoredAvatar.isNotBlank()) profile = profile.copy(avatarUri = restoredAvatar)
        }
        val progress = root.optJSONArray("progress").mapObjects(::progressFromJson).sortedByDescending { it.createdAt }
        val workouts = root.optJSONArray("workouts").mapObjects(::workoutFromJson).distinctBy { it.id }.sortedByDescending { it.finishedAt }
        val nutrition = root.optJSONArray("nutrition").mapObjects(::nutritionFromJson).sortedByDescending { it.createdAt }
        val goals = root.optJSONArray("goals").mapObjects(::goalFromJson).sortedByDescending { it.createdAt }
        val records = root.optJSONArray("records").mapObjects(::recordFromJson).distinctBy { it.id }.sortedByDescending { it.updatedAt }
        val active = if (root.has("activeWorkout") && !root.isNull("activeWorkout")) activeWorkoutFromJson(root.getJSONObject("activeWorkout")) else null
        val importedPlan = root.optJSONArray("trainingPlan").mapObjects(::workoutDayCacheFromJson)
        val trainingPlan = importedPlan.takeIf { it.isNotEmpty() } ?: workoutsFor(profile)
        val musicLibrary = root.optJSONArray("musicLibrary").mapObjects(::musicTrackFromJson).distinctBy { it.uri }
        val playlists = root.optJSONArray("playlists").mapObjects(::playlistFromJson)
        val playerPreferences = root.optJSONObject("playerPreferences")?.let(::playerPreferencesFromJson) ?: PlayerPreferences()
        val templates = root.optJSONArray("templates").mapObjects(::templateFromJson).distinctBy { it.id }
        saveProfile(profile); saveProgress(progress); saveWorkoutLogs(workouts); saveNutrition(nutrition); saveGoals(goals); saveRecords(records); saveActiveWorkout(active)
        saveCachedWorkoutPlan(profile, trainingPlan); saveMusicLibrary(musicLibrary); savePlaylists(playlists); savePlayerPreferences(playerPreferences); saveTemplates(templates)
        return GymFlowBackup(profile, progress, workouts, nutrition, goals, records, active, trainingPlan, musicLibrary, playlists, playerPreferences, templates)
    }

    private fun profileToJson(p: UserProfile) = JSONObject().apply {
        put("proteinGoal", p.proteinGoal); put("calorieGoal", p.calorieGoal); put("waterGoalMl", p.waterGoalMl)
        put("nutritionAutoTargets", p.nutritionAutoTargets); put("activityLevel", p.activityLevel); put("reminderEnabled", p.reminderEnabled)
        put("themeMode", p.themeMode); put("colorStyle", p.colorStyle); put("appLanguage", p.appLanguage); putNullable("birthDate", normalizeBirthDate(p.birthDate))
        put("sex", p.sex); put("trainingExperience", p.trainingExperience); put("profilePromptDismissedAt", p.profilePromptDismissedAt)
        put("onboardingCompleted", p.onboardingCompleted); put("monthlyCheckInEnabled", p.monthlyCheckInEnabled); put("nextMonthlyCheckInAt", p.nextMonthlyCheckInAt)
        put("firstName", p.firstName); put("lastName", p.lastName); put("profileDescription", p.profileDescription); put("avatarUri", p.avatarUri)
        put("trainingPlace", p.trainingPlace); put("homeEquipment", JSONArray(p.homeEquipment.toList())); put("trainingDays", JSONArray(p.trainingDays.toList()))
        put("customWorkoutName", p.customWorkoutName); put("selectedProgram", p.selectedProgram); put("dayFocus", JSONObject(p.dayFocus)); put("selectedExerciseIds", JSONObject().apply { p.selectedExerciseIds.forEach { (k,v) -> put(k, JSONArray(v)) } })
        put("fontScale", p.fontScale.toDouble()); put("termsAccepted", p.termsAccepted); put("privacyAccepted", p.privacyAccepted)
        put("notificationsEnabled", p.notificationsEnabled); put("workoutNotifications", p.workoutNotifications); put("proteinNotifications", p.proteinNotifications)
        put("motivationNotifications", p.motivationNotifications); put("measurementNotifications", p.measurementNotifications)
        put("fitnessGoals", JSONArray(p.fitnessGoals.toList())); put("sessionDurationMinutes", p.sessionDurationMinutes.coerceIn(15, 180)); put("reminderHour", p.reminderHour); put("reminderMinute", p.reminderMinute)
    }

    private fun profileFromJson(o: JSONObject) = UserProfile(
        proteinGoal = o.optInt("proteinGoal", 0), calorieGoal = o.optInt("calorieGoal", 0), waterGoalMl = o.optInt("waterGoalMl", 2000).coerceIn(250, 10000),
        nutritionAutoTargets = o.optBoolean("nutritionAutoTargets", false), activityLevel = o.optString("activityLevel", "ACTIVE"), reminderEnabled = o.optBoolean("reminderEnabled", true),
        themeMode = o.optString("themeMode", "DARK"), colorStyle = o.optString("colorStyle", "MONO"), appLanguage = o.optString("appLanguage", "RU"),
        birthDate = normalizeBirthDate(o.optString("birthDate", "").takeIf { it.isNotBlank() }) ?: birthDateFromLegacyMillis(o.optNullableLong("birthDateMillis")),
        sex = o.optString("sex", "UNSPECIFIED"), trainingExperience = o.optString("trainingExperience", "UNSPECIFIED"), profilePromptDismissedAt = o.optLong("profilePromptDismissedAt", 0L),
        onboardingCompleted = o.optBoolean("onboardingCompleted", true), monthlyCheckInEnabled = o.optBoolean("monthlyCheckInEnabled", true), nextMonthlyCheckInAt = o.optLong("nextMonthlyCheckInAt", 0L),
        firstName = o.optString("firstName", ""), lastName = o.optString("lastName", ""), profileDescription = o.optString("profileDescription", ""), avatarUri = o.optString("avatarUri", ""),
        trainingPlace = o.optString("trainingPlace", "GYM"), homeEquipment = o.optJSONArray("homeEquipment").toStringSet(), trainingDays = if (o.has("trainingDays")) o.optJSONArray("trainingDays").toStringSet() else if (o.optBoolean("onboardingCompleted", true)) setOf("TUESDAY", "FRIDAY", "SUNDAY") else emptySet(),
        customWorkoutName = o.optString("customWorkoutName", ""), selectedProgram = o.optString("selectedProgram", "FULL_BODY"), dayFocus = o.optJSONObject("dayFocus").toStringMap(), selectedExerciseIds = o.optJSONObject("selectedExerciseIds").toStringListMap(),
        fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(.85f, 1.35f), termsAccepted = o.optBoolean("termsAccepted", false), privacyAccepted = o.optBoolean("privacyAccepted", o.optBoolean("termsAccepted", false)),
        notificationsEnabled = o.optBoolean("notificationsEnabled", o.optBoolean("reminderEnabled", true)), workoutNotifications = o.optBoolean("workoutNotifications", true), proteinNotifications = o.optBoolean("proteinNotifications", true),
        motivationNotifications = o.optBoolean("motivationNotifications", true), measurementNotifications = o.optBoolean("measurementNotifications", true),
        fitnessGoals = o.optJSONArray("fitnessGoals").toStringSet(),
        sessionDurationMinutes = o.optInt("sessionDurationMinutes", 45).coerceIn(15, 180),
        reminderHour = o.optInt("reminderHour", 16).coerceIn(0, 23),
        reminderMinute = o.optInt("reminderMinute", 0).coerceIn(0, 59)
    )

    private fun progressToJson(e: ProgressEntry) = JSONObject().apply {
        put("id", e.id); put("createdAt", e.createdAt); putNullable("weightKg", e.weightKg); putNullable("heightCm", e.heightCm); put("note", e.note)
        putNullable("upperArmCm", e.upperArmCm); putNullable("forearmCm", e.forearmCm); putNullable("chestCm", e.chestCm); putNullable("waistCm", e.waistCm); putNullable("thighCm", e.thighCm); putNullable("calfCm", e.calfCm)
    }
    private fun progressFromJson(o: JSONObject) = ProgressEntry(
        o.optLong("id", System.currentTimeMillis()), o.optLong("createdAt", System.currentTimeMillis()), o.optNullableDouble("weightKg"), o.optNullableDouble("heightCm"), o.optString("note", ""),
        o.optNullableDouble("upperArmCm"), o.optNullableDouble("forearmCm"), o.optNullableDouble("chestCm"), o.optNullableDouble("waistCm"), o.optNullableDouble("thighCm"), o.optNullableDouble("calfCm")
    )

    private fun goalToJson(e: GoalEntry) = JSONObject().apply { put("id", e.id); put("title", e.title); put("value", e.value); put("unit", e.unit); put("createdAt", e.createdAt) }
    private fun goalFromJson(o: JSONObject) = GoalEntry(o.optLong("id", System.currentTimeMillis()), o.optString("title"), o.optString("value"), o.optString("unit"), o.optLong("createdAt", System.currentTimeMillis()))
    private fun recordToJson(e: PersonalRecord) = JSONObject().apply { put("id", e.id); put("title", e.title); put("value", e.value); put("unit", e.unit); put("updatedAt", e.updatedAt) }
    private fun recordFromJson(o: JSONObject) = PersonalRecord(o.optLong("id", System.currentTimeMillis()), o.optString("title"), o.optString("value"), o.optString("unit"), o.optLong("updatedAt", System.currentTimeMillis()))

    private fun completedSetToJson(s: CompletedSet) = JSONObject().apply {
        put("exerciseId", s.exerciseId); put("setNumber", s.setNumber); put("completedAt", s.completedAt)
        putNullable("weightKg", s.weightKg); putNullable("reps", s.reps); putNullable("durationSec", s.durationSec)
    }
    private fun completedSetFromJson(o: JSONObject) = CompletedSet(
        o.optString("exerciseId"), o.optInt("setNumber"), o.optLong("completedAt"),
        o.optNullableDouble("weightKg"), o.optNullableInt("reps"), o.optNullableInt("durationSec")
    )
    private fun completedSetsToJson(sets: List<CompletedSet>) = JSONArray().apply { sets.forEach { put(completedSetToJson(it)) } }
    private fun completedSetsFromJson(arr: JSONArray?): List<CompletedSet> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) completedSetFromJson(arr.getJSONObject(i)).takeIf { it.exerciseId.isNotBlank() && it.setNumber > 0 }?.let(::add)
    }

    private fun workoutToJson(l: WorkoutLog) = JSONObject().apply {
        put("id", l.id); put("startedAt", l.startedAt); put("finishedAt", l.finishedAt); put("dayKey", l.dayKey); put("title", l.title); put("completedSets", l.completedSets); put("durationMillis", l.durationMillis)
        put("exerciseIds", JSONArray(l.exerciseIds)); put("completedSetDetails", completedSetsToJson(l.completedSetDetails)); putNullable("moodRating", l.moodRating); put("exerciseNotes", notesMapToJson(l.exerciseNotes))
    }
    private fun workoutFromJson(o: JSONObject): WorkoutLog {
        val started = o.optLong("startedAt", 0L); val finished = o.optLong("finishedAt", 0L)
        return WorkoutLog(
            o.optLong("id", started.takeIf { it > 0 } ?: System.currentTimeMillis()),
            started,
            finished,
            o.optString("dayKey"),
            o.optString("title"),
            o.optInt("completedSets"),
            o.optLong("durationMillis", (finished - started).coerceAtLeast(0)),
            o.optJSONArray("exerciseIds").toStringList(),
            completedSetsFromJson(o.optJSONArray("completedSetDetails")),
            o.optNullableInt("moodRating"),
            notesMapFromJson(o.optJSONObject("exerciseNotes"))
        )
    }

    private fun nutritionToJson(e: NutritionEntry) = JSONObject().apply { put("id", e.id); put("createdAt", e.createdAt); put("calories", e.calories); put("proteinG", e.proteinG); put("waterMl", e.waterMl); put("note", e.note) }
    private fun nutritionFromJson(o: JSONObject) = NutritionEntry(o.optLong("id", System.currentTimeMillis()), o.optLong("createdAt", System.currentTimeMillis()), o.optInt("calories", 0), o.optDouble("proteinG", 0.0), o.optInt("waterMl", 0), o.optString("note", ""))

    private fun activeWorkoutToJson(s: ActiveWorkoutState) = JSONObject().apply {
        put("dayKey", s.dayKey); put("currentExerciseIndex", s.currentExerciseIndex); put("currentSetIndex", s.currentSetIndex)
        put("completedSets", completedSetsToJson(s.completedSets)); put("restUntil", s.restUntil); put("restState", s.restState.name)
        put("workoutCompleted", s.workoutCompleted); put("startedAt", s.startedAt)
        put("isPaused", s.isPaused); put("restRemainingMs", s.restRemainingMs); put("exerciseNotes", notesMapToJson(s.exerciseNotes))
    }
    private fun activeWorkoutFromJson(o: JSONObject): ActiveWorkoutState {
        val restUntil = o.optLong("restUntil", 0L)
        val rest = runCatching { RestState.valueOf(o.optString("restState", "")) }.getOrNull()
            ?: if (restUntil > System.currentTimeMillis()) RestState.RESTING else RestState.IDLE
        return ActiveWorkoutState(
            dayKey = o.getString("dayKey"),
            currentExerciseIndex = if (o.has("currentExerciseIndex")) o.optInt("currentExerciseIndex") else o.optInt("exerciseIndex"),
            currentSetIndex = if (o.has("currentSetIndex")) o.optInt("currentSetIndex") else o.optInt("setIndex"),
            completedSets = completedSetsFromJson(o.optJSONArray("completedSets")),
            restUntil = restUntil,
            restState = rest,
            workoutCompleted = o.optBoolean("workoutCompleted", false),
            startedAt = o.optLong("startedAt", System.currentTimeMillis()),
            isPaused = o.optBoolean("isPaused", false),
            restRemainingMs = o.optLong("restRemainingMs", 0L),
            exerciseNotes = notesMapFromJson(o.optJSONObject("exerciseNotes"))
        )
    }

    private fun notesMapToJson(map: Map<String, String>) = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }
    private fun notesMapFromJson(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optString(k, "")
            if (k.isNotBlank() && v.isNotBlank()) out[k] = v
        }
        return out
    }

    private fun exerciseCacheToJson(e: Exercise) = JSONObject().apply {
        put("id", e.id); put("muscle", e.muscle); put("title", e.title); put("sets", e.sets); put("restSec", e.restSec)
        put("cues", JSONArray(e.cues)); put("reps", e.reps); putNullable("weightLabel", e.weightLabel)
    }
    private fun exerciseCacheFromJson(o: JSONObject) = Exercise(
        id = o.optString("id"), muscle = o.optString("muscle"), title = o.optString("title"),
        sets = o.optInt("sets", 1).coerceAtLeast(1), restSec = o.optInt("restSec", 90).coerceAtLeast(0),
        cues = o.optJSONArray("cues").toStringList(), reps = o.optString("reps", "8–12"),
        weightLabel = if (o.has("weightLabel") && !o.isNull("weightLabel")) o.optString("weightLabel") else null
    )
    private fun workoutDayCacheToJson(day: WorkoutDay) = JSONObject().apply {
        put("key", day.key); put("dayOfWeek", day.dayOfWeek.name); put("shortDay", day.shortDay); put("title", day.title); put("compactTitle", day.compactTitle)
        put("exercises", JSONArray().apply { day.exercises.forEach { put(exerciseCacheToJson(it)) } })
    }
    private fun workoutDayCacheFromJson(o: JSONObject): WorkoutDay {
        val dayOfWeek = runCatching { java.time.DayOfWeek.valueOf(o.optString("dayOfWeek")) }.getOrDefault(java.time.DayOfWeek.MONDAY)
        return WorkoutDay(
            key = o.optString("key"), dayOfWeek = dayOfWeek, shortDay = o.optString("shortDay"),
            title = o.optString("title"), compactTitle = o.optString("compactTitle"),
            exercises = o.optJSONArray("exercises").mapObjects(::exerciseCacheFromJson)
        )
    }

    private fun musicTrackToJson(t: MusicTrack) = JSONObject().apply {
        put("uri", t.uri); put("title", t.title); put("artist", t.artist); put("album", t.album); put("durationMs", t.durationMs); put("addedAt", t.addedAt); put("artworkUri", t.artworkUri)
    }
    private fun musicTrackFromJson(o: JSONObject) = MusicTrack(
        uri = o.optString("uri"), title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
        durationMs = o.optLong("durationMs", 0L), addedAt = o.optLong("addedAt", System.currentTimeMillis()), artworkUri = o.optString("artworkUri", "")
    )

    private fun playlistToJson(p: MusicPlaylist) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("trackUris", JSONArray(p.trackUris)); put("createdAt", p.createdAt)
    }
    private fun playlistFromJson(o: JSONObject) = MusicPlaylist(
        id = o.optLong("id", System.currentTimeMillis()), name = o.optString("name"), trackUris = o.optJSONArray("trackUris").toStringList(),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun templateToJson(t: WorkoutTemplate) = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("exerciseIds", JSONArray(t.exerciseIds))
        put("createdAt", t.createdAt)
    }

    private fun templateFromJson(o: JSONObject) = WorkoutTemplate(
        id = o.optLong("id", System.currentTimeMillis()),
        name = o.optString("name", ""),
        exerciseIds = o.optJSONArray("exerciseIds").toStringList().filter { it.isNotBlank() },
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun playerPreferencesToJson(p: PlayerPreferences) = JSONObject().apply {
        put("queueUris", JSONArray(p.queueUris)); put("currentUri", p.currentUri); put("positionMs", p.positionMs)
        put("shuffleEnabled", p.shuffleEnabled); put("repeatMode", p.repeatMode)
    }
    private fun playerPreferencesFromJson(o: JSONObject) = PlayerPreferences(
        queueUris = o.optJSONArray("queueUris").toStringList(), currentUri = o.optString("currentUri"), positionMs = o.optLong("positionMs", 0L),
        shuffleEnabled = o.optBoolean("shuffleEnabled", false), repeatMode = o.optInt("repeatMode", 0).coerceIn(0, 2)
    )

    fun resetAll() {
        activeWorkoutWriteEpoch.incrementAndGet()
        synchronized(activeWorkoutWriteLock) {
            prefs.edit().clear().commit()
        }
        deleteStoredAvatar(context)
        runCatching { java.io.File(context.filesDir, "music_art").deleteRecursively() }
    }

    private fun <T> readArray(key: String, parse: (JSONObject) -> T): List<T> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) add(parse(arr.getJSONObject(i))) }
    }.getOrDefault(emptyList())
    private fun putArray(key: String, values: List<JSONObject>) { prefs.edit().putString(key, JSONArray().apply { values.forEach { value -> put(value) } }.toString()).apply() }
    private fun encodeDayFocus(map: Map<String, String>): String = map.entries.joinToString("|") { "${it.key}=${it.value}" }
    private fun decodeDayFocus(raw: String): Map<String, String> = raw.split("|").mapNotNull { val i = it.indexOf('='); if (i > 0) it.substring(0,i) to it.substring(i+1) else null }.toMap()
    private fun encodeExerciseMap(map: Map<String, List<String>>): String = JSONObject().apply { map.forEach { (k,v) -> put(k, JSONArray(v)) } }.toString()
    private fun decodeExerciseMap(raw: String): Map<String, List<String>> = runCatching { JSONObject(raw).toStringListMap() }.getOrDefault(emptyMap())
}

private fun JSONObject.optNullableDouble(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
private fun JSONObject.optNullableInt(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.optNullableLong(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
private fun JSONArray?.toStringList(): List<String> = buildList { val a=this@toStringList ?: return@buildList; for(i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let(::add) }
private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()
private fun JSONObject?.toStringMap(): Map<String,String> = buildMap { val o=this@toStringMap ?: return@buildMap; o.keys().forEach { k -> this[k] = o.optString(k) } }
private fun JSONObject?.toStringListMap(): Map<String,List<String>> = buildMap { val o=this@toStringListMap ?: return@buildMap; o.keys().forEach { k -> this[k] = o.optJSONArray(k).toStringList() } }
private fun <T> JSONArray?.mapObjects(parse:(JSONObject)->T): List<T> = buildList { val a=this@mapObjects ?: return@buildList; for(i in 0 until a.length()) add(parse(a.getJSONObject(i))) }
