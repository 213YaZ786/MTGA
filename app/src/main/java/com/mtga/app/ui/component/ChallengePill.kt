package com.mtga.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.AppError
import com.mtga.app.ui.icon.MtgaIcons

/**
 * Asks for a bot check, and keeps asking until it is done.
 *
 * It floats at the bottom of the screen rather than sitting in the list. The
 * same request used to be a card at the top of the feed, which a reader who
 * had scrolled never saw, so a stalled timeline looked like an empty one. The
 * whole point of this control is that scroll position cannot hide it.
 *
 * Filled primary rather than a tint: this is the one thing on screen that
 * needs a tap before the feed can move again.
 */
@Composable
fun ChallengePill(
    challenge: AppError.ChallengeRequired?,
    onVerify: (AppError.ChallengeRequired) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // The last check asked about, kept so the pill still reads correctly while
    // it fades out. Without it the label would blank the instant the check
    // passes, before the exit animation has run.
    var last by remember { mutableStateOf<AppError.ChallengeRequired?>(null) }
    LaunchedEffect(challenge) { if (challenge != null) last = challenge }
    val shown = challenge ?: last ?: return

    AnimatedVisibility(
        visible = challenge != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            onClick = {
                // Firm, the way starting a selection is. This one interrupts.
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onVerify(shown)
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(MtgaIcons.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    "Do the check for ${shown.host}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
