package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(StepsyTheme.colors.muted).padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SettingDivider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 20.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, color = ink)
            summary?.let {
                Text(
                    it, fontSize = 13.sp, lineHeight = 17.sp, color = StepsyTheme.colors.accent,
                    style = LocalTextStyle.current.copy(
                        lineBreak = LineBreak(
                            strictness = LineBreak.Strictness.Default,
                            wordBreak = LineBreak.WordBreak.Default,
                            strategy = LineBreak.Strategy.Balanced,
                        ),
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(12.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_right), null,
                tint = StepsyTheme.colors.accent.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title,
        modifier.semantics {
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        },
        summary, icon, enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            val dark = StepsyTheme.dark
            val scheme = MaterialTheme.colorScheme
            val checkedThumb = if (dark) scheme.primary else scheme.onPrimary
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = if (dark) scheme.onPrimary else scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = checkedThumb,
                    checkedTrackColor = if (dark) scheme.onSurface else scheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = StepsyTheme.colors.accent,
                    uncheckedTrackColor = scheme.onSurface.copy(alpha = 0.08f),
                    uncheckedBorderColor = scheme.onSurface.copy(alpha = 0.25f),
                    disabledCheckedThumbColor = checkedThumb.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = (if (dark) scheme.onSurface else scheme.primary).copy(alpha = 0.4f),
                    disabledUncheckedThumbColor = StepsyTheme.colors.accent.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = scheme.onSurface.copy(alpha = 0.05f),
                    disabledUncheckedBorderColor = scheme.onSurface.copy(alpha = 0.15f),
                ),
            )
        },
    )
}
