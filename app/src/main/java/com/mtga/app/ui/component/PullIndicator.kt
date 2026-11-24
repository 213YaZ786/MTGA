package com.mtga.app.ui.component

import androidx.compose.ui.graphics.Color
import com.mtga.app.ui.glass.glassFloating
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.LocalGlass
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The pull to refresh indicator, with the app's own loading mark in place of
 * the spinner: the mark follows the pull, as far as it has gone, then plays
 * on its own while the refresh runs. Placed like the standard indicator,
 * sliding down from the top edge of its box in a small raised disc.
 *
 * The hand feels the threshold: a crisp tick when the pull has gone far
 * enough to refresh on release, a faint one if it is brought back short.
 * Only the reader's own drag does that, not the indicator settling or
 * leaving on its own.
 *
 * Each app draws its mark in LoadingMark, with the same parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val threshold = PullToRefreshDefaults.PositionalThreshold
    val haptics = rememberHaptics()
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        var reached = false
        snapshotFlow { (state.distanceFraction >= 1f) to (state.isAnimating || refreshing) }
            .collect { (over, settling) ->
                if (!settling && over != reached) haptics.threshold(over)
                reached = over
            }
    }
    // The pull is read in the layer and in the mark's draw, never in composition.
    Box(
        modifier.graphicsLayer {
            val fraction = state.distanceFraction
            translationY = fraction * threshold.toPx() - this.size.height
            alpha = if (isRefreshing) 1f else (fraction * 2f).coerceIn(0f, 1f)
        }
    ) {
        // In glass, the disc bends the list under it, with no shadow.
        val look = LocalGlass.current
        val backdrop = LocalGlassBackdrop.current
        val glass = look != null && backdrop != null
        Surface(
            shape = CircleShape,
            color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (glass) 0.dp else 3.dp,
            modifier = if (glass) Modifier.size(56.dp).glassFloating(backdrop!!, CircleShape, look!!) else Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                LoadingMark(size = 44.dp, running = isRefreshing, progress = { state.distanceFraction.coerceIn(0f, 1f) })
            }
        }
    }
}

/**
 * The pull to refresh that shows only the app's mark: LoadingMark (the falling feathers) at 72 dp, straight in the layer,
 * no disc, glass, shadow, blur, ring, count or "Updated". The list does not move: the mark slides down over it from the
 * top edge and lands just under the folder capsule, over the first post, at the threshold and while refreshing. Its alpha
 * comes from the pull and is held at 1 while refreshing.
 *
 * The hand feels the threshold as in PullIndicator (the same effect).
 *
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#IndicatorBox
 * (PullToRefreshBox's Box with Modifier.pullToRefresh, then content, then the indicator driven by state.distanceFraction)
 * not ported: IndicatorBox's drawWithContent clipRect, shape, containerColor, background and shadowElevation (the mark has none)
 * not ported: pullToRefresh (Material's own modifier is called, not copied)
 * not ported: IndicatorBox's SpinnerContainerSize (the mark's own 72 dp takes its place)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRevealBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    // progress, count, countLabel and lastUpdated are ignored since the ring and its count left the pull (point 43): the
    // signature stays so callers of the other readers still compile.
    progress: (() -> Float)? = null,
    count: Pair<Int, Int>? = null,
    countLabel: String = "accounts",
    lastUpdated: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val threshold = PullToRefreshDefaults.PositionalThreshold
    val haptics = rememberHaptics()
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        var reached = false
        snapshotFlow { (state.distanceFraction >= 1f) to (state.isAnimating || refreshing) }
            .collect { (over, settling) ->
                if (!settling && over != reached) haptics.threshold(over)
                reached = over
            }
    }
    // The mark is composed only while something of it is on screen: its infinite motion never runs at rest.
    val markOn by remember { derivedStateOf { refreshing || state.distanceFraction > 0f } }
    Box(
        modifier.pullToRefresh(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            threshold = threshold
        )
    ) {
        content()
        if (markOn) {
            PullMarkBox(
                distanceFraction = { state.distanceFraction },
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
            ) {
                LoadingMark(size = 72.dp, running = isRefreshing, progress = { state.distanceFraction.coerceIn(0f, 1f) })
            }
        }
    }
}

// The distance at which the mark's foot lands: the banner's 64 dp, the mark's 72 dp and 4 dp of air, so at the threshold
// the mark sits just under the folder capsule, over the first post card.
private val PullMaxDistance = 140.dp

// IndicatorBox of PullToRefresh.kt without its shape, background and elevation: the box places itself with a layer,
// translationY = distanceFraction * maxDistance - height, read in the layer and not in composition. The alpha comes from
// the pull (fraction * 2, clamped) and is 1 while refreshing.
@Composable
private fun PullMarkBox(
    distanceFraction: () -> Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    maxDistance: Dp = PullMaxDistance,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(
                        0,
                        0,
                        layerBlock = {
                            translationY = distanceFraction() * maxDistance.roundToPx() - size.height
                            alpha = if (isRefreshing) 1f else (distanceFraction() * 2f).coerceIn(0f, 1f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** MBProgressHUDAnimation: Zoom picks ZoomIn or ZoomOut by the direction. */
