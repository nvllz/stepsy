package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
) {
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(selectedIndex)

    StepsyDialog(title = title, onDismiss = onDismiss, confirmText = null, dismissText = null) {
        if (description != null) {
            Text(
                description,
                Modifier.padding(bottom = 10.dp),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = LocalTextStyle.current.copy(
                    lineBreak = LineBreak(
                        strictness = LineBreak.Strictness.Default,
                        wordBreak = LineBreak.WordBreak.Default,
                        strategy = LineBreak.Strategy.Balanced,
                    ),
                ),
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 400.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 1.dp),
        ) {
            items(options.size) { index ->
                val (value, label) = options[index]
                val active = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).border(
                            1.5.dp,
                            if (active) StepsyTheme.colors.accent
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(StepsyTheme.colors.accent))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        label, fontSize = 16.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
