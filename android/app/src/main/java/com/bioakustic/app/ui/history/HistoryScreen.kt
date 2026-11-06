package com.bioakustic.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioakustic.app.AppContainer
import com.bioakustic.app.data.db.DetectionEntity
import com.bioakustic.app.data.db.DetectionSource
import com.bioakustic.app.util.TimeUtils

@Composable
fun HistoryScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onOpenDetail: (Long) -> Unit,
) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(container))
    val entries by vm.entries.collectAsState()
    val query by vm.query.collectAsState()
    var pendingDelete by remember { mutableStateOf<DetectionEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Historie rozpoznávání", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            label = { Text("Hledat (druh, místo, poznámka)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (entries.isEmpty()) {
            Text(
                if (query.isBlank()) {
                    "Zatím žádné záznamy. Spusťte monitoring na záložce Záznam – " +
                        "detekce se sem ukládají automaticky."
                } else {
                    "Nic neodpovídá hledání."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpen = { onOpenDetail(entry.id) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Smazat záznam?") },
            text = { Text("Smaže se i nahraný zvuk, fotky a videa tohoto záznamu.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(entry); pendingDelete = null }) {
                    Text("Smazat")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Zrušit") }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: DetectionEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.speciesCommon ?: "Neznámý zvuk",
                    style = MaterialTheme.typography.titleMedium,
                )
                entry.speciesScientific?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${TimeUtils.formatShort(entry.recordedAt)} • " +
                        TimeUtils.formatDuration(entry.durationMs) +
                        (entry.placeName?.let { " • $it" } ?: "") +
                        gpsSuffix(entry),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(sourceLabel(entry.source)) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(TimeUtils.formatPercent(entry.confidence)) },
                    )
                    if (entry.behavior != null) {
                        AssistChip(onClick = {}, label = { Text("chování") })
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Smazat")
            }
        }
    }
}

private fun gpsSuffix(entry: DetectionEntity): String =
    if (entry.latitude != null && entry.longitude != null) " • GPS" else ""

private fun sourceLabel(source: String): String = when (source) {
    DetectionSource.TFLITE -> "BirdNET"
    DetectionSource.MANUAL -> "ručně"
    else -> "heuristika"
}
