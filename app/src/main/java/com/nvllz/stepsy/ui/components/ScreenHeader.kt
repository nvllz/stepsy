package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onBack) {
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
