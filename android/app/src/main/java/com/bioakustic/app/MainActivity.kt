package com.bioakustic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bioakustic.app.ui.detail.DetailScreen
import com.bioakustic.app.ui.history.HistoryScreen
import com.bioakustic.app.ui.record.RecordScreen
import com.bioakustic.app.ui.settings.SettingsScreen
import com.bioakustic.app.ui.theme.BioAkusticTheme
import com.bioakustic.app.ui.usb.UsbScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BioAkusticApp).container
        setContent {
            BioAkusticTheme {
                AppNav(container)
            }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    RECORD("Záznam", Icons.Filled.Mic),
    HISTORY("Historie", Icons.Filled.History),
    USB("USB", Icons.Filled.Usb),
    SETTINGS("Nastavení", Icons.Filled.Settings),
}

@Composable
private fun AppNav(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(Tab.RECORD) }
    var detailId by rememberSaveable { mutableLongStateOf(-1L) }

    if (detailId >= 0) {
        BackHandler { detailId = -1L }
        DetailScreen(
            container = container,
            entryId = detailId,
            onBack = { detailId = -1L },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        }
    ) { padding ->
        Modifier.padding(padding).let { modifier ->
            when (tab) {
                Tab.RECORD -> RecordScreen(container, modifier)
                Tab.HISTORY -> HistoryScreen(
                    container = container,
                    modifier = modifier,
                    onOpenDetail = { detailId = it },
                )
                Tab.USB -> UsbScreen(container, modifier)
                Tab.SETTINGS -> SettingsScreen(container, modifier)
            }
        }
    }
}
