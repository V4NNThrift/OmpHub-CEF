package id.omphub

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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

@Composable
fun FilesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = Env.serverDir
    var cwd by remember { mutableStateOf(root) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<File?>(null) }
    var editText by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf<Boolean?>(null) }
    var nameInput by remember { mutableStateOf("") }

    val entries = remember(cwd, tick) {
        cwd.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch(Dispatchers.IO) {
            var n = 0
            uris.forEach { uri ->
                val name = displayName(ctx, uri)
                val out = Zips.safeResolve(cwd, name) ?: return@forEach
                ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                n++
            }
            tick++
            toast(ctx, "$n file diunggah")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { cwd.parentFile?.let { cwd = it } },
                enabled = cwd != root
            ) { Icon(Icons.Default.ArrowBack, "Naik") }
            Text(
                "/" + cwd.relativeTo(root).path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Upload, "Unggah") }
            IconButton(onClick = { newName = true; nameInput = "" }) { Icon(Icons.Default.Add, "Folder baru") }
            IconButton(onClick = { newName = false; nameInput = "" }) { Icon(Icons.Default.Description, "File baru") }
            IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, "Muat ulang") }
        }
        HorizontalDivider()

        if (entries.isEmpty()) Text("Folder kosong", Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries.size) { idx ->
                val f = entries[idx]
                Column {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (f.isDirectory) {
                            cwd = f
                        } else if (f.length() > 512 * 1024 || looksBinary(f)) {
                            scope.launch { toast(ctx, "File biner / terlalu besar untuk diedit") }
                        } else {
                            editText = f.readText()
                            editing = f
                        }
                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(if (f.isDirectory) Icons.Default.Folder else Icons.Default.Description, null)
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (f.isFile) Text(humanSize(f.length()), style = MaterialTheme.typography.bodySmall)
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
                        }) { Icon(Icons.Default.Archive, "Unzip") }
                    }
                    IconButton(onClick = { toDelete = f }) { Icon(Icons.Default.Delete, "Hapus") }
                }
                HorizontalDivider()
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
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus ${f.name}?") },
            text = { Text(if (f.isDirectory) "Seluruh isi folder ikut terhapus." else "File akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { deleteTree(f); tick++ }
                    toDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }

    newName?.let { isFolder ->
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text(if (isFolder) "Folder baru" else "File baru") },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Nama") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = Zips.safeResolve(cwd, nameInput.trim())
                    if (target != null && nameInput.isNotBlank()) {
                        if (isFolder) target.mkdirs() else { target.parentFile?.mkdirs(); target.createNewFile() }
                        tick++
                    }
                    newName = null
                }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Batal") } }
        )
    }
}
