package com.smartcache.cleaner

import android.app.Application
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("history", 0)

    var storage by mutableStateOf(StorageInfo(0, 0)); private set
    var ownCache by mutableStateOf(0L); private set
    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var hasAccess by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var pending by mutableStateOf<List<File>?>(null); private set
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<String>>(emptyList()); private set

    fun refresh() {
        viewModelScope.launch {
            try {
                storage = Storage.info()
                hasAccess = Storage.hasUsageAccess(ctx)
                ownCache = withContext(Dispatchers.IO) { Storage.ownCacheSize(ctx) }
                apps = withContext(Dispatchers.IO) { Storage.loadApps(ctx) }
                history = (prefs.getString("h", "") ?: "").split("\n").filter { it.isNotBlank() }
            } catch (e: Exception) { message = "Could not read some storage information." }
        }
    }

    fun startClean() {
        if (busy) return
        viewModelScope.launch {
            busy = true; progress = 0f
            try {
                val files = withContext(Dispatchers.IO) { Storage.junkFiles(ctx) }
                if (files.isEmpty()) message = "No removable temporary files were found." else pending = files
            } catch (e: Exception) { message = "Scanning failed: ${e.message}" }
            busy = false
        }
    }

    fun cancel() { pending = null }

    fun confirm() {
        val files = pending ?: return
        pending = null
        viewModelScope.launch {
            busy = true; progress = 0f
            var freed = 0L; var failed = 0
            withContext(Dispatchers.IO) {
                files.forEachIndexed { i, f ->
                    val len = f.length()
                    if (f.delete()) freed += len else failed++
                    progress = (i + 1f) / files.size
                }
            }
            delay(500)
            val fs = Formatter.formatFileSize(ctx, freed)
            val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            val old = (prefs.getString("h", "") ?: "").split("\n").filter { it.isNotBlank() }
            prefs.edit().putString("h", (listOf("$now - freed $fs") + old).take(20).joinToString("\n")).apply()
            message = "Done. Freed $fs from this app's own cache." +
                if (failed > 0) " $failed file(s) could not be deleted." else ""
            busy = false
            refresh()
        }
    }
}
