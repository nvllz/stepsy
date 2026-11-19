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
        title = "FOSS vs Regular",
        badge = "⚠️",
        body = """
            The <b>Activity Recognition API</b>, introduced a few months ago, helps Stepsy to filter out steps while cycling or travelling by car or bus. After several months of testing, it's proven stable, with negligible battery impact.<br><br>

            The <b>FOSS</b> build remains unchanged for F-Droid and IzzyOnDroid, without Google dependencies. The <b>regular</b> build includes this API for more reliable step counting.<br><br>

            Both versions work entirely offline, without internet access or changes to your privacy. You can switch builds without uninstalling the app.<br><br>

            Have Google Play Services installed? Get <a href='https://github.com/nvllz/stepsy/releases/latest'><b><tt>Stepsy-2.0.0.apk</tt></b></a> from GitHub Releases, or use <a href='https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/{"id":"com.nvllz.stepsy","url":"https://github.com/nvllz/stepsy","author":"nvllz","name":"Stepsy","additionalSettings":"{\"apkFilterRegEx\":\"^Stepsy-[0-9].*[.]apk$\",\"appName\":\"Stepsy\",\"appAuthor\":\"nvllz\",\"about\":\"Reliable step counter.\"}"}'>Obtainium</a> to track the Full build. See the <a href='https://github.com/nvllz/stepsy#build-flavors'>build differences</a> for details.
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