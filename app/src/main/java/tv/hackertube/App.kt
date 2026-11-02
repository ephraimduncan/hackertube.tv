package tv.hackertube

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
val LocalLibrary = staticCompositionLocalOf<Library> { error("No Library") }

/**
 * The sidebar of hackertube.org, with Search moved in from the top bar.
 * Like YouTube, the icon is outlined, and filled for the current section.
 */
enum class Section(val label: String, @DrawableRes val icon: Int, @DrawableRes val currentIcon: Int) {
    Search("Search", R.drawable.ic_search, R.drawable.ic_search_filled),
    Home("Home", R.drawable.ic_home, R.drawable.ic_home_filled),
    Longs("Longs", R.drawable.ic_smart_display, R.drawable.ic_smart_display_filled),
    Liked("Liked", R.drawable.ic_thumb_up, R.drawable.ic_thumb_up_filled),
    History("History", R.drawable.ic_history, R.drawable.ic_history_filled),
    Later("Watch later", R.drawable.ic_schedule, R.drawable.ic_schedule_filled),
    Categories("Categories", R.drawable.ic_grid_view, R.drawable.ic_grid_view_filled),
    Playlists("Playlists", R.drawable.ic_playlist_play, R.drawable.ic_playlist_play_filled),
}

@Composable
fun App(library: Library) {
    val context = LocalContext.current
    val nav = remember { Navigator(context, library) }
    BackHandler(nav.stack.size > 1) { nav.back() }
    CompositionLocalProvider(LocalNavigator provides nav, LocalLibrary provides library) {
        ModalNavigationDrawer(drawerContent = { value ->
            val open = value == DrawerValue.Open
            val current = remember { FocusRequester() }
            Column(
                Modifier.fillMaxHeight().background(Colors.bg).padding(12.dp).selectableGroup()
                    // entering the sidebar lands on the current section, not the nearest one
                    .focusProperties { onEnter = { current.requestFocus() } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Logo(open)
                Spacer(Modifier.height(12.dp))
                for (section in Section.entries) {
                    val isCurrent = section == nav.section
                    SidebarItem(
                        section,
                        isCurrent,
                        open,
                        onClick = { nav.go(section) },
                        modifier = if (isCurrent) Modifier.focusRequester(current) else Modifier,
                    )
                }
            }
        }) {
            // the collapsed drawer sits on top of the content's left edge
            Box(Modifier.fillMaxSize().background(Colors.bg).padding(start = 80.dp)) {
                key(nav.page) {
                    when (val page = nav.page) {
                        is HomePage -> HomeScreen(page)
                        is GridPage -> GridScreen(page)
                        is SearchPage -> SearchScreen(page)
                        is CategoriesPage -> CategoriesScreen(page)
                        is PlaylistsPage -> PlaylistsScreen(page)
                        is PlaylistPage -> PlaylistScreen(page)
                        is DetailsPage -> DetailsScreen(page)
                    }
                }
            }
        }
    }
}

class Navigator(private val context: Context, private val library: Library) {
    private val home = HomePage(library)
    val stack = mutableStateListOf<Page>(home)
    val page get() = stack.last()

    /** The sidebar entry the current page belongs to. */
    var section by mutableStateOf(Section.Home)
        private set

    fun open(page: Page) {
        stack += page
    }

    fun go(section: Section) {
        // picking the section that is already showing means "take me back to the top"
        val again = section == this.section
        this.section = section
        stack.removeRange(1, stack.size)
        val fresh = pageFor(section)
        if (fresh != null) {
            stack += fresh
            page.needsFocus = true
        } else if (again) {
            page.scrollToTop()
        } else {
            page.needsFocus = true
        }
    }

    fun back() {
        stack.removeAt(stack.lastIndex)
        if (stack.size == 1) section = Section.Home
        page.needsFocus = true
    }

    fun play(video: Video) {
        library.markSeen(video.vid)
        context.playOnYouTube(video.vid)
    }

    private fun pageFor(section: Section): Page? = when (section) {
        Section.Home -> null
        Section.Search -> SearchPage()
        Section.Longs -> GridPage.filtered(
            "Longs", mapOf("dur" to "xlong"), subtitle = "Every video over an hour. The opposite of Shorts.",
        )
        Section.Liked -> GridPage.saved(
            "Liked videos", library.liked, "Videos you like will show up here, and shape your Home feed.",
        )
        Section.History -> GridPage.saved("History", library.seen, "Videos you open will show up here.")
        Section.Later -> GridPage.saved(
            "Watch later", library.later, "Hold OK on a video, then choose Watch later to save it here.",
        )
        Section.Categories -> CategoriesPage()
        Section.Playlists -> PlaylistsPage()
    }
}

/**
 * One sidebar entry. The icon keeps the same place whether the drawer is open or closed,
 * in line with the logo above it.
 */
@Composable
private fun SidebarItem(
    section: Section,
    current: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (open) 220.dp else 56.dp, label = "sidebar item width")
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Colors.bg2 else Color.Transparent,
            contentColor = Colors.text,
            focusedContainerColor = Colors.text,
            focusedContentColor = Colors.bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (current) section.currentIcon else section.icon),
                contentDescription = section.label,
                modifier = Modifier.size(24.dp),
            )
            if (open) {
                Text(
                    section.label,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

/** The orange "H" of the site's icon, with the wordmark beside it while the drawer is open. */
@Composable
private fun Logo(open: Boolean) {
    Row(
        Modifier.padding(start = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(Colors.accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = Colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (open) {
            Spacer(Modifier.size(12.dp))
            Wordmark()
        }
    }
}
