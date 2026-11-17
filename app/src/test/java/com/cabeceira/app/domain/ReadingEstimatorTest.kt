package com.cabeceira.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadingEstimatorTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun `usa ritmo padrao sem historico`() {
        val pace = ReadingEstimator.pace(emptyList(), emptyList(), 30)
        assertEquals(PaceSource.DEFAULT, pace.source)
        assertEquals(30.0, pace.pagesPerHour, 0.001)
    }

    @Test
    fun `prefere ritmo do livro quando ha tempo suficiente`() {
        val book = listOf(SessionSample(pages = 20, minutes = 40)) // 30 p/h
        val all = book + SessionSample(pages = 100, minutes = 60)
        val pace = ReadingEstimator.pace(book, all, 10)
        assertEquals(PaceSource.BOOK, pace.source)
        assertEquals(30.0, pace.pagesPerHour, 0.001)
    }

    @Test
    fun `usa ritmo pessoal quando o livro tem pouco historico`() {
        val book = listOf(SessionSample(pages = 5, minutes = 10))
        val all = book + SessionSample(pages = 55, minutes = 110) // 60p em 120min = 30 p/h
        val pace = ReadingEstimator.pace(book, all, 10)
        assertEquals(PaceSource.PERSONAL, pace.source)
        assertEquals(30.0, pace.pagesPerHour, 0.001)
    }

    @Test
    fun `estimativa de 300 paginas a 30 por hora lendo 30 min por dia`() {
        val est = ReadingEstimator.estimate(300, Pace(30.0, PaceSource.DEFAULT), 30, today)
        assertEquals(600, est.remainingMinutes) // 10 h
        assertEquals(20, est.readingDays)
        assertEquals(today.plusDays(19), est.finishDate)
    }

    @Test
    fun `fila e agendada em sequencia apos livros em andamento`() {
        val slots = ReadingEstimator.schedule(
            queue = listOf(1L to 60, 2L to 90),
            alreadyBookedMinutes = 30,
            dailyMinutes = 30,
            today = today,
        )
        assertEquals(today.plusDays(1), slots[0].startDate)
        assertEquals(today.plusDays(2), slots[0].finishDate)
        assertEquals(today.plusDays(3), slots[1].startDate)
        assertEquals(today.plusDays(5), slots[1].finishDate)
    }
}
