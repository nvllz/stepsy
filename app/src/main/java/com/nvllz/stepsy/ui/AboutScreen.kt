package com.nvllz.stepsy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.ScreenHeader
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme

private const val URL_REPO = "https://github.com/nvllz/stepsy"
private const val URL_RELEASES = "$URL_REPO/releases"
private const val URL_ISSUES = "$URL_REPO/issues"
private const val URL_AUTHOR = "https://github.com/nvllz"
private const val URL_ORIGINAL = "https://github.com/0xf4b1/motionmate"
private const val URL_LICENSE = "https://www.gnu.org/licenses/gpl-3.0.html"

private val APP_ICON = R.drawable.ic_launcher_foreground_mono

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = StepsyTheme.colors
    val cs = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    fun open(url: String) { runCatching { uriHandler.openUri(url) } }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.about_stepsy), onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(cs.onSurface.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(APP_ICON), null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.requiredSize(106.dp),
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.padding(top = 16.dp),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Chivo,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .background(c.accentOpaque)
                        .border(1.dp, c.accent.copy(alpha = 0.3f), CircleShape)
                        .clickable(role = Role.Button) { open(URL_RELEASES) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent,
                )
            }

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.about_source_code),
                    summary = stringResource(R.string.about_source_code_summary),
                    icon = R.drawable.ic_about_code,
                    onClick = { open(URL_REPO) },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.about_report_bug),
                    icon = R.drawable.ic_about_bug,
                    onClick = { open(URL_ISSUES) },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.about_author),
                    summary = "nvllz",
                    icon = R.drawable.ic_about_author,
                    onClick = { open(URL_AUTHOR) },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.about_original_creator),
                    summary = stringResource(R.string.about_original_creator_summary),
                    icon = R.drawable.ic_bulb,
                    onClick = { open(URL_ORIGINAL) },
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.about_license),
                    summary = stringResource(R.string.about_license_summary),
                    icon = R.drawable.ic_about_license,
                    onClick = { open(URL_LICENSE) },
                )
            }

            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}