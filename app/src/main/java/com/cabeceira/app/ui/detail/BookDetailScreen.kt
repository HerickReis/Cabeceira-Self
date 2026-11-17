@file:OptIn(ExperimentalMaterial3Api::class)

package com.cabeceira.app.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabeceira.app.data.local.Book
import com.cabeceira.app.data.local.ReadingSession
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.local.progress
import com.cabeceira.app.data.remote.RemoteBook
import com.cabeceira.app.domain.Estimate
import com.cabeceira.app.ui.components.AddBookDialog
import com.cabeceira.app.ui.components.BookCover
import com.cabeceira.app.ui.components.NumberField
import com.cabeceira.app.ui.components.StatusChips
import com.cabeceira.app.ui.components.formatDate
import com.cabeceira.app.ui.components.formatDuration
import com.cabeceira.app.ui.components.formatPace
import com.cabeceira.app.ui.components.formatTimestamp
import com.cabeceira.app.ui.components.plural

@Composable
fun BookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onSearchAuthor: (String) -> Unit,
    vm: BookDetailViewModel = viewModel(key = "book-$bookId", factory = BookDetailViewModel.factory(bookId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val authorBooks by vm.authorBooks.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()
    var showCoverMenu by remember { mutableStateOf(false) }
    // Photo Picker do Android: não precisa de permissão de armazenamento
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCustomCover(uri)
    }
    var showSession by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingFromAuthor by remember { mutableStateOf<RemoteBook?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Remover livro") }
                },
            )
        },
    ) { padding ->
        val book = state.book
        if (book == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text("Livro não encontrado.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(book, coverBusy = cover.busy, onCoverClick = { showCoverMenu = true }) }
            cover.message?.let { msg ->
                item {
                    Text(
                        msg, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { vm.clearCoverMessage() },
                    )
                }
            }
            item { StatusChips(selected = book.status, onSelect = vm::changeStatus) }
            item {
                ProgressCard(
                    book = book,
                    estimate = state.estimate,
                    dailyMinutes = state.dailyMinutes,
                    minutesSpent = state.minutesSpent,
                    onLogSession = { showSession = true },
                    onEditProgress = { showProgress = true },
                    onStart = { vm.changeStatus(ReadingStatus.READING) },
                )
            }
            if (state.sessions.isNotEmpty()) {
                item { Text("Sessões de leitura", style = MaterialTheme.typography.titleMedium) }
                items(state.sessions, key = { it.id }) { s -> SessionRow(s, onDelete = { vm.deleteSession(s) }) }
            }
            if (authorBooks.author.isNotBlank()) {
                item {
                    AuthorSection(
                        author = authorBooks.author,
                        loading = authorBooks.loading,
                        books = authorBooks.books,
                        onPick = { addingFromAuthor = it },
                        onSeeAll = { onSearchAuthor(authorBooks.author) },
                    )
                }
            }
            book.description?.let { desc ->
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Column(Modifier.clickable { expanded = !expanded }) {
                        Text("Sinopse", style = MaterialTheme.typography.titleMedium)
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (showCoverMenu) {
            AlertDialog(
                onDismissRequest = { showCoverMenu = false },
                title = { Text("Capa do livro") },
                text = {
                    Column {
                        TextButton(onClick = {
                            showCoverMenu = false
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Escolher foto da galeria") }
                        TextButton(onClick = { showCoverMenu = false; vm.searchCoverOnline() }) {
                            Text("Buscar capa online")
                        }
                        if (book.coverUrl != null) {
                            TextButton(onClick = { showCoverMenu = false; vm.removeCover() }) { Text("Remover capa") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showCoverMenu = false }) { Text("Fechar") } },
            )
        }
        if (showSession) {
            SessionDialog(
                book = book,
                onDismiss = { showSession = false },
                onSave = { page, minutes -> vm.logSession(page, minutes); showSession = false },
            )
        }
        if (showProgress) {
            ProgressDialog(
                book = book,
                onDismiss = { showProgress = false },
                onSave = { page, total -> vm.updateProgress(page, total); showProgress = false },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Remover livro?") },
                text = { Text("\"${book.title}\" e suas sessões de leitura serão apagados.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Remover") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            )
        }
        addingFromAuthor?.let { remote ->
            AddBookDialog(
                book = remote,
                editableTitle = false,
                pagesPerHour = state.generalPace?.pagesPerHour ?: 30.0,
                dailyMinutes = state.dailyMinutes,
                onDismiss = { addingFromAuthor = null },
                onConfirm = { title, authors, pages, status ->
                    addingFromAuthor = null
                    vm.addAuthorBook(remote, title, authors, pages, status, onOpenBook)
                },
            )
        }
    }
}

@Composable
private fun Header(book: Book, coverBusy: Boolean, onCoverClick: () -> Unit) {
    Row {
        Box(contentAlignment = Alignment.Center) {
            BookCover(book.coverUrl, book.title, width = 96.dp, modifier = Modifier.clickable(onClick = onCoverClick))
            if (coverBusy) CircularProgressIndicator()
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodyLarge)
            val meta = listOfNotNull("${book.pageCount} páginas", book.publishedDate?.take(4), book.isbn?.let { "ISBN $it" })
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressCard(
    book: Book,
    estimate: Estimate?,
    dailyMinutes: Int,
    minutesSpent: Int,
    onLogSession: () -> Unit,
    onEditProgress: () -> Unit,
    onStart: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (book.status) {
                ReadingStatus.FINISHED -> {
                    Text("Leitura concluída 🎉", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    book.finishedAt?.let { Text("Terminado em ${formatTimestamp(it)}") }
                    if (minutesSpent > 0) Text("Tempo total registrado: ${formatDuration(minutesSpent)}")
                }
                else -> {
                    if (estimate != null) {
                        Text(
                            if (book.currentPage == 0) "Tempo estimado de leitura" else "Tempo restante",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "≈ ${formatDuration(estimate.remainingMinutes)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (estimate.readingDays > 0) {
                            Text(
                                "Lendo $dailyMinutes min/dia: ${plural(estimate.readingDays, "dia", "dias")} " +
                                    "→ termina ${formatDate(estimate.finishDate)}"
                            )
                        }
                        Text(
                            "${formatPace(estimate.pace.pagesPerHour)} · ${estimate.pace.source.label}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (book.status == ReadingStatus.READING || book.currentPage > 0) {
                        LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Text(
                            "p. ${book.currentPage} de ${book.pageCount} (${(book.progress * 100).toInt()}%)" +
                                if (minutesSpent > 0) " · ${formatDuration(minutesSpent)} lidos" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                when (book.status) {
                    ReadingStatus.READING -> Button(onClick = onLogSession) { Text("Registrar leitura") }
                    ReadingStatus.WANT_TO_READ, ReadingStatus.ABANDONED -> Button(onClick = onStart) { Text("Começar a ler") }
                    ReadingStatus.FINISHED -> {}
                }
                OutlinedButton(onClick = onEditProgress) { Text("Ajustar páginas") }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ReadingSession, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatTimestamp(session.date), style = MaterialTheme.typography.bodySmall)
                val pace = if (session.minutes > 0) " · ${formatPace(session.pagesRead / (session.minutes / 60.0))}" else ""
                Text("${session.pagesRead} páginas em ${formatDuration(session.minutes)}$pace")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Apagar sessão") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun AuthorSection(
    author: String,
    loading: Boolean,
    books: List<RemoteBook>,
    onPick: (RemoteBook) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mais de $author", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("Ver todos") }
        }
        when {
            loading -> CircularProgressIndicator(Modifier.padding(8.dp))
            books.isEmpty() -> Text("Nada encontrado nas APIs públicas.", style = MaterialTheme.typography.bodySmall)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(books) { b ->
                    Column(Modifier.width(88.dp).clickable { onPick(b) }) {
                        BookCover(b.coverUrl, b.title, width = 88.dp)
                        Text(b.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDialog(book: Book, onDismiss: () -> Unit, onSave: (page: Int, minutes: Int) -> Unit) {
    var page by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    val p = page.toIntOrNull()
    val m = minutes.toIntOrNull()
    val valid = p != null && m != null && m > 0 && p > book.currentPage && p <= book.pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar leitura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Você estava na página ${book.currentPage}.", style = MaterialTheme.typography.bodyMedium)
                NumberField(page, { page = it }, "Parei na página", supportingText = "Máx. ${book.pageCount}")
                NumberField(minutes, { minutes = it }, "Minutos lidos")
                if (valid) {
                    val read = p!! - book.currentPage
                    Text(
                        "$read páginas · ${formatPace(read / (m!! / 60.0))}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(p!!, m!!) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ProgressDialog(book: Book, onDismiss: () -> Unit, onSave: (page: Int, total: Int) -> Unit) {
    var page by remember { mutableStateOf(book.currentPage.toString()) }
    var total by remember { mutableStateOf(book.pageCount.toString()) }
    val p = page.toIntOrNull()
    val t = total.toIntOrNull()
    val valid = p != null && t != null && t > 0 && p <= t
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar páginas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Corrige o progresso sem registrar tempo (não afeta o cálculo do seu ritmo).",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField(page, { page = it }, "Página atual")
                NumberField(total, { total = it }, "Total de páginas")
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(p!!, t!!) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
