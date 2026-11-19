package com.nvllz.stepsy.ui.announcement

import android.content.Context
import com.nvllz.stepsy.util.AppPreferences

object AnnouncementManager {

    private const val STAR_MIN_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val STAR_QUIET_MS = 7L * 24 * 60 * 60 * 1000

    fun pendingKind(context: Context): Kind? {
        if (hasPendingAnnouncement()) return Kind.ANNOUNCEMENT
        if (shouldShowStarPrompt(context)) return Kind.STAR
        return null
    }

    private fun hasPendingAnnouncement(): Boolean {
        val a = AnnouncementContent.current ?: return false
        return a.id > AppPreferences.lastSeenAnnouncementVersion && a.showIf()
    }

    private fun shouldShowStarPrompt(context: Context): Boolean {
        if (AppPreferences.starPromptHandled) return false
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val now = System.currentTimeMillis()
            val quietSince = maxOf(
                info.firstInstallTime,
                info.lastUpdateTime,
                AppPreferences.lastPromptShownMs,
            )
            now - info.firstInstallTime >= STAR_MIN_AGE_MS &&
                now - quietSince >= STAR_QUIET_MS
        } catch (_: Exception) {
            false
        }
    }

    internal fun markHandled(kind: Kind) {
        AppPreferences.lastPromptShownMs = System.currentTimeMillis()
        when (kind) {
            Kind.ANNOUNCEMENT ->
                AnnouncementContent.current?.let { AppPreferences.lastSeenAnnouncementVersion = it.id }
            Kind.STAR -> AppPreferences.starPromptHandled = true
        }
    }
}
