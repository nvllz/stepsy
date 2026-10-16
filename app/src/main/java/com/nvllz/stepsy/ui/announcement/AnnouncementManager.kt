package com.nvllz.stepsy.ui.announcement

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.nvllz.stepsy.util.AppPreferences

object AnnouncementManager {

    private const val STAR_DELAY_MS = 7L * 24 * 60 * 60 * 1000

    fun showIfNeeded(activity: FragmentActivity) {
        val fm = activity.supportFragmentManager
        if (fm.isStateSaved || fm.findFragmentByTag(AnnouncementSheet.TAG) != null) return

        if (hasPendingAnnouncement()) {
            AnnouncementSheet.show(fm, AnnouncementSheet.Kind.ANNOUNCEMENT)
        } else if (shouldShowStarPrompt(activity)) {
            AnnouncementSheet.show(fm, AnnouncementSheet.Kind.STAR)
        }
    }

    private fun hasPendingAnnouncement(): Boolean {
        val a = AnnouncementContent.current ?: return false
        return a.id > AppPreferences.lastSeenAnnouncementVersion && a.showIf()
    }

    private fun shouldShowStarPrompt(context: Context): Boolean {
        if (AppPreferences.starPromptHandled) return false
        return try {
            val installed = context.packageManager
                .getPackageInfo(context.packageName, 0).firstInstallTime
            System.currentTimeMillis() - installed >= STAR_DELAY_MS
        } catch (_: Exception) {
            false
        }
    }

    internal fun markHandled(kind: AnnouncementSheet.Kind) {
        when (kind) {
            AnnouncementSheet.Kind.ANNOUNCEMENT ->
                AnnouncementContent.current?.let { AppPreferences.lastSeenAnnouncementVersion = it.id }
            AnnouncementSheet.Kind.STAR -> AppPreferences.starPromptHandled = true
        }
    }
}