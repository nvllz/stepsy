package com.nvllz.stepsy.util

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nvllz.stepsy.service.MidnightResetReceiver
import com.nvllz.stepsy.service.MotionService
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.io.PushbackReader
import java.io.Reader

private const val TAG = "BackupImport"
private const val FILE_PROBE_CHARS = 65536

internal class UnsupportedBackupException : Exception()

internal object BackupImport {

    sealed interface Result {
        class Imported(val rows: Int, val failed: Int, val todaySteps: Int) : Result
        object Unsupported : Result
        object Failed : Result
    }

    enum class Format { FULL, CSV, PATH, PASEO, INVALID, UNSUPPORTED }

    private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val SETTINGS_KEY_REGEX = Regex("[A-Za-z0-9_]+")
    private const val SETTINGS_KEY_MAX_LENGTH = 64

    private class ParsedBackup {
        val entries = mutableListOf<Pair<String, Int>>()
        val slots = mutableListOf<Triple<String, Int, Int>>()
        val adjustments = mutableListOf<Triple<String, Int, Int>>()
        val settings = mutableListOf<ParsedSetting>()
        var failed = 0
    }

    private class ParsedSetting(val key: String, val type: String, val value: Any)

    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }

    fun hasHistory(ctx: Context): Boolean = Database.getInstance(ctx).firstEntry != null

    fun isFileValid(ctx: Context, uri: Uri): Boolean {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    val buffer = CharArray(FILE_PROBE_CHARS)
                    val filled = reader.read(buffer)
                    val probe = if (filled <= 0) "" else String(buffer, 0, filled)
                    for (line in probe.lineSequence()) {
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

    fun detectFormat(ctx: Context, uri: Uri): Format = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).reader().use { streamReader ->
                var c = streamReader.read()
                while (c != -1 && c.toChar().isWhitespace()) c = streamReader.read()
                when {
                    c == -1 -> Format.INVALID
                    c.toChar() == '{' -> {
                        val pushback = PushbackReader(streamReader, 1)
                        pushback.unread(c)
                        readJsonHeaderFormat(pushback)
                    }
                    else -> {
                        val pushback = PushbackReader(streamReader, 1)
                        pushback.unread(c)
                        if (isPaseoDatabase(pushback)) Format.PASEO
                        else if (isFileValid(ctx, uri)) Format.CSV
                        else Format.INVALID
                    }
                }
            }
        } ?: Format.INVALID
    } catch (_: Exception) {
        Format.INVALID
    }

    private fun isPaseoDatabase(reader: Reader): Boolean {
        val header = CharArray(16)
        var filled = 0
        while (filled < header.size) {
            val read = reader.read(header, filled, header.size - filled)
            if (read == -1) break
            filled += read
        }
        val expected = SQLITE_HEADER
        if (filled < expected.length) return false
        for (i in expected.indices) {
            if (header[i] != expected[i]) return false
        }
        return true
    }

    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    private fun readJsonHeaderFormat(reader: Reader): Format {
        return try {
            JsonReader(reader).use { json ->
                var formatValid = false
                var version = -1
                var isPathExport = false
                json.beginObject()
                while (json.hasNext() && !(!isPathExport && formatValid && version >= 0)) {
                    when (json.nextName()) {
                        "format" -> formatValid = json.nextString() == BackupExport.FORMAT_ID
                        "version" -> version = json.nextInt()
                        "step_history_list" -> {
                            isPathExport = true
                            json.skipValue()
                        }
                        else -> json.skipValue()
                    }
                }
                when {
                    formatValid && version > BackupExport.FORMAT_VERSION -> Format.UNSUPPORTED
                    formatValid && version >= 1 -> Format.FULL
                    !formatValid && isPathExport -> Format.PATH
                    else -> Format.INVALID
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup header", e)
            Format.INVALID
        }
    }

    fun importAndClear(ctx: Context, uri: Uri, repushService: Boolean = true): Result {
        return when (detectFormat(ctx, uri)) {
            Format.CSV -> importCsv(ctx, uri, repushService)
            Format.PATH -> try {
                importPath(ctx, uri, repushService)
            } catch (e: Exception) {
                Log.e(TAG, "Path import failed", e)
                Result.Failed
            }
            Format.PASEO -> try {
                importPaseo(ctx, uri, repushService)
            } catch (e: Exception) {
                Log.e(TAG, "Paseo import failed", e)
                Result.Failed
            }
            Format.FULL -> try {
                importFull(ctx, uri, repushService)
            } catch (e: UnsupportedBackupException) {
                Log.w(TAG, "Backup is from a newer app version", e)
                Result.Unsupported
            } catch (e: Exception) {
                Log.e(TAG, "Full backup import failed", e)
                Result.Failed
            }
            Format.UNSUPPORTED -> Result.Unsupported
            Format.INVALID -> Result.Failed
        }
    }

    private fun importCsv(ctx: Context, uri: Uri, repushService: Boolean): Result {
        val db = Database.getInstance(ctx)
        val entries = mutableListOf<Pair<String, Int>>()
        var failed = 0

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
                        } catch (ex: Exception) {
                            Log.e(TAG, "Cannot parse line", ex)
                            failed++
                        }
                    }
                }
            } ?: return Result.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Cannot open file", ex)
            return Result.Failed
        }

        sendImportHold(ctx, true)
        try {
            db.clearAllAndImport(entries)
            return finishImport(ctx, db, entries.size, failed, { date ->
                var sum = 0
                for ((d, steps) in entries) if (d == date) sum += steps
                sum
            }, repushService)
        } catch (ex: Exception) {
            Log.e(TAG, "CSV import failed", ex)
            sendImportHold(ctx, false)
            return Result.Failed
        }
    }

    private fun importPath(ctx: Context, uri: Uri, repushService: Boolean): Result {
        val db = Database.getInstance(ctx)
        val entries = LinkedHashMap<String, Int>()
        var failed = 0
        var dailyGoal = 0
        var theme: String? = null

        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            JsonReader(FileInputStream(pfd.fileDescriptor).reader(Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "step_history_list" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                try {
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.skipValue()
                                        failed++
                                        continue
                                    }
                                    val raw = reader.nextString()
                                    val colon = raw.indexOf(':')
                                    val date = if (colon > 0) raw.substring(0, colon).trim() else ""
                                    val steps = if (colon > 0) raw.substring(colon + 1).trim().toIntOrNull() else null
                                    if (steps != null && steps >= 0 && isValidBackupDate(date)) {
                                        entries[date] = steps
                                    } else {
                                        failed++
                                    }
                                } catch (_: Exception) {
                                    failed++
                                }
                            }
                            reader.endArray()
                        }
                        "daily_goal" -> dailyGoal = readScalar(reader)?.toIntOrNull() ?: 0
                        "theme_mode" -> theme = readScalar(reader)?.trim()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } ?: return Result.Failed

        sendImportHold(ctx, true)
        try {
            db.clearAllAndImport(entries.map { it.key to it.value })
            runBlocking {
                AppPreferences.dataStore.edit { prefs ->
                    if (dailyGoal > 0) {
                        prefs[AppPreferences.PreferenceKeys.DAILY_GOAL_TARGET] = dailyGoal
                    }
                    val appliedTheme = theme
                    if (appliedTheme != null && appliedTheme in PATH_THEMES) {
                        prefs[AppPreferences.PreferenceKeys.THEME] = appliedTheme
                    }
                }
            }
            return finishImport(ctx, db, entries.size, failed, { date -> entries[date] ?: 0 }, repushService)
        } catch (ex: Exception) {
            Log.e(TAG, "Path import failed", ex)
            sendImportHold(ctx, false)
            return Result.Failed
        }
    }

    private fun importPaseo(ctx: Context, uri: Uri, repushService: Boolean): Result {
        val dbFile = File.createTempFile("paseo_import", ".db", ctx.cacheDir)
        var sqlite: SQLiteDatabase? = null
        val hourlyByDate = LinkedHashMap<String, IntArray>()
        val dailyExtra = HashMap<String, Int>()
        var failed = 0

        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                }
            } ?: return Result.Failed

            sqlite = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            sqlite?.query("hours", null, null, null, null, null, null)?.use { cursor ->
                val dateIndex = cursor.getColumnIndexOrThrow("date")
                val hourIndex = (cursor.getColumnIndex("time").takeIf { it >= 0 })
                    ?: cursor.getColumnIndexOrThrow("hour")
                val startIndex = cursor.getColumnIndexOrThrow("startSteps")
                val endIndex = cursor.getColumnIndexOrThrow("endSteps")
                val editIndex = cursor.getColumnIndex("edit")

                while (cursor.moveToNext()) {
                    try {
                        val dateRaw = cursor.getString(dateIndex)
                        val date = parsePaseoDate(dateRaw)
                        if (date == null) { failed++; continue }

                        val steps = when {
                            editIndex >= 0 && cursor.getInt(editIndex) == 1 ->
                                if (cursor.isNull(endIndex)) null else cursor.getInt(endIndex)
                            cursor.isNull(startIndex) || cursor.isNull(endIndex) -> null
                            else -> cursor.getInt(endIndex) - cursor.getInt(startIndex)
                        }?.coerceAtLeast(0)
                        if (steps == null) { failed++; continue }

                        val hour = if (cursor.isNull(hourIndex)) null
                        else cursor.getInt(hourIndex).takeIf { it in 0 until Database.SLOT_COUNT }

                        if (hour != null) {
                            hourlyByDate.getOrPut(date) { IntArray(Database.SLOT_COUNT) }[hour] += steps
                        } else {
                            dailyExtra[date] = (dailyExtra[date] ?: 0) + steps
                        }
                    } catch (_: Exception) {
                        failed++
                    }
                }
            } ?: return Result.Failed
        } finally {
            try { sqlite?.close() } catch (_: Exception) { }
            try { dbFile.delete() } catch (_: Exception) { }
        }

        try {
            sendImportHold(ctx, true)
            val db = Database.getInstance(ctx)
            val entries = mutableMapOf<String, Int>()
            val slots = mutableMapOf<String, IntArray>()
            for ((date, hourly) in hourlyByDate) {
                val hasHourly = hourly.any { it > 0 }
                val extra = dailyExtra[date] ?: 0
                val total = hourly.sum() + extra
                if (total > 0) {
                    entries[date] = total
                    if (hasHourly) slots[date] = hourly
                }
            }
            for ((date, extra) in dailyExtra) {
                if (!hourlyByDate.containsKey(date) && extra > 0) entries[date] = extra
            }

            db.clearAllAndInsert(entries.map { it.key to it.value }.sortedBy { it.first }, slots, emptyList())
            return finishImport(ctx, db, entries.size, failed, { date -> entries[date] ?: 0 }, repushService)
        } catch (ex: Exception) {
            Log.e(TAG, "Paseo import failed", ex)
            sendImportHold(ctx, false)
            return Result.Failed
        }
    }

    private fun parsePaseoDate(raw: String?): String? {
        if (raw == null || !raw.matches(PASEO_DATE_REGEX)) return null
        return try {
            LocalDate.parse(
                raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    private val PATH_THEMES = setOf("system", "light", "dark")
    private val PASEO_DATE_REGEX = Regex("\\d{8}")

    private fun importFull(ctx: Context, uri: Uri, repushService: Boolean): Result {
        val db = Database.getInstance(ctx)
        val parsed = parseFullBackup(ctx, uri)

        sendImportHold(ctx, true)
        try {
            db.replaceAllFromBackup(parsed.entries, parsed.slots, parsed.adjustments)

            runBlocking {
                AppPreferences.dataStore.edit { prefs ->
                    val keys = prefs.asMap().keys.filter { it.name !in AppPreferences.EXPORT_EXCLUDED_KEYS }
                    for (key in keys) prefs.remove(key)
                    prefs.remove(AppPreferences.PreferenceKeys.DAY_START_PREV_HOUR)
                    prefs.remove(AppPreferences.PreferenceKeys.DAY_START_SWITCH_MS)
                    for (setting in parsed.settings) {
                        when (setting.type) {
                            "bool" -> prefs[booleanPreferencesKey(setting.key)] = setting.value as Boolean
                            "int" -> prefs[intPreferencesKey(setting.key)] = setting.value as Int
                            "long" -> prefs[longPreferencesKey(setting.key)] = setting.value as Long
                            "float" -> prefs[floatPreferencesKey(setting.key)] = (setting.value as Double).toFloat()
                            "string" -> prefs[stringPreferencesKey(setting.key)] = setting.value as String
                            "stringset" -> prefs[stringSetPreferencesKey(setting.key)] = setting.value as Set<String>
                        }
                    }
                }
            }

            val effective = LinkedHashMap<String, Int>()
            for ((date, steps) in parsed.entries) effective[date] = steps

            return finishImport(ctx, db, parsed.entries.size, parsed.failed, { date ->
                effective[date] ?: 0
            }, repushService)
        } catch (ex: Exception) {
            Log.e(TAG, "Full backup import failed", ex)
            sendImportHold(ctx, false)
            return Result.Failed
        }
    }

    private fun finishImport(
        ctx: Context,
        db: Database,
        rows: Int,
        failed: Int,
        todayStepsFor: (String) -> Int,
        repushService: Boolean,
    ): Result {
        Util.refreshDayStartCache()
        MidnightResetReceiver.scheduleNextMidnightAlarm(ctx)

        val today = Util.todayDateString()
        val importedTodaySteps = todayStepsFor(today)

        val total = db.getSumSteps("2000-01-01", "9999-12-31").toLong()
        runBlocking {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.STEPS] = importedTodaySteps
                prefs[AppPreferences.PreferenceKeys.DATE] = today

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
                    putExtra(MotionService.KEY_STEPS, importedTodaySteps)
                    putExtra(MotionService.KEY_DATE, today)
                }
            )
            ctx.startService(
                Intent(ctx, MotionService::class.java).setAction("UPDATE_NOTIFICATION")
            )
        } else {
            sendImportHold(ctx, false)
        }

        return Result.Imported(rows, failed, importedTodaySteps)
    }

    private fun sendImportHold(ctx: Context, hold: Boolean): Boolean {
        if (!MotionService.isRunning) return false
        try {
            ctx.startService(
                Intent(ctx, MotionService::class.java)
                    .setAction(MotionService.ACTION_IMPORT_HOLD)
                    .putExtra(MotionService.EXTRA_IMPORT_HOLD, hold)
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Could not signal MotionService for import", e)
            return false
        }
    }

    private fun parseFullBackup(ctx: Context, uri: Uri): ParsedBackup {
        val parsed = ParsedBackup()
        var formatRaw: String? = null
        var version = -1

        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            JsonReader(FileInputStream(pfd.fileDescriptor).reader(Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "format" -> formatRaw = reader.nextString()
                        "version" -> version = reader.nextInt()
                        "history" -> parseHistorySection(reader, parsed)
                        "hourly" -> parseHourlySection(reader, parsed)
                        "settings" -> parseSettingsSection(reader, parsed)
                        else -> reader.skipValue()
                    }
                }
            }
        } ?: throw InvalidBackupException()

        if (formatRaw != BackupExport.FORMAT_ID) throw InvalidBackupException()
        if (version > BackupExport.FORMAT_VERSION) throw UnsupportedBackupException()
        if (version < 1) throw InvalidBackupException()
        return parsed
    }

    private class InvalidBackupException : Exception()

    private fun parseHistorySection(reader: JsonReader, parsed: ParsedBackup) {
        reader.beginArray()
        while (reader.hasNext()) {
            val row = readEntryElements(reader, 2)
            val date = row?.get(0)
            val steps = row?.get(1)?.trim()?.toIntOrNull()
            if (date != null && isValidBackupDate(date) && steps != null && steps >= 0) {
                parsed.entries.add(date to steps)
            } else {
                parsed.failed++
            }
        }
        reader.endArray()
    }

    private fun parseHourlySection(reader: JsonReader, parsed: ParsedBackup) {
        var slotMinutes: Int? = null
        var rawSlots: List<Pair<String, List<Pair<Int, Int>>>>? = null
        var rawAdjust: List<Pair<String, List<Pair<Int, Int>>>>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "slotMinutes" -> slotMinutes = readScalar(reader)?.toIntOrNull()
                "slots" -> rawSlots = readDayGroups(reader, parsed)
                "adjust" -> rawAdjust = readDayGroups(reader, parsed)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (slotMinutes == null || slotMinutes != Database.SLOT_MINUTES) {
            parsed.failed++
            return
        }

        for ((date, values) in rawSlots.orEmpty()) {
            for ((slot, steps) in values) {
                if (slot in 0 until Database.SLOT_COUNT && steps > 0) {
                    parsed.slots.add(Triple(date, slot, steps))
                } else {
                    parsed.failed++
                }
            }
        }

        for ((date, values) in rawAdjust.orEmpty()) {
            for ((minute, delta) in values) {
                if (minute in 0..1440) {
                    parsed.adjustments.add(Triple(date, minute, delta))
                } else {
                    parsed.failed++
                }
            }
        }
    }

    private fun readDayGroups(reader: JsonReader, parsed: ParsedBackup): List<Pair<String, List<Pair<Int, Int>>>> {
        val days = mutableListOf<Pair<String, List<Pair<Int, Int>>>>()
        reader.beginArray()
        while (reader.hasNext()) {
            var inside = false
            try {
                var date: String? = null
                val values = mutableListOf<Pair<Int, Int>>()
                reader.beginArray()
                inside = true
                if (reader.peek() == JsonToken.END_ARRAY) {
                    date = null
                } else {
                    date = readScalar(reader)?.trim()
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        collectValuePairs(reader, values, parsed)
                    } else if (reader.hasNext()) {
                        reader.skipValue()
                        parsed.failed++
                    }
                    while (reader.hasNext()) reader.skipValue()
                }
                reader.endArray()
                inside = false

                if (date != null && isValidBackupDate(date)) {
                    days.add(date to values)
                } else {
                    parsed.failed++
                }
            } catch (_: Exception) {
                parsed.failed++
                if (inside) {
                    try {
                        while (reader.hasNext()) reader.skipValue()
                        reader.endArray()
                    } catch (_: Exception) {
                        recoverSub(reader)
                    }
                } else {
                    recoverSub(reader)
                }
            }
        }
        reader.endArray()
        return days
    }

    private fun collectValuePairs(reader: JsonReader, target: MutableList<Pair<Int, Int>>, parsed: ParsedBackup) {
        reader.beginArray()
        var inside = false
        try {
            inside = true
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    var first: Int? = null
                    var second: Int? = null
                    if (reader.hasNext()) first = readScalar(reader)?.trim()?.toIntOrNull()
                    if (reader.hasNext()) second = readScalar(reader)?.trim()?.toIntOrNull()
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                    if (first != null && second != null) {
                        target.add(first to second)
                    } else {
                        parsed.failed++
                    }
                } else {
                    reader.skipValue()
                    parsed.failed++
                }
            }
            reader.endArray()
        } catch (_: Exception) {
            parsed.failed++
            if (inside) {
                try {
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                } catch (_: Exception) {
                    recoverSub(reader)
                }
            } else {
                recoverSub(reader)
            }
        }
    }

    private fun parseSettingsSection(reader: JsonReader, parsed: ParsedBackup) {
        reader.beginArray()
        while (reader.hasNext()) {
            try {
                var key: String? = null
                var type: String? = null
                var value: Any? = null

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "key" -> key = readScalar(reader)?.trim()
                        "type" -> type = readScalar(reader)?.trim()
                        "value" -> value = readSettingValue(reader, type)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (key == null || type == null || value == null) continue
                if (key.length > SETTINGS_KEY_MAX_LENGTH || !SETTINGS_KEY_REGEX.matches(key)) continue
                if (key in AppPreferences.EXPORT_EXCLUDED_KEYS) continue
                if (key == AppPreferences.PreferenceKeys.DAY_START_HOUR.name &&
                    !(type == "int" && value is Int && value in Util.DAY_START_PRESETS)) {
                    parsed.failed++
                    continue
                }
                if (type == "float" && value is Double && !value.isFinite()) {
                    parsed.failed++
                    continue
                }
                parsed.settings.add(ParsedSetting(key, type, value))
            } catch (_: Exception) {
                recoverSub(reader)
            }
        }
        reader.endArray()
    }

    private fun readSettingValue(reader: JsonReader, type: String?): Any? {
        return when (type) {
            "bool" -> when (reader.peek()) {
                JsonToken.BOOLEAN -> reader.nextBoolean()
                else -> { reader.skipValue(); null }
            }
            "int", "long", "float" -> when (reader.peek()) {
                JsonToken.NULL, JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { reader.skipValue(); null }
                else -> {
                    val raw = reader.nextString()
                    when (type) {
                        "int" -> raw.toIntOrNull()
                        "long" -> raw.toLongOrNull()
                        else -> raw.toDoubleOrNull()
                    }
                }
            }
            "string" -> when (reader.peek()) {
                JsonToken.NULL, JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { reader.skipValue(); null }
                else -> reader.nextString()
            }
            "stringset" -> {
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    reader.skipValue()
                    null
                } else {
                    val values = mutableSetOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        when (reader.peek()) {
                            JsonToken.NULL, JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> reader.skipValue()
                            else -> values.add(reader.nextString())
                        }
                    }
                    reader.endArray()
                    values
                }
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readEntryElements(reader: JsonReader, count: Int): List<String?>? {
        var inside = false
        return try {
            reader.beginArray()
            inside = true
            val result = mutableListOf<String?>()
            for (index in 0 until count) {
                when (reader.peek()) {
                    JsonToken.END_ARRAY -> break
                    JsonToken.NULL -> { reader.skipValue(); result.add(null) }
                    JsonToken.BOOLEAN -> result.add(reader.nextBoolean().toString())
                    else -> result.add(reader.nextString())
                }
            }
            while (result.size < count) result.add(null)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            result
        } catch (_: Exception) {
            if (inside) {
                try {
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                } catch (_: Exception) { }
            } else {
                try { reader.skipValue() } catch (_: Exception) { }
            }
            null
        }
    }

    private fun readScalar(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.NULL -> { reader.skipValue(); null }
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> { reader.skipValue(); null }
            else -> reader.nextString()
        }
    }

    private fun recoverSub(reader: JsonReader) {
        try {
            if (reader.hasNext()) reader.skipValue()
        } catch (_: Exception) { }
    }

    private fun parseImportDate(raw: String): String {
        if (isValidBackupDate(raw)) return raw
        return Database.snapTimestampToDate(raw.toLong())
    }

    private fun isValidBackupDate(raw: String): Boolean {
        if (!raw.matches(DATE_REGEX)) return false
        return try {
            LocalDate.parse(raw)
            true
        } catch (_: Exception) {
            false
        }
    }
}
