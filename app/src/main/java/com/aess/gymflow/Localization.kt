package com.aess.gymflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppLanguage = staticCompositionLocalOf { "RU" }

private data class ExerciseLocale(
    val muscle: String,
    val title: String,
    val cues: List<String>
)

private val EN_EXERCISES = mapOf(
    "tu_warmup" to ExerciseLocale("WARM-UP", "Upper-body warm-up", listOf("5–7 minutes of easy cardio", "Warm up shoulders and elbows", "Make the first working set lighter")),
    "tu_lats" to ExerciseLocale("LATS", "Wide-grip lat pulldown", listOf("Keep the chest slightly lifted", "Drive elbows down toward your sides", "Do not swing your torso")),
    "tu_row" to ExerciseLocale("MID BACK", "Seated cable row", listOf("Keep a neutral spine", "Squeeze the shoulder blades", "Do not let the weight snap back")),
    "tu_backext" to ExerciseLocale("SPINAL ERECTORS", "Back extension", listOf("Move under control", "Do not overextend the lower back", "Keep the torso as one unit")),
    "tu_latraise" to ExerciseLocale("SIDE DELTS", "Dumbbell lateral raise", listOf("Keep elbows slightly bent", "Do not shrug the shoulders", "No swinging")),
    "tu_rearfly" to ExerciseLocale("REAR DELTS", "Reverse fly", listOf("Lead with the elbows", "Keep the torso stable", "Control the return")),
    "tu_curl" to ExerciseLocale("BICEPS", "Dumbbell curl", listOf("Keep elbows close to your sides", "Do not swing", "Lower the weight slowly")),
    "tu_hammer" to ExerciseLocale("BRACHIALIS", "Hammer curl", listOf("Use a neutral grip", "Keep elbows still", "Do not move the upper arm forward")),
    "tu_forearm" to ExerciseLocale("FOREARMS", "Wrist flexion and extension", listOf("Use a light weight", "Use a full controlled range", "Do not train through pain")),
    "tu_stretch" to ExerciseLocale("STRETCH", "Upper-body stretch", listOf("Gently stretch the lats", "Stretch biceps and forearms", "No pain or jerky movements")),

    "fr_warmup" to ExerciseLocale("WARM-UP", "Leg warm-up", listOf("5–7 minutes of walking or cycling", "Warm up knees and ankles", "Do a few bodyweight squats")),
    "fr_legpress" to ExerciseLocale("QUADS", "Leg press", listOf("Keep knees tracking with the feet", "Keep the lower back supported", "Do not snap the knees straight")),
    "fr_legcurl" to ExerciseLocale("HAMSTRINGS", "Leg curl", listOf("Keep the hips down", "Curl smoothly", "Do not drop the weight")),
    "fr_hip" to ExerciseLocale("GLUTES", "Hip thrust", listOf("Drive through the heels", "Do not overextend the lower back", "Pause at the top")),
    "fr_abduct" to ExerciseLocale("ABDUCTORS", "Hip abduction", listOf("Keep the torso stable", "No jerky swings", "Control the return")),
    "fr_adduct" to ExerciseLocale("ADDUCTORS", "Hip adduction", listOf("Do not squeeze with a jerk", "Use a comfortable range", "Do not train through pain")),
    "fr_calf" to ExerciseLocale("CALVES", "Standing calf raise", listOf("Use a full range", "Pause at the top", "Lower slowly")),
    "fr_seatedcalf" to ExerciseLocale("SOLEUS", "Seated calf raise", listOf("Keep knees bent", "Do not bounce", "Control the stretch")),
    "fr_tib" to ExerciseLocale("TIBIALIS", "Toe raise", listOf("Keep heels on the floor", "Move through the ankle", "Do not rush")),
    "fr_shoulder" to ExerciseLocale("SIDE DELTS", "Lateral raise", listOf("Use a light controlled weight", "Keep shoulders down", "No swinging")),
    "fr_core" to ExerciseLocale("CORE", "Crunches and plank", listOf("Do not pull the neck", "Curl the pelvis gently", "Breathe steadily")),
    "fr_stretch" to ExerciseLocale("STRETCH", "Leg stretch", listOf("Quadriceps", "Hamstrings", "Glutes and calves")),

    "su_warmup" to ExerciseLocale("WARM-UP", "Shoulder and chest warm-up", listOf("Shoulder circles", "Easy push-ups", "Warm-up set before pressing")),
    "su_incline" to ExerciseLocale("UPPER CHEST", "Incline bench press", listOf("Set the shoulder blades down and back", "Keep feet firmly on the floor", "Lower the weight under control")),
    "su_bench" to ExerciseLocale("CHEST", "Flat bench press", listOf("Control the bar path", "Do not flare elbows straight out", "Keep a stable base")),
    "su_fly" to ExerciseLocale("CHEST FLY", "Cable fly / pec deck", listOf("Keep elbows slightly bent", "Do not round shoulders forward", "Return smoothly")),
    "su_overhead" to ExerciseLocale("LONG HEAD TRICEPS", "Overhead triceps extension", listOf("Keep elbows pointing forward", "Keep upper arms still", "Do not arch the lower back")),
    "su_pushdown" to ExerciseLocale("LATERAL TRICEPS", "Cable pushdown", listOf("Keep elbows pinned", "Only the forearms move", "Control the return")),
    "su_forearm" to ExerciseLocale("FOREARMS", "Reverse curl", listOf("Use an overhand grip with palms down", "Keep elbows next to the body", "Curl with the forearms without swinging")),
    "su_stretch" to ExerciseLocale("STRETCH", "Chest and arm stretch", listOf("Chest", "Triceps", "Forearms"))
)

