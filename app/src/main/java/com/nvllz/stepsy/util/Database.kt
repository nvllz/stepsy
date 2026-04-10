/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

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

    /** Overwrites the step count for the given date. Used by MotionService for live tracking. */
    internal fun addEntry(date: String, steps: Int) {
        val values = ContentValues().apply {
            put("date", date)
            put("stepsy", steps)
        }
        if (writableDatabase.update(HISTORY_TABLE, values, "date = ?", arrayOf(date)) == 0) {
            writableDatabase.insertOrThrow(HISTORY_TABLE, null, values)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DATABASE_CREATE_HISTORY)
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
            for ((date, steps) in entries) {
                val values = ContentValues().apply {
                    put("date", date)
                    put("stepsy", steps)
                }
                insertOrThrow(HISTORY_TABLE, null, values)
            }
        }
    }

    internal class Entry(
        val timestamp: Long,
        val date: String,
        val steps: Int
    )

    companion object {
        private const val TAG = "Database"
        private const val DATABASE_NAME = "Stepsy"
        private const val DATABASE_VERSION = 2
        private const val HISTORY_TABLE = "History"
        private const val DATABASE_CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS $HISTORY_TABLE (
                date TEXT PRIMARY KEY,
                stepsy INT NOT NULL
            )
        """

        private var instance: Database? = null

        internal fun getInstance(context: Context): Database {
            return instance ?: Database(context.applicationContext).also { instance = it }
        }

        /**
         * Converts a legacy unix-ms "midnight local" timestamp to a yyyy-MM-dd string
         * without relying on the current device timezone.
         *
         * The old schema stored the start of each local day (midnight) as a unix
         * millisecond timestamp. That timestamp is always within ±12 h of a UTC
         * midnight boundary. Snapping to the nearest UTC midnight and reading the
         * UTC date therefore always recovers the intended calendar date, regardless
         * of what timezone the device is currently in or whether DST has changed.
         *
         * DST transitions (±1 h) may produce two legacy rows that snap to the same
         * date; callers are responsible for merging those (summing steps).
         */
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