package com.bioakustic.app.ui.record

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioakustic.app.AppContainer
import com.bioakustic.app.classify.Classifier
import com.bioakustic.app.util.TimeUtils

@Composable
fun RecordScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: RecordViewModel = viewModel(factory = RecordViewModel.Factory(container))
    val running by vm.running.collectAsState()
    val levelDb by vm.levelDb.collectAsState()
    val info by vm.info.collectAsState()
    val saving by vm.saving.collectAsState()
    val autoDetect by vm.autoDetect.collectAsState()
    val threshold by vm.thresholdDb.collectAsState()
    val lastDetection by vm.lastDetection.collectAsState()
    val results by vm.lastResults.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            vm.toggle()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Živý monitoring", style = MaterialTheme.typography.headlineSmall)
        Text(info, style = MaterialTheme.typography.bodyMedium)

        LevelBar(levelDb = levelDb, thresholdDb = threshold)

        Card(modifier = Modifier.fillMaxWidth()) {
            SpectrogramCanvas(
                columns = vm.columns,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (running) {
                        vm.toggle()
                    } else if (vm.engine.hasPermission()) {
                        vm.toggle()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "Zastavit" else "Spustit monitoring")
            }
            OutlinedButton(
                onClick = { vm.captureNow() },
                enabled = running && !saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (saving) "Ukládám…" else "Uložit detekci")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Automatická detekce", modifier = Modifier.weight(1f))
            Switch(checked = autoDetect, onCheckedChange = { vm.setAutoDetect(it) })
        }

        Text("Práh detekce: ${threshold.toInt()} dB")
        Slider(
            value = threshold,
            onValueChange = { vm.setThresholdDb(it) },
            valueRange = -60f..-10f,
        )

        Text("Klasifikátor: ${vm.classifierName}", style = MaterialTheme.typography.bodySmall)

        lastDetection?.let { detection ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Poslední detekce", style = MaterialTheme.typography.titleMedium)
                    Text(detection.speciesCommon ?: "–")
                    Text(
                        "${TimeUtils.formatDateTime(detection.recordedAt)} • " +
                            TimeUtils.formatPercent(detection.confidence),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Pravděpodobné výsledky",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    results.forEachIndexed { index, candidate ->
                        ResultRow(rank = index + 1, candidate = candidate)
                    }
                    Text(
                        "Chování se vyhodnocuje od spolehlivosti " +
                            "${(RecordViewModel.CONFIDENCE_GATE * 100).toInt()} %.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResultRow(rank: Int, candidate: Classifier.ScoredLabel) {
    val belowGate = candidate.confidence < RecordViewModel.CONFIDENCE_GATE
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.commonName ?: "–", style = MaterialTheme.typography.bodyMedium)
                candidate.scientificName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "${(candidate.confidence * 100).toInt()} %",
                style = MaterialTheme.typography.titleMedium,
                color = if (belowGate) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        LinearProgressIndicator(
            progress = { candidate.confidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LevelBar(levelDb: Double, thresholdDb: Float) {
    val fraction = ((levelDb + 90.0) / 90.0).coerceIn(0.0, 1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Úroveň signálu")
            Text("${levelDb.toInt()} dB")
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            "Práh: ${thresholdDb.toInt()} dB",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SpectrogramCanvas(columns: SnapshotStateList<FloatArray>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color.Black, size = size)
        if (columns.isEmpty()) return@Canvas
        val n = columns.size
        val colWidth = size.width / RecordViewModel.MAX_COLUMNS
        val startX = size.width - n * colWidth
        columns.forEachIndexed { ci, column ->
            if (column.isEmpty()) return@forEachIndexed
            val cellHeight = size.height / column.size
            for (mi in column.indices) {
                val v = column[mi]
                if (v <= 0.02f) continue
                // Low bands at the bottom.
                val y = size.height - (mi + 1) * cellHeight
                drawRect(
                    color = heatColor(v),
                    topLeft = Offset(startX + ci * colWidth, y),
                    size = Size(colWidth + 0.5f, cellHeight + 0.5f),
                )
            }
        }
    }
}

private fun heatColor(v: Float): Color {
    // Black -> green -> yellow -> white.
    return when {
        v < 0.33f -> {
            val t = v / 0.33f
            Color(0f, t * 0.55f, 0f)
        }
        v < 0.66f -> {
            val t = (v - 0.33f) / 0.33f
            Color(t, 0.55f + t * 0.45f, 0f)
        }
        else -> {
            val t = ((v - 0.66f) / 0.34f).coerceIn(0f, 1f)
            Color(1f, 1f, t)
        }
    }
}

