package tv.hackertube

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlin.coroutines.cancellation.CancellationException

/**
 * One screen on the back stack. It keeps its data, scroll position and focused item
 * while the viewer looks at a later page, so Back returns to the same spot.
 */
sealed class Page {
    /** The key of the item that had focus last; null means the page's first item. */
    var focus: String? = null
    val focusRequester = FocusRequester()

    /** Set when the page comes into view, cleared once focus is back on [focus]. */
    var needsFocus = true

    /** Counts the times the viewer asked to go back to the top; the screens watch it. */
    var topTick by mutableStateOf(0)
        private set

    /** Returns to the first item, as if the page had just opened. */
    fun scrollToTop() {
        focus = null
        needsFocus = true
        topTick++
    }

    /**
     * Scrolls a lazy list to its start and takes focus there. The screen owns the scroll, so it
     * passes in the jump. Focus is asked for a frame later, once the first item is composed.
     */
    suspend fun goToTop(scrollToStart: suspend () -> Unit) {
        scrollToStart()
        repeat(3) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                needsFocus = false
                return
            }
        }
    }
}

/**
 * How the Home feed is ordered. The first one leaves the order to the API; the rest are the
 * sorts of the site's filter bar, minus "Most recommended", which is the default here already.
 */
enum class Sort(val label: String, val value: String?) {
    Best("Best first", null),
    Recent("Recent", "recent"),
    Newest("Newest", "date"),
    Oldest("Oldest", "old"),
    Views("Most viewed", "views"),
    Liked("Best liked", "liked");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** The focus key of the Home sort chip, so that cycling it leaves focus where it was. */
const val SORT_FOCUS = "sort:"

class HomePage(private val library: Library) : Page() {
    private val seed = (0 until 1_000_000).random()
    private val everything: Feed = Feed { offset, n -> Api.feed(library, seed, offset, n) }

    val areas = Remote { Api.areas() }
    var area by mutableStateOf<Area?>(null)
        private set
    var sort by mutableStateOf(Sort.Best)
        private set
    var feed: Feed by mutableStateOf(everything)
        private set
    var grid = LazyGridState()
        private set

    fun show(area: Area?) {
        this.area = area
        refresh()
    }

    /** Moves on to the next sort. The chip keeps focus, so its label is what changes. */
    fun cycleSort() {
        sort = sort.next()
        refresh()
    }

    private fun refresh() {
        feed = feedFor()
        grid = LazyGridState()
    }

    /** The personal feed when nothing narrows it down, and a listing when something does. */
    private fun feedFor(): Feed {
        val filters = buildMap {
            area?.let { put("area", it.id) }
            sort.value?.let { put("sort", it) }
        }
        return if (filters.isEmpty()) everything else Feed { offset, n -> Api.list(filters, offset, n) }
    }
}

/** A titled grid of videos, such as a category, Longs or Watch later. */
class GridPage(
    val title: String,
    val feed: Feed,
    val crumb: String? = null,
    val subtitle: String? = null,
    val empty: String = "Nothing here yet.",
) : Page() {
    val grid = LazyGridState()

    companion object {
        fun filtered(title: String, filters: Map<String, String>, crumb: String? = null, subtitle: String? = null) =
            GridPage(title, Feed { offset, n -> Api.list(filters, offset, n) }, crumb, subtitle, "No videos match these filters.")

        /** The videos in one of the library's lists, newest first. */
        fun saved(title: String, vids: List<String>, empty: String): GridPage {
            val newest = vids.reversed()
            return GridPage(title, Feed { offset, n -> Api.docs(newest.drop(offset).take(n)) }, empty = empty)
        }
    }
}

class SearchPage : Page() {
    var query by mutableStateOf("")
    var results by mutableStateOf<Remote<List<Video>>?>(null)
        private set
    val list = LazyListState()

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        results = Remote { Api.search(q) }
        focus = null
        needsFocus = true
    }
}

class CategoriesPage : Page() {
    val areas = Remote { Api.areas() }
    val list = LazyListState()
}

class PlaylistsPage : Page() {
    val playlists = Remote { Api.playlists() }
    val list = LazyListState()
}

class PlaylistPage(val slug: String) : Page() {
    val playlist = Remote { Api.playlist(slug) }
    val list = LazyListState()
}

/** A video's own page: what it is, why HN likes it, the HN comments and what to watch next. */
class DetailsPage(val video: Video) : Page() {
    val details = Remote { Api.video(video.vid) }
    val related = Remote { Api.related(video.vid) }
    val list = LazyListState()
}

/**
 * A list of videos that loads one page at a time as the viewer scrolls down.
 * The API takes longer the more videos it ranks at once, so the first page is short.
 */
class Feed(private val load: suspend (offset: Int, n: Int) -> List<Video>) {
    val videos = mutableStateListOf<Video>()
    var loading by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    var error by mutableStateOf<Exception?>(null)
        private set
    private var offset = 0

    suspend fun more() {
        if (loading || done) return
        loading = true
        error = null
        try {
            val page = load(offset, if (offset == 0) 12 else 24)
            offset += page.size
            done = page.isEmpty()
            // a lazy grid needs unique keys, and a shuffled feed can repeat a video
            val known = videos.mapTo(HashSet()) { it.vid }
            videos += page.filter { known.add(it.vid) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        } finally {
            loading = false
        }
    }
}

/** One value from the API, loaded once. */
class Remote<T : Any>(private val fetch: suspend () -> T) {
    var value by mutableStateOf<T?>(null)
        private set
    var error by mutableStateOf<Exception?>(null)
        private set

    suspend fun load() {
        if (value != null) return
        error = null
        try {
            value = fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
    }
}
