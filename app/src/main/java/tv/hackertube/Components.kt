package tv.hackertube

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** The grid card of hackertube.org: OK plays the video on YouTube, holding OK opens its page. */
@Composable
fun VideoCard(video: Video, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    Column {
        Surface(
            onClick = { nav.play(video) },
            onLongClick = { nav.open(DetailsPage(video)) },
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Colors.bg2, focusedContainerColor = Colors.bg2),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Colors.text))),
        ) {
            Thumbnail(video)
        }
        Text(
            video.title,
            style = Type.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        Text(video.channel, style = Type.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.stats(), style = Type.sub, maxLines = 1)
    }
}

/** The search-result row of hackertube.org, also used for playlists. */
@Composable
fun VideoRow(video: Video, modifier: Modifier = Modifier, number: Int? = null) {
    val nav = LocalNavigator.current
    Surface(
        onClick = { nav.play(video) },
        onLongClick = { nav.open(DetailsPage(video)) },
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Colors.bg2),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (number != null) {
                Text("$number", style = Type.sub, modifier = Modifier.width(20.dp).padding(top = 4.dp))
            }
            Thumbnail(video, Modifier.width(240.dp).clip(RoundedCornerShape(12.dp)).background(Colors.bg2))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = Type.title.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(listOf(video.channel, video.stats()).joinToString(" · "), style = Type.sub)
                val text = video.note.ifEmpty { video.summary }
                Text(
                    text,
                    style = if (video.note.isEmpty()) Type.sub else Type.body,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (video.via != null) {
                    Text(
                        "Topic match · ${video.via}",
                        style = Type.sub.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(top = 8.dp).background(Colors.bg2, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** A frame from the middle of the video (the site's default), with the duration in the corner. */
@Composable
fun Thumbnail(video: Video, modifier: Modifier = Modifier) {
    val saved = video.vid in LocalLibrary.current.later
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        YouTubeImage(video.vid, Modifier.fillMaxSize())
        if (saved) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                    .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_check), contentDescription = "In Watch later", Modifier.size(18.dp), tint = Color.White) }
        }
        val length = duration(video.durationSeconds)
        if (length.isNotEmpty()) {
            Text(
                length,
                style = Type.badge,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** YouTube's mid-video frame, or the regular thumbnail if a video has no frame. */
@Composable
fun YouTubeImage(vid: String, modifier: Modifier = Modifier) {
    var frame by remember(vid) { mutableStateOf(true) }
    AsyncImage(
        model = "https://i.ytimg.com/vi/$vid/${if (frame) "hq2" else "hqdefault"}.jpg",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { frame = false },
        modifier = modifier,
    )
}

/** Four thumbnails in a 2×2 grid, the cover of a playlist. */
@Composable
fun Mosaic(vids: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Colors.bg2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (pair in vids.take(4).chunked(2)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (vid in pair) YouTubeImage(vid, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

/** The site's chips and pills: white when selected, outlined when focused. */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    @DrawableRes icon: Int? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    trailing: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Colors.text else Colors.bg2,
            contentColor = if (selected) Colors.bg else Colors.text,
            focusedContainerColor = if (selected) Colors.text else Colors.bg3,
            focusedContentColor = if (selected) Colors.bg else Colors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Colors.text), shape = shape)),
    ) {
        Row(
            Modifier.height(36.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (trailing != null) Text(trailing, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = .6f))
        }
    }
}

/** A round pill, like the buttons on the site's video page. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    selected: Boolean = false,
) = Chip(text, onClick, modifier, selected, icon, CircleShape)

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Hacker")
            withStyle(SpanStyle(color = Colors.accent)) { append("Tube") }
        },
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        color = Colors.text,
        modifier = modifier,
    )
}

/**
 * The title at the top of a page. It takes focus, so that a viewer who has scrolled past it can
 * press Up to bring it back. Without that, nothing above the first row can be reached on a TV.
 */
@Composable
fun PageHead(
    title: String,
    crumb: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {},
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Colors.bg2,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            if (crumb != null) Text(crumb, style = Type.sub.copy(fontSize = 14.sp))
            Text(title, style = Type.pageTitle)
            if (subtitle != null) {
                Text(subtitle, style = Type.sub.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun Status(text: String) {
    Text(
        text,
        style = Type.sub.copy(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

/** Loads [remote], and shows progress or the error until its value is ready. */
@Composable
fun <T : Any> Loaded(remote: Remote<T>, content: @Composable (T) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(remote) { remote.load() }
    val value = remote.value
    val error = remote.error
    when {
        value != null -> content(value)
        error != null -> Failed(error, retry = { scope.launch { remote.load() } })
        else -> Status("Loading…")
    }
}

/** What went wrong, with a button to try again. */
@Composable
fun Failed(error: Exception, retry: () -> Unit) {
    val busy = error is HttpError && (error.code == 429 || error.code == 503)
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (busy) "Lots of people on HackerTube right now. Give it a minute and try again."
            else "Couldn't reach HackerTube. Check the TV's internet connection.",
            style = Type.sub.copy(fontSize = 14.sp),
        )
        Spacer(Modifier.height(12.dp))
        Chip("Try again", retry)
    }
}

/**
 * Remembers that this item had focus on [page], and puts focus back on it when the page
 * comes into view again. With no remembered item, the [first] item gets focus.
 */
@Composable
fun Modifier.restoreFocus(page: Page, key: String, first: Boolean = false): Modifier {
    val target = page.focus?.let { it == key } ?: first
    if (target) {
        LaunchedEffect(page) {
            if (!page.needsFocus) return@LaunchedEffect
            withFrameNanos { }
            // a lazy list can compose an item ahead of showing it; then it can't take focus yet
            if (runCatching { page.focusRequester.requestFocus() }.getOrDefault(false)) page.needsFocus = false
        }
    }
    return onFocusChanged { if (it.isFocused) page.focus = key }
        .then(if (target) Modifier.focusRequester(page.focusRequester) else Modifier)
}
