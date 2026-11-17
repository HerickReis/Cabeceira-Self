@file:OptIn(ExperimentalMaterial3Api::class)

package com.cabeceira.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabeceira.app.data.remote.RemoteBook
import com.cabeceira.app.ui.components.AddBookDialog
import com.cabeceira.app.ui.components.BookCover
import com.cabeceira.app.ui.components.EmptyState

@Composable
fun SearchScreen(
    initialAuthor: String?,
    onBack: () -> Unit,
    onBookAdded: (Long) -> Unit,
    onOpenExisting: (Long) -> Unit,
    vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pace by vm.pace.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    LaunchedEffect(initialAuthor) {
        if (!initialAuthor.isNullOrBlank()) vm.prefill(SearchMode.AUTHOR, initialAuthor)
    }

    fun doSearch() {
        focus.clearFocus()
        vm.search()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar livro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SearchMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { vm.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, SearchMode.entries.size),
                            label = { Text(mode.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar por ${state.mode.label.lowercase()}") },
                    placeholder = { Text(state.mode.hint) },
                    trailingIcon = {
                        IconButton(onClick = ::doSearch) { Icon(Icons.Default.Search, contentDescription = "Buscar") }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        keyboardType = if (state.mode == SearchMode.ISBN) KeyboardType.Number else KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scanBookBarcode(context, onResult = vm::onBarcodeScanned, onError = vm::showError)
                        },
                    ) { Text("Escanear código de barras") }
                    OutlinedButton(onClick = vm::startManual) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text("Manual", Modifier.padding(start = 4.dp))
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.searched && state.results.isEmpty() -> Column(
                    Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState("Nenhum livro encontrado.")
                    Button(onClick = vm::startManual) { Text("Cadastrar manualmente") }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results) { book -> ResultRow(book) { vm.choose(book) } }
                }
            }
        }
    }

    val pending = state.pending
    val duplicate = state.duplicateOf
    if (pending != null && duplicate != null) {
        AlertDialog(
            onDismissRequest = vm::dismissPending,
            title = { Text("Já está na estante") },
            text = { Text("\"${pending.title}\" já foi adicionado antes.") },
            confirmButton = {
                TextButton(onClick = { vm.dismissPending(); onOpenExisting(duplicate) }) { Text("Abrir") }
            },
            dismissButton = { TextButton(onClick = vm::ignoreDuplicate) { Text("Adicionar outra cópia") } },
        )
    } else if (pending != null) {
        AddBookDialog(
            book = pending,
            editableTitle = state.pendingIsManual,
            pagesPerHour = pace.pagesPerHour,
            dailyMinutes = pace.dailyMinutes,
            onDismiss = vm::dismissPending,
            onConfirm = { title, authors, pages, status -> vm.add(title, authors, pages, status, onBookAdded) },
        )
    }
}

@Composable
private fun ResultRow(book: RemoteBook, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverUrl, book.title, width = 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authorsText.isNotBlank()) {
                    Text(book.authorsText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(
                    book.publishedDate?.take(4),
                    book.pageCount?.let { "$it páginas" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
