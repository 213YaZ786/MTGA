package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.FloatingPane
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 22), nothing of app/src/main changes: the age beside the folder pill.
 * banner_folder_now is FeedBanner as it is: when the pill and "Updated just now" do not fit side by side the age drops
 * under the pill ("il suffit que je change de folder pour que le message repasse juste en dessous"). banner_folder_draft
 * is always one line: the pill keeps its icon, its chevron and its label (a label over 12 characters ends with an
 * ellipsis, "Journalists..."), the age gives way first in shorter forms of the same words ("Updated 3d ago", "3d ago",
 * "3d"), and only when even "3d" does not fit is the label cut further (3 characters at least). Four rows, as
 * TimelineScreen's banner (the alert at the left, Search at the right): "All", "main", "Journalists and newsrooms", each
 * three days after its update, then "main" just updated.
 * [FeedBannerDraft] and [MenuPillDraft] are private copies of the app's own (internal, same module) with the one change:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/FeedBanner.kt#FeedBanner
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/GlassMenu.kt#MenuPill
 * The age in shorter forms is AOSP's abbreviated relative time (FORMAT_ABBREV_RELATIVE: the same age in a shorter form when
 * space is short), see [getRelativeTimeSpanString]:
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/text/format/DateUtils.java#getRelativeTimeSpanString
 * not ported: FeedBanner's under-the-centre branch (the draft has no such case), the folder menu itself (GlassMenu),
 * TimelineScreen's view model, the failure dialog and the list
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class BannerFolderDraftScreenshotTest : ScreenshotBase() {

    private val labels = listOf("All", "main", "Journalists and newsrooms")

    @Test
    fun now() = capture("banner_folder_now") {
        Rows(labels) { label ->
            FeedBanner(
                centre = {
                    MenuPill(icon = MtgaIcons.Folder, label = label, expanded = false, trailingIcon = ExpandMore, onClick = {})
                },
                subtitle = "Updated just now",
                leading = { Alert() },
                trailing = { Search() }
            )
        }
    }

    @Test
    fun draft() = capture("banner_folder_draft") { DraftRows() }

    // The draft at 200 % text and on a 360 dp phone: banner_folder_draft_edge_font200 and banner_folder_draft_edge_narrow.
    // The font scale set and restored around the capture as Roborazzi's captureRoboImage does, the device by qualifiers as
    // Now in Android's captureMultiDevice
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("banner_folder_draft") { DraftRows() }

    // The three folders updated three days ago (the real form, relativeTime's "3d"), then "main" updated just now.
    @Composable
    private fun DraftRows() {
        val now = FIXED_NOW
        val rows = labels.map { it to now - 3 * DAY_IN_MILLIS } + ("main" to now)
        Rows(rows) { (label, time) ->
            FeedBannerDraft(
                label = label,
                subtitle = ageForms(time, now),
                centre = { MenuPillDraft(icon = MtgaIcons.Folder, label = it, trailingIcon = ExpandMore) },
                leading = { Alert() },
                trailing = { Search() }
            )
        }
    }

    @Composable
    private fun <T> Rows(items: List<T>, banner: @Composable (T) -> Unit) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEach { banner(it) }
        }
    }

    @Composable
    private fun Alert() = BannerAction(
        icon = WarningRounded,
        label = "What went wrong",
        onClick = {},
        container = MaterialTheme.colorScheme.errorContainer,
        tint = MaterialTheme.colorScheme.error
    )

    @Composable
    private fun Search() = BannerAction(icon = MtgaIcons.Search, label = "Search saved posts", onClick = {})
}

