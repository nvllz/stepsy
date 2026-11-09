package com.nvllz.stepsy.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.ChoiceDialog
import com.nvllz.stepsy.ui.components.ImportWarningDialog
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.ScreenHeader
import com.nvllz.stepsy.ui.components.SectionLabel
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.components.ToastKind
import com.nvllz.stepsy.ui.components.ToastState
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.BackupExport
import com.nvllz.stepsy.util.BackupImport
import com.nvllz.stepsy.util.BackupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BackupScreen"

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()

    val storedFrequency by prefsState(
        { AppPreferences.backupFrequencyFlow() },
        { AppPreferences.backupFrequency },
    )
    val storedRetention by prefsState(
        { AppPreferences.backupRetentionFlow() },
        { AppPreferences.backupRetention },
    )
    val storedLocationUri by prefsState(
        { AppPreferences.backupLocationUriFlow() },
        { AppPreferences.backupLocationUri },
    )

    var pendingFrequency by remember { mutableStateOf<Int?>(null) }
    var pendingRetention by remember { mutableStateOf<Int?>(null) }
    val frequency = pendingFrequency ?: storedFrequency
    val retention = pendingRetention ?: storedRetention

    var resumeTick by remember { mutableIntStateOf(0) }
    var scheduleTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationUsable = remember(storedLocationUri, resumeTick) {
        val uriString = storedLocationUri
        uriString != null && ctx.contentResolver.persistedUriPermissions.any {
            it.uri == Uri.parse(uriString) && it.isWritePermission
        }
    }
    val locationSet = storedLocationUri != null && locationUsable

    LaunchedEffect(storedLocationUri, resumeTick) {
        val uriString = storedLocationUri
        if (uriString == null || locationUsable) return@LaunchedEffect
        Log.d(TAG, "Backup folder access denied or revoked - clearing location and disabling auto backup")
        BackupScheduler.cancelBackup(ctx)
        AppPreferences.dataStore.edit { prefs ->
            if (prefs[AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI] == uriString) {
                prefs.remove(AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI)
                prefs[AppPreferences.PreferenceKeys.BACKUP_FREQUENCY] = "0"
            }
        }
    }

    var choosingFrequency by rememberSaveable { mutableStateOf(false) }
    var pendingFrequencyLocation by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportFormat by remember { mutableStateOf(BackupImport.Format.FULL) }
    var pendingExportFull by rememberSaveable { mutableStateOf(false) }

    val frequencyOptions = remember(ctx) {
        val entries = ctx.resources.getStringArray(R.array.backup_frequency_entries)
        val values = ctx.resources.getStringArray(R.array.backup_frequency_values)
        entries.zip(values) { label, value -> value.toInt() to label }
    }
    val frequencyLabel =
        frequencyOptions.firstOrNull { it.first == frequency }?.second ?: frequency.toString()

    val retentionAll = stringResource(R.string.backup_retention_all)
    val retentionOptions = remember(retentionAll) {
        listOf(0 to retentionAll) + (1..5).map { it to it.toString() }
    }

    val backupLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val picked = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && picked != null) {
            scope.launch {
                val wantedFrequencyDialog = pendingFrequencyLocation
                pendingFrequencyLocation = false
                val applied = updateBackupLocation(ctx, picked)
                if (applied) {
                    BackupScheduler.ensureBackupScheduled(ctx)
                    scheduleTick++
                    if (wantedFrequencyDialog) {
                        choosingFrequency = true
                    }
                } else if (wantedFrequencyDialog) {
                    toast.show(ctx.getString(R.string.backup_location_required), ToastKind.ERROR)
                }
            }
        } else if (pendingFrequencyLocation) {
            pendingFrequencyLocation = false
            toast.show(ctx.getString(R.string.backup_location_required), ToastKind.ERROR)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch {
                    val format = withContext(Dispatchers.IO) { BackupImport.detectFormat(ctx, uri) }
                    when (format) {
                        BackupImport.Format.FULL -> {
                            pendingImportUri = uri
                            pendingImportFormat = BackupImport.Format.FULL
                        }
                        BackupImport.Format.CSV -> {
                            pendingImportUri = uri
                            pendingImportFormat = BackupImport.Format.CSV
                        }
                        BackupImport.Format.PATH -> {
                            pendingImportUri = uri
                            pendingImportFormat = BackupImport.Format.PATH
                        }
                        BackupImport.Format.PASEO -> {
                            pendingImportUri = uri
                            pendingImportFormat = BackupImport.Format.PASEO
                        }
                        BackupImport.Format.UNSUPPORTED ->
                            toast.show(ctx.getString(R.string.import_unsupported), ToastKind.ERROR)
                        BackupImport.Format.INVALID ->
                            toast.show(ctx.getString(R.string.import_invalid_file), ToastKind.ERROR)
                    }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch {
                    withContext(Dispatchers.IO) { exportToUri(ctx, uri, toast, pendingExportFull) }
                }
            }
        }
    }

    fun promptFullExport() {
        pendingExportFull = true
        val fileName = "stepsy_${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
            storedLocationUri?.let { uriString ->
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(uriString))
            }
        }
        exportLauncher.launch(intent)
    }

    fun promptLegacyExport() {
        pendingExportFull = false
        val fileName = "stepsy_${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
            storedLocationUri?.let { uriString ->
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(uriString))
            }
        }
        exportLauncher.launch(intent)
    }

    fun promptImport() {
        importLauncher.launch(BackupImport.pickerIntent())
    }

    fun promptBackupLocation(openFrequencyAfterwards: Boolean) {
        pendingFrequencyLocation = openFrequencyAfterwards
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        backupLocationLauncher.launch(intent)
    }

    fun importRequested(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    BackupImport.importAndClear(ctx, uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    BackupImport.Result.Failed
                }
            }
            when (result) {
                is BackupImport.Result.Failed ->
                    toast.show(ctx.getString(R.string.cannot_open_file), ToastKind.ERROR)
                is BackupImport.Result.Unsupported ->
                    toast.show(ctx.getString(R.string.import_unsupported), ToastKind.ERROR)
                is BackupImport.Result.Imported -> {
                    val todayNote = if (result.todaySteps > 0)
                        ctx.getString(R.string.today_steps_set, result.todaySteps) else ""
                    toast.show(
                        ctx.getString(R.string.import_result, result.rows, result.failed, todayNote),
                        ToastKind.SUCCESS,
                    )
                    restartApp(ctx)
                }
            }
        }
    }

    fun selectRetention(value: Int) {
        if (value == retention) return
        pendingRetention = value
        scope.launch {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.BACKUP_RETENTION_COUNT] = value
            }
            if (frequency > 0) BackupScheduler.scheduleImmediateCleanup(ctx)
            pendingRetention = null
        }
    }

    val locationDisplayPath = remember(storedLocationUri) {
        storedLocationUri?.let { uriString ->
            try {
                DocumentsContract.getTreeDocumentId(Uri.parse(uriString)).substringAfter(':', "")
            } catch (_: Exception) {
                ""
            }
        }
    }
    val locationSummary = locationDisplayPath ?: stringResource(R.string.backup_location_not_set)

    val nextBackupParts = remember(resumeTick, scheduleTick, frequency, locationSet) {
        val time = BackupScheduler.getNextBackupTime(ctx)
        if (frequency > 0 && locationSet && time > 0L) {
            val formattedDate = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
                .format(Date(time))
            val formattedTime = android.text.format.DateFormat.getTimeFormat(ctx).format(time)
            formattedDate to formattedTime
        } else {
            null
        }
    }

    val frequencySummary = when (val parts = nextBackupParts) {
        null -> frequencyLabel
        else -> stringResource(R.string.next_backup_summary, frequencyLabel, parts.first, parts.second)
    }

    Column(
        Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.header_data_backup), onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionLabel(stringResource(R.string.section_import))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.section_import),
                    summary = stringResource(R.string.import_data_summary),
                    icon = R.drawable.ic_import,
                    onClick = ::promptImport,
                )
            }

            SectionLabel(stringResource(R.string.section_export))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.backup_frequency),
                    summary = frequencySummary,
                    icon = R.drawable.ic_frequency,
                    onClick = {
                        if (locationSet) {
                            choosingFrequency = true
                        } else {
                            promptBackupLocation(openFrequencyAfterwards = true)
                        }
                    },
                )

                AnimatedVisibility(
                    visible = frequency > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        Modifier.padding(start = 56.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                    ) {
                        Text(
                            stringResource(R.string.backup_retention_count),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PillSelector(
                            options = retentionOptions,
                            selected = retention.takeIf { it in 0..5 },
                            onSelect = ::selectRetention,
                        )
                    }
                }

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.backup_location),
                    summary = locationSummary,
                    icon = R.drawable.ic_location,
                    onClick = { promptBackupLocation(openFrequencyAfterwards = false) },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.manual_backup),
                    summary = stringResource(R.string.backup_all_summary),
                    icon = R.drawable.ic_export,
                    onClick = ::promptFullExport,
                )
            }

            SectionLabel(stringResource(R.string.section_legacy))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.legacy_export_title),
                    summary = stringResource(R.string.legacy_export_summary),
                    icon = R.drawable.ic_export_csv,
                    onClick = ::promptLegacyExport,
                )
            }

            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (choosingFrequency) {
        ChoiceDialog(
            title = stringResource(R.string.backup_frequency),
            options = frequencyOptions,
            selected = frequency,
            onSelect = { value ->
                choosingFrequency = false
                if (value != frequency) {
                    pendingFrequency = value
                    scope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.BACKUP_FREQUENCY] = value.toString()
                        }
                        BackupScheduler.cancelBackup(ctx)
                        BackupScheduler.scheduleBackup(ctx)
                        scheduleTick++
                        Log.d(TAG, "Backup rescheduled with new frequency: $value days")
                        pendingFrequency = null
                    }
                }
            },
            onDismiss = { choosingFrequency = false },
        )
    }

    if (pendingImportUri != null) {
        ImportWarningDialog(
            message = if (pendingImportFormat == BackupImport.Format.FULL) {
                stringResource(R.string.import_warning_full)
            } else {
                stringResource(R.string.import_warning_csv)
            },
            onConfirm = {
                val uri = pendingImportUri
                pendingImportUri = null
                uri?.let { importRequested(it) }
            },
            onDismiss = { pendingImportUri = null },
        )
    }
}