private val EN_MUSCLES = mapOf(
    "РАЗМИНКА" to "WARM-UP", "РАСТЯЖКА" to "STRETCH", "МОБИЛИТИ" to "MOBILITY", "НОГИ" to "LEGS", "ЯГОДИЦЫ" to "GLUTES", "БЕДРО" to "THIGHS",
    "СПИНА" to "BACK", "БИЦЕПС" to "BICEPS", "ГРУДЬ" to "CHEST", "ПЛЕЧИ" to "SHOULDERS", "КОР" to "CORE", "ПРЕСС" to "ABS"
)
private val EN_GENERATED_TITLES = mapOf(
    "Разминка" to "Warm-up", "Заминка" to "Cool-down", "Мобильность суставов" to "Joint mobility", "Мягкая растяжка и мобильность" to "Gentle stretching and mobility",
    "Приседания" to "Squats", "Приседания с гантелью" to "Goblet squat", "Ягодичный мост" to "Glute bridge", "Выпады" to "Lunges", "Подтягивания" to "Pull-ups",
    "Тяга гантели в наклоне" to "One-arm dumbbell row", "Тяга резинки" to "Band row", "Сгибания с гантелями" to "Dumbbell curl", "Отжимания" to "Push-ups",
    "Жим гантелей на скамье" to "Dumbbell bench press", "Жим гантелей лёжа на полу" to "Dumbbell floor press", "Жим гантелей" to "Dumbbell shoulder press", "Планка" to "Plank",
    "Спокойный комплекс мобильности" to "Gentle mobility flow"
)
private val EN_CUES = mapOf(
    "5–7 минут лёгкого движения" to "5–7 minutes of easy movement", "Разомни суставы" to "Warm up your joints", "Начинай постепенно" to "Build up gradually",
    "Снизь пульс" to "Let your heart rate come down", "Мягкая растяжка" to "Stretch gently", "Без боли" to "No pain", "Работай в комфортной амплитуде" to "Use a comfortable range",
    "Не пружинь" to "Do not bounce", "Дыши спокойно" to "Breathe calmly", "Колени по линии стоп" to "Keep knees tracking with feet", "Спина нейтральна" to "Keep a neutral spine",
    "Движение контролируемое" to "Move under control", "Толкайся пятками" to "Drive through your heels", "Пауза наверху" to "Pause at the top", "Не переразгибай поясницу" to "Do not overextend the lower back",
    "Колено по линии стопы" to "Keep the knee tracking with the foot", "Корпус стабилен" to "Keep your torso stable", "Шаг комфортной длины" to "Use a comfortable stride", "Начинай лопатками" to "Start with the shoulder blades",
    "Без раскачки" to "Do not swing", "Контролируй спуск" to "Control the lowering phase", "Локоть к тазу" to "Drive the elbow toward the hip", "Без рывка" to "No jerking",
    "Лопатки назад" to "Pull the shoulder blades back", "Не поднимай плечи" to "Keep shoulders down", "Возвращай плавно" to "Return smoothly", "Локти у корпуса" to "Keep elbows by your sides",
    "Не раскачивайся" to "Do not swing", "Медленно вниз" to "Lower slowly", "Корпус одной линией" to "Keep the body in one line", "Локти не разводи широко" to "Do not flare the elbows too wide",
    "Опускайся контролируемо" to "Lower under control", "Лопатки стабильны" to "Keep shoulder blades stable", "Кисти над локтями" to "Keep wrists over elbows", "Без рывков" to "No jerking",
    "Не прогибай поясницу" to "Do not arch the lower back", "Движение плавное" to "Move smoothly", "Подкрути таз" to "Tuck the pelvis slightly", "Дыши ровно" to "Breathe steadily",
    "Не проваливай поясницу" to "Do not let the lower back sag", "Двигайся плавно" to "Move smoothly", "Не работай через боль" to "Do not train through pain"
)

