package com.nvllz.stepsy.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.Cursor.*
import android.util.Log
import java.util.Calendar
import java.util.TimeZone
import androidx.core.database.sqlite.transaction

internal class Database private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private fun querySingle(columns: Array<String>, selection: String? = null, selectionArgs: Array<String>? = null): Any? {
        val cursor = readableDatabase.query(HISTORY_TABLE, columns, selection, selectionArgs, null, null, null)
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        val result: Any? = when (cursor.getType(0)) {
            FIELD_TYPE_INTEGER -> cursor.getLong(0)
            FIELD_TYPE_FLOAT   -> cursor.getFloat(0)
            FIELD_TYPE_STRING  -> cursor.getString(0)
            FIELD_TYPE_NULL    -> null
            else               -> null
        }
        cursor.close()
        return result
    }

    internal val firstEntry: String?
        get() = querySingle(arrayOf("min(date)"), "date > ?", arrayOf("")) as? String

    internal val lastEntry: String?
        get() = querySingle(arrayOf("max(date)"), "date > ?", arrayOf("")) as? String

    internal fun avgSteps(minDate: String, maxDate: String): Int {
        val result = querySingle(arrayOf("avg(stepsy)"), "date >= ? AND date <= ?", arrayOf(minDate, maxDate))
        return when (result) {
            is Long  -> result.toInt()
            is Float -> result.toInt()
            else     -> 0
        }
    }

    internal fun getSumSteps(minDate: String, maxDate: String): Int {
        val result = querySingle(arrayOf("sum(stepsy)"), "date >= ? AND date <= ?", arrayOf(minDate, maxDate))
        return when (result) {
            is Long  -> result.toInt()
            is Float -> result.toInt()
            else     -> 0
        }
    }

    internal fun totalStepsExcluding(excludeDate: String): Long {
        val result = querySingle(arrayOf("sum(stepsy)"), "date <> ?", arrayOf(excludeDate))
        return (result as? Long) ?: 0L
    }

    internal fun getEntries(minDate: String?, maxDate: String?): List<Entry> {
        val entries = mutableListOf<Entry>()
        val cursor = readableDatabase.query(
            HISTORY_TABLE, null,
            "date >= ? AND date <= ?",
            arrayOf(minDate, maxDate),
            null, null, "date ASC"
        )
        while (cursor.moveToNext()) {
            val dateStr = cursor.getString(0)
            entries.add(Entry(Util.dateStringToCalendarMillis(dateStr), dateStr, cursor.getInt(1)))
        }
        cursor.close()
        return entries
    }

    internal fun addEntry(date: String, steps: Int) {
        val values = ContentValues().apply {
            put("date", date)
            put("stepsy", steps)
        }
        if (writableDatabase.update(HISTORY_TABLE, values, "date = ?", arrayOf(date)) == 0) {
            writableDatabase.insertOrThrow(HISTORY_TABLE, null, values)
        }
    }

    internal fun loadSlots(date: String): IntArray {
        val slots = IntArray(SLOT_COUNT)
        val cursor = readableDatabase.query(
            SLOTS_TABLE, arrayOf("slot", "steps"), "date = ?", arrayOf(date), null, null, null
        )
        while (cursor.moveToNext()) {
            val slot = cursor.getInt(0)
            if (slot in 0 until SLOT_COUNT) slots[slot] = cursor.getInt(1)
        }
        cursor.close()
        return slots
    }

    internal fun saveSlots(date: String, slots: IntArray, dirtyMask: Long) {
        writableDatabase.transaction {
            for (slot in 0 until SLOT_COUNT) {
                if ((dirtyMask and (1L shl slot)) == 0L) continue
                val steps = slots[slot]
                if (steps > 0) {
                    val values = ContentValues().apply {
                        put("date", date)
                        put("slot", slot)
                        put("steps", steps)
                    }
                    insertWithOnConflict(SLOTS_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                } else {
                    delete(SLOTS_TABLE, "date = ? AND slot = ?", arrayOf(date, slot.toString()))
                }
            }
        }
    }

    internal fun addAdjust(date: String, minute: Int, delta: Int) {
        val values = ContentValues().apply {
            put("date", date)
            put("minute", minute)
            put("delta", delta)
        }
        writableDatabase.insertOrThrow(ADJUST_TABLE, null, values)
    }

    internal fun hasSlots(date: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT 1 FROM $SLOTS_TABLE WHERE date = ? LIMIT 1", arrayOf(date)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    internal fun firstHourlyDate(): String? {
        val cursor = readableDatabase.rawQuery(
            "SELECT MIN(date) FROM $SLOTS_TABLE", emptyArray()
        )
        val value = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return value
    }

    internal fun loadAdjust(date: String): List<Adjustment> {
        val adjustments = mutableListOf<Adjustment>()
        val cursor = readableDatabase.query(
            ADJUST_TABLE, arrayOf("minute", "delta"), "date = ?", arrayOf(date), null, null, "minute ASC"
        )
        while (cursor.moveToNext()) {
            adjustments.add(Adjustment(cursor.getInt(0), cursor.getInt(1)))
        }
        cursor.close()
        return adjustments
    }

    internal fun loadSlotsRange(minDate: String, maxDate: String): List<SlotDay> {
        val days = mutableListOf<SlotDay>()
        var currentDate: String? = null
        var currentSlots: IntArray? = null
        val cursor = readableDatabase.query(
            SLOTS_TABLE, arrayOf("date", "slot", "steps"),
            "date >= ? AND date <= ?", arrayOf(minDate, maxDate), null, null, "date ASC, slot ASC"
        )
        while (cursor.moveToNext()) {
            val date = cursor.getString(0)
            if (date != currentDate) {
                currentSlots?.let { days.add(SlotDay(currentDate!!, it)) }
                currentDate = date
                currentSlots = IntArray(SLOT_COUNT)
            }
            val slot = cursor.getInt(1)
            if (slot in 0 until SLOT_COUNT) currentSlots?.set(slot, cursor.getInt(2))
        }
        currentSlots?.let { days.add(SlotDay(currentDate!!, it)) }
        cursor.close()
        return days
    }

    internal fun clearDay(date: String) {
        writableDatabase.transaction {
            delete(SLOTS_TABLE, "date = ?", arrayOf(date))
            delete(ADJUST_TABLE, "date = ?", arrayOf(date))
        }
    }

    internal fun clearAllHourly() {
        writableDatabase.transaction {
            delete(SLOTS_TABLE, null, null)
            delete(ADJUST_TABLE, null, null)
        }
    }

    internal fun loadSlotsAll(): List<Triple<String, Int, Int>> {
        val rows = mutableListOf<Triple<String, Int, Int>>()
        val cursor = readableDatabase.query(
            SLOTS_TABLE, arrayOf("date", "slot", "steps"),
            null, null, null, null, "date ASC, slot ASC"
        )
        while (cursor.moveToNext()) {
            rows.add(Triple(cursor.getString(0), cursor.getInt(1), cursor.getInt(2)))
        }
        cursor.close()
        return rows
    }

    internal fun loadAdjustAll(): List<Triple<String, Int, Int>> {
        val rows = mutableListOf<Triple<String, Int, Int>>()
        val cursor = readableDatabase.query(
            ADJUST_TABLE, arrayOf("date", "minute", "delta"),
            null, null, null, null, "date ASC, rowid ASC"
        )
        while (cursor.moveToNext()) {
            rows.add(Triple(cursor.getString(0), cursor.getInt(1), cursor.getInt(2)))
        }
        cursor.close()
        return rows
    }

    internal fun replaceAllFromBackup(
        entries: List<Pair<String, Int>>,
        slots: List<Triple<String, Int, Int>>,
        adjustments: List<Triple<String, Int, Int>>,
    ) {
        val db = writableDatabase
        db.transaction {
            db.compileStatement("DELETE FROM $HISTORY_TABLE").use { it.execute() }
            db.compileStatement("DELETE FROM $SLOTS_TABLE").use { it.execute() }
            db.compileStatement("DELETE FROM $ADJUST_TABLE").use { it.execute() }

            db.compileStatement(
                "INSERT OR REPLACE INTO $HISTORY_TABLE (date, stepsy) VALUES (?, ?)"
            ).use { insertHistory ->
                for ((date, steps) in entries) {
                    insertHistory.bindString(1, date)
                    insertHistory.bindLong(2, steps.toLong())
                    insertHistory.executeInsert()
                }
            }
            db.compileStatement(
                "INSERT OR REPLACE INTO $SLOTS_TABLE (date, slot, steps) VALUES (?, ?, ?)"
            ).use { insertSlot ->
                for ((date, slot, steps) in slots) {
                    insertSlot.bindString(1, date)
                    insertSlot.bindLong(2, slot.toLong())
                    insertSlot.bindLong(3, steps.toLong())
                    insertSlot.executeInsert()
                }
            }
            db.compileStatement(
                "INSERT INTO $ADJUST_TABLE (date, minute, delta) VALUES (?, ?, ?)"
            ).use { insertAdjust ->
                for ((date, minute, delta) in adjustments) {
                    insertAdjust.bindString(1, date)
                    insertAdjust.bindLong(2, minute.toLong())
                    insertAdjust.bindLong(3, delta.toLong())
                    insertAdjust.executeInsert()
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DATABASE_CREATE_HISTORY)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL(DATABASE_CREATE_SLOTS)
            db.execSQL(DATABASE_CREATE_ADJUST)
            db.execSQL(DATABASE_CREATE_ADJUST_DATE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Upgrading database from version $oldVersion to $newVersion")
        when (oldVersion) {
            1    -> migrateV1ToV2(db)
            else -> {
                Log.w(TAG, "Unknown database version $oldVersion, recreating")
                db.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE")
                onCreate(db)
            }
        }
    }

    private fun migrateV1ToV2(db: SQLiteDatabase) {
        Log.i(TAG, "Migrating database from v1 (timestamp) to v2 (date string)")

        val tableCursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$HISTORY_TABLE'", null
        )
        val tableExists = tableCursor.moveToFirst()
        tableCursor.close()

        if (!tableExists) {
            Log.i(TAG, "No existing table found, creating fresh")
            db.execSQL(DATABASE_CREATE_HISTORY)
            return
        }

        val tableInfo = db.rawQuery("PRAGMA table_info($HISTORY_TABLE)", null)
        var hasTimestampColumn = false
        var hasDateColumn = false
        while (tableInfo.moveToNext()) {
            when (tableInfo.getString(1)) {
                "timestamp" -> hasTimestampColumn = true
                "date"      -> hasDateColumn = true
            }
        }
        tableInfo.close()

        if (hasDateColumn && !hasTimestampColumn) {
            Log.i(TAG, "Already on date format, nothing to do")
            return
        }

        if (!hasTimestampColumn) {
            Log.w(TAG, "Unknown schema, recreating")
            db.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE")
            db.execSQL(DATABASE_CREATE_HISTORY)
            return
        }

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ${HISTORY_TABLE}_new (
                date TEXT PRIMARY KEY,
                stepsy INT NOT NULL
            )
        """)

        val oldEntries = db.query(HISTORY_TABLE, arrayOf("timestamp", "stepsy"), null, null, null, null, null)
        var migrated = 0
        var merged = 0
        var skipped = 0

        while (oldEntries.moveToNext()) {
            val timestampMs = oldEntries.getLong(0)
            val steps = oldEntries.getInt(1)

            if (timestampMs <= 0 || steps < 0) {
                skipped++
                continue
            }

            try {
                val dateStr = snapTimestampToDate(timestampMs)
                val existing = db.query(
                    "${HISTORY_TABLE}_new", arrayOf("stepsy"),
                    "date = ?", arrayOf(dateStr),
                    null, null, null
                )
                val newSteps = if (existing.moveToFirst()) existing.getInt(0) + steps else steps
                existing.close()

                val values = ContentValues().apply {
                    put("date", dateStr)
                    put("stepsy", newSteps)
                }
                if (db.update("${HISTORY_TABLE}_new", values, "date = ?", arrayOf(dateStr)) == 0) {
                    db.insertOrThrow("${HISTORY_TABLE}_new", null, values)
                    migrated++
                } else {
                    Log.d(TAG, "Merged duplicate date $dateStr, combined steps: $newSteps")
                    merged++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating timestamp $timestampMs: ${e.message}")
                skipped++
            }
        }
        oldEntries.close()

        db.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE")
        db.execSQL("ALTER TABLE ${HISTORY_TABLE}_new RENAME TO $HISTORY_TABLE")

        Log.i(TAG, "Migration complete: $migrated rows migrated, $merged merged, $skipped skipped")
    }

    fun clearAllAndImport(entries: List<Pair<String, Int>>) {
        writableDatabase.transaction {
            execSQL("DELETE FROM $HISTORY_TABLE")
            execSQL("DELETE FROM $SLOTS_TABLE")
            execSQL("DELETE FROM $ADJUST_TABLE")
            for ((date, steps) in entries) {
                val values = ContentValues().apply {
                    put("date", date)
                    put("stepsy", steps)
                }
                insertOrThrow(HISTORY_TABLE, null, values)
            }
        }
    }

    internal fun clearAllAndInsert(
        entries: List<Pair<String, Int>>,
        slots: Map<String, IntArray>,
        adjustments: List<Triple<String, Int, Int>>,
    ) {
        writableDatabase.transaction {
            execSQL("DELETE FROM $HISTORY_TABLE")
            execSQL("DELETE FROM $SLOTS_TABLE")
            execSQL("DELETE FROM $ADJUST_TABLE")
            for ((date, steps) in entries) {
                val values = ContentValues().apply {
                    put("date", date)
                    put("stepsy", steps)
                }
                insertOrThrow(HISTORY_TABLE, null, values)
            }
            for ((date, slotArray) in slots) {
                for (slot in 0 until SLOT_COUNT) {
                    val steps = slotArray[slot]
                    if (steps <= 0) continue
                    val values = ContentValues().apply {
                        put("date", date)
                        put("slot", slot)
                        put("steps", steps)
                    }
                    insertWithOnConflict(SLOTS_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            for ((date, minute, delta) in adjustments) {
                val values = ContentValues().apply {
                    put("date", date)
                    put("minute", minute)
                    put("delta", delta)
                }
                insertOrThrow(ADJUST_TABLE, null, values)
            }
        }
    }

    internal class Entry(
        val timestamp: Long,
        val date: String,
        val steps: Int
    )

    internal class Adjustment(
        val minute: Int,
        val delta: Int
    )

    internal class SlotDay(
        val date: String,
        val slots: IntArray
    )

    companion object {
        private const val TAG = "Database"
        private const val DATABASE_NAME = "Stepsy"
        private const val DATABASE_VERSION = 2
        internal const val SLOT_MINUTES = 60
        internal const val SLOT_COUNT = 1440 / SLOT_MINUTES
        private const val HISTORY_TABLE = "History"
        private const val SLOTS_TABLE = "Slots"
        private const val ADJUST_TABLE = "Adjust"
        private const val DATABASE_CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS $HISTORY_TABLE (
                date TEXT PRIMARY KEY,
                stepsy INT NOT NULL
            )
        """
        private const val DATABASE_CREATE_SLOTS = """
            CREATE TABLE IF NOT EXISTS $SLOTS_TABLE (
                date TEXT NOT NULL,
                slot INTEGER NOT NULL,
                steps INTEGER NOT NULL,
                PRIMARY KEY(date, slot)
            ) WITHOUT ROWID
        """
        private const val DATABASE_CREATE_ADJUST = """
            CREATE TABLE IF NOT EXISTS $ADJUST_TABLE (
                date TEXT NOT NULL,
                minute INTEGER NOT NULL,
                delta INTEGER NOT NULL
            )
        """
        private const val DATABASE_CREATE_ADJUST_DATE =
            "CREATE INDEX IF NOT EXISTS ${ADJUST_TABLE}_date ON $ADJUST_TABLE(date)"

        @Volatile private var instance: Database? = null

        internal fun getInstance(context: Context): Database {
            return instance ?: synchronized(this) {
                instance ?: Database(context.applicationContext).also { instance = it }
            }
        }

        internal fun snapTimestampToDate(timestampMs: Long): String {
            val msIntoDay = timestampMs % 86_400_000L
            val snappedMs = if (msIntoDay >= 12 * 3_600_000L) {
                timestampMs + (86_400_000L - msIntoDay)
            } else {
                timestampMs - msIntoDay
            }
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = snappedMs
            }
            return "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}