package com.nvllz.stepsy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun <T> ToggleGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().clip(CircleShape).background(StepsyTheme.colors.accentOpaque).padding(4.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            val fill by animateColorAsState(if (active) onSurface else Color.Transparent, label = "toggle")
            val ink by animateColorAsState(if (active) StepsyTheme.colors.background else onSurface, label = "toggleInk")
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape).background(fill).clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, color = ink, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
        }
    }
}