fun exerciseMuscle(exercise: Exercise, language: String): String =
    if (language == "EN") EN_EXERCISES[exercise.id]?.muscle ?: EN_MUSCLES[exercise.muscle] ?: exercise.muscle else exercise.muscle

fun exerciseTitle(exercise: Exercise, language: String): String =
    if (language == "EN") EN_EXERCISES[exercise.id]?.title ?: EN_GENERATED_TITLES[exercise.title] ?: exercise.title else exercise.title

fun exerciseCues(exercise: Exercise, language: String): List<String> =
    if (language == "EN") EN_EXERCISES[exercise.id]?.cues ?: exercise.cues.map { EN_CUES[it] ?: it } else exercise.cues

private fun focusEn(value: String): String = when (value) {
    "Всё тело" -> "Full Body"
    "Грудь · Трицепс" -> "Chest · Triceps"
    "Спина · Бицепс" -> "Back · Biceps"
    "Ноги · Ягодицы" -> "Legs · Glutes"
    "Плечи · Пресс" -> "Shoulders · Abs"
    "Мобилити · Растяжка" -> "Mobility · Stretching"
    "Верх тела" -> "Upper body"
    "Растяжка" -> "Stretching"
    else -> value
}

fun workoutShortDay(day: WorkoutDay, language: String): String = dayShort(day.dayOfWeek, language)
fun workoutTitle(day: WorkoutDay, language: String): String = if (language == "EN") when (day.key) {
    "tuesday" -> "Back · Shoulders · Biceps · Forearms"
    "friday" -> "Legs · Shoulders · Core"
    "sunday" -> "Chest · Triceps · Forearms"
    else -> focusEn(day.title)
} else day.title
fun workoutCompactTitle(day: WorkoutDay, language: String): String = if (language == "EN") when (day.key) {
    "tuesday" -> "Back · Shoulders"
    "friday" -> "Legs · Shoulders"
    "sunday" -> "Chest · Triceps"
    else -> focusEn(day.compactTitle)
} else day.compactTitle

fun dayShort(day: java.time.DayOfWeek, language: String): String =
    gsa(language, R.array.weekdays_short).getOrElse(day.value - 1) { day.name }

fun dayName(day: java.time.DayOfWeek, language: String): String =
    gsa(language, R.array.weekdays_full).getOrElse(day.value - 1) { day.name }

fun monthName(month: Int, language: String): String =
    gsa(language, R.array.months_date).getOrElse(month - 1) { "" }

fun exerciseReps(exercise: Exercise, language: String): String {
    if (language != "EN") return exercise.reps
    return exercise.reps
        .replace("мин", "min", ignoreCase = true)
        .replace("сек", "sec", ignoreCase = true)
        .replace("сторона", "side", ignoreCase = true)
}

fun programName(program: String, language: String): String = when (program) {
    "FULL_BODY" -> gs(language, R.string.full_body)
    "UPPER_LOWER" -> gs(language, R.string.upper_lower)
    "PPL" -> gs(language, R.string.push_pull_legs)
    "STRENGTH" -> gs(language, R.string.strength)
    "BASIC" -> gs(language, R.string.basic_program)
    "BODYWEIGHT" -> gs(language, R.string.bodyweight)
    "DUMBBELLS" -> gs(language, R.string.dumbbells)
    "PULLUP" -> gs(language, R.string.pull_up_bar)
    "DUMBBELLS_PULLUP" -> gs(language, R.string.dumbbells_pull_up_bar)
    "HOME_MIX" -> gs(language, R.string.mixed_home_program)
    "LEGS_GLUTES" -> gs(language, R.string.legs_glutes)
    "UPPER" -> gs(language, R.string.upper_body)
    "MOBILITY" -> gs(language, R.string.mobility)
    "STRETCH" -> gs(language, R.string.stretching)
    "YOGA_MOBILITY" -> gs(language, R.string.yoga_style_mobility)
    else -> program
}
