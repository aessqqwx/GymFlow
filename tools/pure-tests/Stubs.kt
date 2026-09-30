package com.aess.gymflow

object R {
    object string {
        const val ach_first_workout_desc = 10
        const val ach_first_workout_title = 11
        const val ach_record_10_desc = 12
        const val ach_record_10_title = 13
        const val ach_record_1_desc = 14
        const val ach_record_1_title = 15
        const val ach_record_5_desc = 16
        const val ach_record_5_title = 17
        const val ach_streak_14_desc = 18
        const val ach_streak_14_title = 19
        const val ach_streak_30_desc = 20
        const val ach_streak_30_title = 21
        const val ach_streak_7_desc = 22
        const val ach_streak_7_title = 23
        const val ach_workouts_10_desc = 24
        const val ach_workouts_10_title = 25
        const val ach_workouts_25_desc = 26
        const val ach_workouts_25_title = 27
        const val ach_workouts_3_desc = 28
        const val ach_workouts_3_title = 29
        const val ach_workouts_50_desc = 30
        const val ach_workouts_50_title = 31
        const val monthly_recap_empty_summary = 32
        const val monthly_recap_summary = 33

        const val kg = 1
        const val reps_unit = 2
        const val sec_unit = 3
        const val workout = 4
    }
}
fun gs(language: String, id: Int, vararg args: Any): String = when (id) {
    R.string.kg -> if (language == "EN") "kg" else "кг"
    R.string.reps_unit -> if (language == "EN") "reps" else "раз"
    R.string.sec_unit -> if (language == "EN") "sec" else "сек"
    R.string.workout -> if (language == "EN") "Workout" else "Тренировка"
    else -> ""
}
fun exerciseTitle(exercise: Exercise, language: String): String = exercise.title

fun dayShort(day: java.time.DayOfWeek, language: String): String = when (day) {
    java.time.DayOfWeek.MONDAY -> if (language == "EN") "MON" else "ПН"
    java.time.DayOfWeek.TUESDAY -> if (language == "EN") "TUE" else "ВТ"
    java.time.DayOfWeek.WEDNESDAY -> if (language == "EN") "WED" else "СР"
    java.time.DayOfWeek.THURSDAY -> if (language == "EN") "THU" else "ЧТ"
    java.time.DayOfWeek.FRIDAY -> if (language == "EN") "FRI" else "ПТ"
    java.time.DayOfWeek.SATURDAY -> if (language == "EN") "SAT" else "СБ"
    java.time.DayOfWeek.SUNDAY -> if (language == "EN") "SUN" else "ВС"
}

fun exerciseMuscle(exercise: Exercise, language: String): String = exercise.muscle
