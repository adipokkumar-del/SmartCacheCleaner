package com.smartcache.cleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val Blue = Color(0xFF2979FF)
private val Green = Color(0xFF00C853)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Blue, secondary = Green)
                else lightColorScheme(primary = Blue, secondary = Green)
            ) { Surface(Modifier.fillMaxSize()) { Root(vm) } }
        }
    }
    override fun onResume() { super.onResume(); vm.refresh() }
}

private fun Context.open(i: Intent) {
    try { startActivity(i) } catch (e: Exception) {
        Toast.makeText(this, "This settings page is not available on your device.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun Root(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("🏠 Home", "📱 Apps", "📊 Storage", "⚙️ Settings")
    Scaffold(
        bottomBar = {
            NavigationBar {
                names.forEachIndexed { i, n ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(n.substringBefore(" ")) }, label = { Text(n.substringAfter(" "), fontSize = 11.sp) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home(vm)
                1 -> AppsTab(vm)
                2 -> Analyzer(vm)
                else -> SettingsTab(vm)
            }
        }
    }
    vm.pending?.let { files ->
        val c = LocalContext.current
        val size = remember(files) { files.sumOf { it.length() } }
        AlertDialog(
            onDismissRequest = { vm.cancel() },
            title = { Text("Delete temporary files?") },
            text = { Text("${files.size} files (${Formatter.formatFileSize(c, size)}) from this app's own cache and temp storage. Your photos, videos and documents are never touched.") },
            confirmButton = { TextButton(onClick = { vm.confirm() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.cancel() }) { Text("Cancel") } }
        )
    }
    vm.message?.let {
        AlertDialog(onDismissRequest = { vm.message = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } })
    }
}

@Composable
fun Ring(fraction: Float, label: String, sub: String, size: Dp = 220.dp) {
    val a by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val s = Stroke(width = 28f, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = s)
            drawArc(Brush.sweepGradient(listOf(Blue, Green, Blue), Offset(this.size.width / 2, this.size.height / 2)),
                -90f, 360f * a, false, style = s)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 13.sp)
        }
    }
}

@Composable
fun Home(vm: MainViewModel) {
    val c = LocalContext.current
    val s = vm.storage
    val p by animateFloatAsState(vm.progress, label = "p")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("🧹 Smart Cache Cleaner", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Ring(s.fraction, "${(s.fraction * 100).toInt()}%", "storage used")
        Text("${Formatter.formatFileSize(c, s.free)} free of ${Formatter.formatFileSize(c, s.total)}")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("This app's cache", fontWeight = FontWeight.SemiBold)
                Text(Formatter.formatFileSize(c, vm.ownCache), fontSize = 24.sp, color = Green)
            }
        }
        if (vm.busy) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(8.dp))
        Button(onClick = { vm.startClean() }, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp)) {
            Text("CLEAN NOW", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("Android does not let any app clear other apps' cache directly. Use the Apps tab to open their storage settings.",
            fontSize = 12.sp)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cleaning history", fontWeight = FontWeight.SemiBold)
                if (vm.history.isEmpty()) Text("No cleanings yet.", fontSize = 13.sp)
                vm.history.take(5).forEach { Text(it, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String) {
    val c = LocalContext.current
    val bmp = remember(pkg) {
        try { c.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(44.dp)) else Box(Modifier.size(44.dp))
}

@Composable
fun AppsTab(vm: MainViewModel) {
    val c = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Installed apps", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!vm.hasAccess) Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("To show each app's size and cache, Android needs you to grant \"Usage access\" to this app. It is used only to read storage statistics on your device; nothing is uploaded.", fontSize = 13.sp)
                    TextButton(onClick = { c.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Grant usage access") }
                }
            }
            TextButton(onClick = { c.open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }) { Text("Open system storage settings") }
        }
        items(vm.apps, key = { it.pkg }) { a ->
            Card(Modifier.fillMaxWidth().clickable {
                c.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            }, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.pkg)
                    Column(Modifier.weight(1f)) {
                        Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (a.size != null) "Size ${Formatter.formatFileSize(c, a.size)} · Cache ${Formatter.formatFileSize(c, a.cache ?: 0)}"
                        else "Size unavailable", fontSize = 12.sp)
                    }
                    Text("Info ›", color = Blue, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Analyzer(vm: MainViewModel) {
    val c = LocalContext.current
    val s = vm.storage
    val top = vm.apps.filter { it.size != null }.take(5)
    val max = (top.firstOrNull()?.size ?: 1L).toFloat()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Storage analyzer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Ring(s.fraction, Formatter.formatFileSize(c, s.used), "used", 200.dp)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total: ${Formatter.formatFileSize(c, s.total)}")
                Text("Used: ${Formatter.formatFileSize(c, s.used)}")
                Text("Available: ${Formatter.formatFileSize(c, s.free)}")
                Text("This app's cache: ${Formatter.formatFileSize(c, vm.ownCache)}")
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Largest apps", fontWeight = FontWeight.SemiBold)
                if (top.isEmpty()) Text("App sizes need Usage access (see Settings tab).", fontSize = 13.sp)
                top.forEach {
                    Text("${it.label} · ${Formatter.formatFileSize(c, it.size ?: 0)}", fontSize = 13.sp)
                    LinearProgressIndicator(progress = { (it.size ?: 0) / max }, Modifier.fillMaxWidth().height(8.dp), color = Green)
                }
            }
        }
    }
}

@Composable
fun SettingsTab(vm: MainViewModel) {
    val c = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Usage access: ${if (vm.hasAccess) "granted ✅" else "not granted"}", fontWeight = FontWeight.SemiBold)
                Text("Why: Android only reports other apps' size and cache to apps with this special access. Optional.", fontSize = 13.sp)
                TextButton(onClick = { c.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open usage access settings") }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Privacy", fontWeight = FontWeight.SemiBold)
                Text("No ads, no tracking, no internet permission. No personal data is collected and no files are uploaded. Only this app's own cache and temporary files are ever deleted, and only after you confirm.", fontSize = 13.sp)
            }
        }
        Text("Smart Cache Cleaner 1.0", fontSize = 12.sp)
    }
}
