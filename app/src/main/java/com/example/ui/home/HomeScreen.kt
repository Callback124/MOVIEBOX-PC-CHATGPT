package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.data.api.LookrCategory
import com.example.data.api.LookrSubTag
import com.example.data.api.StoryTvLanguage
import com.example.data.download.DownloadStatus
import com.example.data.download.MovieDownloadManager
import com.example.data.model.AppServer
import com.example.data.model.CategorySection
import com.example.data.model.MovieItem
import com.example.data.model.toMovieItem
import com.example.ui.home.components.HeroBannerCarousel
import com.example.ui.home.components.LookrServerView
import com.example.ui.home.components.StoryTvServerView
import com.example.ui.home.components.FreeReelsServerView
import com.example.ui.home.components.MovieRow
import com.example.ui.home.components.NetworkStreamDialog
import com.example.ui.home.components.ShortsReelPlayer
import com.example.ui.music.FullScreenMusicPlayer
import com.example.ui.music.JioSaavnSearchScreen
import com.example.ui.music.JioSaavnServerView
import com.example.ui.music.components.MiniMusicPlayer
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MovieBoxRed

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNetworkStreamDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTvDevice = remember { com.example.util.DeviceUtils.isAndroidTv(context) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top-Level Screen Navigation Container with Smooth Animated Transitions
        AnimatedContent(
            targetState = uiState.currentScreen,
            transitionSpec = {
                if (targetState is AppScreen.Home) {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                } else {
                    (slideInHorizontally(animationSpec = tween(240)) { width -> width / 4 } + fadeIn(animationSpec = tween(240)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> -width / 4 } + fadeOut(animationSpec = tween(240)))
                }
            },
            label = "screen_page_navigation",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
        when (screen) {
            is AppScreen.Home -> {
                if (isTvDevice) {
                    com.example.ui.tv.TvHomeMainFeedView(
                        uiState = uiState,
                        onOpenSearch = { viewModel.openSearch() },
                        onOpenSearchWithQuery = { query -> viewModel.openSearchWithQuery(query) },
                        onOpenDownloads = { viewModel.openDownloads() },
                        onOpenWatchHistory = { viewModel.openWatchHistory() },
                        onBannerClick = { banner ->
                            val movieItem = banner.toMovieItem()
                            if (movieItem.subjectType == 7 || movieItem.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movieItem.isStoryTvServer || movieItem.isFreeReelsServer) {
                                viewModel.openShortsPlayer(movieItem)
                            } else {
                                viewModel.openBannerDetail(banner)
                            }
                        },
                        onPlayBanner = { banner ->
                            val movieItem = banner.toMovieItem()
                            if (movieItem.subjectType == 7 || movieItem.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movieItem.isStoryTvServer || movieItem.isFreeReelsServer) {
                                viewModel.openShortsPlayer(movieItem)
                            } else {
                                viewModel.openMovieDetail(movieItem, isFromShortsPage = false)
                            }
                        },
                        onMovieClick = { movie, playlist, isFromHotShortTv ->
                            if (movie.subjectType == 7 || movie.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movie.isStoryTvServer || movie.isFreeReelsServer || (uiState.activeServer == AppServer.SERVER_1 && isFromHotShortTv)) {
                                viewModel.openShortsPlayer(movie, playlist)
                            } else {
                                viewModel.openMovieDetail(movie, playlist = playlist, isFromShortsPage = false)
                            }
                        },
                        onViewMoreClick = { section, isShorts, isLandscape ->
                            val isHotShortTv = if (uiState.activeServer == AppServer.SERVER_1) {
                                section.isHotShortTvSection
                            } else {
                                section.isHotShortTvSection || isShorts || true
                            }
                            viewModel.openSectionPage(section, isShortsSection = isHotShortTv, isLandscape = isLandscape)
                        },
                        onToggleServer = { viewModel.toggleServer() },
                        onSelectServer = { server -> viewModel.switchServer(server) },
                        onLoadMoreRecommend = { viewModel.loadMoreVskitFilterShorts() },
                        onSelectLookrCategory = { viewModel.selectLookrCategory(it) },
                        onSelectLookrSubTag = { viewModel.selectLookrSubTag(it) },
                        onLoadMoreLookr = { viewModel.loadMoreLookrItems() },
                        onSelectStoryTvLanguage = { viewModel.selectStoryTvLanguage(it) },
                        onLoadMoreStoryTv = { viewModel.loadMoreStoryTvItems() },
                        onLoadMoreFreeReels = { viewModel.loadMoreFreeReelsItems() }
                    )
                } else {
                    HomeMainFeedView(
                        uiState = uiState,
                        onOpenSearch = { viewModel.openSearch() },
                        onOpenSearchWithQuery = { query -> viewModel.openSearchWithQuery(query) },
                        onOpenDownloads = { viewModel.openDownloads() },
                        onOpenWatchHistory = { viewModel.openWatchHistory() },
                        onBannerClick = { banner ->
                            val movieItem = banner.toMovieItem()
                            if (movieItem.subjectType == 7 || movieItem.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movieItem.isStoryTvServer || movieItem.isFreeReelsServer) {
                                viewModel.openShortsPlayer(movieItem)
                            } else {
                                viewModel.openBannerDetail(banner)
                            }
                        },
                        onPlayBanner = { banner ->
                            val movieItem = banner.toMovieItem()
                            if (movieItem.subjectType == 7 || movieItem.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movieItem.isStoryTvServer || movieItem.isFreeReelsServer) {
                                viewModel.openShortsPlayer(movieItem)
                            } else {
                                viewModel.openMovieDetail(movieItem, isFromShortsPage = false)
                            }
                        },
                        onMovieClick = { movie, playlist, isFromHotShortTv ->
                            if (movie.subjectType == 7 || movie.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movie.isStoryTvServer || movie.isFreeReelsServer || (uiState.activeServer == AppServer.SERVER_1 && isFromHotShortTv)) {
                                viewModel.openShortsPlayer(movie, playlist)
                            } else {
                                viewModel.openMovieDetail(movie, playlist = playlist, isFromShortsPage = false)
                            }
                        },
                        onViewMoreClick = { section, isShorts, isLandscape ->
                            val isHotShortTv = if (uiState.activeServer == AppServer.SERVER_1) {
                                section.isHotShortTvSection
                            } else {
                                section.isHotShortTvSection || isShorts || true
                            }
                            viewModel.openSectionPage(section, isShortsSection = isHotShortTv, isLandscape = isLandscape)
                        },
                        onToggleServer = { viewModel.toggleServer() },
                        onSelectServer = { server -> viewModel.switchServer(server) },
                        onLoadMoreRecommend = { viewModel.loadMoreVskitFilterShorts() },
                        onSelectLookrCategory = { viewModel.selectLookrCategory(it) },
                        onSelectLookrSubTag = { viewModel.selectLookrSubTag(it) },
                        onLoadMoreLookr = { viewModel.loadMoreLookrItems() },
                        onSelectStoryTvLanguage = { viewModel.selectStoryTvLanguage(it) },
                        onLoadMoreStoryTv = { viewModel.loadMoreStoryTvItems() },
                        onLoadMoreFreeReels = { viewModel.loadMoreFreeReelsItems() }
                    )
                }
            }

            is AppScreen.Downloads -> {
                DownloadScreen(
                    onBackClick = { viewModel.navigateBack() },
                    onPlayOffline = { downloadItem ->
                        viewModel.playOfflineMovie(downloadItem)
                    }
                )
            }

            is AppScreen.WatchHistory -> {
                val historyItems by com.example.data.repository.WatchHistoryManager.historyFlow.collectAsState()
                WatchHistoryScreen(
                    historyItems = historyItems,
                    onBackClick = { viewModel.navigateBack() },
                    onPlayItem = { historyItem ->
                        viewModel.playHistoryItem(historyItem)
                    },
                    onDeleteItem = { id ->
                        com.example.data.repository.WatchHistoryManager.deleteItem(id)
                    },
                    onClearAll = {
                        com.example.data.repository.WatchHistoryManager.clearAll()
                    }
                )
            }

            is AppScreen.Search -> {
                if (uiState.activeServer == AppServer.SERVER_6) {
                    JioSaavnSearchScreen(
                        initialQuery = uiState.searchQuery.ifBlank { null },
                        onBackClick = { viewModel.navigateBack() }
                    )
                } else {
                    val recentSearches by remember(uiState.activeServer) {
                        com.example.data.repository.RecentSearchesManager.getRecentSearchesFlow(uiState.activeServer)
                    }.collectAsState()

                    SearchScreen(
                        searchQuery = uiState.searchQuery,
                        committedSearchQuery = uiState.committedSearchQuery,
                        searchSuggestions = uiState.searchSuggestions,
                        searchResults = uiState.searchResults,
                        isSearchingSuggestions = uiState.isSearchingSuggestions,
                        isSearchingMovies = uiState.isSearchingMovies,
                        isMovieBoxSearchAutoLoading = uiState.isMovieBoxSearchAutoLoading,
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onSubmitSearch = { viewModel.submitSearch(it) },
                        onClearSearch = { viewModel.clearSearch() },
                        onBackClick = { viewModel.navigateBack() },
                        onMovieClick = { movie ->
                            if (movie.subjectType == 7 || movie.isShort || movie.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || uiState.activeServer == AppServer.SERVER_4 || uiState.activeServer == AppServer.SERVER_5 || movie.isStoryTvServer || movie.isFreeReelsServer) {
                                viewModel.openShortsPlayer(movie, playlist = uiState.searchResults)
                            } else {
                                viewModel.openMovieDetail(movie, playlist = uiState.searchResults, isFromShortsPage = false)
                            }
                        },
                        onLoadMore = { viewModel.loadMoreSearchResults() },
                        recentSearches = recentSearches,
                        onSelectRecentSearch = { query ->
                            viewModel.onSearchQueryChanged(query)
                            viewModel.submitSearch(query)
                        },
                        onClearRecentSearches = {
                            viewModel.clearRecentSearches()
                        }
                    )
                }
            }

            is AppScreen.Shorts -> {
                val movie = screen.movie
                val targetEpStr = if (screen.initialEpisode > 0) String.format("%02d", screen.initialEpisode) else "01"
                val targetIndex = if (screen.initialEpisode > 0) screen.initialEpisode - 1 else 0
                ShortsReelPlayer(
                    movie = movie,
                    playlist = screen.playlist,
                    currentEpisode = targetEpStr,
                    initialIndex = targetIndex,
                    onClose = { viewModel.navigateBack() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is AppScreen.Genre -> {
                val section = uiState.selectedSectionPage ?: screen.section
                val isShorts = screen.isShortsPage || uiState.isGenrePageShorts || section.isShortsSection
                val isLandscape = screen.isLandscape || uiState.isGenrePageLandscape || section.isLandscapeDetected
                GenreDetailScreen(
                    section = section,
                    movies = uiState.genrePageMovies,
                    isLoading = uiState.isGenrePageLoading,
                    isLoadingMore = uiState.isGenrePageLoadingMore,
                    hasMore = uiState.genrePageHasMore,
                    isShortsPage = if (uiState.activeServer == AppServer.SERVER_1) {
                        section.isHotShortTvSection
                    } else {
                        isShorts
                    },
                    isLandscape = isLandscape,
                    onBackClick = { viewModel.navigateBack() },
                    onMovieClick = { movie, playlist, isShortsFromPage ->
                        if (movie.subjectType == 7 || movie.subjectTypeInfo.isDirectShortsPlayer || uiState.activeServer == AppServer.SERVER_2 || (uiState.activeServer == AppServer.SERVER_1 && isShortsFromPage && section.isHotShortTvSection)) {
                            viewModel.openShortsPlayer(movie, playlist)
                        } else {
                            viewModel.openMovieDetail(movie, playlist = playlist, isFromShortsPage = false)
                        }
                    },
                    onLoadMore = { viewModel.loadMoreGenreMovies() }
                )
            }

            is AppScreen.MovieDetail -> {
                val movie = screen.movie
                MovieDetailScreen(
                    movie = movie,
                    playlist = screen.playlist,
                    isFromShortsPage = screen.isFromShortsPage,
                    isDownloaded = screen.isDownloaded,
                    downloadedSeason = screen.downloadedSeason,
                    downloadedEpisode = screen.downloadedEpisode,
                    initialSeekMs = screen.initialSeekMs,
                    isInWatchlist = uiState.watchlistIds.contains(movie.id),
                    onBackClick = { viewModel.navigateBack() },
                    onToggleWatchlist = { viewModel.toggleWatchlist(it) }
                )
            }
        }
    }

    // Mini Music Player (Persistent Floating Bar across the entire app whenever playing)
    MiniMusicPlayer(
        modifier = Modifier.align(Alignment.BottomCenter)
    )

    // Full Screen Music Player Overlay (Available across all screens)
    FullScreenMusicPlayer()
}

    // Network Stream Dialog
    if (showNetworkStreamDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkStreamDialog = false },
            onPlayStream = { streamMovie ->
                showNetworkStreamDialog = false
                viewModel.openMovieDetail(streamMovie, isFromShortsPage = false)
            }
        )
    }
}

