package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(StepsyTheme.colors.accentOpaque)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                Modifier.size(24.dp).rotate(180f),
            )
        }
        Text(
            title,
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
