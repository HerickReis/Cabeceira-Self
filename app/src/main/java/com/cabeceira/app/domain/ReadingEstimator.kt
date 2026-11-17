package com.cabeceira.app.domain

import java.time.LocalDate
import kotlin.math.ceil

enum class PaceSource(val label: String) {
    BOOK("seu ritmo neste livro"),
    PERSONAL("seu ritmo médio"),
    DEFAULT("ritmo padrão — ajuste nas configurações"),
}

data class Pace(val pagesPerHour: Double, val source: PaceSource)

/** Uma sessão reduzida ao que importa para o cálculo (desacoplado do Room). */
data class SessionSample(val pages: Int, val minutes: Int)

data class Estimate(
    val remainingPages: Int,
    val remainingMinutes: Int,
    /** Quantos dias de leitura (no ritmo diário configurado) faltam. */
    val readingDays: Int,
    val finishDate: LocalDate,
    val pace: Pace,
)

data class QueueSlot(
    val bookId: Long,
    val minutes: Int,
    val startDate: LocalDate,
    val finishDate: LocalDate,
)

/**
 * Coração do app: transforma páginas em tempo.
 *
 * O ritmo (páginas/hora) é escolhido assim:
 *  1. se você já leu pelo menos [MIN_BOOK_MINUTES] deste livro, usa o ritmo dele
 *     (cada livro tem densidade diferente);
 *  2. senão, se tiver pelo menos [MIN_PERSONAL_MINUTES] registrados no total, usa seu ritmo médio;
 *  3. senão, usa o ritmo padrão das configurações.
 */
object ReadingEstimator {
    const val MIN_BOOK_MINUTES = 30
    const val MIN_PERSONAL_MINUTES = 60

    fun pace(
        bookSessions: List<SessionSample>,
        allSessions: List<SessionSample>,
        defaultPagesPerHour: Int,
    ): Pace {
        pagesPerHour(bookSessions, MIN_BOOK_MINUTES)?.let { return Pace(it, PaceSource.BOOK) }
        pagesPerHour(allSessions, MIN_PERSONAL_MINUTES)?.let { return Pace(it, PaceSource.PERSONAL) }
        return Pace(defaultPagesPerHour.coerceAtLeast(1).toDouble(), PaceSource.DEFAULT)
    }

    private fun pagesPerHour(sessions: List<SessionSample>, minMinutes: Int): Double? {
        val valid = sessions.filter { it.pages > 0 && it.minutes > 0 }
        val minutes = valid.sumOf { it.minutes }
        if (minutes < minMinutes) return null
        return valid.sumOf { it.pages } / (minutes / 60.0)
    }

    fun minutesFor(pages: Int, pagesPerHour: Double): Int =
        if (pages <= 0 || pagesPerHour <= 0) 0 else ceil(pages / pagesPerHour * 60.0).toInt()

    /** Dia em que você termina, considerando que hoje conta como o 1º dia de leitura. */
    fun finishDate(totalMinutes: Int, dailyMinutes: Int, today: LocalDate): LocalDate {
        if (totalMinutes <= 0) return today
        val days = ceil(totalMinutes.toDouble() / dailyMinutes.coerceAtLeast(1)).toLong()
        return today.plusDays(days - 1)
    }

    fun estimate(
        remainingPages: Int,
        pace: Pace,
        dailyMinutes: Int,
        today: LocalDate,
    ): Estimate {
        val minutes = minutesFor(remainingPages, pace.pagesPerHour)
        val days = if (minutes == 0) 0 else ceil(minutes.toDouble() / dailyMinutes.coerceAtLeast(1)).toInt()
        return Estimate(
            remainingPages = remainingPages,
            remainingMinutes = minutes,
            readingDays = days,
            finishDate = finishDate(minutes, dailyMinutes, today),
            pace = pace,
        )
    }

    /**
     * Agenda a fila "Quero ler" em sequência, depois do que você já está lendo.
     * [alreadyBookedMinutes] = minutos que faltam nos livros em andamento.
     */
    fun schedule(
        queue: List<Pair<Long, Int>>, // (bookId, minutos estimados)
        alreadyBookedMinutes: Int,
        dailyMinutes: Int,
        today: LocalDate,
    ): List<QueueSlot> {
        val daily = dailyMinutes.coerceAtLeast(1)
        var cursor = alreadyBookedMinutes.coerceAtLeast(0)
        return queue.map { (id, minutes) ->
            val start = today.plusDays((cursor / daily).toLong())
            cursor += minutes
            QueueSlot(id, minutes, start, finishDate(cursor, daily, today))
        }
    }
}
