package com.aess.gymflow

object R {
    object string {
        const val kg = 1
        const val reps_unit = 2
        const val sec_unit = 3
    }
}
fun gs(language: String, id: Int, vararg args: Any): String = when (id) {
    R.string.kg -> if (language == "EN") "kg" else "кг"
    R.string.reps_unit -> if (language == "EN") "reps" else "раз"
    R.string.sec_unit -> if (language == "EN") "sec" else "сек"
    else -> ""
}
fun exerciseTitle(exercise: Exercise, language: String): String = exercise.title