// FeedBanner with one change: the centre and the age are chosen together, so the pair is always on one line. Both slots keep
// their room, as in the app. The pair is measured as candidates (SubcomposeLayout): the pill with its label capped and the
// age longest form first; if none fits, the shortest age is kept and the label is cut shorter and shorter, to 3 characters.
// [subtitle] holds the same words from the longest form to the shortest.
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/FeedBanner.kt#FeedBanner
@Composable
private fun FeedBannerDraft(
    label: String,
    subtitle: List<String>,
    centre: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        SubcomposeLayout(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) { constraints ->
            val gap = 12.dp.roundToPx()
            val unbounded = Constraints()
            val centres = HashMap<String, Placeable>()
            val subtitles = HashMap<String, Placeable>()
            // Each candidate is composed and measured once, at the width it asks for.
            fun centreItem(text: String) = centres.getOrPut(text) {
                subcompose("centre:$text") { centre(text) }.first().measure(unbounded)
            }
            fun subtitleText(text: String) = subtitles.getOrPut(text) {
                subcompose("subtitle:$text") {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }.first().measure(unbounded)
            }
            fun fits(text: String, age: String) =
                centreItem(text).width + gap + subtitleText(age).width <= constraints.maxWidth

            val cuts = labelCuts(label)
            // The age gives way first; the label only when the shortest age is not enough.
            val age = subtitle.firstOrNull { fits(cuts.first(), it) } ?: subtitle.last()
            val text = if (fits(cuts.first(), age)) cuts.first() else cuts.firstOrNull { fits(it, age) } ?: cuts.last()
            val centreChosen = centreItem(text)
            val subtitleChosen = subtitleText(age)
            val height = maxOf(centreChosen.height, subtitleChosen.height)
            val width = centreChosen.width + gap + subtitleChosen.width
            layout(constraints.maxWidth, height) {
                val start = ((constraints.maxWidth - width) / 2).coerceAtLeast(0)
                centreChosen.place(start, (height - centreChosen.height) / 2)
                subtitleChosen.place(start + centreChosen.width + gap, (height - subtitleChosen.height) / 2)
            }
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

// The label as the pill shows it, longest first: a label over 12 characters ends with an ellipsis ("Journalists..."), then
// cut shorter by one character, down to its first 3 ("All" is never cut).
private fun labelCuts(label: String): List<String> {
    fun cut(length: Int) = if (label.length <= length) label else label.take(length).trimEnd() + "…"
    return (LabelCap downTo LabelMin).map { cut(it) }.distinct()
}

private const val LabelCap = 12
private const val LabelMin = 3

// The age in forms of one meaning, the longest first: "Updated 3d ago", "3d ago", "3d" (just now: "Updated just now",
// "just now", "now"), each one asked of getRelativeTimeSpanString with its flag.
private fun ageForms(time: Long, now: Long): List<String> =
    listOf(0, FORMAT_ABBREV_RELATIVE, FORMAT_ABBREV_ALL).map { getRelativeTimeSpanString(time, now, MINUTE_IN_MILLIS, it) }

// DateUtils.getRelativeTimeSpanString(startTime): the span since startTime, the minimum timespan to report one minute.
// https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/text/format/DateUtils.java#getRelativeTimeSpanString
private fun getRelativeTimeSpanString(startTime: Long): String =
    getRelativeTimeSpanString(startTime, FIXED_NOW, MINUTE_IN_MILLIS)

// DateUtils.getRelativeTimeSpanString(time, now, minResolution): no flag, the full words.
private fun getRelativeTimeSpanString(time: Long, now: Long, minResolution: Long): String =
    getRelativeTimeSpanString(time, now, minResolution, 0)

// DateUtils.getRelativeTimeSpanString(time, now, minResolution, flags): "Updated 3d ago" in full; FORMAT_ABBREV_RELATIVE
// drops the first word ("3d ago"), FORMAT_ABBREV_ALL the last one too ("3d"). The number is the app's own relativeTime
// ("3d", "5h", "12m"); a span under minResolution is "just now" (relativeTime's "now").
// not ported: the Context overloads (a date and a time of day past a day, formatDateRange, the year, the preposition: Home
// only writes how long ago), the future ("In 42 minutes": an update is never ahead), RelativeDateTimeFormatter's locales
private fun getRelativeTimeSpanString(time: Long, now: Long, minResolution: Long, flags: Int): String {
    val span = Math.abs(now - time)
    val abbreviated = flags and (FORMAT_ABBREV_RELATIVE or FORMAT_ABBREV_ALL) != 0
    val all = flags and FORMAT_ABBREV_ALL != 0
    val result = if (span < minResolution) {
        if (all) "now" else if (abbreviated) "just now" else "Updated just now"
    } else {
        val age = relativeTime(time)
        if (all) age else if (abbreviated) "$age ago" else "Updated $age ago"
    }
    return result
}

private const val FORMAT_ABBREV_RELATIVE = 0x40000
private const val FORMAT_ABBREV_ALL = 0x80000
private const val MINUTE_IN_MILLIS = 60_000L
private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

// MenuPill with one change: the label arrives already cut, so the pill never drops a part (one line, the width it asks for).
@Composable
private fun MenuPillDraft(icon: ImageVector, label: String, trailingIcon: ImageVector? = null) {
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        FloatingPane(
            shape = CircleShape,
            onClick = {},
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = "Collapsed"
            }
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// FeedBanner's BannerGap and BannerActionSize (private there).
private val BannerGap = 8.dp
private val BannerActionSize = 44.dp
