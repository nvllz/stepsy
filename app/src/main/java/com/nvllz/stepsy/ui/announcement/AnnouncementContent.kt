package com.nvllz.stepsy.ui.announcement

import androidx.fragment.app.FragmentActivity
import com.nvllz.stepsy.BuildConfig

data class Announcement(
    val id: Int,
    val title: String,
    val body: String,
    val bodyIsHtml: Boolean = true,
    val badge: String = "",
    val showIf: () -> Boolean = { true },
    val actionLabel: String? = null,
    val onAction: ((FragmentActivity) -> Unit)? = null,
    val swipeDismiss: Boolean = true
)

object AnnouncementContent {

    val current: Announcement? = Announcement(
        id = 24,
        showIf = { !BuildConfig.HAS_PROPRIETARY_LIBRARIES && BuildConfig.VERSION_CODE <= 28 },
        swipeDismiss = false,
        title = "FOSS vs FULL",
        badge = "⚠️",
        body = """
            The <b>Activity Recognition API</b>, introduced in the previous update, helps Stepsy filter out steps counted while cycling or travelling by car or bus. After several months of testing, it's proven stable, with negligible battery impact.<br><br>

            The <b>FOSS</b> build remains unchanged for F-Droid and IzzyOnDroid, without Google dependencies. The <b>Full</b> build includes this API for more reliable step counting.<br><br>

            Both versions work entirely offline, with no Internet access or changes to your privacy. You can switch builds without uninstalling the app.<br><br>

            Have Google Play Services installed? Get <a href='https://github.com/nvllz/stepsy/releases/latest'><b><tt>app-full-release.apk</tt></b></a> from GitHub Releases, or use <a href='https://github.com/ImranR98/Obtainium'>Obtainium</a> to track the Full build. See the <a href='https://github.com/nvllz/stepsy#build-flavors'>build differences</a> for details.
        """.trimIndent()
        /*
        actionLabel = "Open app settings",
        onAction = { act ->
            act.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${act.packageName}".toUri())
            )
        }
        */
    )
}