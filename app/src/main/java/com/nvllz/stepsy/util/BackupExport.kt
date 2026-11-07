package com.nvllz.stepsy.util

import android.content.Context
import android.util.JsonWriter
import com.nvllz.stepsy.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal object BackupExport {

    const val FORMAT_ID = "stepsy-backup"
    const val FORMAT_VERSION = 1

    fun writeFull(context: Context, outputStream: OutputStream) {
        val db = Database.getInstance(context)
        val settingsSnapshot = runBlocking {
            AppPreferences.dataStore.data.first().asMap()
        }

        JsonWriter(BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))).use { writer ->
            writer.beginObject()

            writer.name("format").value(FORMAT_ID)
            writer.name("version").value(FORMAT_VERSION)
            writer.name("created").value(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now())
            )

            writer.name("app")
            writer.beginObject()
            writer.name("name").value(BuildConfig.VERSION_NAME)
            writer.name("code").value(BuildConfig.VERSION_CODE)
            writer.endObject()

            writer.name("history")
            writer.beginArray()
            val firstDate = db.firstEntry
            val lastDate = db.lastEntry
            if (firstDate != null && lastDate != null) {
                for (entry in db.getEntries(firstDate, lastDate)) {
                    writer.beginArray()
                    writer.value(entry.date)
                    writer.value(entry.steps)
                    writer.endArray()
                }
            }
            writer.endArray()

            writer.name("hourly")
            writer.beginObject()
            writer.name("slotMinutes").value(Database.SLOT_MINUTES)

            writer.name("slots")
            writer.beginArray()
            var slotsDate: String? = null
            var slotsDayOpen = false
            for ((date, slot, steps) in db.loadSlotsAll()) {
                if (date != slotsDate) {
                    if (slotsDayOpen) {
                        writer.endArray()
                        writer.endArray()
                    }
                    slotsDate = date
                    writer.beginArray()
                    writer.value(date)
                    writer.beginArray()
                    slotsDayOpen = true
                }
                writer.beginArray()
                writer.value(slot)
                writer.value(steps)
                writer.endArray()
            }
            if (slotsDayOpen) {
                writer.endArray()
                writer.endArray()
            }
            writer.endArray()

            writer.name("adjust")
            writer.beginArray()
            var adjustDate: String? = null
            var adjustDayOpen = false
            for ((date, minute, delta) in db.loadAdjustAll()) {
                if (date != adjustDate) {
                    if (adjustDayOpen) {
                        writer.endArray()
                        writer.endArray()
                    }
                    adjustDate = date
                    writer.beginArray()
                    writer.value(date)
                    writer.beginArray()
                    adjustDayOpen = true
                }
                writer.beginArray()
                writer.value(minute)
                writer.value(delta)
                writer.endArray()
            }
            if (adjustDayOpen) {
                writer.endArray()
                writer.endArray()
            }
            writer.endArray()

            writer.endObject()

            writer.name("settings")
            writer.beginArray()
            for ((key, value) in settingsSnapshot) {
                if (key.name in AppPreferences.EXPORT_EXCLUDED_KEYS) continue
                writer.beginObject()
                writer.name("key").value(key.name)
                when (value) {
                    is Boolean -> {
                        writer.name("type").value("bool")
                        writer.name("value").value(value)
                    }
                    is Int -> {
                        writer.name("type").value("int")
                        writer.name("value").value(value)
                    }
                    is Long -> {
                        writer.name("type").value("long")
                        writer.name("value").value(value)
                    }
                    is Float -> {
                        writer.name("type").value("float")
                        writer.name("value").value(value.toDouble())
                    }
                    is String -> {
                        writer.name("type").value("string")
                        writer.name("value").value(value)
                    }
                    is Set<*> -> {
                        writer.name("type").value("stringset")
                        writer.name("value")
                        writer.beginArray()
                        for (item in value) {
                            if (item is String) writer.value(item)
                        }
                        writer.endArray()
                    }
                }
                writer.endObject()
            }
            writer.endArray()

            writer.endObject()
        }
    }

    fun writeCsv(context: Context, outputStream: OutputStream) {
        val db = Database.getInstance(context)
        outputStream.bufferedWriter().use { writer ->
            val firstDate = db.firstEntry
            val lastDate = db.lastEntry
            if (firstDate != null && lastDate != null) {
                for (entry in db.getEntries(firstDate, lastDate)) {
                    writer.write("${entry.date},${entry.steps}\r\n")
                }
            }
            writer.flush()
        }
    }
}
