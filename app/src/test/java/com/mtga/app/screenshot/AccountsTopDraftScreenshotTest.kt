package com.mtga.app.screenshot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.accounts.AccountRow
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.DropdownMenuItem
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.GlassSearchField
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.ScreenBanner
import com.mtga.app.ui.component.SelectableDropdownMenuItem
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 3), nothing of app/src/main changes: the top of Accounts as Home's, as
 * LinkedOut built it (its point 12): accounts_top_now (ScreenBanner "Accounts" with the count and the Folders action, then
 * the search field), accounts_top_draft (the reader kit's FeedBanner with both sides empty, the folder MenuPill with
 * "N followed" beside it, then the field), accounts_menu_draft (the pill's menu open: All and the folders, then
 * "Manage folders" in a group of its own, Tune at its start, a chevron at its end; Home's drawn open in
 * HomeTopDraftScreenshotTest). The row is the shape of Bluesky's header:
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * Drawn as LinkedOut's AccountsScreen draws it, and LinkedOut's draft is the model:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/AccountsTopDraftScreenshotTest.kt#AccountsTopDraftScreenshotTest
 * The rows and the field are MTGA's own, private to AccountsScreen.kt, so a faithful copy of them (the rows keep their
 * folder label as today: the draft changes the top only):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/AccountsScreen.kt#AccountsScreen
 * The paste button and the folder label are drawn as boxes with the look of Material's IconButton and AssistChip (the
 * guard refuses a new Material control, even in a test): same size, shape, border and colours.
 * not ported: HomeHeaderLayoutMobile's MenuButton, the Logo, the Feeds link, haptics and soft reset, the hide on scroll,
 * StatusBarFade; AccountsScreen's view model, paste() and the clipboard, the clear button, CleanLinkEffect, the candidate
 * card, the hint, the filing dialog, the empty state, the pill's menu itself (drawn open by MenuOverScreen, Robolectric
 * draws no Popup); LinkedOut's Sample unread counts and headline (MTGA's rows carry neither), the "nobody followed" picture
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AccountsTopDraftScreenshotTest : ScreenshotBase() {

    private val now = System.currentTimeMillis()

    // The folders as AccountStore keeps them (FolderNames.ordered): Main first, the rest by name.
    private val folders = listOf("Main", "Friends", "News")

    private val rows = listOf(
        AccountRow("NASA", "NASA", null, now - 2 * 60 * 60 * 1000, "News"),
        AccountRow("BBCWorld", "BBC News (World)", null, now - 26 * 60 * 60 * 1000, "News"),
        AccountRow("github", "GitHub", null, now - 3 * 24 * 60 * 60 * 1000, "Main"),
        AccountRow("jsmith", null, null, null, "Friends")
    )

    // ScreenBanner, with the Folders action, and the search field, as AccountsScreen draws them today.
    @Test
    fun now() = capture("accounts_top_now") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(horizontal = LocalReadableInset.current)) {
                ScreenBanner(
                    title = "Accounts",
                    subtitle = "12 followed",
                    trailing = { BannerAction(icon = MtgaIcons.Folder, label = "Folders", onClick = {}) }
                )
            }
            Field()
            List()
        }
    }

    // FeedBanner as LinkedOut's AccountsScreen: both slots empty, the folder pill with the count beside it.
    @Test
    fun draft() = capture("accounts_top_draft") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Banner(onPill = {})
            Field()
            List()
        }
    }

    // The pill's menu open: the folders as a group of choices, then in a group of its own the action "Manage folders".
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
    @Test
    fun menu() = capture("accounts_menu_draft") {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            screen = {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Banner(onPill = { anchor = it })
                    Field()
                    List()
                }
            }
        ) {
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All")
                folders.forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                DropdownMenuItem(
                    text = "Manage folders",
                    onClick = {},
                    leadingIcon = { Icon(Tune, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    trailingIcon = {
                        Icon(
                            ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }

    @Composable
    private fun Banner(onPill: (IntOffset) -> Unit) {
        Column(Modifier.padding(horizontal = LocalReadableInset.current)) {
            FeedBanner(
                centre = {
                    MenuPill(
                        icon = MtgaIcons.Folder,
                        label = "All",
                        expanded = false,
                        trailingIcon = ExpandMore,
                        modifier = Modifier.onGloballyPositioned {
                            val at = it.positionInRoot()
                            onPill(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                        },
                        onClick = {}
                    )
                },
                subtitle = "12 followed"
            )
        }
    }

    // The search field as AccountsScreen builds it: search icon, the paste button at its end.
    @Composable
    private fun Field() {
        GlassSearchField(
            value = "",
            onValueChange = {},
            placeholder = "Search or open a handle",
            leadingIcon = { Icon(MtgaIcons.Search, contentDescription = null) },
            trailingIcon = {
                // Material's IconButton: a 40 dp circle, the icon 24 dp in onSurfaceVariant.
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        MtgaIcons.Paste,
                        contentDescription = "Paste a link or a handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + LocalReadableInset.current, end = 16.dp + LocalReadableInset.current, bottom = 8.dp)
        )
    }

    @Composable
    private fun List() {
        Column(
            Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { AccountCard(it) }
        }
    }

    // A faithful copy of AccountCard (AccountsScreen.kt, private), the folder label shown as the reader with folders sees it.
    @Composable
    private fun AccountCard(row: AccountRow) {
        ZoneSurface(
            onClick = {},
            shape = ShapeDefaults.ExtraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        row.name ?: "@${row.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (row.name != null) {
                        Text(
                            "@${row.handle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FolderLabel(row.folder)
                }
            }
        }
    }

    // Material's AssistChip: 32 dp high, the small shape, a hairline in outlineVariant, the icon 16 dp as AccountsScreen
    // sets it, the label in labelLarge.
    @Composable
    private fun FolderLabel(folder: String) {
        ZoneSurface(
            onClick = {},
            shape = ShapeDefaults.Small,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 140.dp)
        ) {
            Row(
                Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    MtgaIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    folder,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Material Symbols "tune" and "chevron_right", as the menu draws them in LinkedOut (LinkedOutIcons): the app's icon set has
// neither, GlassMenu.kt keeps its chevron private.
// https://github.com/google/material-design-icons/blob/master/symbols/android/tune/materialsymbolsoutlined/tune_24px.xml
// https://github.com/google/material-design-icons/blob/master/symbols/android/chevron_right/materialsymbolsoutlined/chevron_right_24px.xml
private val Tune: ImageVector by lazy {
    val pathData = "M440,840L440,600L520,600L520,680L840,680L840,760L520,760L520,840L440,840ZM120,760L120,680L360,680" +
        "L360,760L120,760ZM280,600L280,520L120,520L120,440L280,440L280,360L360,360L360,600L280,600ZM440,520L440,440" +
        "L840,440L840,520L440,520ZM600,360L600,120L680,120L680,200L840,200L840,280L680,280L680,360L600,360Z" +
        "M120,280L120,200L520,200L520,280L120,280Z"
    ImageVector.Builder("Tune", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

private val ChevronRight: ImageVector by lazy {
    val pathData = "M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"
    ImageVector.Builder("ChevronRight", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = true)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}
