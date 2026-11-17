@file:OptIn(ExperimentalMaterial3Api::class)

package com.cabeceira.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cabeceira.app.CabeceiraApp
import com.cabeceira.app.data.ReadingSettings
import com.cabeceira.app.ui.components.NumberField

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = (LocalContext.current.applicationContext as CabeceiraApp).container.settings
    val current = remember { store.settings.value }
    var pph by remember { mutableStateOf(current.defaultPagesPerHour.toString()) }
    var daily by remember { mutableStateOf(current.dailyMinutes.toString()) }
    val p = pph.toIntOrNull()
    val d = daily.toIntOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = daily, onValueChange = { daily = it },
                label = "Minutos de leitura por dia",
                supportingText = "Usado para prever as datas de término.",
            )
            NumberField(
                value = pph, onValueChange = { pph = it },
                label = "Ritmo padrão (páginas por hora)",
                supportingText = "Usado até você registrar ~1 h de leitura. Depois o app aprende seu ritmo real.",
            )
            Text(
                "Dica: um leitor médio lê de 20 a 40 páginas por hora em livros de ficção.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = p != null && p > 0 && d != null && d > 0,
                onClick = {
                    store.update(ReadingSettings(defaultPagesPerHour = p!!, dailyMinutes = d!!))
                    onBack()
                },
            ) { Text("Salvar") }
        }
    }
}
