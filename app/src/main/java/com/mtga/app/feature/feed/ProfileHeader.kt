package com.mtga.app.feature.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.ui.component.VerifiedIcon
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.rememberMediaPolicy
import com.mtga.app.ui.icon.MtgaIcons
import java.util.Locale

/**
 * A profile's header, compact as Bluesky's: the banner edge to edge from the window's top (status bar shadow kept),
 * the avatar at the start overlapping it in a ring of the page's colour, Follow at the end below the banner, then at
 * the start the name, the handle, the counts, the bio, and the website, location and joined on one wrapping line.
 * The banner obeys the media policy (held: its ground colour only). [LocalReadableInset] narrows the text part only.
 * Ports:
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#ProfileHeaderStandard
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#ProfileHeaderShell
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#onPressBanner
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/DisplayName.tsx#ProfileHeaderDisplayName
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Handle.tsx#ProfileHeaderHandle
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/StatusBarShadow.tsx#StatusBarShadow
 * not ported: Shell.tsx's live status card, moderation blur (X has no labels), analytics metric, banner growth on
 * overscroll, haptics, web offsets; ProfileHeaderStandard's labeler, block, mute, subscribe and message buttons (a
 * reader has no account). The verified mark comes from the account's own posts (PostCard.kt's VerifiedIcon).
 * not ported: measureScrollMarginTop (web scroll offset)
 * not ported: onPressFollow (the follow runs through onToggleFollow)
 * not ported: onPressUnfollow (the existing Unfollow dialog in FeedScreen asks first)
 * not ported: onRequestHide (moderation, X has no labels)
 * not ported: headerStandardButtons (FollowButton is its one button, a reader has no account for the others)
 * not ported: unblockAccount (a reader has no account)
 * not ported: pin (a reader has no account)
 * not ported: pinInitialHash (web page hash)
 * not ported: watchScrollMarginTop (web scroll offset)
 * not ported: shouldUseProfileLightStatusBar (the status bar's icons are the app's, set in MainActivity)
 * not ported: profileBannerSafeAreaCoverHeight (web safe area, the inset is read from WindowInsets.statusBars)
 * not ported: ProfileHeaderDisplayName, ProfileHeaderHandle, StatusBarShadow (drawn inline in ProfileHeader)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileHeader(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit,
    onOpenBanner: (String) -> Unit,
    verified: VerifiedType = VerifiedType.NONE
) {
    val uriHandler = LocalUriHandler.current
    val hold = rememberMediaPolicy().hold
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val readable = LocalReadableInset.current
    val banner = feed?.bannerUrl
    val avatar = feed?.avatarUrl
    Box(Modifier.fillMaxWidth()) {
        // Shell.tsx onPressBanner: only a banner that exists opens the lightbox (here MediaViewer).
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .clickable(enabled = banner != null, role = Role.Image) { banner?.let(onOpenBanner) }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // The banner is a picture like any other, so Wi-Fi only holds it too.
            if (banner != null && !hold) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(ProfileBannerHeight)
                )
            }
        }
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, width 94, size 90: a ring of the page's colour around the avatar.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft + readable)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Profile picture" }
                        // Shell.tsx onPressAvi: only an avatar that exists opens the lightbox.
                        .clickable(enabled = avatar != null, role = Role.Image) { avatar?.let(onOpenAvatar) },
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = avatar, name = name ?: handle, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 12 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = ProfileBannerHeight + 8.dp, end = 16.dp + readable),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = isFollowing, onClick = onToggleFollow)
                }
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.padding(start = 16.dp + readable, end = 16.dp + readable)) {
                val websiteMax = maxWidth * 0.6f
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // DisplayName.tsx: the name wraps, capped here at 2 lines.
                        // The mark sits outside the Text, as PostCard's NameRow, so it stays after the last visible word.
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                name ?: "@$handle",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            VerifiedIcon(verified, Modifier.padding(start = 4.dp, bottom = 4.dp), size = 20.dp)
                        }
                        // Handle.tsx ProfileHeaderHandle: numberOfLines={1}.
                        if (name != null) {
                            Text(
                                "@$handle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    feed?.stats?.let { stats ->
                        if (stats.followers != null || stats.following != null || stats.posts != null) {
                            ProfileHeaderMetrics(
                                followers = stats.followers?.let(::compactCount),
                                following = stats.following?.let(::compactCount),
                                posts = stats.posts?.let(::compactCount)
                            )
                        }
                    }
                    feed?.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val site = feed?.website
                    val place = feed?.location
                    val joined = feed?.joined
                    if (site != null || place != null || joined != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // The website label: one line, at most 60 % of the width.
                            site?.let {
                                ProfileItem(
                                    LinkRounded, "Website",
                                    it.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                                    MaterialTheme.colorScheme.primary,
                                    Modifier.widthIn(max = websiteMax).clickable { uriHandler.openUri(it) },
                                    singleLine = true
                                )
                            }
                            place?.let { ProfileItem(LocationOnRounded, "Location", it) }
                            joined?.let { ProfileItem(CalendarMonthRounded, "Joined", it) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/TwidereProject/TwidereX-Android/blob/develop/common/src/commonMain/kotlin/com/twidere/twiderex/component/UserComponent.kt#ProfileItem
// an icon (16 dp here) then the text; X's app puts them in one wrapping line.
@Composable
private fun ProfileItem(
    icon: ImageVector,
    description: String,
    text: String,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = textColor,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Button.kt#textStyleFor
// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Box, no Material control.
// Filled (ContainerColor Primary, IconColor and LabelTextColor OnPrimary) or, once followed, outlined (OutlinedOutlineWidth).
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
// not ported: textStyleFor (its one case here, titleMedium from the medium height up, is the Text's style below)
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val height = ButtonMediumContainerHeight
    // ContainerShapeRound (CornerFull) morphs to PressedContainerShape (CornerMedium = ShapeDefaults.Medium) with a spring.
    val full = with(density) { height.toPx() / 2 }
    val medium = ShapeDefaults.Medium.topStart.toPx(Size(full * 2, full * 2), density)
    val corner by animateFloatAsState(
        targetValue = if (pressed) medium else full,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "followCorner"
    )
    // Same hue, alpha only: a fade to Color.Transparent would pass through grey.
    val container by animateColorAsState(if (following) colors.primary.copy(alpha = 0f) else colors.primary, tween(200), label = "followContainer")
    val line by animateColorAsState(if (following) colors.outline else colors.outline.copy(alpha = 0f), tween(200), label = "followLine")
    val contentColor by animateColorAsState(if (following) colors.primary else colors.onPrimary, tween(200), label = "followContent")
    Row(
        Modifier
            .height(height)
            // The clip is drawn through the layer: its block reads the corner (snapshot observed, no recomposition per
            // frame) and builds the shape from it, a new one each time, so the outline is made again for certain.
            .graphicsLayer {
                this.shape = RoundedCornerShape(corner)
                clip = true
            }
            // Container and outline drawn in the draw block (snapshot observed): the corner read here redraws them each
            // frame of the press spring, no recomposition. The stroke is twice the width: the clip keeps its inner half.
            .drawBehind {
                val outline = RoundedCornerShape(corner).createOutline(size, layoutDirection, this)
                drawOutline(outline, container)
                drawOutline(outline, line, style = Stroke(ButtonMediumOutlineWidth.toPx() * 2f))
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = ButtonMediumLeadingSpace),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
        // not ported: foo, bar, baz (the sample's own demo functions), simpleAnimatedContentSample (used here, inline)
        // Icon and label fade through, the width follows without clipping (SizeTransform(clip = false)).
        AnimatedContent(
            targetState = following,
            transitionSpec = {
                (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false))
            },
            label = "followLabel"
        ) { isFollowing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isFollowing) MtgaIcons.CheckRounded else PersonAddRounded,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonMediumIconSize),
                    tint = contentColor
                )
                Spacer(Modifier.width(ButtonMediumIconLabelSpace))
                // textStyleFor: titleMedium from the medium height up.
                Text(if (isFollowing) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
            }
        }
    }
}


// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp
private val ButtonMediumOutlineWidth: Dp = 1.dp

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// followers, following, posts on one line, 10 pt apart (12 dp here), a flow so 200 % wraps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeaderMetrics(followers: String?, following: String?, posts: String?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        followers?.let { MetricsItem(it, "followers") }
        following?.let { MetricsItem(it, "following") }
        posts?.let { MetricsItem(it, "posts") }
    }
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the count semi bold, its word in the contrast medium colour.
@Composable
private fun MetricsItem(primaryText: String, secondaryText: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$primaryText ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(secondaryText) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/layout.ts#PROFILE_BANNER_HEIGHT
// Shell.tsx GrowableAvatar (top 104, left 10, width 94, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

/** 870, 12,345, 566K, 53.2M: exact while short, rounded once it would not fit. */
private fun compactCount(value: Long): String = when {
    value < 10_000 -> String.format(Locale.getDefault(), "%,d", value)
    value < 1_000_000 -> "${value / 1_000}K"
    else -> {
        val millions = value / 100_000 / 10.0
        String.format(Locale.getDefault(), "%.1fM", millions).replace(".0M", "M").replace(",0M", "M")
    }
}

