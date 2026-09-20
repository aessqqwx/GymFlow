package com.aess.gymflow

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

fun parseBirthDate(value: String?): LocalDate? = value
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    ?.takeIf { !it.isAfter(LocalDate.now()) }

fun normalizeBirthDate(value: String?): String? = parseBirthDate(value)?.toString()

fun birthDateFromLegacyMillis(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    return runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now()) }?.toString()
}

fun birthDateToEpochMillis(value: String?): Long? = parseBirthDate(value)
    ?.atStartOfDay(ZoneId.systemDefault())
    ?.toInstant()
    ?.toEpochMilli()

fun ageYearsFromBirthDate(value: String?): Int? {
    val birth = parseBirthDate(value) ?: return null
    return Period.between(birth, LocalDate.now()).years.takeIf { it >= 0 }
}

fun maxBirthMonth(year: Int, today: LocalDate = LocalDate.now()): Int =
    if (year >= today.year) today.monthValue else 12

fun maxBirthDay(year: Int, month: Int, today: LocalDate = LocalDate.now()): Int {
    val safeYear = year.coerceAtMost(today.year)
    val safeMonth = month.coerceIn(1, maxBirthMonth(safeYear, today))
    val calendarMax = java.time.YearMonth.of(safeYear, safeMonth).lengthOfMonth()
    return if (safeYear == today.year && safeMonth == today.monthValue) {
        minOf(calendarMax, today.dayOfMonth)
    } else calendarMax
}

fun clampBirthDate(year: Int, month: Int, day: Int, today: LocalDate = LocalDate.now()): LocalDate {
    val safeYear = year.coerceIn(1900, today.year)
    val safeMonth = month.coerceIn(1, maxBirthMonth(safeYear, today))
    val safeDay = day.coerceIn(1, maxBirthDay(safeYear, safeMonth, today))
    return LocalDate.of(safeYear, safeMonth, safeDay)
}
