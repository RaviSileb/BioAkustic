package com.bioakustic.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bioakustic.app.AppContainer
import com.bioakustic.app.data.db.DetectionSource
import com.bioakustic.app.util.TimeUtils
import java.io.File

@Composable
fun DetailScreen(
    container: AppContainer,
    entryId: Long,
    onBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(
        key = "detail-$entryId",
        factory = DetailViewModel.Factory(container, entryId),
    )
    val context = LocalContext.current
    val entry by vm.entry.collectAsState()
    val message by vm.message.collectAsState()
    val locating by vm.locating.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Camera launchers: capture straight into app-private media dirs.
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingPhoto?.let { vm.addPhoto(it) }
        pendingPhoto = null
    }
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) pendingVideo?.let { vm.addVideo(it) }
        pendingVideo = null
    }
    fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
            }
            Text(
                "Detail záznamu",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                entry?.let { shareEntry(context, container, it) }
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Sdílet")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Smazat")
            }
        }

        val current = entry
        if (current == null) {
            Text("Načítání…", modifier = Modifier.padding(16.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(sourceLabel(current.source)) })
                    AssistChip(
                        onClick = {},
                        label = { Text(TimeUtils.formatPercent(current.confidence)) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(TimeUtils.formatDuration(current.durationMs)) },
                    )
                }

                SectionTitle("Druh (lze opravit)")
                val common by vm.speciesCommon.collectAsState()
                val scientific by vm.speciesScientific.collectAsState()
                OutlinedTextField(
                    value = common,
                    onValueChange = { vm.speciesCommon.value = it },
                    label = { Text("Obecný název") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = scientific,
                    onValueChange = { vm.speciesScientific.value = it },
                    label = { Text("Vědecký název") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionTitle("Čas záznamu")
                val recordedAt by vm.recordedAtText.collectAsState()
                OutlinedTextField(
                    value = recordedAt,
                    onValueChange = { vm.recordedAtText.value = it },
                    label = { Text("RRRR-MM-DD HH:MM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionTitle("Místo")
                val place by vm.placeName.collectAsState()
                val lat by vm.latitudeText.collectAsState()
                val lon by vm.longitudeText.collectAsState()
                OutlinedTextField(
                    value = place,
                    onValueChange = { vm.placeName.value = it },
                    label = { Text("Název místa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { vm.latitudeText.value = it },
                        label = { Text("Šířka") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { vm.longitudeText.value = it },
                        label = { Text("Délka") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = { vm.useCurrentLocation() },
                    enabled = !locating,
                ) {
                    Text(if (locating) "Zjišťuji polohu…" else "Použít aktuální polohu")
                }

                SectionTitle("Chování a poznámka")
                val behaviorText by vm.behavior.collectAsState()
                val noteText by vm.note.collectAsState()
                OutlinedTextField(
                    value = behaviorText,
                    onValueChange = { vm.behavior.value = it },
                    label = { Text("Chování / kontext") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { vm.note.value = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Uložit změny")
                }

                SectionTitle("Zvuk")
                AudioRow(vm = vm)

                SectionTitle("Fotky")
                PhotoRow(
                    photos = vm.photoFiles(),
                    onAdd = {
                        val file = container.repository.newPhotoFile()
                        pendingPhoto = file
                        photoLauncher.launch(fileUri(file))
                    },
                    onView = { fullPhoto = it },
                    onRemove = {
                        vm.removePhoto(container.repository.relative(it))
                    },
                )

                SectionTitle("Videa")
                VideoRow(
                    videos = vm.videoFiles(),
                    onAdd = {
                        val file = container.repository.newVideoFile()
                        pendingVideo = file
                        videoLauncher.launch(fileUri(file))
                    },
                    onPlay = { playVideo(context, container, it) },
                    onRemove = {
                        vm.removeVideo(container.repository.relative(it))
                    },
                )
            }
        }
        SnackbarHost(snackbar)
    }

    fullPhoto?.let { file ->
        Dialog(
            onDismissRequest = { fullPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { fullPhoto = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Zavřít", tint = MaterialTheme.colorScheme.onBackground)
                }
                AsyncImage(
                    model = file,
                    contentDescription = "Fotka",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Smazat záznam?") },
            text = { Text("Smaže se i nahraný zvuk, fotky a videa.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Smazat") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Zrušit") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AudioRow(vm: DetailViewModel) {
    val file = vm.audioFile()
    if (file == null) {
        Text("Bez zvukové nahrávky.")
        return
    }
    val playing by vm.player.playingPath.collectAsState()
    val position by vm.player.positionMs.collectAsState()
    val duration by vm.player.durationMs.collectAsState()
    val isPlaying = playing == file.absolutePath
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { vm.player.toggle(file) }) {
                Icon(
                    if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Zastavit" else "Přehrát",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodySmall)
                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    photos: List<File>,
    onAdd: () -> Unit,
    onView: (File) -> Unit,
    onRemove: (File) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos) { file ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = file,
                    contentDescription = "Fotka",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clickable { onView(file) },
                )
                TextButton(onClick = { onRemove(file) }) { Text("Odebrat") }
            }
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.height(96.dp)) {
                Text("+ Fotka")
            }
        }
    }
}

@Composable
private fun VideoRow(
    videos: List<File>,
    onAdd: () -> Unit,
    onPlay: (File) -> Unit,
    onRemove: (File) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        videos.forEach { file ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onPlay(file) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(file.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemove(file) }) { Text("Odebrat") }
                }
            }
        }
        OutlinedButton(onClick = onAdd) { Text("+ Video") }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    DetectionSource.TFLITE -> "BirdNET"
    DetectionSource.MANUAL -> "ručně"
    else -> "heuristika"
}

private fun playVideo(context: android.content.Context, container: AppContainer, file: File) {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareEntry(
    context: android.content.Context,
    container: AppContainer,
    entry: com.bioakustic.app.data.db.DetectionEntity,
) {
    val text = buildString {
        appendLine("BioAkustic záznam")
        appendLine("Druh: ${entry.speciesCommon ?: "neznámý"}")
        entry.speciesScientific?.let { appendLine("Latinsky: $it") }
        appendLine("Čas: ${TimeUtils.formatDateTime(entry.recordedAt)}")
        entry.placeName?.let { appendLine("Místo: $it") }
        if (entry.latitude != null && entry.longitude != null) {
            appendLine("GPS: ${entry.latitude}, ${entry.longitude}")
        }
        appendLine("Spolehlivost: ${TimeUtils.formatPercent(entry.confidence)}")
        entry.note?.let { appendLine("Poznámka: $it") }
    }
    val audio = entry.audioPath
        ?.let { container.repository.resolve(it) }
        ?.takeIf { it.exists() }
    val intent = if (audio != null) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", audio
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Sdílet záznam")) }
}
