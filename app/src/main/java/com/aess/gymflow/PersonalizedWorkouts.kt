package com.aess.gymflow

import java.time.DayOfWeek

private fun px(
    id: String,
    muscle: String,
    title: String,
    sets: Int = 3,
    c1: String,
    c2: String,
    c3: String,
    reps: String? = null,
    weighted: Boolean = true
) = Exercise(
    id = id,
    muscle = muscle,
    title = title,
    sets = sets,
    restSec = if (sets <= 1) 30 else 120,
    cues = listOf(c1, c2, c3),
    reps = reps ?: if (sets <= 1) "5–7 мин" else "8–12",
    weightLabel = if (weighted) "Рабочий вес" else null
)

private fun shortRu(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "ПН"; DayOfWeek.TUESDAY -> "ВТ"; DayOfWeek.WEDNESDAY -> "СР"; DayOfWeek.THURSDAY -> "ЧТ"
    DayOfWeek.FRIDAY -> "ПТ"; DayOfWeek.SATURDAY -> "СБ"; DayOfWeek.SUNDAY -> "ВС"
}

private fun programFocus(program: String, index: Int): String = when (program) {
    "UPPER_LOWER" -> if (index % 2 == 0) "Верх тела" else "Ноги · Ягодицы"
    "PPL" -> listOf("Грудь · Трицепс", "Спина · Бицепс", "Ноги · Ягодицы")[index % 3]
    "STRENGTH" -> listOf("Грудь · Трицепс", "Ноги · Ягодицы", "Спина · Бицепс")[index % 3]
    "LEGS_GLUTES" -> "Ноги · Ягодицы"
    "UPPER" -> "Верх тела"
    "MOBILITY" -> "Мобилити · Растяжка"
    "STRETCH" -> "Растяжка"
    "YOGA_MOBILITY" -> "Мобилити · Растяжка"
    else -> "Всё тело"
}

private fun candidates(home: Boolean, focus: String, equipment: Set<String>, dayTag: String): List<Exercise> {
    val warmup = px("${dayTag}_warmup", "РАЗМИНКА", "Разминка", 1, "5–7 минут лёгкого движения", "Разомни суставы", "Начинай постепенно", weighted = false)
    val stretch = px("${dayTag}_stretch", "РАСТЯЖКА", "Заминка", 1, "Снизь пульс", "Мягкая растяжка", "Без боли", reps = "30–60 сек", weighted = false)
    val mobility = px("${dayTag}_mobility", "МОБИЛИТИ", "Мобильность суставов", 2, "Работай в комфортной амплитуде", "Не пружинь", "Дыши спокойно", reps = "8–12 / сторона", weighted = false)

    if (!home) {
        val gym = when {
            focus.contains("Ноги") -> FridayWorkout.exercises.drop(1).dropLast(1)
            focus.contains("Спина") || focus.contains("Верх") -> TuesdayWorkout.exercises.drop(1).dropLast(1)
            focus.contains("Мобил") || focus.contains("Растяж") || focus.contains("Yoga") -> listOf(mobility,
                px("${dayTag}_core", "КОР", "Планка", 3, "Корпус одной линией", "Не проваливай поясницу", "Дыши ровно", reps = "30–60 сек", weighted = false))
            else -> SundayWorkout.exercises.drop(1).dropLast(1)
        }
        return listOf(warmup) + gym + listOf(stretch)
    }

    val hasDumbbells = "DUMBBELLS" in equipment
    val hasBar = "PULLUP_BAR" in equipment
    val hasBands = "BANDS" in equipment
    val hasBench = "BENCH" in equipment
    val bodyOnly = equipment.isEmpty() || "BODYWEIGHT" in equipment

    val pool = mutableListOf<Exercise>()
    if (focus.contains("Ноги") || focus == "Всё тело") {
        pool += px("${dayTag}_squat", "НОГИ", if (hasDumbbells) "Приседания с гантелью" else "Приседания", 3, "Колени по линии стоп", "Спина нейтральна", "Движение контролируемое", weighted = hasDumbbells)
        pool += px("${dayTag}_bridge", "ЯГОДИЦЫ", "Ягодичный мост", 3, "Толкайся пятками", "Пауза наверху", "Не переразгибай поясницу", weighted = hasDumbbells)
        pool += px("${dayTag}_lunge", "НОГИ", "Выпады", 3, "Колено по линии стопы", "Корпус стабилен", "Шаг комфортной длины", weighted = hasDumbbells)
    }
    if (focus.contains("Спина") || focus.contains("Верх") || focus == "Всё тело") {
        if (hasBar) pool += px("${dayTag}_pullup", "СПИНА", "Подтягивания", 3, "Начинай лопатками", "Без раскачки", "Контролируй спуск", weighted = false)
        if (hasDumbbells) pool += px("${dayTag}_row", "СПИНА", "Тяга гантели в наклоне", 3, "Спина нейтральна", "Локоть к тазу", "Без рывка")
        if (hasBands) pool += px("${dayTag}_bandrow", "СПИНА", "Тяга резинки", 3, "Лопатки назад", "Не поднимай плечи", "Возвращай плавно", weighted = false)
        if (hasDumbbells) pool += px("${dayTag}_curl", "БИЦЕПС", "Сгибания с гантелями", 3, "Локти у корпуса", "Не раскачивайся", "Медленно вниз")
    }
    if (focus.contains("Грудь") || focus.contains("Верх") || focus == "Всё тело") {
        pool += px("${dayTag}_pushup", "ГРУДЬ", "Отжимания", 3, "Корпус одной линией", "Локти не разводи широко", "Опускайся контролируемо", weighted = false)
        if (hasDumbbells) pool += px("${dayTag}_press", "ГРУДЬ", if (hasBench) "Жим гантелей на скамье" else "Жим гантелей лёжа на полу", 3, "Лопатки стабильны", "Кисти над локтями", "Без рывков")
        if (hasDumbbells) pool += px("${dayTag}_shoulder", "ПЛЕЧИ", "Жим гантелей", 3, "Не прогибай поясницу", "Кисти над локтями", "Движение плавное")
    }
    if (focus.contains("Мобил") || focus.contains("Растяж") || focus.contains("Yoga")) {
        pool += mobility
        pool += px("${dayTag}_flow", "МОБИЛИТИ", "Спокойный комплекс мобильности", 3, "Двигайся плавно", "Не работай через боль", "Дыши ровно", reps = "45–60 сек", weighted = false)
    }
    if (bodyOnly || pool.isEmpty()) {
        pool += px("${dayTag}_plank", "КОР", "Планка", 3, "Подкрути таз", "Дыши ровно", "Не проваливай поясницу", reps = "30–60 сек", weighted = false)
    }
    return listOf(warmup) + pool.distinctBy { it.id } + listOf(stretch)
}