@Composable
private fun <T> prefsState(flow: () -> Flow<T>, initial: () -> T): State<T> {
    val remembered = remember { flow() }
    return remembered.collectAsStateWithLifecycle(initialValue = remember { initial() })
}

private suspend fun updateBackupLocation(ctx: Context, uri: Uri): Boolean {
    return try {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        AppPreferences.dataStore.edit { prefs ->
            prefs[AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI] = uri.toString()
        }

        val displayPath = try {
            DocumentsContract.getTreeDocumentId(uri).substringAfter(':', "")
        } catch (_: Exception) { "" }

        Log.d(TAG, "Backup location set to: $displayPath (URI: $uri)")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error setting backup location URI permissions", e)
        false
    }
}

private fun exportToUri(ctx: Context, uri: Uri, toast: ToastState, full: Boolean) {
    try {
        val stream = ctx.contentResolver.openOutputStream(uri)
        if (stream == null) {
            Log.e(TAG, "Could not open output stream for export")
            toast.show(ctx.getString(R.string.cannot_open_file), ToastKind.ERROR)
            return
        }
        stream.use { outputStream ->
            if (full) BackupExport.writeFull(ctx, outputStream) else BackupExport.writeCsv(ctx, outputStream)
        }
        toast.show(ctx.getString(R.string.manual_backup_successful), ToastKind.SUCCESS)
    } catch (e: Exception) {
        Log.e(TAG, "Manual export failed", e)
        toast.show(ctx.getString(R.string.cannot_open_file), ToastKind.ERROR)
    }
}

private fun restartApp(ctx: Context) {
    val intent = Intent(ctx, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    ctx.startActivity(intent)
    (ctx as? Activity)?.finishAffinity()
}
