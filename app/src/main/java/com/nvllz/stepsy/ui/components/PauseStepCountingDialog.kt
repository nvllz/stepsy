package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.Util
import java.util.Calendar

@Composable
fun PauseStepCountingDialog(
    onDismiss: () -> Unit,
    onPauseForMinutes: (Int) -> Unit,
    onCustomTime: () -> Unit,
    onPauseIndefinitely: () -> Unit,
) {
    val ctx = LocalContext.current
    fun resumeAt(minutes: Int): String {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) + minutes
        return Util.timeLabel(ctx, minute)
    }

    StepsyDialog(
        title = stringResource(R.string.pause_step_counting),
        onDismiss = onDismiss,
        dismissText = stringResource(android.R.string.cancel),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PauseCard(stringResource(R.string.pause_30_minutes), resumeAt(30), Modifier.weight(1f).fillMaxHeight()) {
                    onPauseForMinutes(30)
                }
                PauseCard(stringResource(R.string.pause_1_hour), resumeAt(60), Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp)) {
                    onPauseForMinutes(60)
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PauseCard(stringResource(R.string.pause_2_hours), resumeAt(120), Modifier.weight(1f).fillMaxHeight()) {
                    onPauseForMinutes(120)
                }
                PauseCard(
                    "",
                    stringResource(R.string.pause_indefinitely),
                    Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp),
                    hero = true,
                ) {
                    onPauseIndefinitely()
                }
            }
            PauseCard(stringResource(R.string.pause_custom_time), "", Modifier.fillMaxWidth()) {
                onCustomTime()
            }
        }
    }
}

@Composable
private fun PauseCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(StepsyTheme.colors.muted)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hero) {
            Text(
                "∞",
                fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Chivo,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            )
        } else {
            Text(
                title,
                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Chivo,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            )
        }
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                fontSize = 12.sp, lineHeight = 15.sp,
                color = StepsyTheme.colors.accent, textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    lineBreak = LineBreak(
                        strictness = LineBreak.Strictness.Default,
                        wordBreak = LineBreak.WordBreak.Default,
                        strategy = LineBreak.Strategy.Balanced,
                    ),
                ),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