private val AvatarVariant = Regex("_(?:normal|bigger|mini|200x200|400x400)(?=\\.[A-Za-z0-9]+(?:$|[?&#]))")
private val BannerWithSize =
    Regex("^(https://pbs\\.twimg\\.com/profile_banners/\\d+/\\d+)(?:/[A-Za-z0-9_]+)?(?:\\?.*)?$")

// The cached copy of the X page is the site's page shell, not the documentation (the sizes are read from the archived
// copy of the same page), so its scripts are no code to port:
// not ported: _cr (page shell script)
// not ported: _createMdxContent (page shell script)
// not ported: _missingMdxReference (page shell script)
// not ported: adopt (page shell script)
// not ported: assistantScriptFn (page shell script)
// not ported: broken (page shell script)
// not ported: button (page shell script)
// not ported: disableAnimation (page shell script)
// not ported: done (page shell script)
// not ported: fits (page shell script)
// not ported: MDXContent (page shell script)
// not ported: onLoad (page shell script)
// not ported: owned (page shell script)
// not ported: pick (page shell script)
// not ported: report (page shell script)
// not ported: schedule (page shell script)
// not ported: settle (page shell script)
// not ported: smooth (page shell script)
// not ported: stop (page shell script)
// not ported: touchesChrome (page shell script)
// not ported: type (page shell script)

