package id.omphub

import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun looksBinary(f: File): Boolean = runCatching {
    f.inputStream().use { s ->
        val buf = ByteArray(1024)
        val n = s.read(buf)
        (0 until maxOf(n, 0)).any { buf[it] == 0.toByte() }
    }
}.getOrDefault(true)

private fun displayName(ctx: android.content.Context, uri: Uri): String {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

private fun fmtFileDate(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("id")).format(Date(ms))

/** Ikon file ala Finder: folder biru, warna beda per tipe file. */
@Composable
private fun FileIcon(f: File, size: Dp, iconSize: Dp) {
    val (icon, tint): Pair<ImageVector, Color> = when {
        f.isDirectory -> Icons.Default.Folder to Mac.Blue
        f.name.endsWith(".zip") || f.name.endsWith(".tar.gz") || f.name.endsWith(".tgz") ||
            f.name.endsWith(".tar.xz") || f.name.endsWith(".tar") -> Icons.Default.Archive to Mac.Red
        f.name.endsWith(".amx") || f.name.endsWith(".so") -> Icons.Default.Memory to Mac.Blue
        f.name.endsWith(".pwn") || f.name.endsWith(".txt") || f.name.endsWith(".log") ||
            f.name.endsWith(".cfg") || f.name.endsWith(".json") -> Icons.Default.Description to Mac.Green
        else -> Icons.Default.Description to Mac.Secondary
    }
    IconTile(icon, tint, size = size, iconSize = iconSize)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = Env.serverDir
    var cwd by remember { mutableStateOf(root) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<File?>(null) }
    var editText by remember { mutableStateOf("") }
    var loadingFile by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf<Boolean?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(false) }

    val entries = remember(cwd, tick) {
        runCatching {
            cwd.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // Lokasi cepat ala Finder sidebar — hanya folder yang benar-benar ada.
    val quickDirs = remember(tick) {
        listOf(
            "Server" to root,
            "Gamemodes" to File(root, "gamemodes"),
            "Scriptfiles" to File(root, "scriptfiles"),
            "Filterscripts" to File(root, "filterscripts"),
            "Logs" to File(root, "logs")
        ).filter { it.second.isDirectory }
    }

    fun openEntry(f: File) {
        if (f.isDirectory) {
            cwd = f
        } else if (f.length() > 512 * 1024 || looksBinary(f)) {
            scope.launch { toast(ctx, "File biner / terlalu besar untuk diedit") }
        } else {
            // Baca di IO supaya UI tidak nge-freeze (dulu di main thread).
            loadingFile = true
            scope.launch(Dispatchers.IO) {
                val text = runCatching { f.readText() }.getOrNull()
                if (text != null) { editText = text; editing = f }
                else toast(ctx, "Gagal membaca file")
                loadingFile = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch(Dispatchers.IO) {
            var n = 0
            var failed = 0
            uris.forEach { uri ->
                runCatching {
                    val name = displayName(ctx, uri)
                    val out = Zips.safeResolve(cwd, name) ?: error("Nama file tidak valid")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                        ?: error("Tidak bisa membaca file")
                    n++
                }.onFailure { failed++ }
            }
            tick++
            toast(ctx, if (failed > 0) "$n file diunggah, $failed gagal" else "$n file diunggah")
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar gaya Finder.
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }
        ) {
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { cwd.parentFile?.let { cwd = it } },
                        enabled = cwd != root
                    ) { Icon(Icons.Default.ChevronLeft, "Naik", tint = Mac.Text) }
                    Text(
                        "/" + cwd.relativeTo(root).path,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = Mac.Text
                    )
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            if (gridMode) Icons.Default.List else Icons.Default.GridView,
                            if (gridMode) "Tampilan list" else "Tampilan grid",
                            tint = Mac.Blue
                        )
                    }
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Upload, "Unggah", tint = Mac.Text) }
                    IconButton(onClick = { newName = true; nameInput = "" }) { Icon(Icons.Default.Add, "Folder baru", tint = Mac.Text) }
                    IconButton(onClick = { newName = false; nameInput = "" }) { Icon(Icons.Default.Description, "File baru", tint = Mac.Text) }
                    IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, "Muat ulang", tint = Mac.Text) }
                }
            }
        }

        // Lokasi cepat (chip horizontal).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickDirs.forEach { (label, dir) ->
                val selected = cwd == dir
                OutlinedButton(
                    onClick = { cwd = dir },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (selected) Mac.Blue else Mac.Text,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Jendela isi folder.
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Mac.Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mac.Border.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (loadingFile) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("Membuka file…", style = MaterialTheme.typography.bodySmall, color = Mac.Text)
                }
            }
            if (entries.isEmpty()) {
                Text("Folder kosong", Modifier.padding(16.dp), color = Mac.Secondary)
            } else if (gridMode) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries, key = { it.absolutePath }) { f ->
                        val pressRow = rememberPressSource()
                        Column(
                            Modifier.animateItem()
                                .combinedClickable(
                                    interactionSource = pressRow,
                                    indication = null,
                                    onClick = { openEntry(f) },
                                    onLongClick = { toDelete = f }
                                )
                                .pressScale(pressRow)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FileIcon(f, 56.dp, 28.dp)
                            Text(
                                f.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, color = Mac.Text,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (f.isFile) Text(
                                humanSize(f.length()), color = Mac.Secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.absolutePath }) { f ->
                        val pressRow = rememberPressSource()
                        Column(Modifier.animateItem()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(interactionSource = pressRow, indication = null) { openEntry(f) }
                                    .pressScale(pressRow)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FileIcon(f, 36.dp, 20.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Mac.Text)
                                    if (f.isFile) Text(
                                        "${humanSize(f.length())} • ${fmtFileDate(f.lastModified())}",
                                        style = MaterialTheme.typography.bodySmall, color = Mac.Secondary
                                    )
                                }
                                val isTar = f.name.endsWith(".tar.gz") || f.name.endsWith(".tgz") ||
                                    f.name.endsWith(".tar.xz") || f.name.endsWith(".tar")
                                if (f.isFile && (f.name.endsWith(".zip") || isTar)) {
                                    IconButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            runCatching {
                                                if (isTar) Extractor.extractTar(f, cwd)
                                                else Zips.unzip(f) { Zips.safeResolve(cwd, it) }
                                            }
                                                .onSuccess { tick++; toast(ctx, "Di-unzip") }
                                                .onFailure { toast(ctx, "Unzip gagal: ${it.message}") }
                                        }
                                    }) { Icon(Icons.Default.Archive, "Unzip", tint = Mac.Text) }
                                }
                                IconButton(onClick = { toDelete = f }) { Icon(Icons.Default.Delete, "Hapus", tint = Mac.Red) }
                            }
                            HorizontalDivider(color = Mac.Border.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }

    editing?.let { file ->
        Dialog(onDismissRequest = { editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            runCatching { file.writeText(editText) }
                                .onSuccess { scope.launch { toast(ctx, "Tersimpan") }; tick++ }
                                .onFailure { scope.launch { toast(ctx, "Gagal simpan: ${it.message}") } }
                        }) { Icon(Icons.Default.Save, "Simpan") }
                        IconButton(onClick = { editing = null }) { Icon(Icons.Default.Close, "Tutup") }
                    }
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }

    toDelete?.let { f ->
        AnimatedAlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus ${f.name}?") },
            text = { Text(if (f.isDirectory) "Seluruh isi folder ikut terhapus." else "File akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { deleteTree(f) }
                            .onSuccess { tick++ }
                            .onFailure { toast(ctx, "Gagal hapus: ${it.message}") }
                    }
                    toDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }

    newName?.let { isFolder ->
        AnimatedAlertDialog(
            onDismissRequest = { newName = null },
            title = { Text(if (isFolder) "Folder baru" else "File baru") },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Nama") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = Zips.safeResolve(cwd, nameInput.trim())
                    if (target != null && nameInput.isNotBlank()) {
                        runCatching {
                            if (isFolder) target.mkdirs() else { target.parentFile?.mkdirs(); target.createNewFile() }
                        }.onSuccess { tick++ }.onFailure { scope.launch { toast(ctx, "Gagal buat: ${it.message}") } }
                    }
                    newName = null
                }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Batal") } }
        )
    }
}