internal fun workoutPlanSignature(profile: UserProfile): String = buildString {
    append(profile.trainingPlace).append('|')
    append(profile.homeEquipment.sorted().joinToString(",")).append('|')
    append(profile.trainingDays.sorted().joinToString(",")).append('|')
    append(profile.selectedProgram).append('|')
    append(profile.dayFocus.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }).append('|')
    append(profile.selectedExerciseIds.toSortedMap().entries.joinToString(",") { (day, ids) -> "$day=${ids.joinToString(";")}" })
}

private object WorkoutPlanMemoryCache {
    @Volatile var signature: String? = null
    @Volatile var plan: List<WorkoutDay>? = null
}

internal fun primeWorkoutPlanCache(profile: UserProfile, plan: List<WorkoutDay>) {
    WorkoutPlanMemoryCache.signature = workoutPlanSignature(profile)
    WorkoutPlanMemoryCache.plan = plan
}

internal fun invalidateWorkoutPlanCache() {
    WorkoutPlanMemoryCache.signature = null
    WorkoutPlanMemoryCache.plan = null
}

fun workoutsFor(profile: UserProfile): List<WorkoutDay> {
    val signature = workoutPlanSignature(profile)
    WorkoutPlanMemoryCache.plan?.takeIf { WorkoutPlanMemoryCache.signature == signature }?.let { return it }

    val chosen = profile.trainingDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.sortedBy { it.value }
        .ifEmpty { listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY) }

    val generated = chosen.mapIndexed { index, day ->
        val focus = profile.dayFocus[day.name]?.takeIf { it.isNotBlank() } ?: programFocus(profile.selectedProgram, index)
        val dayTag = "custom_${day.name.lowercase()}"
        val all = candidates(profile.trainingPlace == "HOME", focus, profile.homeEquipment, dayTag)
        val selectedIds = profile.selectedExerciseIds[day.name].orEmpty()
        val exercises = if (selectedIds.isEmpty()) all.take(8) else selectedIds.mapNotNull { id -> all.firstOrNull { it.id == id } }.ifEmpty { all.take(8) }
        WorkoutDay(
            key = "custom_${day.name}",
            dayOfWeek = day,
            shortDay = shortRu(day),
            title = focus,
            compactTitle = focus,
            exercises = exercises
        )
    }
    primeWorkoutPlanCache(profile, generated)
    return generated
}

fun workoutCandidatesFor(profile: UserProfile, day: DayOfWeek, index: Int): List<Exercise> {
    val focus = profile.dayFocus[day.name]?.takeIf { it.isNotBlank() } ?: programFocus(profile.selectedProgram, index)
    return candidates(profile.trainingPlace == "HOME", focus, profile.homeEquipment, "custom_${day.name.lowercase()}")
}
