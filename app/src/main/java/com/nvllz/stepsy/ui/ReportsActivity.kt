package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.ReportKind
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class ReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val boot = runBlocking { ReportBoot.load(applicationContext) }
        val bootRange = parseBootRange(intent)

        setContent {
            StepsyTheme {
                ReportsScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    boot = boot,
                    bootKind = bootRange?.first,
                    bootStartIso = bootRange?.second,
                    bootEndIso = bootRange?.third,
                )
            }
        }
    }

    private fun parseBootRange(intent: Intent?): Triple<ReportKind, String?, String?>? {
        val kindName = intent?.getStringExtra(EXTRA_KIND)
        val start = intent?.getStringExtra(EXTRA_START)?.let { parseDate(it) }
        val end = intent?.getStringExtra(EXTRA_END)?.let { parseDate(it) }
        val kind = kindName?.let { name ->
            ReportKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        val (s, e) = if (start != null && end != null && end < start) end to start else start to end
        return when {
            kind == ReportKind.CUSTOM || (kind == null && s != null) ->
                if (s == null || e == null) null else Triple(ReportKind.CUSTOM, s.toString(), e.toString())
            kind != null -> Triple(kind, s?.toString(), e?.toString())
            else -> null
        }
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    companion object {
        const val EXTRA_KIND = "reports:kind"
        const val EXTRA_START = "reports:start"
        const val EXTRA_END = "reports:end"

        fun intent(
            context: Context,
            kind: ReportKind? = null,
            start: LocalDate? = null,
            end: LocalDate? = null,
        ): Intent {
            val i = Intent(context, ReportsActivity::class.java)
            if (kind != null) i.putExtra(EXTRA_KIND, kind.name.lowercase())
            if (start != null) i.putExtra(EXTRA_START, start.toString())
            if (end != null) i.putExtra(EXTRA_END, end.toString())
            return i
        }
    }
}
