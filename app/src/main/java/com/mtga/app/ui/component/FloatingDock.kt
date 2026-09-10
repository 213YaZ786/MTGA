package com.mtga.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Space the floating dock covers at the bottom of the screen. Scrolling
 * screens add it to their bottom padding so their last item is not hidden.
 */
val LocalDockPadding = staticCompositionLocalOf { 0.dp }

/** Height of the dock plus the gap under it, for [LocalDockPadding]. */
val DockClearance: Dp = 96.dp

data class DockItem(val icon: ImageVector, val label: String)

/**
 * A floating pill with one icon per tab. The highlight follows the finger
 * while swiping between tabs, so the dock and the pages always agree.
 *
 * [position] is the current page plus how far the swipe has moved toward the
 * next one, from 0 to the last index.
 */
@Composable
fun FloatingDock(
    items: List<DockItem>,
    position: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemWidth = 64.dp
    val itemHeight = 48.dp
    val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
    // No extra animation here: the pager already animates taps, and while
    // swiping the highlight must stay exactly under the finger.
    val animated = position

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Box(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .offset { IntOffset((animated * itemWidthPx).roundToInt(), 0) }
                    .size(itemWidth, itemHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
            Row {
                items.forEachIndexed { index, item ->
                    val closeness = (1f - abs(animated - index)).coerceIn(0f, 1f)
                    val tint = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        closeness
                    )
                    Box(
                        modifier = Modifier
                            .size(itemWidth, itemHeight)
                            .clip(CircleShape)
                            .clickable(onClickLabel = item.label, role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = tint)
                    }
                }
            }
        }
    }
}
