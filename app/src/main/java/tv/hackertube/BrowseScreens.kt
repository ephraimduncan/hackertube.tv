package tv.hackertube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(page: CategoriesPage) {
    val nav = LocalNavigator.current
    Loaded(page.areas) { areas ->
        PageColumn(page, page.list) {
            item(key = "#head") {
                Column {
                    PageHead("Categories", modifier = Modifier.restoreFocus(page, "head"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        quickLinks.forEachIndexed { i, (title, filters) ->
                            Pill(
                                title,
                                onClick = { nav.open(GridPage.filtered(title, filters)) },
                                modifier = Modifier.restoreFocus(page, "quick:$title", first = i == 0),
                            )
                        }
                    }
                }
            }
            items(areas, key = { it.id }) { area ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(area.name, style = Type.section)
                        Text(count(area.count), style = Type.sub)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (category in area.categories) {
                            Chip(
                                category.name,
                                onClick = {
                                    nav.open(GridPage.filtered(category.name, mapOf("cat" to category.id), crumb = area.name))
                                },
                                modifier = Modifier.restoreFocus(page, "cat:${category.id}"),
                                trailing = count(category.count),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The shortcuts at the top of the site's Categories page. */
private val quickLinks = listOf(
    "Recently recommended" to mapOf("sort" to "recent"),
    "Longs" to mapOf("dur" to "xlong"),
    "Hidden gems" to mapOf("gems" to "1"),
    "All videos" to emptyMap(),
)

@Composable
fun PlaylistsScreen(page: PlaylistsPage) {
    Loaded(page.playlists) { playlists ->
        PageColumn(page, page.list) {
            item(key = "#head") {
                PageHead(
                    "Playlists",
                    subtitle = "Curated by Claude from what Hacker News recommends, each in a deliberate order.",
                    modifier = Modifier.restoreFocus(page, "head"),
                )
            }
            val first = playlists.firstOrNull { (it.tier ?: "deep") == tiers.first().tier }
            for (tier in tiers) {
                val shelf = playlists.filter { (it.tier ?: "deep") == tier.tier }
                if (shelf.isEmpty()) continue
                item(key = "#${tier.tier}") {
                    Column {
                        Text(tier.title, style = Type.section)
                        Text(tier.blurb, style = Type.sub)
                    }
                }
                items(shelf.chunked(4), key = { row -> row.first().slug }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (playlist in row) {
                            PlaylistTile(
                                playlist,
                                Modifier.weight(1f),
                                Modifier.restoreFocus(page, playlist.slug, first = playlist == first),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private class Tier(val tier: String, val title: String, val blurb: String)

private val tiers = listOf(
    Tier("start", "Start here", "Broad, essential lists for your first visit."),
    Tier("deep", "Deep dives", "One obsession each, for when you want to go down a rabbit hole."),
)

@Composable
private fun PlaylistTile(playlist: PlaylistCard, modifier: Modifier, focusModifier: Modifier) {
    val nav = LocalNavigator.current
    Column(modifier) {
        Surface(
            onClick = { nav.open(PlaylistPage(playlist.slug)) },
            modifier = focusModifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Colors.bg2, focusedContainerColor = Colors.bg2),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Colors.text))),
        ) {
            Box {
                Mosaic(playlist.cover)
                Text(
                    "${playlist.count} videos",
                    style = Type.badge,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            playlist.title,
            style = Type.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        Text(hours(playlist.minutes), style = Type.sub)
    }
}

@Composable
fun PlaylistScreen(page: PlaylistPage) {
    Loaded(page.playlist) { playlist ->
        PageColumn(page, page.list) {
            item(key = "#head") {
                // focusable, so that scrolling back up reveals the whole introduction
                Surface(
                    onClick = {},
                    modifier = Modifier.restoreFocus(page, "head").padding(bottom = 16.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Colors.bg2,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Mosaic(playlist.videos.map { it.vid }, Modifier.width(320.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Playlists", style = Type.sub)
                            Text(playlist.title, style = Type.pageTitle)
                            Text(
                                "Curated by Claude · ${playlist.videos.size} videos · ${hours(playlist.minutes)}",
                                style = Type.sub,
                            )
                            Text(playlist.intro, style = Type.body, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            itemsIndexed(playlist.videos, key = { _, video -> video.vid }) { i, video ->
                VideoRow(
                    video,
                    Modifier.widthIn(max = 1000.dp).restoreFocus(page, video.vid, first = i == 0),
                    number = i + 1,
                )
            }
        }
    }
}

@Composable
private fun PageColumn(page: Page, state: LazyListState, content: LazyListScope.() -> Unit) {
    // picking the current section in the sidebar asks for the top of the page
    LaunchedEffect(page.topTick) {
        if (page.topTick > 0) page.goToTop { state.scrollToItem(0) }
    }
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

private fun count(n: Int) = "%,d".format(Locale.US, n)
