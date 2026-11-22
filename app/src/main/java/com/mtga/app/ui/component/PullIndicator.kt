package com.mtga.app.ui.component

import androidx.compose.ui.graphics.Color
import com.mtga.app.ui.glass.glassFloating
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.LocalGlass
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
 * The pull to refresh that shows the load as a ring of feathers at the screen's centre. The content does not move:
 * while refreshing it is blurred (16 dp) and takes no tap, as Neko's PullRefresh does with its blurBackground; the
 * ring (168 dp, FeatherRing) stands over it, its feathers coming in with the pull (state.distanceFraction) and then
 * filling as the load goes ([progress], 0 to 1). [progress] null means no count: the ring runs round on its own, as
 * one account. [count] (done, total) is the text in the middle, "30 of 40" and "accounts".
 *
 * The hand feels the threshold as in PullIndicator (the same effect).
 *
 * Sources: Modifier.pullToRefresh on a Box, the indicator outside the content driven by state.distanceFraction, from androidx's
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/PullToRefreshSamples.kt#PullToRefreshLinearProgressIndicatorSample
 * and https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#pullToRefresh
 * (PullToRefreshBox's Box with the modifier, then content, then indicator); the blur and the disabled click from
 * https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
 * (Size.medium is 16 dp, https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/theme/Constants.kt#Size);
 * the haptics from https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PullIndicator.kt#PullIndicator.
 * not ported: PullIndicator (kept as it is for the other readers; its disc, glass, fade-in alpha and translation are not wanted here)
 * not ported: pullToRefresh (Material's own modifier is called, not copied)
 * not ported: PullToRefreshLinearProgressIndicatorSample's Scaffold, TopAppBar, Refresh action, LazyColumn and LinearProgressIndicator (the screen's own list and the ring take their place)
 * not ported: PullRefresh's enabled and onRefresh null branch (the callers always refresh), its PullToRefreshBox (Modifier.pullToRefresh is used on a Box), the black dim under API 31 (minSdk is 31), WavyLinearIndicator (the ring takes its place)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRevealBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
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
    // not ported: animateToHidden (the pull's own settling, Material's modifier does it)
    // not ported: animateToThreshold (the pull's own settling, Material's modifier does it)
    // not ported: snapTo (the state's own, the fill uses Animatable.snapTo)
    val bezelView = remember { MBBackgroundView() }
    val fill = remember { Animatable(0f) }
    val currentProgress by rememberUpdatedState(progress)
    // The bezelView of the ring comes in when the refresh starts and goes out after it ends, from where it stands
    // (animateIn, MBProgressHUD: https://github.com/jdg/MBProgressHUD/blob/master/MBProgressHUD.m#animateIn). The fill
    // is an Animatable moved by animateTo on the load, as PullToRefresh's state moves its distance on the pull:
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#PullToRefreshState
    LaunchedEffect(isRefreshing) {
        animateRing(bezelView, fill, isRefreshing, if (progress == null) null else ({ currentProgress?.invoke() ?: 0f }))
    }
    // The last count, kept while the ring leaves: the screen's own count is gone when the load is over.
    var kept by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(isRefreshing) { if (isRefreshing) kept = null }
    LaunchedEffect(count) { if (count != null) kept = count }
    // The ring is composed only while something of it is on screen: its infinite motion never runs at rest.
    val ringOn by remember { derivedStateOf { refreshing || state.distanceFraction > 0f || bezelView.alpha.value > 0f } }
    Box(
        modifier.pullToRefresh(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            threshold = threshold
        )
    ) {
        // PullRefresh's blur on the content, as a layer effect read while drawing (the radius follows the ring's alpha):
        // https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val blur = BlurRadius.toPx() * bezelView.alpha.value
                    if (blur > 0.5f) {
                        clip = true
                        renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                    }
                }
        ) {
            content()
        }
        // The content takes no tap while it is blurred, and gets them back as soon as the refresh is over.
        if (isRefreshing) Scrim()
        if (ringOn) {
            PullRing(state, bezelView, fill, progress != null, kept, countLabel, lastUpdated, isRefreshing, Modifier.align(Alignment.Center))
        }
    }
}

/**
 * A transparent layer over the content that eats taps, as androidx's Scrim does with its dismissModifier
 * (pointerInput and detectTapGestures); TalkBack does not read through it (clearAndSetSemantics). Drags are not taken.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Scrim.kt#Scrim
 * not ported: Scrim's colour, alpha animation and onClick dismissal
 */
@Composable
private fun Scrim(modifier: Modifier = Modifier) {
    val dismissModifier = Modifier.pointerInput(Unit) { detectTapGestures { } }.clearAndSetSemantics { }
    Box(modifier.fillMaxSize().then(dismissModifier))
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

/** Neko's Size.medium, how much the content is blurred while refreshing. */
private val BlurRadius = 16.dp

/**
 * The ring at the centre: its feathers come in with the pull, then fill with [progress], or turn once round on their
 * own when there is none (one account).
 * Source: PullToRefreshLinearProgressIndicatorSample drives its indicator from state.distanceFraction.
 * not ported: PullIndicator, pullToRefresh (see PullRevealBox)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRing(
    state: PullToRefreshState,
    bezelView: MBBackgroundView,
    fill: Animatable<Float, AnimationVector1D>,
    counted: Boolean,
    count: Pair<Int, Int>?,
    countLabel: String,
    lastUpdated: String?,
    refreshing: Boolean,
    modifier: Modifier = Modifier
) {
    // The whole ring shows by the pull or by the zoom, the more of the two, and scales from small as it shows; every
    // value is read while drawing.
    val zoom = modifier.graphicsLayer {
        val shown = max(LinearOutSlowInEasing.transform(state.distanceFraction.coerceIn(0f, 1f)), bezelView.alpha.value)
        alpha = shown.coerceIn(0f, 1f)
        val scale = max(0.5f + 0.5f * shown.coerceIn(0f, 1f), bezelView.transform.value)
        scaleX = scale
        scaleY = scale
    }
    // While pulled and not yet refreshing (the ring's zoom not begun), the last update is what the centre says, as
    // MJRefresh's header shows lastUpdatedTimeLabel under its state: "Updated" over the age (ageForms' second form).
    // https://github.com/CoderMJLee/MJRefresh/blob/master/MJRefresh/Custom/Header/MJRefreshStateHeader.m#lastUpdatedTimeLabel
    // not ported: MJRefreshStateHeader's stateLabel and titles per state, lastUpdatedTimeText's date formats, the stored key
    val lastUpdatedTimeLabel by remember { derivedStateOf { !refreshing && lastUpdated != null && bezelView.alpha.value == 0f } }
    val centre: @Composable ColumnScope.() -> Unit = {
        if (lastUpdatedTimeLabel && lastUpdated != null) {
            Text(
                "Updated",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                ageForms(lastUpdated)[1],
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        } else if (count != null) {
            Text(
                "${count.first} of ${count.second}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                countLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    if (counted) {
        // The fill starts at 0 with the load: the pull only shows the faint track.
        FeatherRing(progress = { fill.value }, modifier = zoom, centre = centre)
    } else {
        // One account: the indeterminate motion, its arc growing from nothing as the ring starts to run.
        FeatherRing(modifier = zoom, intro = { bezelView.alpha.value }, centre = centre)
    }
}
