/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.*

/**
 * Database to store step data when a day is over.
 *
 * Created by tiefensuche on 19.10.16.
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
        get() {
            return query(arrayOf("min(timestamp)")).toLong()
        }


    internal val lastEntry: Long
        get() {
            return query(arrayOf("max(timestamp)")).toLong()
        }

    internal fun avgSteps(minDate: Long, maxDate: Long) = getSteps("avg(stepsy)", minDate, maxDate)

    internal fun getSumSteps(minDate: Long, maxDate: Long) = getSteps("sum(stepsy)", minDate, maxDate)

    private fun getSteps(columns: String, minDate: Long, maxDate: Long) = query(arrayOf(columns),
        "timestamp >= ? AND timestamp <= ?", arrayOf(minDate.toString(), maxDate.toString())).toInt()

    internal fun addEntry(timestamp: Long, steps: Int) {
        Log.d(TAG, "add entry to database: $timestamp, $steps")
        val values = ContentValues()
        values.put("timestamp", timestamp)
        values.put("stepsy", steps)

        // Check if the entry already exists
        val rowsUpdated = writableDatabase.update(HISTORY_TABLE, values, "timestamp = ?", arrayOf(timestamp.toString()))

        // If no rows were updated (entry didn't exist), insert a new one
        if (rowsUpdated == 0) {
            writableDatabase.insertOrThrow(HISTORY_TABLE, null, values)
        }
    }

    internal fun getEntries(minDate: Long, maxDate: Long): List<Entry> {
        val entries = ArrayList<Entry>()
        val cursor = readableDatabase.query(HISTORY_TABLE, null, "timestamp >= ? AND timestamp <= ?", arrayOf(minDate.toString(), maxDate.toString()), null, null, null)
        while (cursor.moveToNext()) {
            val cal = Util.calendar
            cal.timeInMillis = cursor.getLong(0)
            entries.add(Entry(cal.timeInMillis, cursor.getInt(1)))
        }
        cursor.close()
        return entries
    }

    internal fun setSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        val rowsUpdated = writableDatabase.update(
            SETTINGS_TABLE, values, "key = ?", arrayOf(key)
        )
        if (rowsUpdated == 0) {
            writableDatabase.insertOrThrow(SETTINGS_TABLE, null, values)
        }
    }

    @SuppressLint("Range")
    internal fun getSetting(key: String, defaultValue: String): String {
        val cursor = readableDatabase.query(
            SETTINGS_TABLE,
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null
        )
        cursor.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex("value"))
            }
        }
        return defaultValue
    }


    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_CREATE_HISTORY)
        sqLiteDatabase.execSQL(DATABASE_CREATE_SETTINGS)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, i: Int, i1: Int) {
        // no-op
    }

    internal inner class Entry internal constructor(val timestamp: Long, val steps: Int)

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

        private const val SETTINGS_TABLE = "Settings"
        private const val DATABASE_CREATE_SETTINGS = """
            CREATE TABLE IF NOT EXISTS $SETTINGS_TABLE (
                `key` TEXT PRIMARY KEY, 
                value TEXT NOT NULL
            );
        """

        private var instance: Database? = null

        internal fun getInstance(context: Context): Database {
            var instance = instance
            if (instance == null) {
                instance = Database(context)
                Companion.instance = instance
            }
            return instance
        }
    }
}
