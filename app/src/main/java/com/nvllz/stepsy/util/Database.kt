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
import java.util.*

/**
 * Created by tiefensuche on 19.10.16, modified by nvllz in April 2025
 */

internal class Database private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private fun query(columns: Array<String>, selection: String? = null, selectionArgs: Array<String>? = null): Number {
        val cursor = readableDatabase.query(HISTORY_TABLE, columns, selection, selectionArgs, null, null, null)
        cursor.moveToFirst()
        val result: Number = when (cursor.getType(0)) {
            FIELD_TYPE_INTEGER -> cursor.getLong(0)
            FIELD_TYPE_FLOAT -> cursor.getFloat(0)
            FIELD_TYPE_NULL -> 0
            else -> throw IllegalStateException("unexpected type")
        }
        cursor.close()
        return result
    }

    internal val firstEntry: Long
        get() = query(arrayOf("min(timestamp)")).toLong()

    internal val lastEntry: Long
        get() = query(arrayOf("max(timestamp)")).toLong()

    internal fun avgSteps(minDate: Long, maxDate: Long) =
        getSteps("avg(stepsy)", minDate, maxDate)

    internal fun getSumSteps(minDate: Long, maxDate: Long) =
        getSteps("sum(stepsy)", minDate, maxDate)

    private fun getSteps(columns: String, minDate: Long, maxDate: Long) =
        query(arrayOf(columns), "timestamp >= ? AND timestamp <= ?", arrayOf(minDate.toString(), maxDate.toString())).toInt()

    internal fun addEntry(timestamp: Long, steps: Int) {
        Log.d(TAG, "add entry to database: $timestamp, $steps")
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("stepsy", steps)
        }

        val rowsUpdated = writableDatabase.update(HISTORY_TABLE, values, "timestamp = ?", arrayOf(timestamp.toString()))
        if (rowsUpdated == 0) {
            writableDatabase.insertOrThrow(HISTORY_TABLE, null, values)
        }
    }

    internal fun getEntries(minDate: Long, maxDate: Long): List<Entry> {
        val entries = mutableListOf<Entry>()
        val cursor = readableDatabase.query(HISTORY_TABLE, null, "timestamp >= ? AND timestamp <= ?", arrayOf(minDate.toString(), maxDate.toString()), null, null, null)
        while (cursor.moveToNext()) {
            val cal = Util.calendar
            cal.timeInMillis = cursor.getLong(0)
            entries.add(Entry(cal.timeInMillis, cursor.getInt(1)))
        }
        cursor.close()
        return entries
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DATABASE_CREATE_HISTORY)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade logic yet
    }

    internal inner class Entry(val timestamp: Long, val steps: Int)

    companion object {
        private val TAG = Database::class.java.simpleName
        private const val DATABASE_NAME = "Stepsy"
        private const val DATABASE_VERSION = 1
        private const val HISTORY_TABLE = "History"
        private const val DATABASE_CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS $HISTORY_TABLE (
                timestamp long primary key, stepsy int not null
            );
        """

        private var instance: Database? = null

        internal fun getInstance(context: Context): Database {
            return instance ?: Database(context).also { instance = it }
        }
    }
}
