package tv.hackertube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(page: SearchPage) {
    val results = page.results
    val scope = rememberCoroutineScope()
    if (results != null) LaunchedEffect(results) { results.load() }
    // picking Search again in the sidebar asks for the top of the results
    LaunchedEffect(page.topTick) {
        if (page.topTick > 0) page.goToTop { page.list.scrollToItem(0) }
    }
    LazyColumn(
        state = page.list,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "#field") {
            SearchField(page, Modifier.restoreFocus(page, "field", first = results == null))
        }
        val error = results?.error
        val videos = results?.value
        when {
            results == null -> item(key = "#hint") {
                Status("Search the videos Hacker News recommends, by words or by meaning.")
            }
            error != null -> item(key = "#status") { Failed(error, retry = { scope.launch { results.load() } }) }
            videos == null -> item(key = "#status") { Status("Searching…") }
            videos.isEmpty() -> item(key = "#status") { Status("No results.") }
            else -> itemsIndexed(videos, key = { _, video -> video.vid }) { i, video ->
                VideoRow(video, Modifier.widthIn(max = 900.dp).restoreFocus(page, video.vid, first = i == 0))
            }
        }
    }
}

/** The site's search box: a pill with a blue outline while typing. */
@Composable
private fun SearchField(page: SearchPage, modifier: Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = page.query,
        onValueChange = { page.query = it },
        singleLine = true,
        textStyle = Type.body.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Colors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            page.search()
        }),
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Row(
                Modifier.height(48.dp)
                    .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Colors.link else Colors.bg3), CircleShape)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Colors.muted)
                Box(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (page.query.isEmpty()) Text("Search", style = Type.body.copy(fontSize = 18.sp, color = Colors.muted))
                    field()
                }
            }
        },
    )
}
