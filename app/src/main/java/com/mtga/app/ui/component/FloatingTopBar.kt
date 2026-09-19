package com.mtga.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mtga.app.navigation.LocalReadableInset
import kotlin.math.roundToInt

/**
 * The top bar as a zone, not as a band.
 *
 * A rounded container with air around it, the same shape and the same margin
 * as a settings section or a post, instead of a slab welded to the edges of
 * the screen. It still folds away as the reader goes down and comes back on
 * the first upward flick.
 *
 * The bar is not given the [scrollBehavior] itself. Material's app bar reacts
 * to one by sliding its own contents, which inside a container would leave an
 * empty rounded box sitting there. The whole zone is moved here instead, its
 * own background included, so what leaves the screen is the zone and not just
 * its text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit
) {
    val state = scrollBehavior?.state

    Box(
        modifier = modifier
            // Measured with its margins, so the zone clears the screen whole
            // and no sliver of it is left under the status bar.
            .onSizeChanged { size -> state?.heightOffsetLimit = -size.height.toFloat() }
            .offset { IntOffset(0, state?.heightOffset?.roundToInt() ?: 0) }
            // Opaque, otherwise posts scroll through the gap above the zone.
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // The inset keeps the zone over the readable column on a wide
            // window. The background above is drawn before it, so it still
            // covers the full width and nothing scrolls through the margins.
            .padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            CenterAlignedTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                // The zone above already sits below the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
                // topAppBarColors, not centerAlignedTopAppBarColors: it is
                // what CenterAlignedTopAppBar itself defaults to in this
                // Material 3, checked against the reference rather than
                // remembered.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    }
}
