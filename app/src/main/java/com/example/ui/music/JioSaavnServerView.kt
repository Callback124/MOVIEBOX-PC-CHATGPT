package com.example.ui.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.JioSaavnApiClient
import com.example.data.model.ArtistDiscography
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SaavnAlbumItem
import com.example.data.model.SaavnArtistItem
import com.example.data.model.SaavnHomeData
import com.example.data.model.SaavnSongItem
import com.example.data.music.MusicDownloadManager
import com.example.data.music.MusicPlayerManager
import com.example.ui.music.components.CardBackground
import com.example.ui.music.components.DarkBackground
import com.example.ui.music.components.MediaItemCard
import com.example.ui.music.components.SaavnTeal
import com.example.ui.music.components.TextPrimary
import com.example.ui.music.components.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioSaavnServerView(
    onOpenSearchWithQuery: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val downloadManager = remember { MusicDownloadManager.getInstance(context) }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val favoriteSongIds by playerManager.favoriteSongIds.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val activeDownloads by downloadManager.activeDownloads.collectAsState()

    var homeData by remember { mutableStateOf<SaavnHomeData?>(null) }
    var isLoadingFeed by remember { mutableStateOf(true) }

    // Search View state
    var activeSearchQuery by remember { mutableStateOf<String?>(null) }

    // Artist Detail View state
    var selectedArtist by remember { mutableStateOf<SaavnArtistItem?>(null) }
    var artistDiscography by remember { mutableStateOf<ArtistDiscography?>(null) }
    var isArtistLoading by remember { mutableStateOf(false) }

    // Tracklist BottomSheet state (for Album or Playlist click)
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var tracklistSongs by remember { mutableStateOf<List<SaavnSongItem>>(emptyList()) }
    var isTracklistLoading by remember { mutableStateOf(false) }

    // 3-dots Song Options Bottom Sheet
    var selectedSongOptions by remember { mutableStateOf<SaavnSongItem?>(null) }

    // Load home feed
    LaunchedEffect(Unit) {
        isLoadingFeed = true
        homeData = JioSaavnApiClient.fetchHomeFeed()
        isLoadingFeed = false
    }

    // Handle MediaItem Clicks (Song, Album, Playlist, Artist, Genre/Mood)
    fun triggerMoodSearch(title: String) {
        if (onOpenSearchWithQuery != null) {
            onOpenSearchWithQuery(title)
        } else {
            activeSearchQuery = title
        }
    }

    fun onMediaItemClick(item: MediaItem) {
        // Genres / Moods directly search and are not playlists or albums
        if (item.badge == "GENRE" || item.badge == "MOOD") {
            triggerMoodSearch(item.title)
            return
        }
        when (item.type) {
            MediaType.SONG -> {
                val songItem = item.toSaavnSongItem()
                playerManager.playSong(songItem, listOf(songItem))
                playerManager.openPlayer()
            }
            MediaType.ALBUM -> {
                selectedMediaItem = item
                isTracklistLoading = true
                coroutineScope.launch {
                    tracklistSongs = JioSaavnApiClient.getAlbumDetails(item.id)
                    isTracklistLoading = false
                }
            }
            MediaType.PLAYLIST -> {
                selectedMediaItem = item
                isTracklistLoading = true
                coroutineScope.launch {
                    tracklistSongs = JioSaavnApiClient.getPlaylistDetails(item.id)
                    isTracklistLoading = false
                }
            }
            MediaType.ARTIST -> {
                selectedMediaItem = null
                selectedArtist = SaavnArtistItem(id = item.id, name = item.title, image = item.imageUrl)
                isArtistLoading = true
                coroutineScope.launch {
                    artistDiscography = JioSaavnApiClient.getArtistPageDetails(item.id)
                    isArtistLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (activeSearchQuery != null) {
            JioSaavnSearchScreen(
                initialQuery = activeSearchQuery,
                onBackClick = { activeSearchQuery = null },
                modifier = Modifier.fillMaxSize()
            )
        } else if (selectedArtist != null) {
            TopArtistDetailView(
                artist = selectedArtist!!,
                artistDiscography = artistDiscography,
                isLoading = isArtistLoading,
                onBackClick = {
                    selectedArtist = null
                    selectedMediaItem = null
                },
                onPlaySong = { song, queue ->
                    playerManager.playSong(song, queue)
                    playerManager.openPlayer()
                },
                onAlbumClick = { album ->
                    val mItem = MediaItem(
                        id = album.id,
                        title = album.title,
                        subtitle = album.subtitle,
                        imageUrl = album.image,
                        type = MediaType.ALBUM
                    )
                    onMediaItemClick(mItem)
                },
                onSongOptions = { song ->
                    selectedSongOptions = song
                },
                downloadManager = downloadManager,
                currentBitrate = playerManager.currentBitrate.value,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            if (isLoadingFeed) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SaavnTeal)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading JioSaavn Music...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp)
                ) {
                    // Section 1: Trending Now (Square MediaCard 140x140 dp)
                    val trending = homeData?.trendingNow?.takeIf { it.isNotEmpty() } ?: homeData?.trendingItems.orEmpty()
                    if (trending.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Trending Now")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(trending) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 4. Section 2: Editorial Picks (Square MediaCard 140x140 dp)
                    val editorial = homeData?.editorialPicks?.takeIf { it.isNotEmpty() } ?: homeData?.featuredPlaylists.orEmpty()
                    if (editorial.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Editorial Picks")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(editorial) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 5. Section 3: New Releases: Pop Hindi (with 'POP HIT' / 'POP ALBUM' badge)
                    val popHindi = homeData?.popHindiItems.orEmpty()
                    if (popHindi.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "New Releases: Pop Hindi")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(popHindi) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 6. Section 4: Top Artists (LazyRow of 90dp Circular Avatars with Verified Check)
                    val topArtists = homeData?.topArtists.orEmpty()
                    if (topArtists.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Top Artists")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(topArtists) { artist ->
                                    TopArtistCard(
                                        artist = artist,
                                        onClick = {
                                            selectedArtist = artist
                                            isArtistLoading = true
                                            coroutineScope.launch {
                                                artistDiscography = JioSaavnApiClient.getArtistPageDetails(artist.id)
                                                isArtistLoading = false
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 7. Section 5: New Trending Albums (Square MediaCard 140x140 dp)
                    val newAlbums = homeData?.newTrendingAlbums?.takeIf { it.isNotEmpty() } ?: homeData?.newReleases.orEmpty()
                    if (newAlbums.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "New Trending Albums")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(newAlbums) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 8. Section 6: Free Hits (Square MediaCard with "FREE HITS" Badge)
                    val freeHits = homeData?.freeHits.orEmpty()
                    if (freeHits.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Free Hits")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(freeHits) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 9. Section 7: Top Charts (Square MediaCard 140x140 dp)
                    val charts = homeData?.topCharts.orEmpty()
                    if (charts.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Top Charts")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(charts) { chart ->
                                    MediaItemCard(
                                        item = chart,
                                        onClick = { onMediaItemClick(chart) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 10. Section 8: Jai Ganesh & Devotional (Square MediaCard with "BHAKTI" Badge)
                    val devotional = homeData?.devotional.orEmpty()
                    if (devotional.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Jai Ganesh & Devotional")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(devotional) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 11. Section 9: Best of 90s (Square MediaCard with "90s RETRO" Badge)
                    val nineties = homeData?.bestOf90s.orEmpty()
                    if (nineties.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Best of 90s")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(nineties) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onMediaItemClick(item) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }

                    // 12. Section 10: Top Genres & Moods (Rounded Gradient Pill 135x75 dp)
                    val moods = homeData?.genresAndMoods.orEmpty()
                    if (moods.isNotEmpty()) {
                        item {
                            HomeSectionHeader(title = "Top Genres & Moods")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(moods) { mood ->
                                    MoodPillCard(
                                        item = mood,
                                        onClick = {
                                            // Top Genres & Moods directly trigger search in the header's search screen
                                            triggerMoodSearch(mood.title)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Bottom spacer to ensure all content stays fully clear of MiniPlayer
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

        // =========================================================================
        // 3-DOTS OPTIONS BOTTOM SHEET
        // =========================================================================
        if (selectedSongOptions != null) {
            val song = selectedSongOptions!!
            val isSongDownloaded = downloadedSongs.any { it.id == song.id }

            ModalBottomSheet(
                onDismissRequest = { selectedSongOptions = null },
                containerColor = CardBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.image,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist.ifBlank { song.subtitle },
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                playerManager.playSong(song, listOf(song))
                                playerManager.openPlayer()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Play Now", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                if (!isSongDownloaded) {
                                    downloadManager.downloadSong(song) { success ->
                                        if (success) Toast.makeText(context, "Downloaded ${song.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSongDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (isSongDownloaded) SaavnTeal else TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isSongDownloaded) "Already Downloaded (Offline)" else "Download in 320 kbps HQ",
                            color = if (isSongDownloaded) SaavnTeal else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                playerManager.addToQueue(song)
                                Toast.makeText(context, "Added to Queue", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add to Queue", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // =========================================================================
        // TRACKLIST BOTTOM SHEET (FOR ALBUMS / PLAYLISTS)
        // =========================================================================
        if (selectedMediaItem != null) {
            val item = selectedMediaItem!!

            ModalBottomSheet(
                onDismissRequest = {
                    selectedMediaItem = null
                    tracklistSongs = emptyList()
                },
                containerColor = CardBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.getHighQualityImage(),
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle.ifBlank { item.type.name },
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        if (tracklistSongs.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = SaavnTeal,
                                modifier = Modifier
                                    .clickable {
                                        selectedMediaItem = null
                                        playerManager.playSong(tracklistSongs.first(), tracklistSongs)
                                        playerManager.openPlayer()
                                    }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play All",
                                    tint = Color.Black,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isTracklistLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SaavnTeal)
                        }
                    } else if (tracklistSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No tracks found", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(tracklistSongs) { idx, song ->
                                val isCurrent = (currentSong?.id == song.id && isPlaying)
                                val isFav = favoriteSongIds.contains(song.id)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedMediaItem = null
                                            playerManager.playSong(song, tracklistSongs)
                                            playerManager.openPlayer()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "%02d".format(idx + 1),
                                        color = if (isCurrent) SaavnTeal else TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(32.dp)
                                    )

                                    AsyncImage(
                                        model = song.image,
                                        contentDescription = song.title,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isCurrent) SaavnTeal else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist.ifBlank { song.subtitle },
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    val isSongDownloaded = downloadedSongs.any { it.id == song.id }
                                    val songDownloadProgress = activeDownloads[song.id]

                                    if (songDownloadProgress != null) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { songDownloadProgress / 100f },
                                                color = SaavnTeal,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    } else if (isSongDownloaded) {
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(context, "Song downloaded for offline playback", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Downloaded",
                                                tint = SaavnTeal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(context, "Downloading ${song.title}...", Toast.LENGTH_SHORT).show()
                                                downloadManager.downloadSong(song) { success ->
                                                    if (success) {
                                                        Toast.makeText(context, "Downloaded: ${song.title}", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download Song",
                                                tint = TextSecondary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopArtistCard(
    artist: SaavnArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hdImage = artist.image
        .replace("150x150", "500x500")
        .replace("50x50", "500x500")

    Column(
        modifier = modifier
            .width(96.dp)
            .clickable { onClick() }
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(CardBackground),
            contentAlignment = Alignment.BottomEnd
        ) {
            AsyncImage(
                model = hdImage,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            if (artist.isVerified) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Artist",
                        tint = SaavnTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (artist.followerCount.isNotBlank()) {
            Text(
                text = artist.followerCount,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MoodPillCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 135.dp, height = 75.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = item.getHighQualityImage(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.5f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(10.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopArtistDetailView(
    artist: SaavnArtistItem,
    artistDiscography: ArtistDiscography?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onPlaySong: (SaavnSongItem, List<SaavnSongItem>) -> Unit,
    onAlbumClick: (SaavnAlbumItem) -> Unit,
    onSongOptions: (SaavnSongItem) -> Unit,
    downloadManager: MusicDownloadManager,
    currentBitrate: String,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val hdArtistImage = artist.image.replace("150x150", "500x500")

    val topSongs = artistDiscography?.topSongs.orEmpty()
    val albums = artistDiscography?.topAlbums.orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. HERO HEADER (280dp)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = hdArtistImage,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        DarkBackground.copy(alpha = 0.8f),
                                        DarkBackground
                                    )
                                )
                            )
                    )

                    // Top Bar Back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    // Bottom Info in Hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artist.name,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (artist.isVerified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = SaavnTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        val subtitleText = artistDiscography?.subtitle?.ifBlank {
                            if (artist.followerCount.isNotBlank()) artist.followerCount else "Artist"
                        } ?: artist.followerCount
                        if (subtitleText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Play All & Shuffle Buttons
                        if (topSongs.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        onPlaySong(topSongs.first(), topSongs)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaavnTeal),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play All",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play All", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        val shuffled = topSongs.shuffled()
                                        onPlaySong(shuffled.first(), shuffled)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Shuffle", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SaavnTeal)
                    }
                }
            } else {
                // 2. TOP SONGS SECTION
                if (topSongs.isNotEmpty()) {
                    item {
                        HomeSectionHeader(title = "Top Songs")
                    }

                    itemsIndexed(topSongs) { idx, song ->
                        val isDownloaded = downloadedSongs.any { it.id == song.id }
                        val downloadProgress = activeDownloads[song.id]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaySong(song, topSongs) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "%02d".format(idx + 1),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )

                            AsyncImage(
                                model = song.image,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.subtitle.ifBlank { song.album },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (song.durationFormatted.isNotBlank()) {
                                Text(
                                    text = song.durationFormatted,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            // Download Icon
                            if (downloadProgress != null) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    color = SaavnTeal,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (isDownloaded) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = SaavnTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                IconButton(
                                    onClick = { downloadManager.downloadSong(song) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Song",
                                        tint = TextSecondary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 3-dots Menu
                            IconButton(
                                onClick = { onSongOptions(song) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextSecondary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 3. DISCOGRAPHY & ALBUMS SECTION
                if (albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        HomeSectionHeader(title = "Discography & Albums (${albums.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(albums) { album ->
                                val hdAlbumImage = album.image
                                    .replace("150x150", "500x500")
                                    .replace("50x50", "500x500")

                                Column(
                                    modifier = Modifier
                                        .width(135.dp)
                                        .clickable { onAlbumClick(album) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(135.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CardBackground)
                                    ) {
                                        AsyncImage(
                                            model = hdAlbumImage,
                                            contentDescription = album.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = album.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val meta = listOfNotNull(
                                        album.year.takeIf { it.isNotBlank() },
                                        if (album.songCount > 0) "${album.songCount} Songs" else null
                                    ).joinToString(" • ")

                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
