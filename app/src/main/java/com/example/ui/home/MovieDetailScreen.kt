package com.example.ui.home

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.api.MovieBoxApiClient
import com.example.data.model.DubLanguage
import com.example.data.model.MovieItem
import com.example.data.model.SubjectDetailResult
import com.example.data.model.SubjectTypeInfo
import kotlinx.coroutines.launch
import com.example.ui.home.components.DetailVideoPlayer
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MovieBoxGold
import com.example.ui.theme.MovieBoxRed

@Composable
fun MovieDetailScreen(
    movie: MovieItem,
    playlist: List<MovieItem> = emptyList(),
    isFromShortsPage: Boolean = false,
    isDownloaded: Boolean = false,
    downloadedSeason: Int = 0,
    downloadedEpisode: Int = 0,
    initialSeekMs: Long = 0L,
    isInWatchlist: Boolean = false,
    onBackClick: () -> Unit,
    onToggleWatchlist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember { com.example.util.DeviceUtils.isAndroidTv(context) }
    var activeMovie by remember(movie) { mutableStateOf(movie) }
    var isFullscreen by remember { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()

    val activity = remember {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    // Ensure portrait orientation is restored on mobile when leaving detail screen or exiting fullscreen
    DisposableEffect(Unit) {
        onDispose {
            if (!isTv) {
                activity?.runOnUiThread {
                    try {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(isFullscreen, isTv) {
        if (!isTv && !isFullscreen) {
            activity?.runOnUiThread {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } catch (_: Exception) {}
            }
        }
    }

    // Intercept back key: if fullscreen exit fullscreen back to detail screen, else exit detail screen to caller
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBackClick()
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Quality state managed inside player
    var selectedQuality by remember { mutableStateOf("1080p") }

    // Currently playing or selected dub language
    var currentSelectedDub by remember(movie.id) { mutableStateOf<DubLanguage?>(null) }

    // Dynamic detail fetch state from MovieBox API
    var detailedInfo by remember(activeMovie.id, activeMovie.detailPath) { mutableStateOf<SubjectDetailResult?>(null) }

    var activeInitialSeekMs by remember(movie.id) { mutableStateOf(initialSeekMs) }
    var hasAppliedInitialSeason by remember(movie.id, downloadedSeason) { mutableStateOf(false) }
    var hasAppliedInitialEpisode by remember(movie.id, downloadedEpisode) { mutableStateOf(false) }

    fun switchDubLanguage(dub: DubLanguage) {
        currentSelectedDub = dub
        val targetSubjId = dub.subjectId.ifBlank {
            if (dub.original) movie.id else ""
        }
        val targetPath = dub.detailPath
        coroutineScope.launch {
            val newDetail = if (activeMovie.source.equals("lookr", ignoreCase = true)) {
                com.example.data.api.LookrApiClient.fetchSubjectDetail(
                    detailPath = targetPath,
                    subjectId = targetSubjId
                )
            } else {
                MovieBoxApiClient.fetchSubjectDetail(
                    context = context,
                    detailPath = targetPath,
                    fallbackSubjectId = targetSubjId
                )
            }
            if (newDetail != null) {
                detailedInfo = newDetail
                activeMovie = activeMovie.copy(
                    id = newDetail.subjectId.ifBlank { activeMovie.id },
                    title = newDetail.title.ifBlank { activeMovie.title },
                    description = newDetail.description.ifBlank { activeMovie.description },
                    coverUrl = newDetail.coverUrl.ifBlank { activeMovie.coverUrl },
                    backdropUrl = newDetail.backdropUrl.ifBlank { activeMovie.backdropUrl },
                    rating = newDetail.rating.ifBlank { activeMovie.rating },
                    ratingCount = if (newDetail.ratingCount > 0) newDetail.ratingCount else activeMovie.ratingCount,
                    releaseDate = newDetail.releaseDate.ifBlank { activeMovie.releaseDate },
                    releaseYear = newDetail.releaseYear.ifBlank { activeMovie.releaseYear },
                    genre = newDetail.genre.ifBlank { activeMovie.genre },
                    country = newDetail.country.ifBlank { activeMovie.country },
                    duration = newDetail.durationFormatted.ifBlank { activeMovie.duration },
                    detailPath = newDetail.detailPath.ifBlank { activeMovie.detailPath },
                    corner = newDetail.corner.ifBlank { activeMovie.corner },
                    dubs = if (newDetail.dubs.isNotEmpty()) newDetail.dubs else activeMovie.dubs
                )
            }
        }
    }

    LaunchedEffect(activeMovie.detailPath, activeMovie.id, isDownloaded) {
        if (!isDownloaded && (activeMovie.detailPath.isNotBlank() || activeMovie.id.isNotBlank())) {
            val res = if (activeMovie.source.equals("lookr", ignoreCase = true)) {
                com.example.data.api.LookrApiClient.fetchSubjectDetail(
                    detailPath = activeMovie.detailPath,
                    subjectId = activeMovie.id
                )
            } else {
                MovieBoxApiClient.fetchSubjectDetail(
                    context = context,
                    detailPath = activeMovie.detailPath,
                    fallbackSubjectId = activeMovie.id
                )
            }
            if (res != null) {
                detailedInfo = res
            }
        }
    }

    // Determine if content is shorts: Strictly ONLY if launched from the Shorts page or Home 1st row!
    val isShortsContent = remember(isFromShortsPage) {
        isFromShortsPage
    }

    val shortsPlaylist = remember(playlist, activeMovie) {
        if (playlist.isNotEmpty()) playlist else listOf(activeMovie)
    }

    // Compute Subject Type Info for icons and behavior
    val currentSubjectTypeInfo = remember(activeMovie, detailedInfo) {
        val typeCode = detailedInfo?.subjectType?.takeIf { it > 0 } ?: activeMovie.subjectType
        SubjectTypeInfo.from(
            code = typeCode,
            isSeriesFallback = detailedInfo?.isSeries ?: activeMovie.isSeries,
            isShortsFallback = activeMovie.isShortsContent || activeMovie.isShort,
            genreOrTag = (detailedInfo?.genre ?: activeMovie.genre).ifBlank { detailedInfo?.corner ?: activeMovie.corner }
        )
    }

    // Determine if content is a series or a movie
    val isSeries = remember(activeMovie, detailedInfo, currentSubjectTypeInfo) {
        if (!currentSubjectTypeInfo.hasEpisodesAndSeasons) {
            false // subjectType 6 (Music Video Song { no episode and season }) or Movie
        } else if (detailedInfo != null) {
            detailedInfo!!.isSeries
        } else {
            val genre = activeMovie.genre
            val corner = activeMovie.corner
            val title = activeMovie.title
            when {
                activeMovie.isSeries -> true
                activeMovie.isShort -> true
                activeMovie.totalEpisodes > 1 -> true
                Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(corner) -> true
                corner.contains("Season", ignoreCase = true) || corner.contains("Series", ignoreCase = true) -> true
                title.contains("Season ", ignoreCase = true) || title.contains("Episode ", ignoreCase = true) -> true
                genre.contains("TV Series", ignoreCase = true) ||
                    genre.contains("TV Show", ignoreCase = true) ||
                    genre.contains("Drama Series", ignoreCase = true) ||
                    genre.contains("Anime Series", ignoreCase = true) ||
                    genre.equals("Series", ignoreCase = true) -> true
                genre.contains("Movie", ignoreCase = true) -> false
                activeMovie.duration.isNotBlank() && (activeMovie.duration.contains("h") || activeMovie.duration.contains("m")) -> false
                else -> false
            }
        }
    }

    // Season list
    val availableSeasons: List<Int> = remember(detailedInfo, isSeries) {
        if (!isSeries) {
            listOf(0)
        } else if (detailedInfo != null && detailedInfo!!.seasons.isNotEmpty()) {
            val sList = detailedInfo!!.seasons.map { it.seasonNumber }.filter { it > 0 }.distinct().sorted()
            if (sList.isNotEmpty()) sList else listOf(1)
        } else {
            listOf(1)
        }
    }

    var selectedSeason by remember(movie.id, downloadedSeason) {
        mutableIntStateOf(
            if (downloadedSeason > 0) downloadedSeason
            else if (isSeries) 1 else 0
        )
    }
    var showSeasonDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(availableSeasons, isSeries, downloadedSeason) {
        if (isSeries) {
            if (downloadedSeason > 0 && !hasAppliedInitialSeason) {
                selectedSeason = downloadedSeason
                hasAppliedInitialSeason = true
            } else if (selectedSeason <= 0) {
                selectedSeason = if (downloadedSeason > 0) downloadedSeason else (availableSeasons.firstOrNull() ?: 1)
            } else if (availableSeasons.isNotEmpty() && !availableSeasons.contains(selectedSeason) && hasAppliedInitialSeason) {
                selectedSeason = availableSeasons.first()
            }
        } else {
            selectedSeason = 0
        }
    }

    // Episode list
    val episodes = remember(selectedSeason, isSeries, detailedInfo, activeMovie, downloadedEpisode) {
        if (!isSeries) {
            emptyList()
        } else {
            val seasonMatch = detailedInfo?.seasons?.find { it.seasonNumber == selectedSeason }
                ?: detailedInfo?.seasons?.firstOrNull { it.seasonNumber > 0 }
            val cornerStr = detailedInfo?.corner?.ifBlank { null } ?: activeMovie.corner
            val cornerEp: Int? = Regex("""(\d+)\s*(?:EP|ep|Episodes|Ep)""").find(cornerStr)?.groupValues?.get(1)?.toIntOrNull()
            val totalEp: Int? = detailedInfo?.totalEpisodes?.takeIf { it > 1 } ?: activeMovie.totalEpisodes.takeIf { it > 1 }
            val candidateMax: Int = seasonMatch?.maxEp?.takeIf { it > 0 } ?: 0

            val effectiveMaxEp: Int = when {
                candidateMax > 1 -> candidateMax
                cornerEp != null && cornerEp > 1 -> cornerEp
                totalEp != null && totalEp > 1 -> totalEp
                candidateMax == 1 -> 1
                else -> 12
            }
            val baseMaxEp: Int = if (effectiveMaxEp <= 1 && isSeries) {
                cornerEp ?: totalEp ?: 24
            } else {
                effectiveMaxEp
            }
            val finalMaxEp = maxOf(baseMaxEp, downloadedEpisode)
            (1..finalMaxEp).map { String.format("%02d", it) }
        }
    }
    var selectedEpisode by remember(movie.id, downloadedEpisode) {
        mutableStateOf(
            if (downloadedEpisode > 0) String.format("%02d", downloadedEpisode)
            else if (isSeries) "01" else "00"
        )
    }

    var showAllEpisodesSheet by remember { mutableStateOf(false) }
    val chunkSize = 50
    val episodeChunks = remember(episodes) { episodes.chunked(chunkSize) }
    val hasMultipleChunks = episodeChunks.size > 1
    var selectedChunkIndex by remember(episodes) { mutableIntStateOf(0) }

    // Intercept back button when All Episodes container is open
    BackHandler(enabled = showAllEpisodesSheet) {
        showAllEpisodesSheet = false
    }

    val initialTargetSeason = remember(movie.id, downloadedSeason) {
        if (downloadedSeason > 0) downloadedSeason else if (isSeries) 1 else 0
    }
    val initialTargetEpisode = remember(movie.id, downloadedEpisode) {
        if (downloadedEpisode > 0) downloadedEpisode else if (isSeries) 1 else 0
    }

    LaunchedEffect(selectedSeason, selectedEpisode) {
        val currentEpNum = selectedEpisode.toIntOrNull() ?: 0
        if (selectedSeason != initialTargetSeason || currentEpNum != initialTargetEpisode) {
            activeInitialSeekMs = 0L
        }
    }

    LaunchedEffect(episodes, isSeries, downloadedEpisode) {
        if (isSeries) {
            if (downloadedEpisode > 0 && !hasAppliedInitialEpisode) {
                selectedEpisode = String.format("%02d", downloadedEpisode)
                hasAppliedInitialEpisode = true
            } else if (selectedEpisode == "00" || selectedEpisode.isBlank()) {
                selectedEpisode = if (downloadedEpisode > 0) String.format("%02d", downloadedEpisode) else "01"
            } else if (episodes.isNotEmpty() && !episodes.contains(selectedEpisode) && hasAppliedInitialEpisode) {
                selectedEpisode = episodes.first()
            }
        } else {
            selectedEpisode = "00"
        }
    }

    // Effective MovieItem combining initial item + detailed metadata
    val effectiveMovie = remember(activeMovie, detailedInfo) {
        if (detailedInfo != null) {
            activeMovie.copy(
                id = detailedInfo!!.subjectId.ifBlank { activeMovie.id },
                title = detailedInfo!!.title.ifBlank { activeMovie.title },
                description = detailedInfo!!.description.ifBlank { activeMovie.description },
                coverUrl = detailedInfo!!.coverUrl.ifBlank { activeMovie.coverUrl },
                backdropUrl = detailedInfo!!.backdropUrl.ifBlank { activeMovie.backdropUrl },
                rating = detailedInfo!!.rating.ifBlank { activeMovie.rating },
                ratingCount = if (detailedInfo!!.ratingCount > 0) detailedInfo!!.ratingCount else activeMovie.ratingCount,
                releaseDate = detailedInfo!!.releaseDate.ifBlank { activeMovie.releaseDate },
                releaseYear = detailedInfo!!.releaseYear.ifBlank { activeMovie.releaseYear },
                genre = detailedInfo!!.genre.ifBlank { activeMovie.genre },
                country = detailedInfo!!.country.ifBlank { activeMovie.country },
                duration = detailedInfo!!.durationFormatted.ifBlank { activeMovie.duration },
                detailPath = detailedInfo!!.detailPath.ifBlank { activeMovie.detailPath },
                corner = detailedInfo!!.corner.ifBlank { activeMovie.corner },
                dubs = if (detailedInfo!!.dubs.isNotEmpty()) detailedInfo!!.dubs else activeMovie.dubs
            )
        } else {
            activeMovie
        }
    }

    // Derived metadata fields
    val displayTitle = detailedInfo?.title?.ifBlank { activeMovie.title } ?: activeMovie.title
    val displayDescription = detailedInfo?.description?.ifBlank { activeMovie.description } ?: activeMovie.description
    val displayRating = detailedInfo?.rating?.ifBlank { activeMovie.rating.ifBlank { "7.8" } } ?: activeMovie.rating.ifBlank { "7.8" }
    val displayYear = detailedInfo?.releaseYear?.ifBlank {
        activeMovie.releaseYear.ifBlank { activeMovie.releaseDate.take(4).ifBlank { "2024" } }
    } ?: "2024"
    val displayCountry = detailedInfo?.country?.ifBlank { activeMovie.country.ifBlank { "United States" } } ?: "United States"

    // Main View (NO back button on detail page)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else DarkBackground)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .testTag("movie_detail_page_screen")
            ) {
                // 1. ORIGINAL ART PLAYER (WITHOUT BACK BUTTON, QUALITY SWITCHER INSIDE PLAYER)
                // Persistent instance prevents reloading / re-fetching during orientation & fullscreen changes
                val isLastEpisodeOfLastSeason = remember(isSeries, selectedSeason, selectedEpisode, episodes, availableSeasons) {
                    if (!isSeries) true
                    else if (episodes.isEmpty()) false
                    else {
                        val currentEpNum = selectedEpisode.toIntOrNull() ?: 1
                        val currentIndex = episodes.indexOfFirst { (it.toIntOrNull() ?: 0) == currentEpNum }
                        val isLastEpInSeason = if (currentIndex != -1) {
                            currentIndex + 1 >= episodes.size
                        } else {
                            val maxEpInList = episodes.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 1
                            currentEpNum >= maxEpInList
                        }
                        val currentSeasonIndex = availableSeasons.indexOf(selectedSeason)
                        val isLastSeason = if (currentSeasonIndex != -1) {
                            currentSeasonIndex + 1 >= availableSeasons.size
                        } else {
                            val maxSeason = availableSeasons.maxOrNull() ?: selectedSeason
                            selectedSeason >= maxSeason
                        }
                        isLastEpInSeason && isLastSeason
                    }
                }

                val playNextEpisode = remember(isSeries, episodes, selectedEpisode, availableSeasons, selectedSeason) {
                    {
                        if (isSeries) {
                            hasAppliedInitialSeason = true
                            hasAppliedInitialEpisode = true
                            activeInitialSeekMs = 0L
                            val currentEpNum = selectedEpisode.toIntOrNull() ?: 1
                            val currentIndex = episodes.indexOfFirst { (it.toIntOrNull() ?: 0) == currentEpNum }
                            if (currentIndex != -1 && currentIndex + 1 < episodes.size) {
                                selectedEpisode = episodes[currentIndex + 1]
                            } else {
                                val nextFromList = episodes.firstOrNull { (it.toIntOrNull() ?: 0) > currentEpNum }
                                if (nextFromList != null) {
                                    selectedEpisode = nextFromList
                                } else {
                                    val currentSeasonIndex = availableSeasons.indexOf(selectedSeason)
                                    val nextSeason = if (currentSeasonIndex != -1 && currentSeasonIndex + 1 < availableSeasons.size) {
                                        availableSeasons[currentSeasonIndex + 1]
                                    } else {
                                        availableSeasons.firstOrNull { it > selectedSeason }
                                    }
                                    if (nextSeason != null) {
                                        selectedSeason = nextSeason
                                        selectedEpisode = "01"
                                    }
                                }
                            }
                        }
                    }
                }

                DetailVideoPlayer(
                    movie = effectiveMovie,
                    onBackClick = onBackClick,
                    isSeries = isSeries,
                    selectedSeason = if (isSeries) selectedSeason else 0,
                    selectedEpisode = if (isSeries) (selectedEpisode.toIntOrNull() ?: 1) else 0,
                    selectedQuality = selectedQuality,
                    dubs = detailedInfo?.dubs?.takeIf { it.isNotEmpty() } ?: effectiveMovie.dubs,
                    selectedDub = currentSelectedDub,
                    onDubSelected = { dub ->
                        currentSelectedDub = dub
                    },
                    onDubsListUpdated = { newDubs ->
                        if (detailedInfo != null) {
                            detailedInfo = detailedInfo?.copy(dubs = newDubs)
                        }
                    },
                    onQualitySelected = { q -> selectedQuality = q },
                    isFullscreen = isFullscreen,
                    onFullscreenChanged = { fs -> isFullscreen = fs },
                    isFromShortsPage = isShortsContent,
                    isDownloaded = isDownloaded,
                    isLastEpisodeOfLastSeason = isLastEpisodeOfLastSeason,
                    onPlayNext = playNextEpisode,
                    onPlaybackEnded = playNextEpisode,
                    availableSeasons = availableSeasons,
                    seasonsInfo = detailedInfo?.seasons ?: emptyList(),
                    initialSeekMs = activeInitialSeekMs,
                    onSubjectDetailUpdated = { newDetail ->
                        detailedInfo = newDetail
                        activeMovie = activeMovie.copy(
                            id = newDetail.subjectId.ifBlank { activeMovie.id },
                            title = newDetail.title.ifBlank { activeMovie.title },
                            description = newDetail.description.ifBlank { activeMovie.description },
                            coverUrl = newDetail.coverUrl.ifBlank { activeMovie.coverUrl },
                            backdropUrl = newDetail.backdropUrl.ifBlank { activeMovie.backdropUrl },
                            rating = newDetail.rating.ifBlank { activeMovie.rating },
                            ratingCount = if (newDetail.ratingCount > 0) newDetail.ratingCount else activeMovie.ratingCount,
                            releaseDate = newDetail.releaseDate.ifBlank { activeMovie.releaseDate },
                            releaseYear = newDetail.releaseYear.ifBlank { activeMovie.releaseYear },
                            genre = newDetail.genre.ifBlank { activeMovie.genre },
                            country = newDetail.country.ifBlank { activeMovie.country },
                            duration = newDetail.durationFormatted.ifBlank { activeMovie.duration },
                            detailPath = newDetail.detailPath.ifBlank { activeMovie.detailPath },
                            corner = newDetail.corner.ifBlank { activeMovie.corner },
                            dubs = if (newDetail.dubs.isNotEmpty()) newDetail.dubs else activeMovie.dubs
                        )
                    },
                    modifier = if (isFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }
                )

            // 2. DETAIL SECTION (BELOW PLAYER) - Visible in portrait / non-fullscreen mode
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(detailScrollState)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Top Row: Left Thumbnail + Right (Title + Other Details)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left: Thumbnail with 5.dp rounded corners, no border
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .width(92.dp)
                                .height(138.dp)
                        ) {
                        AsyncImage(
                            model = effectiveMovie.coverUrl.ifBlank { effectiveMovie.backdropUrl },
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Right: Title & Other Details (ONLY Type Icon, Star, Year, Country)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                    ) {
                        // Title
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp,
                            modifier = Modifier.testTag("detail_title_text")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Other Details Row: Subject Type Icon & Badge, Star, Year, Country
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Distinct Subject Type Icon and Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = currentSubjectTypeInfo.primaryColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = currentSubjectTypeInfo.icon,
                                    contentDescription = currentSubjectTypeInfo.title,
                                    tint = currentSubjectTypeInfo.primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentSubjectTypeInfo.shortBadge.uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(text = "|", color = Color(0xFF52525B), fontSize = 11.sp)

                            // Star Rating
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = MovieBoxGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = displayRating,
                                color = MovieBoxGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(text = "|", color = Color(0xFF52525B), fontSize = 11.sp)

                            // Year
                            Text(
                                text = displayYear,
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp
                            )

                            Text(text = "|", color = Color(0xFF52525B), fontSize = 11.sp)

                            // Country
                            Text(
                                text = displayCountry,
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Audio / Dub Language selector for both MovieBox and Lookr
                val availableDubs = remember(detailedInfo?.dubs, effectiveMovie.dubs) {
                    val raw = detailedInfo?.dubs?.takeIf { it.isNotEmpty() } ?: effectiveMovie.dubs
                    raw.filter { it.lanName.isNotBlank() || it.lanCode.isNotBlank() }
                }

                if (availableDubs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Audio / Dub Language",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableDubs) { dub ->
                            val isCurrentDub = if (currentSelectedDub != null) {
                                (dub.subjectId.isNotBlank() && dub.subjectId == currentSelectedDub?.subjectId) ||
                                (dub.detailPath.isNotBlank() && dub.detailPath == currentSelectedDub?.detailPath) ||
                                (dub.lanCode.isNotBlank() && dub.lanCode.equals(currentSelectedDub?.lanCode, ignoreCase = true) && dub.original == currentSelectedDub?.original) ||
                                (dub.lanCode.isNotBlank() && dub.lanCode.equals(currentSelectedDub?.lanCode, ignoreCase = true))
                            } else {
                                (dub.detailPath.isNotBlank() && dub.detailPath == effectiveMovie.detailPath) ||
                                (dub.subjectId.isNotBlank() && dub.subjectId == effectiveMovie.id) ||
                                (dub.original && (effectiveMovie.detailPath.isBlank() || effectiveMovie.detailPath == movie.detailPath))
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentDub) MovieBoxRed else Color(0xFF22222A),
                                border = if (isCurrentDub) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .clickable {
                                        if (!isCurrentDub) {
                                            currentSelectedDub = dub
                                            switchDubLanguage(dub)
                                        }
                                    }
                            ) {
                                Text(
                                    text = dub.lanName.ifBlank { dub.lanCode.uppercase() } + if (dub.original) " (Orig)" else "",
                                    color = if (isCurrentDub) Color.White else Color(0xFFD4D4D8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrentDub) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thumbnail & detail ke neeche synopsis hoga
                Text(
                    text = "Synopsis",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = displayDescription.ifBlank { "No detailed synopsis available." },
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )

                // Cast & Crew (if available, skipped when offline downloaded)
                if (!isDownloaded && detailedInfo?.cast?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cast & Crew",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(detailedInfo!!.cast.take(8)) { actor ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(60.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = DarkSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    if (actor.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = actor.avatarUrl,
                                            contentDescription = actor.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = actor.name,
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. BOTTOM SECTION: SEASONS & EPISODES (Only for online series, skipped for downloaded offline video)
            if (!isDownloaded && isSeries) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141418))
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF27272A), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Season Selector Row (Border radius 10px)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF22222A),
                                modifier = Modifier
                                    .clickable { showSeasonDropdown = true }
                                    .testTag("season_selector_box")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = String.format("Season %02d", selectedSeason),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Choose Season",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSeasonDropdown,
                                onDismissRequest = { showSeasonDropdown = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                availableSeasons.forEach { sNum ->
                                    val sLabel = String.format("Season %02d", sNum)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sLabel,
                                                color = if (sNum == selectedSeason) MovieBoxRed else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (sNum == selectedSeason) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            if (selectedSeason != sNum) {
                                                hasAppliedInitialSeason = true
                                                hasAppliedInitialEpisode = true
                                                activeInitialSeekMs = 0L
                                                selectedSeason = sNum
                                                selectedEpisode = "01"
                                            }
                                            showSeasonDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Episodes Selector Row with FIXED "ALL" Button on Left
                    if (episodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("episodes_selector_container"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // FIXED "ALL" BUTTON (Remains pinned to left even when episodes scroll horizontally)
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF262634),
                                border = BorderStroke(1.dp, Color(0xFF3F3F52)),
                                modifier = Modifier
                                    .clickable {
                                        val currentEpNum = selectedEpisode.toIntOrNull() ?: 1
                                        selectedChunkIndex = ((currentEpNum - 1) / chunkSize).coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0))
                                        showAllEpisodesSheet = true
                                    }
                                    .width(52.dp)
                                    .height(36.dp)
                                    .testTag("all_episodes_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Canvas(modifier = Modifier.size(11.dp)) {
                                        val gap = 1.8.dp.toPx()
                                        val boxSize = (size.width - gap) / 2f
                                        val corner = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                        drawRoundRect(color = MovieBoxRed, topLeft = Offset(0f, 0f), size = Size(boxSize, boxSize), cornerRadius = corner)
                                        drawRoundRect(color = MovieBoxRed, topLeft = Offset(boxSize + gap, 0f), size = Size(boxSize, boxSize), cornerRadius = corner)
                                        drawRoundRect(color = MovieBoxRed, topLeft = Offset(0f, boxSize + gap), size = Size(boxSize, boxSize), cornerRadius = corner)
                                        drawRoundRect(color = MovieBoxRed, topLeft = Offset(boxSize + gap, boxSize + gap), size = Size(boxSize, boxSize), cornerRadius = corner)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "All",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Horizontally Scrollable Episode Cards
                            LazyRow(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("episodes_lazy_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(episodes) { ep ->
                                    val isSelected = (ep == selectedEpisode)
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = if (isSelected) MovieBoxRed else Color(0xFF22222A),
                                        modifier = Modifier
                                            .clickable {
                                                if (selectedEpisode != ep) {
                                                    hasAppliedInitialEpisode = true
                                                    activeInitialSeekMs = 0L
                                                    selectedEpisode = ep
                                                }
                                            }
                                            .width(46.dp)
                                            .height(36.dp)
                                            .testTag("episode_card_$ep")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = ep,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

        // 45% Height Bottom Container for All Episodes (Grid type, tabs 1-50, 51-100... when episodes > 50)
        AnimatedVisibility(
            visible = showAllEpisodesSheet,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showAllEpisodesSheet = false }
            )
        }

        AnimatedVisibility(
            visible = showAllEpisodesSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                color = Color(0xFF16161E),
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color(0xFF282836))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Drag handle indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF454558))
                        )
                    }

                    // Header Row: Season / Episodes title, season switcher if multiple seasons, and close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val gap = 2.dp.toPx()
                                val boxSize = (size.width - gap) / 2f
                                val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                                drawRoundRect(color = MovieBoxRed, topLeft = Offset(0f, 0f), size = Size(boxSize, boxSize), cornerRadius = corner)
                                drawRoundRect(color = MovieBoxRed, topLeft = Offset(boxSize + gap, 0f), size = Size(boxSize, boxSize), cornerRadius = corner)
                                drawRoundRect(color = MovieBoxRed, topLeft = Offset(0f, boxSize + gap), size = Size(boxSize, boxSize), cornerRadius = corner)
                                drawRoundRect(color = MovieBoxRed, topLeft = Offset(boxSize + gap, boxSize + gap), size = Size(boxSize, boxSize), cornerRadius = corner)
                            }

                            Text(
                                text = if (isSeries) String.format("Season %02d Episodes", selectedSeason) else "All Episodes",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MovieBoxRed.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, MovieBoxRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${episodes.size} eps",
                                    color = MovieBoxRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (availableSeasons.size > 1) {
                                var showSheetSeasonMenu by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF262634),
                                        modifier = Modifier.clickable { showSheetSeasonMenu = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = String.format("S%02d", selectedSeason),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Switch Season",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSheetSeasonMenu,
                                        onDismissRequest = { showSheetSeasonMenu = false },
                                        modifier = Modifier.background(DarkSurface)
                                    ) {
                                        availableSeasons.forEach { sNum ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = String.format("Season %02d", sNum),
                                                        color = if (sNum == selectedSeason) MovieBoxRed else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (sNum == selectedSeason) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    if (selectedSeason != sNum) {
                                                        hasAppliedInitialSeason = true
                                                        hasAppliedInitialEpisode = true
                                                        activeInitialSeekMs = 0L
                                                        selectedSeason = sNum
                                                        selectedEpisode = "01"
                                                    }
                                                    showSheetSeasonMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showAllEpisodesSheet = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFA0A0B0),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Serial Tabs (1-50, 51-100, 101-150, etc. when episodes.size > 50)
                    if (hasMultipleChunks) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(episodeChunks.size) { chunkIdx ->
                                val startEp = chunkIdx * chunkSize + 1
                                val endEp = minOf((chunkIdx + 1) * chunkSize, episodes.size)
                                val tabLabel = "$startEp-$endEp"
                                val isTabSelected = (chunkIdx == selectedChunkIndex)

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isTabSelected) MovieBoxRed else Color(0xFF22222E),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isTabSelected) MovieBoxRed else Color(0xFF333342)
                                    ),
                                    modifier = Modifier
                                        .clickable { selectedChunkIndex = chunkIdx }
                                        .testTag("chunk_tab_$tabLabel")
                                ) {
                                    Text(
                                        text = tabLabel,
                                        color = if (isTabSelected) Color.White else Color(0xFFC0C0D0),
                                        fontSize = 12.sp,
                                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFF262634),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )

                    // Episode Grid (10 columns, 5 rows for 50 episodes, wraps content tightly with no bottom gap)
                    val displayedEpisodes = if (hasMultipleChunks) {
                        episodeChunks.getOrElse(selectedChunkIndex) { episodes }
                    } else {
                        episodes
                    }

                    val episodeRows = remember(displayedEpisodes) {
                        displayedEpisodes.chunked(10)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
                            .testTag("all_episodes_grid"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        episodeRows.forEach { rowEps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowEps.forEach { ep ->
                                    val isSelected = (ep == selectedEpisode)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) MovieBoxRed else Color(0xFF22222D),
                                        border = BorderStroke(
                                            if (isSelected) 1.dp else 0.5.dp,
                                            if (isSelected) Color.White else Color(0xFF333342)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clickable {
                                                if (selectedEpisode != ep) {
                                                    hasAppliedInitialEpisode = true
                                                    activeInitialSeekMs = 0L
                                                    selectedEpisode = ep
                                                }
                                                showAllEpisodesSheet = false
                                            }
                                            .testTag("grid_episode_item_$ep")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = ep,
                                                color = Color.White,
                                                fontSize = if (ep.length > 2) 9.sp else 11.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                val remainingSlots = 10 - rowEps.size
                                if (remainingSlots > 0) {
                                    repeat(remainingSlots) {
                                        Spacer(modifier = Modifier.weight(1f))
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
