package com.bioakustic.app.ui.usb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioakustic.app.AppContainer

@Composable
fun UsbScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: UsbViewModel = viewModel(factory = UsbViewModel.Factory(container))
    val devices by vm.devices.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("USB zařízení", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pro detekci netopýrů připojte přes USB-C ultrazvukový mikrofon " +
                "(např. Dodotronic Ultramic 384K nebo Vleermic 384).",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { vm.refresh() }) {
            Text("Obnovit seznam")
        }
        if (devices.isEmpty()) {
            Text("Žádná USB zařízení nenalezena.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "VID ${device.vendorId} • PID ${device.productId} • " +
                                    "třída ${device.deviceClass}/${device.deviceSubclass}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (device.isAudio) {
                                AssistChip(onClick = {}, label = { Text("USB audio") })
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Poznámka (MVP): tato obrazovka pouze detekuje připojený hardware. " +
                "Plný UAC2 záznam 384 kHz je naplánován ve fázi 3 (viz docs).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
