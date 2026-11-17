package com.cabeceira.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cabeceira.app.data.local.ReadingStatus
import com.cabeceira.app.data.remote.RemoteBook

@Composable
fun BookCover(url: String?, title: String, width: Dp = 56.dp, modifier: Modifier = Modifier) {
    val height = width * 1.5f
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Capa de $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width, height),
            )
        }
    }
}

@Composable
fun StatusChips(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadingStatus.entries.forEach { status ->
            FilterChip(
                selected = status == selected,
                onClick = { onSelect(status) },
                label = { Text(status.label) },
            )
        }
    }
}

/** Campo numérico simples (só dígitos). */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Diálogo para adicionar um livro à estante. Mostra na hora quanto tempo
 * ele vai levar para ser lido no seu ritmo — a ideia central do app.
 */
@Composable
fun AddBookDialog(
    book: RemoteBook,
    editableTitle: Boolean,
    pagesPerHour: Double,
    dailyMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (title: String, authors: String, pages: Int, status: ReadingStatus) -> Unit,
) {
    var title by remember { mutableStateOf(book.title) }
    var authors by remember { mutableStateOf(book.authorsText) }
    var pages by remember { mutableStateOf(book.pageCount?.toString().orEmpty()) }
    var status by remember { mutableStateOf(ReadingStatus.WANT_TO_READ) }
    val pageCount = pages.toIntOrNull() ?: 0
    val canSave = title.isNotBlank() && pageCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editableTitle) "Cadastrar livro" else "Adicionar à estante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editableTitle) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Título") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = authors, onValueChange = { authors = it },
                        label = { Text("Autor(es)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book.coverUrl, book.title, width = 48.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (book.authorsText.isNotBlank()) {
                                Text(book.authorsText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                NumberField(
                    value = pages,
                    onValueChange = { pages = it },
                    label = "Número de páginas",
                    supportingText = if (book.pageCount == null) "A API não informou — confira no livro" else null,
                )
                if (pageCount > 0) {
                    val minutes = com.cabeceira.app.domain.ReadingEstimator.minutesFor(pageCount, pagesPerHour)
                    val days = (minutes + dailyMinutes - 1) / dailyMinutes.coerceAtLeast(1)
                    Text(
                        "≈ ${formatDuration(minutes)} de leitura · ${plural(days, "dia", "dias")} lendo $dailyMinutes min/dia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Status", style = MaterialTheme.typography.labelLarge)
                StatusChips(selected = status, onSelect = { status = it })
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onConfirm(title, authors, pageCount, status) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
