package com.cabeceira.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cabeceira.app.data.BookRepository
import com.cabeceira.app.data.SettingsStore
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.remote.ApiException
import com.cabeceira.app.data.remote.BookApi
import com.cabeceira.app.data.remote.RemoteBook
import com.cabeceira.app.domain.ReadingEstimator
import com.cabeceira.app.domain.SessionSample
import com.cabeceira.app.ui.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class SearchMode(val label: String, val hint: String) {
    TITLE("Título", "Ex.: Dom Casmurro"),
    AUTHOR("Autor", "Ex.: Clarice Lispector"),
    ISBN("ISBN", "Ex.: 9788535914849"),
}

data class SearchUiState(
    val mode: SearchMode = SearchMode.TITLE,
    val query: String = "",
    val loading: Boolean = false,
    val results: List<RemoteBook> = emptyList(),
    val searched: Boolean = false,
    val error: String? = null,
    /** Livro escolhido para o diálogo de adicionar. */
    val pending: RemoteBook? = null,
    val pendingIsManual: Boolean = false,
    /** Já existe na estante (mesmo ISBN) → id para abrir. */
    val duplicateOf: Long? = null,
)

/** Ritmo atual do usuário, para mostrar a estimativa já no momento de adicionar. */
data class PaceInfo(val pagesPerHour: Double = 30.0, val dailyMinutes: Int = 30)

class SearchViewModel(
    private val repo: BookRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val pace: StateFlow<PaceInfo> = combine(repo.observeAllSessions(), settings.settings) { sessions, cfg ->
        val p = ReadingEstimator.pace(
            emptyList(), sessions.map { SessionSample(it.pagesRead, it.minutes) }, cfg.defaultPagesPerHour,
        )
        PaceInfo(p.pagesPerHour, cfg.dailyMinutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaceInfo())

    private var searchJob: Job? = null

    fun setMode(mode: SearchMode) = _state.update { it.copy(mode = mode) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    /** Usado ao vir da tela de detalhes ("mais deste autor"). */
    fun prefill(mode: SearchMode, query: String) {
        if (_state.value.searched && _state.value.query == query) return
        _state.update { it.copy(mode = mode, query = query) }
        search()
    }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = when (s.mode) {
                    SearchMode.TITLE -> repo.searchByTitle(q)
                    SearchMode.AUTHOR -> repo.searchByAuthor(q)
                    SearchMode.ISBN -> repo.findByIsbn(q)
                }
                _state.update { it.copy(loading = false, results = results, searched = true) }
                // ISBN com um único resultado: já abre o diálogo de adicionar
                if (s.mode == SearchMode.ISBN && results.size == 1) choose(results.first())
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = "Sem conexão com a internet.") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Erro na busca: ${e.localizedMessage}") }
            }
        }
    }

    fun onBarcodeScanned(code: String) {
        if (!BookApi.looksLikeIsbn(code)) {
            _state.update { it.copy(error = "O código $code não parece ser o ISBN de um livro.") }
            return
        }
        _state.update { it.copy(mode = SearchMode.ISBN, query = BookApi.normalizeIsbn(code)) }
        search()
    }

    fun showError(msg: String) = _state.update { it.copy(error = msg) }

    fun choose(book: RemoteBook) {
        viewModelScope.launch {
            val existing = repo.findSavedByIsbn(book.isbn)
            _state.update { it.copy(pending = book, pendingIsManual = false, duplicateOf = existing?.id) }
        }
    }

    fun startManual() {
        val s = _state.value
        val blank = RemoteBook(
            title = if (s.mode == SearchMode.TITLE) s.query else "",
            authors = if (s.mode == SearchMode.AUTHOR && s.query.isNotBlank()) listOf(s.query) else emptyList(),
            isbn = if (s.mode == SearchMode.ISBN) s.query.ifBlank { null } else null,
            pageCount = null, coverUrl = null, description = null, publishedDate = null,
        )
        _state.update { it.copy(pending = blank, pendingIsManual = true, duplicateOf = null) }
    }

    fun ignoreDuplicate() = _state.update { it.copy(duplicateOf = null) }

    fun dismissPending() = _state.update { it.copy(pending = null, duplicateOf = null) }

    fun add(title: String, authors: String, pages: Int, status: ReadingStatus, onAdded: (Long) -> Unit) {
        val book = _state.value.pending ?: return
        viewModelScope.launch {
            val id = repo.addBook(book, title, authors, pages, status)
            _state.update { it.copy(pending = null, duplicateOf = null) }
            onAdded(id)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                SearchViewModel(c.repository, c.settings)
            }
        }
    }
}