/**
 * The original avatar: X's documentation page "user profile images and banners" says the original is had by omitting
 * the variant ("_normal", "_bigger", "_mini", "_200x200", "_400x400") before the extension of a
 * pbs.twimg.com/profile_images URL, also when it sits percent-encoded inside a Nitter /pic/ proxy URL. Unknown
 * formats are returned unchanged.
 * https://developer.x.com/en/docs/x-api/v1/accounts-and-users/user-profile-images-and-banners
 */
internal fun originalAvatar(url: String): String {
    val known = url.contains("pbs.twimg.com/profile_images/", ignoreCase = true) ||
        url.contains("pbs.twimg.com%2Fprofile_images%2F", ignoreCase = true)
    return if (known) url.replaceFirst(AvatarVariant, "") else url
}

/**
 * The banner at its largest: the same page lists the sizes /web, /web_retina, /ipad, /ipad_retina, /mobile,
 * /mobile_retina, /300x100, /600x200 and /1500x500. A pbs.twimg.com/profile_banners/<id>/<ts> URL gets "/1500x500",
 * one with a size already has it set to /1500x500. A Nitter proxied or unknown URL is returned unchanged.
 * https://developer.x.com/en/docs/x-api/v1/accounts-and-users/user-profile-images-and-banners
 */
