package com.smartcache.cleaner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StorageInfo(val total: Long, val free: Long) {
    val used get() = total - free
    val fraction get() = if (total > 0) used.toFloat() / total else 0f
}

data class AppItem(val label: String, val pkg: String, val cache: Long?, val size: Long?)

object Storage {
    fun info(): StorageInfo = try {
        val s = StatFs(Environment.getDataDirectory().path)
        StorageInfo(s.totalBytes, s.availableBytes)
    } catch (e: Exception) { StorageInfo(0, 0) }

    private fun cacheDirs(c: Context) = listOfNotNull(c.cacheDir, c.externalCacheDir)

    fun dirSize(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    fun ownCacheSize(c: Context) = cacheDirs(c).sumOf { dirSize(it) }

    /** Only this app's own cache and *.tmp files in its private storage. Never user photos/videos/documents. */
    fun junkFiles(c: Context): List<File> {
        val out = mutableListOf<File>()
        cacheDirs(c).forEach { d -> d.walkTopDown().filter { it.isFile }.forEach { out.add(it) } }
        c.filesDir.walkTopDown().filter { it.isFile && it.extension == "tmp" }.forEach { out.add(it) }
        return out
    }

    fun hasUsageAccess(c: Context): Boolean = try {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    fun loadApps(c: Context): List<AppItem> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgs = try {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.distinct()
        } catch (e: Exception) { emptyList() }
        val access = hasUsageAccess(c)
        val ssm = c.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        return pkgs.mapNotNull { p ->
            try {
                val label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
                var cache: Long? = null
                var size: Long? = null
                if (access) try {
                    val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, p, Process.myUserHandle())
                    cache = st.cacheBytes
                    size = st.appBytes + st.dataBytes
                } catch (e: Exception) { }
                AppItem(label, p, cache, size)
            } catch (e: Exception) { null }
        }.sortedByDescending { it.size ?: -1L }
    }
}
