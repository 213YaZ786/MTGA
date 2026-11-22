package com.mtga.app.feature.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.LocalDockPadding
import com.mtga.app.ui.component.ScrollUpButton
import com.mtga.app.ui.component.rememberHaptics
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.glassFloating
import kotlin.math.roundToInt
import org.koin.compose.koinInject

/**
 * The way from one folder to another, as the yaz apps place their main
 * action: a large round pane of glass washed with the accent, bending the
 * list under it. A tap opens the folders; held, it lifts (a little larger,
 * a firm tick) and follows the finger anywhere over Home, and stays where it
 * is let go, kept for next time. Until moved it sits at the thumb's side,
 * just above the dock.
 *
 * The way back to the top travels with it, stacked above it (below it when
 * it was put near the top), so the two still read as one stack.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun FolderButton(
    folderIcon: ImageVector,
    upIcon: ImageVector,
    onClick: () -> Unit,
    showBackToTop: Boolean,
    onBackToTop: () -> Unit
) {
    val haptics = rememberHaptics()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val inset = LocalReadableInset.current
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis

    // Pops in when Home appears rather than being there already.
    val entrance = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = LocalDockPadding.current + 12.dp)
    ) {
        val side = with(density) { FolderButtonSize.toPx() }
        val roomX = (constraints.maxWidth - side).coerceAtLeast(0f)
        val roomY = (constraints.maxHeight - side).coerceAtLeast(0f)
        // At the right edge of the readable column, where the thumb is.
        val usual = with(density) { Offset((roomX - (4.dp + inset).toPx()).coerceAtLeast(0f), (roomY - 4.dp.toPx()).coerceAtLeast(0f)) }
        val saved = if (settings.folderButtonX >= 0f) Offset(settings.folderButtonX * roomX, settings.folderButtonY * roomY) else null
        var dragging by remember { mutableStateOf<Offset?>(null) }
        var pressed by remember { mutableStateOf(false) }
        // The live place is read only in the offset lambdas (a State), never in composition; after a release the
        // placed value stays in [dragging] while [settling], until the settings carry it (no snap back for a frame).
        // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
        var settling by remember { mutableStateOf(false) }
        val resting = rememberUpdatedState(saved ?: usual)
        fun at(): Offset = dragging ?: resting.value
        val lifted by remember { derivedStateOf { dragging != null && !settling } }
        val lift by animateFloatAsState(
            when {
                lifted -> 1.14f
                pressed -> 0.92f
                else -> 1f
            },
            spring(dampingRatio = 0.5f, stiffness = 500f),
            label = "lift"
        )

        // Above the folder button when there is room for it, else below.
        val upSize = with(density) { 48.dp.toPx() }
        val gap = with(density) { 10.dp.toPx() }
        // Held until the settings carry the placed value, then let go.
        LaunchedEffect(settings.folderButtonX, settings.folderButtonY, settling) {
            if (settling) {
                if (saved == null || abs(saved.x - at().x) >= 1f || abs(saved.y - at().y) >= 1f) delay(500)
                dragging = null
                settling = false
            }
        }
        // Only the flip between above and below is animated (0 above, 1 below), never the place itself, which
        // follows the finger at once; read in the offset lambda.
        val below by remember { derivedStateOf { at().y < upSize + gap } }
        val flip = animateFloatAsState(if (below) 1f else 0f, spring(dampingRatio = 1f, stiffness = 400f), label = "upFlip")
        ScrollUpButton(
            visible = showBackToTop,
            icon = upIcon,
            onClick = onBackToTop,
            modifier = Modifier.offset {
                val upX = at().x + (side - upSize) / 2f
                val upY = at().y - upSize - gap + flip.value * (side + 2f * gap + upSize)
                IntOffset(upX.roundToInt(), upY.roundToInt())
            }
        )

        val shape = CircleShape
        val base = Modifier
            .offset { IntOffset(at().x.roundToInt(), at().y.roundToInt()) }
            .size(FolderButtonSize)
            .graphicsLayer {
                val scale = lift * entrance.value
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
        Box(
            contentAlignment = Alignment.Center,
            modifier = when {
                look != null && backdrop != null -> base.glassFloating(backdrop, shape, look, tint = look.accentTint, lens = 1.4f)
                else -> base.background(MaterialTheme.colorScheme.primaryContainer)
            }
                .semantics {
                    role = Role.Button
                    contentDescription = "Choose which folder to read"
                    onClick {
                        onClick()
                        true
                    }
                }
                .pointerInput(roomX, roomY) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        val up = withTimeoutOrNull(longPress) { waitForUpOrCancellation() }
                        pressed = false
                        if (up != null) {
                            haptics.firm()
                            onClick()
                            return@awaitEachGesture
                        }
                        // Held: picked up, it follows the finger.
                        haptics.firm()
                        settling = false
                        var where = at()
                        dragging = where
                        drag(down.id) { change ->
                            val d = change.positionChange()
                            change.consume()
                            where = Offset((where.x + d.x).coerceIn(0f, roomX), (where.y + d.y).coerceIn(0f, roomY))
                            dragging = where
                        }
                        haptics.tick()
                        val placed = where
                        store.update {
                            it.copy(
                                folderButtonX = if (roomX > 0f) placed.x / roomX else 1f,
                                folderButtonY = if (roomY > 0f) placed.y / roomY else 1f
                            )
                        }
                        settling = true
                    }
                }
        ) {
            Icon(folderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
    }
}

private val FolderButtonSize = 64.dp
