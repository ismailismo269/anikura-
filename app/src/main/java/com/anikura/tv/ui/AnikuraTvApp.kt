package com.anikura.tv.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.SolidColor
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.anikura.tv.data.Anime
import com.anikura.tv.data.AniListRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFF09090B)
private val Panel = Color(0xFF151519)
private val PanelRaised = Color(0xFF202026)
private val Accent = Color(0xFFBAA7FF)
private val Muted = Color(0xFFAAA7B5)
private val FocusWhite = Color.White

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DISCOVER("Discover", Icons.Filled.Explore),
    TRENDING("Trending", Icons.Filled.TrendingUp),
    SCHEDULE("Schedule", Icons.Filled.CalendarMonth),
    WATCHLIST("Watchlist", Icons.Filled.Bookmark),
    SEARCH("Search", Icons.Filled.Search),
    ANNOUNCEMENTS("Announcements", Icons.Filled.Campaign),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun AnikuraTvApp() {
    val context = LocalContext.current
    val api = remember { AniListRepository() }
    val prefs = remember { context.getSharedPreferences("anikura-tv-library", 0) }

    var destination by remember { mutableStateOf(Destination.HOME) }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var playerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    var trending by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var popular by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var browseResults by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var watchlist by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            trending = api.browse(sort = "TRENDING_DESC")
            popular = api.browse(sort = "POPULARITY_DESC")
        } catch (e: Exception) {
            error = e.message ?: "Could not connect to AniList."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(destination, query, genre, yearText, format, status) {
        when (destination) {
            Destination.SEARCH, Destination.DISCOVER -> {
                if (destination == Destination.SEARCH && query.isBlank()) {
                    browseResults = trending
                    return@LaunchedEffect
                }
                if (destination == Destination.SEARCH) delay(350)
                loading = true
                error = null
                try {
                    browseResults = api.browse(
                        search = if (destination == Destination.SEARCH) query else "",
                        genre = genre.ifBlank { null },
                        year = yearText.toIntOrNull(),
                        format = format.ifBlank { null },
                        status = status.ifBlank { null },
                        sort = "TRENDING_DESC"
                    )
                } catch (e: Exception) {
                    browseResults = emptyList()
                    error = e.message ?: "Could not load catalogue."
                } finally {
                    loading = false
                }
            }
            Destination.TRENDING -> {
                loading = true
                try {
                    browseResults = api.browse(sort = "TRENDING_DESC")
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not load trending."
                } finally {
                    loading = false
                }
            }
            Destination.SCHEDULE -> {
                loading = true
                try {
                    browseResults = api.browse(status = "RELEASING", sort = "START_DATE_DESC")
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not load airing anime."
                } finally {
                    loading = false
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(favoriteIds) {
        prefs.edit().putStringSet("favorites", favoriteIds.map(Int::toString).toSet()).apply()
        if (favoriteIds.isEmpty()) {
            watchlist = emptyList()
        } else {
            watchlist = runCatching { api.browse(ids = favoriteIds) }.getOrDefault(emptyList())
        }
    }

    BackHandler(enabled = playerOpen || selectedAnime != null) {
        if (playerOpen) playerOpen = false else selectedAnime = null
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = Panel,
            onBackground = FocusWhite,
            onSurface = FocusWhite
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            when {
                playerOpen && selectedAnime != null -> {
                    NativePlayerScreen(
                        anime = selectedAnime!!,
                        onBack = { playerOpen = false }
                    )
                }
                selectedAnime != null -> {
                    AnimeDetailsScreen(
                        anime = selectedAnime!!,
                        isFavorite = selectedAnime!!.id in favoriteIds,
                        onBack = { selectedAnime = null },
                        onFavorite = {
                            favoriteIds = if (selectedAnime!!.id in favoriteIds) {
                                favoriteIds - selectedAnime!!.id
                            } else {
                                favoriteIds + selectedAnime!!.id
                            }
                        },
                        onPlay = { playerOpen = true }
                    )
                }
                else -> {
                    Row(Modifier.fillMaxSize()) {
                        TvSidebar(destination = destination, onSelect = {
                            destination = it
                            selectedAnime = null
                            playerOpen = false
                        })
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when (destination) {
                                Destination.HOME -> HomeScreen(
                                    trending = trending,
                                    popular = popular,
                                    loading = loading,
                                    error = error,
                                    onAnime = { selectedAnime = it },
                                    onSearch = { destination = Destination.SEARCH }
                                )
                                Destination.DISCOVER -> BrowseScreen(
                                    title = "Discover",
                                    results = browseResults,
                                    loading = loading,
                                    error = error,
                                    genre = genre,
                                    year = yearText,
                                    format = format,
                                    status = status,
                                    onGenre = { genre = it },
                                    onYear = { yearText = it },
                                    onFormat = { format = it },
                                    onStatus = { status = it },
                                    onReset = {
                                        genre = ""
                                        yearText = ""
                                        format = ""
                                        status = ""
                                    },
                                    onAnime = { selectedAnime = it }
                                )
                                Destination.TRENDING -> CatalogueScreen(
                                    title = "Trending Anime",
                                    items = browseResults,
                                    loading = loading,
                                    error = error,
                                    onAnime = { selectedAnime = it }
                                )
                                Destination.SCHEDULE -> ScheduleScreen(
                                    items = browseResults,
                                    loading = loading,
                                    error = error,
                                    onAnime = { selectedAnime = it }
                                )
                                Destination.WATCHLIST -> CatalogueScreen(
                                    title = "Your Watchlist",
                                    items = watchlist,
                                    loading = loading,
                                    error = if (favoriteIds.isEmpty()) "Add anime to your watchlist from its details page." else error,
                                    onAnime = { selectedAnime = it }
                                )
                                Destination.SEARCH -> SearchScreen(
                                    query = query,
                                    onQuery = { query = it },
                                    results = browseResults,
                                    loading = loading,
                                    error = error,
                                    onAnime = { selectedAnime = it }
                                )
                                Destination.ANNOUNCEMENTS -> AnnouncementsScreen()
                                Destination.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSidebar(destination: Destination, onSelect: (Destination) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (expanded) 222.dp else 78.dp)
            .background(Color(0xFF070709))
            .animateContentSize()
            .padding(horizontal = 8.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (expanded) "ANIKURA" else "A",
            color = FocusWhite,
            fontSize = if (expanded) 21.sp else 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 12.dp, bottom = 24.dp, top = 8.dp)
        )
        Destination.entries.forEach { item ->
            val selected = item == destination
            var focused by remember(item) { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            focused -> Color.White.copy(alpha = 0.14f)
                            selected -> Color.White.copy(alpha = 0.09f)
                            else -> Color.Transparent
                        }
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) expanded = true
                    }
                    .clickable { onSelect(item) }
                    .focusable()
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (selected || focused) FocusWhite else Muted)
                if (expanded) {
                    Text(
                        item.label,
                        color = if (selected || focused) FocusWhite else Muted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    trending: List<Anime>,
    popular: List<Anime>,
    loading: Boolean,
    error: String?,
    onAnime: (Anime) -> Unit,
    onSearch: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 34.dp, end = 38.dp, top = 26.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ANIME FOR YOUR SCREEN", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Discover something great", color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                TvActionButton("Search anime", onClick = onSearch)
            }
        }
        if (trending.isNotEmpty()) {
            item {
                SpotlightHero(anime = trending.first(), onDetails = { onAnime(trending.first()) })
            }
        } else if (loading) {
            item { LoadingPanel("Loading the catalogue…") }
        } else if (error != null) {
            item { ErrorPanel(error) }
        }
        if (trending.isNotEmpty()) item { AnimeRow("Trending", trending, onAnime) }
        if (popular.isNotEmpty()) item { AnimeRow("Popular", popular, onAnime) }
        if (trending.isNotEmpty()) {
            val airing = trending.filter { it.status == "RELEASING" }
            if (airing.isNotEmpty()) item { AnimeRow("Top Airing", airing, onAnime) }
        }
    }
}

@Composable
private fun SpotlightHero(anime: Anime, onDetails: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(344.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
    ) {
        AsyncImage(
            model = anime.banner.ifBlank { anime.poster },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xF009090B), Color(0xAA09090B), Color.Transparent)
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(0.68f).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FEATURED", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(anime.title, color = FocusWhite, fontSize = 32.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(anime.format.takeIf(String::isNotBlank), anime.year?.toString(), anime.score?.let { "Score $it%" }).joinToString("  •  "),
                color = Muted,
                fontSize = 14.sp
            )
            Text(anime.description.ifBlank { "Explore the details, genres and available information for this title." }, color = FocusWhite.copy(alpha = 0.84f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            TvActionButton("View details", onClick = onDetails)
        }
    }
}

@Composable
private fun AnimeRow(title: String, items: List<Anime>, onAnime: (Anime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = FocusWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.id }) { anime -> AnimePoster(anime, onClick = { onAnime(anime) }) }
        }
    }
}

@Composable
private fun AnimePoster(anime: Anime, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "posterFocus")
    Column(
        Modifier
            .width(142.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(198.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(if (focused) BorderStroke(3.dp, FocusWhite) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                .background(PanelRaised)
        ) {
            AsyncImage(
                model = anime.poster,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize().padding(if (focused) 2.dp else 0.dp).clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop
            )
            if (anime.score != null) {
                Text(
                    "${anime.score}%",
                    Modifier.align(Alignment.BottomEnd).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 7.dp, vertical = 4.dp),
                    color = FocusWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(anime.title, color = if (focused) FocusWhite else Muted, fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CatalogueScreen(title: String, items: List<Anime>, loading: Boolean, error: String?, onAnime: (Anime) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        when {
            loading && items.isEmpty() -> LoadingPanel("Loading anime…")
            error != null && items.isEmpty() -> ErrorPanel(error)
            items.isEmpty() -> Text("No titles to show yet.", color = Muted)
            else -> AnimeGrid(items, onAnime)
        }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Anime>, loading: Boolean, error: String?, onAnime: (Anime) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Search anime", color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(0.62f),
            singleLine = true,
            label = { Text("Title") },
            placeholder = { Text("Search by anime name…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Muted.copy(alpha = 0.45f),
                focusedTextColor = FocusWhite,
                unfocusedTextColor = FocusWhite
            )
        )
        if (loading && results.isEmpty()) LoadingPanel("Searching…")
        else if (error != null && results.isEmpty()) ErrorPanel(error)
        else if (results.isEmpty()) Text("Start typing to search the catalogue.", color = Muted)
        else AnimeGrid(results, onAnime)
    }
}

@Composable
private fun BrowseScreen(
    title: String,
    results: List<Anime>,
    loading: Boolean,
    error: String?,
    genre: String,
    year: String,
    format: String,
    status: String,
    onGenre: (String) -> Unit,
    onYear: (String) -> Unit,
    onFormat: (String) -> Unit,
    onStatus: (String) -> Unit,
    onReset: () -> Unit,
    onAnime: (Anime) -> Unit
) {
    val genres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Slice of Life")
    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Genres", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.forEach { item ->
                FilterChip(
                    selected = genre == item,
                    onClick = { onGenre(if (genre == item) "" else item) },
                    label = { Text(item) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = year,
                onValueChange = { onYear(it.filter(Char::isDigit).take(4)) },
                modifier = Modifier.width(130.dp),
                singleLine = true,
                label = { Text("Year") }
            )
            listOf("TV", "MOVIE", "OVA", "ONA").forEach { item ->
                FilterChip(selected = format == item, onClick = { onFormat(if (format == item) "" else item) }, label = { Text(item) })
            }
            listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED").forEach { item ->
                FilterChip(selected = status == item, onClick = { onStatus(if (status == item) "" else item) }, label = { Text(item.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) })
            }
            TextButton(onClick = onReset) { Text("Reset filters", color = Accent) }
        }
        when {
            loading && results.isEmpty() -> LoadingPanel("Applying filters…")
            error != null && results.isEmpty() -> ErrorPanel(error)
            results.isEmpty() -> Text("No results match these filters.", color = Muted)
            else -> AnimeGrid(results, onAnime)
        }
    }
}

@Composable
private fun ScheduleScreen(items: List<Anime>, loading: Boolean, error: String?, onAnime: (Anime) -> Unit) {
    val airing = remember(items) { items.filter { it.nextAiringAt != null }.sortedBy { it.nextAiringAt } }
    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Schedule", color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Upcoming episodes from AniList airing metadata.", color = Muted)
        when {
            loading && items.isEmpty() -> LoadingPanel("Loading schedule…")
            error != null && airing.isEmpty() -> ErrorPanel(error)
            airing.isEmpty() -> Text("No upcoming episode data is available right now.", color = Muted)
            else -> AnimeGrid(airing, onAnime, showAiring = true)
        }
    }
}

@Composable
private fun AnimeGrid(items: List<Anime>, onAnime: (Anime) -> Unit, showAiring: Boolean = false) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 142.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        gridItems(items, key = { it.id }) { anime ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimePoster(anime, onClick = { onAnime(anime) })
                if (showAiring && anime.nextEpisode != null) {
                    Text("Next: episode ${anime.nextEpisode}", color = Accent, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AnimeDetailsScreen(
    anime: Anime,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(330.dp).background(Panel)) {
                AsyncImage(
                    model = anime.banner.ifBlank { anime.poster },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Background.copy(alpha = 0.98f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(anime.title, color = FocusWhite, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(
                        listOfNotNull(anime.format.takeIf(String::isNotBlank), anime.season.takeIf(String::isNotBlank), anime.year?.toString(), anime.score?.let { "$it/100" }).joinToString("  •  "),
                        color = Muted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton("Open player", onClick = onPlay)
                        TvActionButton(if (isFavorite) "Remove from watchlist" else "Add to watchlist", onClick = onFavorite, secondary = true)
                        TvActionButton("Back", onClick = onBack, secondary = true)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 38.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Overview", color = FocusWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(anime.description.ifBlank { "No synopsis is available." }, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                Text("Genres", color = FocusWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(anime.genres.joinToString("  •  ").ifBlank { "Not available" }, color = Accent)
                Text("Information", color = FocusWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Status: ${anime.status.ifBlank { "Unknown" }}    Episodes: ${anime.episodes?.toString() ?: "Unknown"}",
                    color = Muted
                )
                Text(
                    "AniList provides catalogue metadata only. Playback needs a separate media URL you are authorised to use.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun AnnouncementsScreen() {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Announcements", color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Anikura TV is being built as a native Android TV app.", color = Muted, fontSize = 17.sp)
        Text("The catalogue is supplied by AniList. Player URLs and subtitle files are provided separately by the user.", color = Muted, fontSize = 15.sp)
        Text("More app announcements can be added here without changing the navigation shell.", color = Muted, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", color = FocusWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Interface", color = Accent, fontWeight = FontWeight.Bold)
        Text("The first TV build uses a dark interface, compact navigation and a white focus outline.", color = Muted)
        Text("Playback", color = Accent, fontWeight = FontWeight.Bold)
        Text("The native player uses Media3. Audio language and subtitle visibility can be changed while playing.", color = Muted)
        Text("Compatibility", color = Accent, fontWeight = FontWeight.Bold)
        Text("Minimum Android API: 23. No native ABI-specific decoder libraries are bundled in this build.", color = Muted)
    }
}

@Composable
private fun NativePlayerScreen(anime: Anime, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    var streamUrl by remember { mutableStateOf("") }
    var subtitleUrl by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var subtitleEnabled by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(anime.title, color = FocusWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TvActionButton("Back", onClick = onBack, secondary = true)
        }
        if (!ready) {
            Text("Open a direct HTTPS stream", color = Accent, fontWeight = FontWeight.Bold)
            Text("Use a media URL you are authorised to play. AniList does not provide video streams.", color = Muted)
            OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, modifier = Modifier.fillMaxWidth(0.72f), singleLine = true, label = { Text("HTTPS video URL (.m3u8 or .mp4)") })
            OutlinedTextField(value = subtitleUrl, onValueChange = { subtitleUrl = it }, modifier = Modifier.fillMaxWidth(0.72f), singleLine = true, label = { Text("Optional WebVTT subtitle URL") })
            TvActionButton("Load video", onClick = {
                val uri = Uri.parse(streamUrl.trim())
                if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                    message = "Enter a valid HTTPS media URL."
                } else {
                    runCatching {
                        val builder = MediaItem.Builder().setUri(uri)
                        if (subtitleUrl.isNotBlank()) {
                            val subtitleUri = Uri.parse(subtitleUrl.trim())
                            require(subtitleUri.scheme == "https" && !subtitleUri.host.isNullOrBlank())
                            val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage("en")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                            builder.setSubtitleConfigurations(listOf(subtitle))
                        }
                        player.setMediaItem(builder.build())
                        player.prepare()
                        player.playWhenReady = true
                        ready = true
                        message = null
                    }.onFailure { message = it.message ?: "Could not load this media URL." }
                }
            })
            message?.let { Text(it, color = Color(0xFFFF9B9B)) }
        } else {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { it.player = player },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton("Original audio", onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage("ja").build()
                }, secondary = true)
                TvActionButton("English audio", onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage("en").build()
                }, secondary = true)
                TvActionButton(if (subtitleEnabled) "Subtitles on" else "Subtitles off", onClick = {
                    subtitleEnabled = !subtitleEnabled
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleEnabled).build()
                }, secondary = true)
            }
        }
    }
}

@Composable
private fun TvActionButton(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        border = if (focused) BorderStroke(2.dp, FocusWhite) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) PanelRaised else Accent,
            contentColor = if (secondary) FocusWhite else Color(0xFF15111E)
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingPanel(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = Accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(label, color = Muted)
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Could not load content", color = FocusWhite, fontWeight = FontWeight.Bold)
        Text(message, color = Muted)
    }
}
