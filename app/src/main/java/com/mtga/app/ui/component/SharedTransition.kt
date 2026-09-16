package com.mtga.app.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Plumbing for shared element transitions between a list and a post.
 *
 * A shared element needs two scopes that live far apart: the transition scope
 * wrapping the whole navigation graph, and the animated scope of the
 * destination being entered or left. A post card sits many layers below both,
 * inside a pager inside a list, so passing them as parameters would mean
 * threading them through every screen. They travel as composition locals
 * instead, and both default to null.
 *
 * Null is the normal case, not a failure: a card drawn outside a navigation
 * destination, in a preview or in the media viewer's own window, simply gets
 * no shared element and animates the ordinary way.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as the same thing as the element carrying [key] in the
 * destination being entered, so it flies and grows between the two instead of
 * fading out on one screen and in on the other.
 *
 * Keys carry the post id, so a card and the post opened from it match, and two
 * different posts never do. Two lists are never on screen at once, since each
 * lives in its own navigation destination, so a key cannot collide with itself.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPostElement(key: String): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(transition) {
        this@sharedPostElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animated
        )
    }
}