@Composable
private fun HomeMainFeedView(
    uiState: HomeUiState,
    onOpenSearch: () -> Unit,
    onOpenSearchWithQuery: (String) -> Unit = {},
    onOpenDownloads: () -> Unit,
    onOpenWatchHistory: () -> Unit,
    onBannerClick: (com.example.data.model.HeroBanner) -> Unit,
    onPlayBanner: (com.example.data.model.HeroBanner) -> Unit,
    onMovieClick: (MovieItem, List<MovieItem>, Boolean) -> Unit,
    onViewMoreClick: (CategorySection, Boolean, Boolean) -> Unit,
    onToggleServer: () -> Unit,
    onSelectServer: (AppServer) -> Unit,
    onLoadMoreRecommend: () -> Unit,
    onSelectLookrCategory: (LookrCategory) -> Unit,
    onSelectLookrSubTag: (LookrSubTag) -> Unit,
    onLoadMoreLookr: () -> Unit,
    onSelectStoryTvLanguage: (StoryTvLanguage) -> Unit,
    onLoadMoreStoryTv: () -> Unit,
    onLoadMoreFreeReels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isServerMenuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = isServerMenuOpen) {
        isServerMenuOpen = false
    }

    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val downloadManager = remember { MovieDownloadManager.getInstance(context) }
    val downloads by downloadManager.downloads.collectAsState()
    val activeDownloadsCount = remember(downloads) {
        downloads.count { it.status == DownloadStatus.DOWNLOADING }
    }

    // Infinite scroll auto-load for VSKit filter shorts
    LaunchedEffect(scrollState, uiState.activeServer, uiState.vskitFilterHasMore, uiState.isVskitFilterLoadingMore) {
        if (uiState.activeServer == AppServer.SERVER_2) {
            snapshotFlow {
                val layoutInfo = scrollState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 6
            }.collect { shouldLoadMore ->
                if (shouldLoadMore && uiState.vskitFilterHasMore && !uiState.isVskitFilterLoadingMore) {
                    onLoadMoreRecommend()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkBackground,
                                DarkBackground.copy(alpha = 0.95f),
                                DarkBackground.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .statusBarsPadding()
            ) {
                // Header Bar (Logo, Title, Server Badge, Search Icon, Downloads Icon)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isServerBrandFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .onFocusChanged { isServerBrandFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (isServerBrandFocused) 2.dp else 0.dp,
                                color = if (isServerBrandFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { isServerMenuOpen = true }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.activeServer) {
                            AppServer.SERVER_6 -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(AppServer.SERVER_6.logoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "JioSaavn Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            AppServer.SERVER_5 -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(AppServer.SERVER_5.logoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "FreeReels Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            AppServer.SERVER_4 -> {
                                // Story TV Wide Logo (Logo already contains "Story TV" text)
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(AppServer.SERVER_4.logoUrl)
                                        .decoderFactory(SvgDecoder.Factory())
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Story TV Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .widthIn(min = 120.dp, max = 160.dp)
                                )
                            }
                            AppServer.SERVER_3 -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(AppServer.SERVER_3.logoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Lookr Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    )
                                }
                            }
                            AppServer.SERVER_2 -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data("https://vskit.online/logo.svg")
                                            .decoderFactory(SvgDecoder.Factory())
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "VSKit Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    )
                                }
                            }
                            AppServer.SERVER_1 -> {
                                // MovieBox App Logo
                                Image(
                                    painter = painterResource(id = R.drawable.ic_moviebox_logo),
                                    contentDescription = "MovieBox Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }

                        if (uiState.activeServer != AppServer.SERVER_4) {
                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = uiState.activeServer.title,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Dedicated Search Page Button (Square with 10dp rounded corners)
                    var isSearchBtnFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(38.dp)
                            .onFocusChanged { isSearchBtnFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (isSearchBtnFocused) 2.dp else 0.dp,
                                color = if (isSearchBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dedicated Downloads Page Button (Right of Search Icon)
                    var isDownloadsBtnFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onOpenDownloads,
                        modifier = Modifier
                            .size(38.dp)
                            .onFocusChanged { isDownloadsBtnFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (isDownloadsBtnFocused) 2.dp else 0.dp,
                                color = if (isDownloadsBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .testTag("home_downloads_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            if (activeDownloadsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .background(MovieBoxRed, CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dedicated Watch History Page Button (Right of Downloads Button)
                    var isHistoryBtnFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onOpenWatchHistory,
                        modifier = Modifier
                            .size(38.dp)
                            .onFocusChanged { isHistoryBtnFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (isHistoryBtnFocused) 2.dp else 0.dp,
                                color = if (isHistoryBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .testTag("home_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Watch History",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeServer) {
                AppServer.SERVER_2 -> {
                    // ==========================================
                    // SERVER 2: SHORTS TV LAYOUT
                    // ==========================================
                    if (uiState.isShortsTvLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MovieBoxRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Loading VSKit...",
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Hero banner carousel for VSKit featured drama
                        if (uiState.shortsTvFeedData.heroBanners.isNotEmpty()) {
                            item(key = "shorts_hero_carousel") {
                                HeroBannerCarousel(
                                    banners = uiState.shortsTvFeedData.heroBanners,
                                    onBannerClick = onBannerClick,
                                    onPlayClick = onPlayBanner
                                )
                            }
                        }

                        // 2. Curated sections from VSKit (Top Searches, Trending, New Releases)
                        itemsIndexed(
                            uiState.shortsTvFeedData.sections,
                            key = { index, section -> "shorts_sec_${section.id}_$index" }
                        ) { _, section ->
                            MovieRow(
                                section = section,
                                isLandscape = false,
                                onMovieClick = { movie ->
                                    // ALL data from VSKit strictly plays in ShortsReelPlayer!
                                    onMovieClick(movie, section.items, true)
                                },
                                onViewMoreClick = { clickedSection ->
                                    onViewMoreClick(clickedSection, true, false)
                                }
                            )
                        }

                        // 3. Custom row added at the last of VSKit (from filter API channelId=1012)
                        if (uiState.vskitFilterShortsList.isNotEmpty()) {
                            val customSection = CategorySection(
                                id = "vskit_custom_row_filter_1012",
                                title = "⚡ Quick Shorts",
                                type = "VSKIT_SHORTS",
                                items = uiState.vskitFilterShortsList.take(24),
                                isVskitSection = true
                            )
                            item(key = "vskit_custom_filter_row") {
                                MovieRow(
                                    section = customSection,
                                    isLandscape = false,
                                    onMovieClick = { movie ->
                                        onMovieClick(movie, uiState.vskitFilterShortsList, true)
                                    },
                                    onViewMoreClick = { clickedSection ->
                                        onViewMoreClick(clickedSection, true, false)
                                    }
                                )
                            }

                            // 4. Infinite Auto-Loaded Feed of Quick Shorts
                            item(key = "vskit_filter_feed_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MovieBoxRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🎬 All Quick Shorts",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${uiState.vskitFilterShortsList.size} loaded",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // 3-column chunk grid of all loaded Quick Shorts
                            val chunks = uiState.vskitFilterShortsList.chunked(3)
                            itemsIndexed(chunks, key = { index, _ -> "vskit_filter_chunk_$index" }) { _, rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { movie ->
                                        ShortsGemCard(
                                            movie = movie,
                                            onClick = {
                                                onMovieClick(movie, uiState.vskitFilterShortsList, true)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size < 3) {
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            // Infinite scroll loading indicator at bottom of list (only shown while loading and more exist)
                            if (uiState.isVskitFilterLoadingMore && uiState.vskitFilterHasMore) {
                                item(key = "auto_loading_vskit_filter") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MovieBoxRed,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        } else if (uiState.shortsTvRecommendList.isNotEmpty()) {
                            val chunks = uiState.shortsTvRecommendList.chunked(3)
                            itemsIndexed(chunks, key = { index, _ -> "gem_chunk_$index" }) { _, rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { movie ->
                                        ShortsGemCard(
                                            movie = movie,
                                            onClick = {
                                                onMovieClick(movie, uiState.shortsTvRecommendList, true)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size < 3) {
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom padding spacer so content is not covered by floating switch button
                        item(key = "shorts_bottom_spacer") {
                            Spacer(modifier = Modifier.height(90.dp))
                        }
                    }
                }
            }
            AppServer.SERVER_6 -> {
                // ==========================================
                // SERVER 6: JIOSAAVN MUSIC SERVER LAYOUT
                // ==========================================
                JioSaavnServerView(
                    onOpenSearchWithQuery = onOpenSearchWithQuery
                )
            }
            AppServer.SERVER_5 -> {
                // ==========================================
                // SERVER 5: FREE REELS SERVER LAYOUT
                // ==========================================
                FreeReelsServerView(
                    items = uiState.freeReelsItems,
                    isLoading = uiState.isFreeReelsLoading,
                    isLoadingMore = uiState.isFreeReelsLoadingMore,
                    hasMore = uiState.freeReelsHasMore,
                    onMovieClick = onMovieClick,
                    onLoadMore = onLoadMoreFreeReels
                )
            }
            AppServer.SERVER_4 -> {
                // ==========================================
                // SERVER 4: STORY TV SERVER LAYOUT
                // ==========================================
                StoryTvServerView(
                    languages = uiState.storyTvLanguages,
                    selectedLanguage = uiState.selectedStoryTvLanguage,
                    items = uiState.storyTvItems,
                    isLoading = uiState.isStoryTvLoading,
                    isLoadingMore = uiState.isStoryTvLoadingMore,
                    hasMore = uiState.storyTvHasMore,
                    onSelectLanguage = onSelectStoryTvLanguage,
                    onMovieClick = onMovieClick,
                    onLoadMore = onLoadMoreStoryTv
                )
            }
            AppServer.SERVER_3 -> {
                // ==========================================
                // SERVER 3: LOOKR SERVER LAYOUT
                // ==========================================
                LookrServerView(
                    categories = uiState.lookrCategories,
                    selectedCategory = uiState.selectedLookrCategory,
                    selectedSubTag = uiState.selectedLookrSubTag,
                    items = uiState.lookrItems,
                    isLoading = uiState.isLookrLoading,
                    isLoadingMore = uiState.isLookrLoadingMore,
                    hasMore = uiState.lookrHasMore,
                    onSelectCategory = onSelectLookrCategory,
                    onSelectSubTag = onSelectLookrSubTag,
                    onMovieClick = onMovieClick,
                    onLoadMore = onLoadMoreLookr
                )
            }
            AppServer.SERVER_1 -> {
                // ==========================================
                // SERVER 1: MOVIEBOX LAYOUT
                // ==========================================
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MovieBoxRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Loading MovieBox...",
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Hero Banner Carousel (Clean landscape thumbnail, no shadow/gradients)
                        if (uiState.feedData.heroBanners.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                HeroBannerCarousel(
                                    banners = uiState.feedData.heroBanners,
                                    onBannerClick = onBannerClick,
                                    onPlayClick = onPlayBanner
                                )
                            }
                        }

                        // 2. User's Watchlist row if items exist
                        if (uiState.watchlistIds.isNotEmpty()) {
                            val watchlistItems = uiState.feedData.sections.flatMap { it.items }
                                .filter { uiState.watchlistIds.contains(it.id) }
                                .distinctBy { it.id }

                            if (watchlistItems.isNotEmpty()) {
                                item(key = "user_watchlist") {
                                    MovieRow(
                                        section = CategorySection(
                                            id = "my_watchlist",
                                            title = "⭐ My Watchlist",
                                            type = "WATCHLIST",
                                            items = watchlistItems
                                        ),
                                        onMovieClick = { movie ->
                                            onMovieClick(movie, watchlistItems, false)
                                        },
                                        onViewMoreClick = { clickedSection ->
                                            onViewMoreClick(clickedSection, false, false)
                                        }
                                    )
                                }
                            }
                        }

                        // 3. Curated Movie Rows from API (Dynamic detection of landscape thumbnails and 🔥Hot Short TV)
                        itemsIndexed(uiState.filteredSections, key = { index, section -> "${section.id}_${section.title}_$index" }) { _, section ->
                            val isLandscape = section.isLandscapeDetected
                            val isShortsRow = section.isShortsSection
                            MovieRow(
                                section = section,
                                isLandscape = isLandscape,
                                onMovieClick = { movie ->
                                    onMovieClick(movie, section.items, isShortsRow)
                                },
                                onViewMoreClick = { clickedSection ->
                                    onViewMoreClick(clickedSection, isShortsRow, isLandscape)
                                }
                            )
                        }

                        // Bottom padding spacer so content is not covered by floating switch button
                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(90.dp))
                        }
                    }
                }
            }
        }

            }
        }

        // Tap-outside scrim to dismiss right-side server drawer
        AnimatedVisibility(
            visible = isServerMenuOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isServerMenuOpen = false
                    }
            )
        }

        // Right-side Server Choose Drawer (Width 30%, Height Full, 70% Background Transparency)
        AnimatedVisibility(
            visible = isServerMenuOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ServerChooseRightDrawer(
                activeServer = uiState.activeServer,
                onSelectServer = { server ->
                    onSelectServer(server)
                    isServerMenuOpen = false
                }
            )
        }
    }
}

/**
 * Right-side server chooser menu drawer:
 * - Width: strictly 30% of screen
 * - Height: full screen
 * - Background transparency: strictly 70% (Color.Black.copy(alpha = 0.70f))
 * - Single column (vertical choose menu)
 * - Square shape server items with server icon and server name
 */
@Composable
fun ServerChooseRightDrawer(
    activeServer: AppServer,
    onSelectServer: (AppServer) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.30f) // Strictly 30% width as requested
            .background(Color.Black.copy(alpha = 0.70f)) // Strictly 70% transparency
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks inside drawer so it won't dismiss */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Only ONE column with square shape server items (vertical choose menu)
            AppServer.entries.forEach { server ->
                val isSelected = server == activeServer
                ServerSquareItem(
                    server = server,
                    isSelected = isSelected,
                    onClick = {
                        onSelectServer(server)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Square shape server item with server icon and server name.
 */
@Composable
private fun ServerSquareItem(
    server: AppServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isFocused -> Color.White
        isSelected -> Color(0xFFE50914)
        else -> Color.White.copy(alpha = 0.18f)
    }
    val bgColor = when {
        isFocused -> Color(0xFFE50914).copy(alpha = 0.45f)
        isSelected -> Color(0xFFE50914).copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp), // Square shape container
        color = bgColor,
        border = BorderStroke(if (isFocused || isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Strictly Square Shape
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag("server_item_${server.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Square server icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                when (server) {
                    AppServer.SERVER_1 -> {
                        Image(
                            painter = painterResource(id = R.drawable.ic_moviebox_logo),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppServer.SERVER_2, AppServer.SERVER_4 -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(server.logoUrl)
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppServer.SERVER_3, AppServer.SERVER_5, AppServer.SERVER_6 -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(server.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Server Name
            Text(
                text = server.title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Vertical short drama poster card for Server 2 "Find Your Gem" grid.
 */
@Composable
fun ShortsGemCard(
    movie: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("shorts_gem_card_${movie.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B1D24))
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Episode count badge
            if (movie.corner.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = movie.corner,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Play badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE50914)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
