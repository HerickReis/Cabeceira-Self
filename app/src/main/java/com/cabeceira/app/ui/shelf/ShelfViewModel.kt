package com.cabeceira.app.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cabeceira.app.data.BookRepository
import com.cabeceira.app.data.SettingsStore
import com.cabeceira.app.data.local.Book
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.local.remainingPages
import com.cabeceira.app.domain.Estimate
import com.cabeceira.app.domain.QueueSlot
import com.cabeceira.app.domain.ReadingEstimator
import com.cabeceira.app.domain.SessionSample
import com.cabeceira.app.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ShelfStats(
    val readingCount: Int = 0,
    val queueCount: Int = 0,
    val queueMinutes: Int = 0,
    val queueFinish: LocalDate? = null,
    val finishedThisYear: Int = 0,
    val minutesThisYear: Int = 0,
)

data class ShelfUiState(
    val loading: Boolean = true,
    val selected: ReadingStatus = ReadingStatus.READING,
    val byStatus: Map<ReadingStatus, List<Book>> = emptyMap(),
    val estimates: Map<Long, Estimate> = emptyMap(),
    val queue: Map<Long, QueueSlot> = emptyMap(),
    val stats: ShelfStats = ShelfStats(),
    val dailyMinutes: Int = 30,
) {
    val visible: List<Book> get() = byStatus[selected].orEmpty()
}

class ShelfViewModel(private val repo: BookRepository, settings: SettingsStore) : ViewModel() {
    private val selected = MutableStateFlow(ReadingStatus.READING)

    val state = combine(
        repo.observeBooks(),
        repo.observeAllSessions(),
        settings.settings,
        selected,
    ) { books, sessions, cfg, sel ->
        val today = LocalDate.now()
        val all = sessions.map { SessionSample(it.pagesRead, it.minutes) }
        val byBook = sessions.groupBy { it.bookId }

        val estimates = books
            .filter { it.status == ReadingStatus.READING || it.status == ReadingStatus.WANT_TO_READ }
            .associate { book ->
                val pace = ReadingEstimator.pace(
                    byBook[book.id].orEmpty().map { SessionSample(it.pagesRead, it.minutes) },
                    all,
                    cfg.defaultPagesPerHour,
                )
                book.id to ReadingEstimator.estimate(book.remainingPages, pace, cfg.dailyMinutes, today)
            }

        val grouped = books.groupBy { it.status }.mapValues { (status, list) ->
            when (status) {
                ReadingStatus.FINISHED -> list.sortedByDescending { it.finishedAt ?: 0 }
                ReadingStatus.READING -> list.sortedByDescending { it.startedAt ?: 0 }
                else -> list // já vem ordenado pela posição na fila
            }
        }

        val reading = grouped[ReadingStatus.READING].orEmpty()
        val queueBooks = grouped[ReadingStatus.WANT_TO_READ].orEmpty()
        val bookedMinutes = reading.sumOf { estimates[it.id]?.remainingMinutes ?: 0 }
        val slots = ReadingEstimator.schedule(
            queue = queueBooks.map { it.id to (estimates[it.id]?.remainingMinutes ?: 0) },
            alreadyBookedMinutes = bookedMinutes,
            dailyMinutes = cfg.dailyMinutes,
            today = today,
        )

        val year = today.year
        fun Long.year() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).year

        ShelfUiState(
            loading = false,
            selected = sel,
            byStatus = grouped,
            estimates = estimates,
            queue = slots.associateBy { it.bookId },
            dailyMinutes = cfg.dailyMinutes,
            stats = ShelfStats(
                readingCount = reading.size,
                queueCount = queueBooks.size,
                queueMinutes = slots.sumOf { it.minutes },
                queueFinish = slots.lastOrNull()?.finishDate,
                finishedThisYear = grouped[ReadingStatus.FINISHED].orEmpty()
                    .count { it.finishedAt?.year() == year },
                minutesThisYear = sessions.filter { it.date.year() == year }.sumOf { it.minutes },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfUiState())

    fun select(status: ReadingStatus) {
        selected.value = status
    }

    /** Move um livro da fila para cima (-1) ou para baixo (+1). */
    fun move(book: Book, direction: Int) {
        val queue = state.value.byStatus[ReadingStatus.WANT_TO_READ].orEmpty()
        val index = queue.indexOfFirst { it.id == book.id }
        if (index < 0 || queue.getOrNull(index + direction) == null) return
        val reordered = queue.toMutableList().apply { add(index + direction, removeAt(index)) }
        viewModelScope.launch { repo.reorderQueue(reordered) }
    }

    fun startReading(book: Book) {
        viewModelScope.launch { repo.changeStatus(book, ReadingStatus.READING) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                ShelfViewModel(c.repository, c.settings)
            }
        }
    }
}
