package com.bioakustic.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioakustic.app.AppContainer
import com.bioakustic.app.classify.ModelManager

@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val modelState by vm.modelState.collectAsState()
    val modelUrl by vm.modelUrl.collectAsState()
    val labelsUrl by vm.labelsUrl.collectAsState()
    val useNnapi by vm.useNnapi.collectAsState()
    val autoDetect by vm.autoDetect.collectAsState()
    val threshold by vm.thresholdDb.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nastavení", style = MaterialTheme.typography.headlineSmall)

        Text("Klasifikační model", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(modelStateLabel(modelState))
                Text(vm.modelFilesInfo(), style = MaterialTheme.typography.bodySmall)
                if (modelState is ModelManager.ModelState.Downloading) {
                    val fraction = (modelState as ModelManager.ModelState.Downloading).fraction
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { vm.cancelDownload() }) {
                        Text("Zrušit stahování")
                    }
                } else {
                    OutlinedTextField(
                        value = modelUrl,
                        onValueChange = { vm.setModelUrl(it) },
                        label = { Text("URL modelu (.tflite)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = labelsUrl,
                        onValueChange = { vm.setLabelsUrl(it) },
                        label = { Text("URL popisků (.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.downloadModel() },
                            enabled = modelUrl.isNotBlank() && labelsUrl.isNotBlank(),
                        ) {
                            Text("Stáhnout model")
                        }
                        OutlinedButton(onClick = { vm.deleteModel() }) {
                            Text("Smazat model")
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "NNAPI (Hexagon DSP) místo CPU",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(checked = useNnapi, onCheckedChange = { vm.setUseNnapi(it) })
                }
                Text(
                    "Bez modelu aplikace používá heuristický detektor – " +
                        "rozpozná zvuk, ale neurčí druh. " +
                        "Model BirdNET získáte dle návodu v docs/CLASSIFIER.md.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text("Detekce", style = MaterialTheme.typography.titleMedium)
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

        Text("O aplikaci", style = MaterialTheme.typography.titleMedium)
        Text(
            "BioAkustic 0.1.0 (MVP)\n" +
                "Akustická identifikace zvířat pro Galaxy S23 Ultra.\n" +
                "Vše běží on-device, nahrávky zůstávají v telefonu.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun modelStateLabel(state: ModelManager.ModelState): String = when (state) {
    is ModelManager.ModelState.NotDownloaded -> "Stav: model není nainstalován (heuristika)"
    is ModelManager.ModelState.Downloading -> "Stav: ${state.status} (${(state.fraction * 100).toInt()} %)"
    is ModelManager.ModelState.Ready -> "Stav: ${state.detail}"
    is ModelManager.ModelState.Error -> "Stav: chyba – ${state.message}"
}
