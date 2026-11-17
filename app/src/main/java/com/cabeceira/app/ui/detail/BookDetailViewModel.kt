package com.cabeceira.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cabeceira.app.data.BookRepository
import com.cabeceira.app.data.SettingsStore
import com.cabeceira.app.data.local.Book
import com.cabeceira.app.data.local.ReadingSession
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.local.firstAuthor
import com.cabeceira.app.data.local.remainingPages
import com.cabeceira.app.data.remote.RemoteBook
import com.cabeceira.app.domain.Estimate
import com.cabeceira.app.domain.Pace
import com.cabeceira.app.domain.ReadingEstimator
import com.cabeceira.app.domain.SessionSample
import com.cabeceira.app.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DetailUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val sessions: List<ReadingSession> = emptyList(),
    val estimate: Estimate? = null,
    /** Ritmo que o app usaria para um livro novo (para o diálogo de "mais do autor"). */
    val generalPace: Pace? = null,
    val dailyMinutes: Int = 30,
    val minutesSpent: Int = 0,
)

data class CoverState(val busy: Boolean = false, val message: String? = null)

data class AuthorBooks(val author: String = "", val loading: Boolean = false, val books: List<RemoteBook> = emptyList())

class BookDetailViewModel(
    private val bookId: Long,
    private val repo: BookRepository,
    settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<DetailUiState> = combine(
        repo.observeBook(bookId),
        repo.observeSessions(bookId),
        repo.observeAllSessions(),
        settings.settings,
    ) { book, sessions, all, cfg ->
        val allSamples = all.map { SessionSample(it.pagesRead, it.minutes) }
        val pace = ReadingEstimator.pace(
            sessions.map { SessionSample(it.pagesRead, it.minutes) }, allSamples, cfg.defaultPagesPerHour,
        )
        DetailUiState(
            loading = false,
            book = book,
            sessions = sessions,
            estimate = book?.let { ReadingEstimator.estimate(it.remainingPages, pace, cfg.dailyMinutes, LocalDate.now()) },
            generalPace = ReadingEstimator.pace(emptyList(), allSamples, cfg.defaultPagesPerHour),
            dailyMinutes = cfg.dailyMinutes,
            minutesSpent = sessions.sumOf { it.minutes },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val _author = MutableStateFlow(AuthorBooks())
    val authorBooks: StateFlow<AuthorBooks> = _author.asStateFlow()

    private val _cover = MutableStateFlow(CoverState())
    val cover: StateFlow<CoverState> = _cover.asStateFlow()

    init {
        // Sem capa? Tenta achar uma online automaticamente (uma vez).
        viewModelScope.launch {
            val book = repo.observeBook(bookId).first { it != null }
            if (book?.coverUrl == null) {
                _cover.value = CoverState(busy = true)
                repo.fillMissingCover(bookId)
                _cover.value = CoverState()
            }
        }
        // Carrega "mais livros deste autor" uma vez, quando o livro aparecer.
        viewModelScope.launch {
            repo.observeBook(bookId).collect { book ->
                val author = book?.firstAuthor ?: return@collect
                if (author == _author.value.author) return@collect
                _author.value = AuthorBooks(author, loading = true)
                val others = runCatching { repo.searchByAuthor(author) }.getOrDefault(emptyList())
                    .filterNot { it.title.equals(book.title, ignoreCase = true) }
                    .take(15)
                _author.value = AuthorBooks(author, loading = false, books = others)
            }
        }
    }

    private fun withBook(block: suspend (Book) -> Unit) {
        val book = state.value.book ?: return
        viewModelScope.launch { block(book) }
    }

    fun searchCoverOnline() = withBook { b ->
        _cover.value = CoverState(busy = true)
        val ok = repo.refreshCover(b)
        _cover.value = CoverState(message = if (ok) null else "Nenhuma capa encontrada online. Tente uma foto da galeria.")
    }

    fun setCustomCover(uri: android.net.Uri) = withBook { b ->
        _cover.value = CoverState(busy = true)
        val ok = repo.setCustomCover(b, uri)
        _cover.value = CoverState(message = if (ok) null else "Não consegui ler essa imagem.")
    }

    fun removeCover() = withBook { repo.removeCover(it) }
    fun clearCoverMessage() = _cover.update { it.copy(message = null) }

    fun changeStatus(status: ReadingStatus) = withBook { repo.changeStatus(it, status) }
    fun logSession(reachedPage: Int, minutes: Int) = withBook { repo.logSession(it, reachedPage, minutes) }
    fun updateProgress(currentPage: Int, pageCount: Int) = withBook { repo.updateProgress(it, currentPage, pageCount) }
    fun deleteSession(session: ReadingSession) = viewModelScope.launch { repo.deleteSession(session) }
    fun delete(onDone: () -> Unit) = withBook { repo.delete(it); onDone() }

    fun addAuthorBook(book: RemoteBook, title: String, authors: String, pages: Int, status: ReadingStatus, onAdded: (Long) -> Unit) {
        viewModelScope.launch { onAdded(repo.addBook(book, title, authors, pages, status)) }
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                val c = container()
                BookDetailViewModel(bookId, c.repository, c.settings)
            }
        }
    }
}