internal fun bannerFull(url: String): String {
    val match = BannerWithSize.matchEntire(url) ?: return url
    return match.groupValues[1] + "/1500x500"
}

private fun symbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()

// Material Symbols Rounded "person_add".
// https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml
private val PersonAddRounded: ImageVector by lazy {
    symbol(
        "PersonAdd",
        "M720,440L640,440Q623,440 611.5,428.5Q600,417 600,400Q600,383 611.5,371.5Q623,360 640,360L720,360L720,280" +
            "Q720,263 731.5,251.5Q743,240 760,240Q777,240 788.5,251.5Q800,263 800,280L800,360L880,360Q897,360 908.5,371.5" +
            "Q920,383 920,400Q920,417 908.5,428.5Q897,440 880,440L800,440L800,520Q800,537 788.5,548.5Q777,560 760,560" +
            "Q743,560 731.5,548.5Q720,537 720,520L720,440ZM360,480Q294,480 247,433Q200,386 200,320Q200,254 247,207" +
            "Q294,160 360,160Q426,160 473,207Q520,254 520,320Q520,386 473,433Q426,480 360,480ZM40,720L40,688" +
            "Q40,654 57.5,625.5Q75,597 104,582Q166,551 230,535.5Q294,520 360,520Q426,520 490,535.5Q554,551 616,582" +
            "Q645,597 662.5,625.5Q680,654 680,688L680,720Q680,753 656.5,776.5Q633,800 600,800L120,800Q87,800 63.5,776.5" +
            "Q40,753 40,720ZM120,720L600,720L600,688Q600,677 594.5,668Q589,659 580,654Q526,627 471,613.5Q416,600 360,600" +
            "Q304,600 249,613.5Q194,627 140,654Q131,659 125.5,668Q120,677 120,688L120,720ZM360,400Q393,400 416.5,376.5" +
            "Q440,353 440,320Q440,287 416.5,263.5Q393,240 360,240Q327,240 303.5,263.5Q280,287 280,320Q280,353 303.5,376.5" +
            "Q327,400 360,400Z"
    )
}

// Material Symbols Rounded "location_on".
// https://github.com/google/material-design-icons/blob/master/symbols/android/location_on/materialsymbolsrounded/location_on_24px.xml
private val LocationOnRounded: ImageVector by lazy {
    symbol(
        "LocationOn",
        "M480,774Q602,662 661,570.5Q720,479 720,408Q720,299 650.5,229.5Q581,160 480,160Q379,160 309.5,229.5Q240,299 240,408" +
            "Q240,479 299,570.5Q358,662 480,774ZM480,853Q466,853 452,848Q438,843 427,833Q362,773 312,716Q262,659 228.5,605.5" +
            "Q195,552 177.5,502.5Q160,453 160,408Q160,258 256.5,169Q353,80 480,80Q607,80 703.5,169Q800,258 800,408" +
            "Q800,453 782.5,502.5Q765,552 731.5,605.5Q698,659 648,716Q598,773 533,833Q522,843 508,848Q494,853 480,853Z" +
            "M480,480Q513,480 536.5,456.5Q560,433 560,400Q560,367 536.5,343.5Q513,320 480,320Q447,320 423.5,343.5" +
            "Q400,367 400,400Q400,433 423.5,456.5Q447,480 480,480Z"
    )
}