internal enum class MBProgressHUDAnimation { Zoom, ZoomIn, ZoomOut }

/** The HUD's bezelView: its alpha and its transform (a scale), read while drawing. */
internal class MBBackgroundView {
    val alpha = Animatable(0f)
    val transform = Animatable(0.5f)
}

/**
 * UIView's animateWithDuration:0.3 delay:0. usingSpringWithDamping:1.f initialSpringVelocity:0.f: a critically damped
 * spring that settles in about 0.3 s (stiffness (2 pi / 0.3) squared, rounded).
 */
private val animateWithDuration = spring<Float>(dampingRatio = 1f, stiffness = 400f)

/**
 * Zooms the bezelView in or out from where it stands now (UIViewAnimationOptionBeginFromCurrentState).
 * https://github.com/jdg/MBProgressHUD/blob/master/MBProgressHUD.m#animateIn
 * not ported: backgroundView's alpha
 */
internal suspend fun animateIn(
    bezelView: MBBackgroundView,
    animatingIn: Boolean,
    type: MBProgressHUDAnimation = MBProgressHUDAnimation.Zoom,
    completion: (Boolean) -> Unit = {}
) {
    var type = type
    // Automatically determine the correct zoom animation type
    if (type == MBProgressHUDAnimation.Zoom) {
        type = if (animatingIn) MBProgressHUDAnimation.ZoomIn else MBProgressHUDAnimation.ZoomOut
    }

    val small = 0.5f
    val large = 1.5f

    // Set starting state
    if (animatingIn && bezelView.alpha.value == 0f && type == MBProgressHUDAnimation.ZoomIn) {
        bezelView.transform.snapTo(small)
    } else if (animatingIn && bezelView.alpha.value == 0f && type == MBProgressHUDAnimation.ZoomOut) {
        bezelView.transform.snapTo(large)
    }

    // Perform animations
    coroutineScope {
        val transform = when {
            animatingIn -> 1f
            type == MBProgressHUDAnimation.ZoomIn -> large
            else -> small
        }
        launch { bezelView.transform.animateTo(transform, animateWithDuration) }
        val alpha = if (animatingIn) 1f else 0f
        bezelView.alpha.animateTo(alpha, animateWithDuration)
    }
    completion(true)
}

/**
 * The ring over a load: coming in, the bezelView zooms in and the fill follows [progress] in 300 ms, never going back
 * down within one load; going out, the fill runs to the end, then the bezelView zooms out. The fill is an Animatable
 * moved by animateTo, as PullToRefresh's state moves its distance on the pull:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#PullToRefreshState
 * Still used by the connection check's ring (DiagnosticsScreen), no longer by the pull.
 * not ported: animateToHidden (the pull's own settling, Material's modifier does it)
 * not ported: animateToThreshold (the pull's own settling, Material's modifier does it)
 * not ported: snapTo (the state's own, the fill uses Animatable.snapTo)
 */
internal suspend fun animateRing(
    bezelView: MBBackgroundView,
    fill: Animatable<Float, AnimationVector1D>,
    animatingIn: Boolean,
    progress: (() -> Float)?
) = coroutineScope {
    if (animatingIn) {
        if (bezelView.alpha.value == 0f) fill.snapTo(0f)
        launch { animateIn(bezelView, animatingIn = true) }
        if (progress != null) {
            snapshotFlow { progress() }.collectLatest { target ->
                fill.animateTo(max(fill.value, target.coerceIn(0f, 1f)), tween(300, easing = FastOutSlowInEasing))
            }
        }
    } else if (bezelView.alpha.value > 0f) {
        if (progress != null) fill.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        animateIn(bezelView, animatingIn = false)
        fill.snapTo(0f)
    }
}
