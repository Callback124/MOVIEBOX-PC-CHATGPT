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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

                    // Episodes Selector Row (Card border radius 5px, no border)
                    if (episodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
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
}
