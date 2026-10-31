package com.nvllz.stepsy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.MotionService.Companion.KEY_DATE
import com.nvllz.stepsy.service.MotionService.Companion.KEY_STEPS
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream

private const val TAG = "BackupImport"

internal object BackupImport {

    sealed interface Result {
        class Imported(val rows: Int, val failed: Int, val todaySteps: Int) : Result
        object Failed : Result
    }

    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/*"
    }

    fun hasHistory(ctx: Context): Boolean = Database.getInstance(ctx).firstEntry != null

    fun isFileValid(ctx: Context, uri: Uri): Boolean {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    for (line in reader.readLines()) {
                        if (line.isBlank()) continue
                        try {
                            val split = line.split(",")
                            if (split.size < 2) continue
                            split[1].trim().toInt()
                            parseImportDate(split[0].trim())
                            return true
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    fun importAndClear(ctx: Context, uri: Uri, repushService: Boolean = true): Result {
        val db = Database.getInstance(ctx)
        val today = Util.todayDateString()
        val entries = mutableListOf<Pair<String, Int>>()
        var failed = 0
        var importedTodaySteps = 0

        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    for (line in reader.readLines()) {
                        if (line.isBlank()) continue
                        try {
                            val split = line.split(",")
                            if (split.size < 2) { failed++; continue }
                            val steps = split[1].trim().toInt()
                            val dateStr = parseImportDate(split[0].trim())
                            entries.add(dateStr to steps)
                            if (dateStr == today) importedTodaySteps += steps
                        } catch (ex: Exception) {
                            Log.e(TAG, "Cannot parse line", ex)
                            failed++
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Cannot open file", ex)
            return Result.Failed
        }

        try {
            db.clearAllAndImport(entries)
        } catch (ex: Exception) {
            Log.e(TAG, "Atomic import failed", ex)
            return Result.Failed
        }

        runBlocking {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.STEPS] = importedTodaySteps
                prefs[AppPreferences.PreferenceKeys.DATE] = today

                val total = db.getSumSteps("2000-01-01", "9999-12-31").toLong()
                prefs[AppPreferences.PreferenceKeys.LAST_NOTIFIED_MILESTONE] =
                    Util.MILESTONES.lastOrNull { it <= total } ?: 0L
                prefs[AppPreferences.PreferenceKeys.BASE_TOTAL_STEPS] =
                    (total - importedTodaySteps).coerceAtLeast(0L)
            }
        }
        if (repushService) {
            ctx.startService(
                Intent(ctx, MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                    putExtra(KEY_STEPS, importedTodaySteps)
                    putExtra(KEY_DATE, today)
                }
            )
        }
        return Result.Imported(entries.size, failed, importedTodaySteps)
    }

    private fun parseImportDate(raw: String): String {
        return if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            raw
        } else {
            Database.snapTimestampToDate(raw.toLong())
        }
    }
}
