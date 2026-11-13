package com.nvllz.stepsy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.StepsyTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> PillSelector(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    iconOf: ((T) -> Int?)? = null,
    chipPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val idx = options.indexOfFirst { it.first == selected }
    var lastIdx by remember { mutableIntStateOf(if (idx >= 0) idx else 0) }
    if (idx >= 0) lastIdx = idx

    val rects = remember { mutableStateMapOf<Int, Rect>() }
    var anchored by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    val h = remember { Animatable(0f) }
    val visible by animateFloatAsState(if (idx >= 0) 1f else 0f, tween(200), label = "pillVisible")
    val snappy = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(rects[lastIdx]) {
        val r = rects[lastIdx] ?: return@LaunchedEffect
        if (!anchored) {
            anchored = true
            x.snapTo(r.left); w.snapTo(r.width); h.snapTo(r.height)
        } else {
            coroutineScope {
                launch { x.animateTo(r.left, snappy) }
                launch { w.animateTo(r.width, snappy) }
                launch { h.animateTo(r.height, snappy) }
            }
        }
    }

    LaunchedEffect(idx, rects[idx]) {
        if (idx < 0) return@LaunchedEffect
        val target = rects[idx]?.left ?: return@LaunchedEffect
        scroll.animateScrollTo(target.roundToInt().coerceAtLeast(0))
    }

    var boxWidth by remember { mutableStateOf(Int.MAX_VALUE) }
    var rowWidth by remember { mutableStateOf(Int.MAX_VALUE) }
    Box(
        modifier.fillMaxWidth().onSizeChanged { boxWidth = it.width },
        contentAlignment = if (rowWidth <= boxWidth) Alignment.Center else Alignment.CenterStart,
    ) {
        Row(
            Modifier.onSizeChanged { rowWidth = it.width }
                .clip(CircleShape).background(StepsyTheme.colors.muted)
                .horizontalScroll(scroll).padding(4.dp)
                .drawBehind {
                    drawRoundRect(
                        onSurface, topLeft = Offset(x.value, 0f), size = Size(w.value, h.value),
                        cornerRadius = CornerRadius(h.value / 2), alpha = visible,
                    )
                },
        ) {
            options.forEachIndexed { i, (value, label) ->
                val active = i == idx
                val ink by animateColorAsState(
                    if (active) StepsyTheme.colors.background else StepsyTheme.colors.accent,
                    tween(200), label = "pillInk",
                )
                val chipModifier = Modifier
                    .onGloballyPositioned {
                        val left = it.positionInParent().x
                        rects[i] = Rect(left, 0f, left + it.size.width, it.size.height.toFloat())
                    }
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Tab,
                        onClick = { onSelect(value) },
                        onLongClick = onLongClick,
                    )
                    .semantics { this.selected = active }
                    .padding(chipPadding)
                val chipIcon = iconOf?.invoke(value)
                if (chipIcon != null) {
                    Icon(
                        painterResource(chipIcon), contentDescription = label,
                        tint = ink, modifier = chipModifier.size(20.dp),
                    )
                } else {
                    Text(
                        label, fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = ink, maxLines = 1,
                        modifier = chipModifier,
                    )
                }
            }
        }
    }
}