// Material Symbols Rounded "calendar_month".
// https://github.com/google/material-design-icons/blob/master/symbols/android/calendar_month/materialsymbolsrounded/calendar_month_24px.xml
private val CalendarMonthRounded: ImageVector by lazy {
    symbol(
        "CalendarMonth",
        "M200,880Q167,880 143.5,856.5Q120,833 120,800L120,240Q120,207 143.5,183.5Q167,160 200,160L240,160L240,120" +
            "Q240,103 251.5,91.5Q263,80 280,80Q297,80 308.5,91.5Q320,103 320,120L320,160L640,160L640,120Q640,103 651.5,91.5" +
            "Q663,80 680,80Q697,80 708.5,91.5Q720,103 720,120L720,160L760,160Q793,160 816.5,183.5Q840,207 840,240L840,800" +
            "Q840,833 816.5,856.5Q793,880 760,880L200,880ZM200,800L760,800L760,400L200,400L200,800ZM200,320L760,320L760,240" +
            "L200,240L200,320ZM480,560Q463,560 451.5,548.5Q440,537 440,520Q440,503 451.5,491.5Q463,480 480,480" +
            "Q497,480 508.5,491.5Q520,503 520,520Q520,537 508.5,548.5Q497,560 480,560ZM320,560Q303,560 291.5,548.5" +
            "Q280,537 280,520Q280,503 291.5,491.5Q303,480 320,480Q337,480 348.5,491.5Q360,503 360,520Q360,537 348.5,548.5" +
            "Q337,560 320,560ZM640,560Q623,560 611.5,548.5Q600,537 600,520Q600,503 611.5,491.5Q623,480 640,480" +
            "Q657,480 668.5,491.5Q680,503 680,520Q680,537 668.5,548.5Q657,560 640,560ZM480,720Q463,720 451.5,708.5" +
            "Q440,697 440,680Q440,663 451.5,651.5Q463,640 480,640Q497,640 508.5,651.5Q520,663 520,680Q520,697 508.5,708.5" +
            "Q497,720 480,720ZM320,720Q303,720 291.5,708.5Q280,697 280,680Q280,663 291.5,651.5Q303,640 320,640" +
            "Q337,640 348.5,651.5Q360,663 360,680Q360,697 348.5,708.5Q337,720 320,720ZM640,720Q623,720 611.5,708.5" +
            "Q600,697 600,680Q600,663 611.5,651.5Q623,640 640,640Q657,640 668.5,651.5Q680,663 680,680Q680,697 668.5,708.5" +
            "Q657,720 640,720Z"
    )
}

// Material Symbols Rounded "link".
// https://github.com/google/material-design-icons/blob/master/symbols/android/link/materialsymbolsrounded/link_24px.xml
private val LinkRounded: ImageVector by lazy {
    symbol(
        "Link",
        "M280,680Q197,680 138.5,621.5Q80,563 80,480Q80,397 138.5,338.5Q197,280 280,280L400,280Q417,280 428.5,291.5" +
            "Q440,303 440,320Q440,337 428.5,348.5Q417,360 400,360L280,360Q230,360 195,395Q160,430 160,480Q160,530 195,565" +
            "Q230,600 280,600L400,600Q417,600 428.5,611.5Q440,623 440,640Q440,657 428.5,668.5Q417,680 400,680L280,680Z" +
            "M360,520Q343,520 331.5,508.5Q320,497 320,480Q320,463 331.5,451.5Q343,440 360,440L600,440Q617,440 628.5,451.5" +
            "Q640,463 640,480Q640,497 628.5,508.5Q617,520 600,520L360,520ZM560,680Q543,680 531.5,668.5Q520,657 520,640" +
            "Q520,623 531.5,611.5Q543,600 560,600L680,600Q730,600 765,565Q800,530 800,480Q800,430 765,395Q730,360 680,360" +
            "L560,360Q543,360 531.5,348.5Q520,337 520,320Q520,303 531.5,291.5Q543,280 560,280L680,280Q763,280 821.5,338.5" +
            "Q880,397 880,480Q880,563 821.5,621.5Q763,680 680,680L560,680Z"
    )
}
