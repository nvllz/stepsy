/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import androidx.core.content.edit

object BackupScheduler {
    private const val TAG = "BackupScheduler"
    private const val IMMEDIATE_BACKUP_WORK_NAME = "stepsy_immediate_backup"
    private const val IMMEDIATE_CLEANUP_WORK_NAME = "stepsy_immediate_cleanup"
    private const val PERIODIC_BACKUP_WORK_NAME = "stepsy_periodic_backup"
    private const val MANUAL_EXPORT_WORK_NAME = "stepsy_manual_export"

    fun ensureBackupScheduled(context: Context) {
        Log.d(TAG, "Ensuring backup is properly scheduled...")

        if (AppPreferences.backupFrequency == 0) {
            Log.d(TAG, "Backup disabled - cancelling all scheduled backups")
            cancelBackup(context)
            return
        }

        if (AppPreferences.backupLocationUri == null) {
            Log.d(TAG, "Backup location not set - skipping scheduling")
            return
        }

        val workManager = WorkManager.getInstance(context)

        val periodicBackupWorkInfos = workManager.getWorkInfosForUniqueWork(PERIODIC_BACKUP_WORK_NAME)
            .get()

        val needsReschedule = periodicBackupWorkInfos.isEmpty() ||
                periodicBackupWorkInfos.all {
                    it.state == WorkInfo.State.CANCELLED ||
                            it.state == WorkInfo.State.FAILED
                }

        if (needsReschedule) {
            Log.d(TAG, "Backup needs to be rescheduled")
            scheduleBackup(context)
        } else {
            Log.d(TAG, "Backup already scheduled properly")
        }
    }

    fun getNextBackupTime(context: Context): Long {
        val frequency = AppPreferences.backupFrequency
        if (frequency <= 0) return 0L

        val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("next_backup_time", 0L)
    }

    fun scheduleBackup(context: Context, immediate: Boolean = false) {
        Log.d(TAG, "Scheduling backup...")
        cancelBackup(context)

        if (AppPreferences.backupFrequency == 0) {
            Log.d(TAG, "Auto backup disabled - backup cancelled")
            return
        }

        if (AppPreferences.backupLocationUri == null) {
            Log.d(TAG, "Backup location not set - cannot schedule backup")
            return
        }

        val backupIntervalDays = AppPreferences.backupFrequency.toLong()
        val initialDelay = calculateNextBackupTime(backupIntervalDays)

        val nextRunMillis = System.currentTimeMillis() + initialDelay
        val nextRunTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(nextRunMillis))

        Log.d(TAG, "Next backup scheduled for: $nextRunTime (every $backupIntervalDays days)")

        val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putLong("next_backup_time", nextRunMillis)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        if (immediate) {
            val backupWork = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .addTag("backup")
                .build()

            val cleanupWork = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .addTag("cleanup")
                .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    IMMEDIATE_BACKUP_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    backupWork
                )
                .then(cleanupWork)
                .enqueue()
        }

        val periodicBackupWork = PeriodicWorkRequestBuilder<BackupWorker>(
            backupIntervalDays, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag("backup_and_cleanup")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicBackupWork
        )
    }

    fun scheduleManualExport(context: Context) {
        Log.d(TAG, "Scheduling manual export...")

        if (AppPreferences.backupLocationUri == null) {
            Log.d(TAG, "Backup location not set - cannot perform manual export")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val exportWork = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .addTag("manual_export")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                MANUAL_EXPORT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                exportWork
            )

        Log.d(TAG, "Manual export scheduled")
    }

    /**
     * Calculate the time to next backup based on the interval days
     * If interval is 1, schedule for next midnight
     * If interval is > 1, schedule for midnight + interval-1 days
     */
    private fun calculateNextBackupTime(intervalDays: Long): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (intervalDays > 1) {
                add(Calendar.DAY_OF_MONTH, intervalDays.toInt() - 1)
            }
        }

        val nextBackupTime = calendar.timeInMillis - now
        Log.d(TAG, "Next backup in ${nextBackupTime / (1000 * 60 * 60)} hours")
        return nextBackupTime
    }

    fun scheduleImmediateCleanup(context: Context) {
        if (AppPreferences.backupFrequency == 0) {
            Log.d(TAG, "Auto backup disabled - cannot schedule cleanup")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val cleanupWork = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .addTag("cleanup")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                IMMEDIATE_CLEANUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                cleanupWork
            )
    }

    fun cancelBackup(context: Context) {
        Log.d(TAG, "Cancelling all backup work")
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_BACKUP_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_CLEANUP_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_BACKUP_WORK_NAME)
    }
}

class BackupWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val TAG = "BackupWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting backup work at ${Date(System.currentTimeMillis())}...")
        Log.d(TAG, "Tags: $tags")

        if (AppPreferences.backupFrequency == 0 &&
            (tags.contains("backup") || tags.contains("backup_and_cleanup"))) {
            Log.d(TAG, "Auto backup disabled - skipping backup task")
            return Result.success()
        }

        return try {
            when {
                tags.contains("manual_export") -> {
                    Log.d(TAG, "Running manual export task")
                    performBackup()
                }
                tags.contains("cleanup") -> {
                    Log.d(TAG, "Running cleanup task only")
                    cleanupOldBackups()
                    Result.success()
                }
                tags.contains("backup") -> {
                    Log.d(TAG, "Running backup task only")
                    performBackup()
                }
                tags.contains("backup_and_cleanup") -> {
                    Log.d(TAG, "Running backup task followed by cleanup")
                    val backupResult = performBackup()
                    if (backupResult == Result.success()) {
                        Log.d(TAG, "Backup successful, now running cleanup")
                        cleanupOldBackups()
                        Result.success()
                    } else {
                        Log.w(TAG, "Backup failed, skipping cleanup")
                        backupResult
                    }
                }
                else -> {
                    Log.w(TAG, "Worker running with no recognized tags")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun performBackup(): Result {
        val uriString = AppPreferences.backupLocationUri
        if (uriString == null) {
            Log.e(TAG, "No backup location URI set")
            return Result.failure()
        }

        Log.d(TAG, "Backup URI: $uriString")
        val uri = uriString.toUri()
        val db = Database.getInstance(applicationContext)

        return try {
            withContext(Dispatchers.IO) {
                // 1. Verify and take permissions
                try {
                    applicationContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Permission check failed, trying to proceed anyway: ${e.message}")
                }

                // 2. Verify directory exists and is writable
                val documentDir = DocumentFile.fromTreeUri(applicationContext, uri) ?: run {
                    Log.e(TAG, "Cannot access backup directory")
                    return@withContext Result.failure()
                }

                if (!documentDir.exists()) {
                    Log.e(TAG, "Backup directory doesn't exist")
                    return@withContext Result.failure()
                }

                if (!documentDir.canWrite()) {
                    Log.e(TAG, "Cannot write to backup directory")
                    return@withContext Result.failure()
                }

                // 3. Create unique filename with timestamp
                val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
                val fileName = "stepsy_${dateFormat.format(Date())}.csv"
                Log.d(TAG, "Attempting to create backup file: $fileName")

                // 4. Try to create the file with retry logic
                var backupFile: DocumentFile? = null
                var attempts = 0
                while (backupFile == null && attempts < 3) {
                    try {
                        backupFile = documentDir.createFile("text/csv", fileName)
                        if (backupFile == null) {
                            Log.w(TAG, "Attempt ${attempts + 1}: File creation returned null")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt ${attempts + 1}: File creation failed", e)
                    }
                    attempts++
                    if (backupFile == null && attempts < 3) {
                        delay(1000)
                    }
                }

                backupFile ?: run {
                    Log.e(TAG, "Failed to create backup file after 3 attempts")
                    return@withContext Result.retry()
                }

                // 5. Write the backup data
                try {
                    applicationContext.contentResolver.openOutputStream(backupFile.uri)?.use { outputStream ->
                        outputStream.bufferedWriter().use { writer ->
                            for (entry in db.getEntries(db.firstEntry, db.lastEntry)) {
                                writer.write("${entry.timestamp},${entry.steps}\r\n")
                            }
                            writer.flush()
                            Log.d(TAG, "Backup completed successfully: ${backupFile.name}")
                        }
                    } ?: run {
                        Log.e(TAG, "Failed to open output stream")
                        return@withContext Result.retry()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing backup data", e)
                    try {
                        backupFile.delete()
                    } catch (deleteEx: Exception) {
                        Log.w(TAG, "Failed to delete partial backup", deleteEx)
                    }
                    return@withContext Result.retry()
                }
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun cleanupOldBackups() {
        val retentionCount = AppPreferences.backupRetention
        if (retentionCount <= 0) {
            Log.d(TAG, "Backup retention is disabled")
            return
        }

        val uriString = AppPreferences.backupLocationUri ?: return
        val uri = uriString.toUri()
        val documentFile = DocumentFile.fromTreeUri(applicationContext, uri) ?: return

        withContext(Dispatchers.IO) {
            try {
                val backupFiles = documentFile.listFiles()
                    .filter { file ->
                        file.isFile &&
                                file.name?.startsWith("stepsy_") == true &&
                                file.name?.endsWith(".csv") == true
                    }
                    .mapNotNull { file ->
                        val name = file.name ?: return@mapNotNull null
                        val timestamp = name.removePrefix("stepsy_").removeSuffix(".csv")
                        val parsedTime = try {
                            when {
                                "-" in timestamp -> {
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                    java.time.LocalDateTime.parse(timestamp, formatter)
                                }
                                else -> {
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                                    java.time.LocalDate.parse(timestamp, formatter).atStartOfDay()
                                }
                            }.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse timestamp from: $name", e)
                            0L
                        }
                        Pair(file, parsedTime)
                    }
                    .sortedByDescending { (_, timestamp) -> timestamp }
                    .map { (file, _) -> file }

                Log.d(TAG, "Found ${backupFiles.size} backup files, retaining $retentionCount")

                if (backupFiles.size > retentionCount) {
                    val filesToDelete = backupFiles.drop(retentionCount)
                    filesToDelete.forEach { file ->
                        try {
                            Log.d(TAG, "Attempting to delete: ${file.name}")
                            val deleted = file.delete()
                            Log.d(TAG, "Deleted old backup ${file.name}: $deleted")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting backup ${file.name}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during backup cleanup", e)
            }
        }
    }
}