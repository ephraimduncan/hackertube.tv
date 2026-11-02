package tv.hackertube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(page: HomePage) {
    LaunchedEffect(page.areas) { page.areas.load() }
    // Until the feed arrives, "All" holds focus, or the sidebar takes it and stays shut.
    // Then focus moves on to the first video, unless the viewer has moved it.
    LaunchedEffect(page.feed) {
        snapshotFlow { page.feed.videos.isNotEmpty() }.first { it }
        if (page.focus == ALL) {
            page.focus = null
            page.needsFocus = true
        }
    }
    VideoGrid(page, page.feed, page.grid, empty = "No videos match these filters.") {
        fullWidth("wordmark") { Wordmark(Modifier.padding(start = 4.dp)) }
        fullWidth("areas") {
            // the topic areas from the site's sidebar, as YouTube's chip bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                item {
                    // one chip that cycles through the sorts, so the bar stays one row tall
                    Chip(
                        "Sort: ${page.sort.label}",
                        { page.cycleSort() },
                        Modifier.restoreFocus(page, SORT_FOCUS),
                        selected = page.sort != Sort.Best,
                    )
                }
                item {
                    Chip(
                        "All",
                        { page.show(null) },
                        Modifier.restoreFocus(page, ALL, first = page.feed.videos.isEmpty()),
                        selected = page.area == null,
                    )
                }
                items(page.areas.value.orEmpty(), key = { it.id }) { area ->
                    Chip(
                        area.name,
                        { page.show(area) },
                        Modifier.restoreFocus(page, "area:${area.id}"),
                        selected = page.area == area,
                    )
                }
            }
        }
    }
}

private const val ALL = "area:"

@Composable
fun GridScreen(page: GridPage) {
    VideoGrid(page, page.feed, page.grid, page.empty) {
        fullWidth("head") {
            PageHead(page.title, page.crumb, page.subtitle, Modifier.restoreFocus(page, "head"))
        }
    }
}

/** Four columns of video cards that load more as focus nears the end. */
@Composable
private fun VideoGrid(
    page: Page,
    feed: Feed,
    state: LazyGridState,
    empty: String,
    header: LazyGridScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    // picking the current section in the sidebar asks for the top of the page
    LaunchedEffect(page.topTick) {
        if (page.topTick > 0) page.goToTop { state.scrollToItem(0) }
    }
    LaunchedEffect(feed) {
        snapshotFlow { state.layoutInfo.run { (visibleItemsInfo.lastOrNull()?.index ?: 0) to totalItemsCount } }
            .collect { (last, total) -> if (last >= total - 12) feed.more() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header()
        itemsIndexed(feed.videos, key = { _, video -> video.vid }) { i, video ->
            VideoCard(video, Modifier.restoreFocus(page, video.vid, first = i == 0))
        }
        fullWidth("status") {
            val error = feed.error
            when {
                error != null -> Failed(error, retry = { scope.launch { feed.more() } })
                feed.loading -> Status("Loading…")
                feed.done && feed.videos.isEmpty() -> Status(empty)
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = "#$key", span = { GridItemSpan(maxLineSpan) }) { content() }
