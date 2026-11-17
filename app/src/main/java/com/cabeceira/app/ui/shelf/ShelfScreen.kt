@file:OptIn(ExperimentalMaterial3Api::class)

package com.cabeceira.app.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabeceira.app.data.local.Book
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.local.progress
import com.cabeceira.app.ui.components.BookCover
import com.cabeceira.app.ui.components.EmptyState
import com.cabeceira.app.ui.components.formatDate
import com.cabeceira.app.ui.components.formatDuration
import com.cabeceira.app.ui.components.formatTimestamp
import com.cabeceira.app.ui.components.plural

@Composable
fun ShelfScreen(
    onAddBook: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cabeceira") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBook,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Adicionar livro") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SummaryCard(state)
            val tabs = ReadingStatus.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(state.selected), edgePadding = 8.dp) {
                tabs.forEach { status ->
                    val count = state.byStatus[status]?.size ?: 0
                    Tab(
                        selected = status == state.selected,
                        onClick = { vm.select(status) },
                        text = { Text(if (count > 0) "${status.label} ($count)" else status.label) },
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.visible.isEmpty() -> EmptyState(emptyMessage(state.selected))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val list = state.visible
                    items(list, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            state = state,
                            isFirst = book == list.first(),
                            isLast = book == list.last(),
                            onClick = { onOpenBook(book.id) },
                            onMove = { dir -> vm.move(book, dir) },
                            onStart = { vm.startReading(book) },
                        )
                    }
                }
            }
        }
    }
}

private fun emptyMessage(status: ReadingStatus) = when (status) {
    ReadingStatus.READING -> "Nenhum livro em andamento.\nToque em \"Adicionar livro\" ou comece um da fila."
    ReadingStatus.WANT_TO_READ -> "Sua fila está vazia.\nBusque um livro pelo título, autor ou código de barras."
    ReadingStatus.FINISHED -> "Os livros que você terminar aparecem aqui."
    ReadingStatus.ABANDONED -> "Nada abandonado por aqui. 👏"
}

@Composable
private fun SummaryCard(state: ShelfUiState) {
    val s = state.stats
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.queueCount > 0 && s.queueFinish != null) {
                Text(
                    "Fila: ${plural(s.queueCount, "livro", "livros")} · ≈ ${formatDuration(s.queueMinutes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Lendo ${state.dailyMinutes} min/dia, você zera a fila em ${formatDate(s.queueFinish)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Sua estante", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${plural(s.finishedThisYear, "livro lido", "livros lidos")} este ano · " +
                    "${formatDuration(s.minutesThisYear)} de leitura registrada",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BookRow(
    book: Book,
    state: ShelfUiState,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val estimate = state.estimates[book.id]
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverUrl, book.title)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotBlank()) {
                    Text(
                        book.authors, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                when (book.status) {
                    ReadingStatus.READING -> {
                        LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("p. ${book.currentPage} de ${book.pageCount}", style = MaterialTheme.typography.bodySmall)
                        if (estimate != null && estimate.remainingMinutes > 0) {
                            Text(
                                "Faltam ≈ ${formatDuration(estimate.remainingMinutes)} · termina ${formatDate(estimate.finishDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    ReadingStatus.WANT_TO_READ -> {
                        val slot = state.queue[book.id]
                        Text(
                            "${book.pageCount} p. · ≈ ${formatDuration(estimate?.remainingMinutes ?: 0)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (slot != null) {
                            Text(
                                "Previsto: ${formatDate(slot.startDate)} → ${formatDate(slot.finishDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(onClick = onStart, contentPadding = PaddingValues(0.dp)) { Text("Começar a ler") }
                    }
                    ReadingStatus.FINISHED -> Text(
                        book.finishedAt?.let { "Lido em ${formatTimestamp(it)}" } ?: "Lido",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ReadingStatus.ABANDONED -> Text(
                        "Parou na p. ${book.currentPage} de ${book.pageCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (book.status == ReadingStatus.WANT_TO_READ) {
                Column {
                    IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir na fila")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descer na fila")
                    }
                }
            }
        }
    }
}
