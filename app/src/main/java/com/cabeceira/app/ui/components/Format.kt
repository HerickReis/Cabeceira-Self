package com.cabeceira.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ptBr = Locale("pt", "BR")
private val shortDate = DateTimeFormatter.ofPattern("dd/MM", ptBr)
private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)

fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

fun formatDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "hoje"
    today.plusDays(1) -> "amanhã"
    else -> if (date.year == today.year) date.format(shortDate) else date.format(fullDate)
}

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(fullDate)

fun formatPace(pagesPerHour: Double): String = "${pagesPerHour.roundToInt()} pág/h"

fun plural(n: Int, singular: String, pluralForm: String) = if (n == 1) "$n $singular" else "$n $pluralForm"
