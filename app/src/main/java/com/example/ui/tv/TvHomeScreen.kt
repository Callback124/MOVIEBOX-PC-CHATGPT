package com.example.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.widthIn
import coil.decode.SvgDecoder
import com.example.R
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.LookrCategory
import com.example.data.api.LookrSubTag
import com.example.data.api.StoryTvLanguage
import com.example.data.model.AppServer
import com.example.data.model.CategorySection
import com.example.data.model.HeroBanner
import com.example.data.model.MovieItem
import com.example.ui.home.HomeUiState
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MovieBoxRed
import kotlinx.coroutines.launch

/**
 * Android TV Home Screen View
 * Optimized 100% for TV screens and remote D-Pad navigation.
 * Features:
 * - Top Hero slide with large backdrop, metadata badges ([TV], rating, year, genre),
 *   and bottom/right carousel with prominent focused white outline & play button (matching Screenshot 1).
 * - Category rows with "🔥 Cinema", "View More", portrait and landscape cards with language badges
 *   and high-contrast focused state (matching Screenshot 2).
 * - Full D-Pad Remote Navigation (Up, Down, Left, Right, OK / Center).
 */
@Composable
fun TvHomeMainFeedView(
    uiState: HomeUiState,
    onOpenSearch: () -> Unit,
    onOpenSearchWithQuery: (String) -> Unit = {},
    onOpenDownloads: () -> Unit,
    onOpenWatchHistory: () -> Unit = {},
    onBannerClick: (HeroBanner) -> Unit,
    onPlayBanner: (HeroBanner) -> Unit,
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
    val banners = uiState.feedData.heroBanners
    var activeHeroBanner by remember(banners) {
        mutableStateOf(banners.firstOrNull())
    }

    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Request focus to primary TV item on launch
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    var showServerChooseDialog by remember { mutableStateOf(false) }

    if (showServerChooseDialog) {
        TvServerChooseDialog(
            activeServer = uiState.activeServer,
            onSelectServer = { server ->
                onSelectServer(server)
                showServerChooseDialog = false
            },
            onDismiss = { showServerChooseDialog = false }
        )
    }

    if (uiState.activeServer == AppServer.SERVER_6) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            TvTopNavBar(
                activeServer = uiState.activeServer,
                onOpenSearch = onOpenSearch,
                onOpenDownloads = onOpenDownloads,
                onOpenWatchHistory = onOpenWatchHistory,
                onOpenSettings = { showServerChooseDialog = true },
                firstFocusRequester = firstFocusRequester
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                com.example.ui.music.JioSaavnServerView(
                    onOpenSearchWithQuery = onOpenSearchWithQuery
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // 1. TV Navigation Header (Top Bar with D-Pad focus)
        item(key = "tv_header") {
            TvTopNavBar(
                activeServer = uiState.activeServer,
                onOpenSearch = onOpenSearch,
                onOpenDownloads = onOpenDownloads,
                onOpenWatchHistory = onOpenWatchHistory,
                onOpenSettings = { showServerChooseDialog = true },
                firstFocusRequester = firstFocusRequester
            )
        }

        // 2. Hero Slide Section (Matching Screenshot 1)
        if (banners.isNotEmpty()) {
            item(key = "tv_hero_slider") {
                val bannerToDisplay = activeHeroBanner ?: banners.first()
                TvHeroSlider(
                    currentBanner = bannerToDisplay,
                    banners = banners,
                    onBannerFocused = { activeHeroBanner = it },
                    onPlayBanner = onPlayBanner,
                    onDetailsClick = onBannerClick
                )
            }
        }

        // 3. Category Rows (Matching Screenshot 2: 🔥 Cinema, Popular series, etc.)
        when (uiState.activeServer) {
            AppServer.SERVER_1 -> {
                itemsIndexed(
                    items = uiState.feedData.sections,
                    key = { _, section -> "sec_${section.title}" }
                ) { _, section ->
                    TvCategoryRow(
                        section = section,
                        movies = section.items,
                        onMovieClick = { movie -> onMovieClick(movie, section.items, section.isShortsSection) },
                        onViewMoreClick = {
                            onViewMoreClick(section, section.isShortsSection, section.isLandscapeDetected)
                        }
                    )
                }
            }
            AppServer.SERVER_2 -> {
                // Server 2: Vskit Shorts curated sections from operating API + All Quick Shorts
                itemsIndexed(
                    items = uiState.shortsTvFeedData.sections,
                    key = { index, sec -> "tv_vskit_sec_${sec.id}_$index" }
                ) { _, section ->
                    TvCategoryRow(
                        section = section,
                        movies = section.items,
                        onMovieClick = { movie -> onMovieClick(movie, section.items, true) },
                        onViewMoreClick = {
                            onViewMoreClick(section, true, false)
                        }
                    )
                }
                item(key = "tv_vskit_shorts") {
                    val sec = CategorySection(
                        id = "vskit_popular",
                        title = "🎬 All Quick Shorts",
                        type = "shorts",
                        items = uiState.vskitFilterShortsList,
                        isVskitSection = true
                    )
                    TvCategoryRow(
                        section = sec,
                        movies = uiState.vskitFilterShortsList,
                        onMovieClick = { movie -> onMovieClick(movie, uiState.vskitFilterShortsList, true) },
                        onViewMoreClick = {
                            onViewMoreClick(sec, true, false)
                        }
                    )
                }
                if (uiState.vskitFilterHasMore && !uiState.isVskitFilterLoadingMore) {
                    item(key = "tv_vskit_load_more") {
                        LaunchedEffect(Unit) {
                            onLoadMoreRecommend()
                        }
                    }
                }
            }
            AppServer.SERVER_3 -> {
                // Server 3: Lookr
                if (uiState.lookrCategories.isNotEmpty()) {
                    item(key = "tv_lookr_categories") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(uiState.lookrCategories) { _, cat ->
                                TvCategoryPill(
                                    label = cat.tagDisplayName,
                                    isSelected = cat.tagName == uiState.selectedLookrCategory.tagName,
                                    onClick = { onSelectLookrCategory(cat) }
                                )
                            }
                        }
                    }
                }

                val currentSubTags = uiState.selectedLookrCategory.subTags
                if (currentSubTags.isNotEmpty()) {
                    item(key = "tv_lookr_subtags") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(currentSubTags) { _, tag ->
                                TvCategoryPill(
                                    label = tag.tagDisplayName,
                                    isSelected = tag.tagName == uiState.selectedLookrSubTag.tagName,
                                    onClick = { onSelectLookrSubTag(tag) }
                                )
                            }
                        }
                    }
                }

                item(key = "tv_lookr_items") {
                    val sec = CategorySection(
                        id = "lookr_trending",
                        title = "🎬 ${uiState.selectedLookrCategory.tagDisplayName} Dramas",
                        type = "shorts",
                        items = uiState.lookrItems,
                        isVskitSection = true
                    )
                    TvCategoryRow(
                        section = sec,
                        movies = uiState.lookrItems,
                        onMovieClick = { movie -> onMovieClick(movie, uiState.lookrItems, true) },
                        onViewMoreClick = {
                            onViewMoreClick(sec, true, false)
                        }
                    )
                }

                if (uiState.lookrHasMore && !uiState.isLookrLoadingMore) {
                    item(key = "tv_lookr_load_more") {
                        LaunchedEffect(Unit) {
                            onLoadMoreLookr()
                        }
                    }
                }
            }
            AppServer.SERVER_4 -> {
                // Server 4: Story TV
                if (uiState.storyTvLanguages.isNotEmpty()) {
                    item(key = "tv_storytv_languages") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(uiState.storyTvLanguages) { _, lang ->
                                TvCategoryPill(
                                    label = lang.title,
                                    isSelected = lang.langId == uiState.selectedStoryTvLanguage?.langId,
                                    onClick = { onSelectStoryTvLanguage(lang) }
                                )
                            }
                        }
                    }
                }

                item(key = "tv_storytv_items") {
                    val sec = CategorySection(
                        id = "storytv_all",
                        title = "🔥 ${uiState.selectedStoryTvLanguage?.title ?: "Story TV"} Shows",
                        type = "shorts",
                        items = uiState.storyTvItems,
                        isVskitSection = true
                    )
                    TvCategoryRow(
                        section = sec,
                        movies = uiState.storyTvItems,
                        onMovieClick = { movie -> onMovieClick(movie, uiState.storyTvItems, true) },
                        onViewMoreClick = {
                            onViewMoreClick(sec, true, false)
                        }
                    )
                }

                if (uiState.storyTvHasMore && !uiState.isStoryTvLoadingMore) {
                    item(key = "tv_storytv_load_more") {
                        LaunchedEffect(Unit) {
                            onLoadMoreStoryTv()
                        }
                    }
                }
            }
            AppServer.SERVER_5 -> {
                // Server 5: FreeReels
                item(key = "tv_freereels_items") {
                    val sec = CategorySection(
                        id = "freereels_popular",
                        title = "⭐ FreeReels Popular Dramas",
                        type = "shorts",
                        items = uiState.freeReelsItems,
                        isVskitSection = true
                    )
                    TvCategoryRow(
                        section = sec,
                        movies = uiState.freeReelsItems,
                        onMovieClick = { movie -> onMovieClick(movie, uiState.freeReelsItems, true) },
                        onViewMoreClick = {
                            onViewMoreClick(sec, true, false)
                        }
                    )
                }

                if (uiState.freeReelsHasMore && !uiState.isFreeReelsLoadingMore) {
                    item(key = "tv_freereels_load_more") {
                        LaunchedEffect(Unit) {
                            onLoadMoreFreeReels()
                        }
                    }
                }
            }
            AppServer.SERVER_6 -> {
                // Handled above
            }
        }
    }
}

/**
 * TV Category & Filter Pill for D-pad navigation
 */
@Composable
private fun TvCategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) MovieBoxRed else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.28f) else if (isSelected) MovieBoxRed else Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text = label,
            color = if (isFocused || isSelected) Color.White else Color.LightGray,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * Top TV navigation bar with D-Pad focus indicators and dynamic active server logo & title
 */
@Composable
private fun TvTopNavBar(
    activeServer: AppServer,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenWatchHistory: () -> Unit = {},
    onOpenSettings: () -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & TV Badge & Active Server Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onOpenSettings() }
        ) {
            when (activeServer) {
                AppServer.SERVER_1 -> {
                    Image(
                        painter = painterResource(id = R.drawable.ic_moviebox_logo),
                        contentDescription = "MovieBox Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                AppServer.SERVER_2 -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp)
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                AppServer.SERVER_3 -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(AppServer.SERVER_3.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Lookr Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                AppServer.SERVER_4 -> {
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
                            .widthIn(min = 100.dp, max = 150.dp)
                    )
                }
                AppServer.SERVER_5 -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(AppServer.SERVER_5.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "FreeReels Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                AppServer.SERVER_6 -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(AppServer.SERVER_6.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "JioSaavn Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            if (activeServer != AppServer.SERVER_4) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activeServer.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = MovieBoxRed,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "TV",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Action Buttons: Search, Server Switcher, Downloads, Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvNavButton(
                icon = Icons.Default.Search,
                label = "Search",
                onClick = onOpenSearch,
                focusRequester = firstFocusRequester
            )
            TvNavButton(
                icon = Icons.Default.Dns,
                label = activeServer.title,
                onClick = onOpenSettings
            )
            TvNavButton(
                icon = Icons.Default.Download,
                label = "Downloads",
                onClick = onOpenDownloads
            )
            TvNavButton(
                icon = Icons.Default.History,
                label = "History",
                onClick = onOpenWatchHistory
            )
            TvNavButton(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = onOpenSettings
            )
        }
    }
}

/**
 * TV Server Chooser Dialog: 3-column grid of square cards for all servers
 */
@Composable
private fun TvServerChooseDialog(
    activeServer: AppServer,
    onSelectServer: (AppServer) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(660.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Server",
                            tint = MovieBoxRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CHOOSE TV SERVER",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        color = MovieBoxRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = activeServer.title,
                            color = MovieBoxRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select content source catalog to browse on TV:",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3-column grid of all servers
                val chunkedServers = AppServer.entries.chunked(3)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunkedServers.forEach { rowServers ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowServers.forEach { server ->
                                Box(modifier = Modifier.weight(1f)) {
                                    TvServerGridCard(
                                        server = server,
                                        isSelected = server == activeServer,
                                        onClick = { onSelectServer(server) }
                                    )
                                }
                            }
                            if (rowServers.size < 3) {
                                repeat(3 - rowServers.size) {
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

@Composable
private fun TvServerGridCard(
    server: AppServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(120),
        label = "server_card_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) MovieBoxRed else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.22f) else if (isSelected) MovieBoxRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                when (server) {
                    AppServer.SERVER_1 -> {
                        Image(
                            painter = painterResource(id = R.drawable.ic_moviebox_logo),
                            contentDescription = server.title,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    AppServer.SERVER_2 -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://vskit.online/logo.svg")
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    AppServer.SERVER_4 -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(server.logoUrl)
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    else -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(server.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = server.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = server.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = server.badge,
                color = if (isSelected) MovieBoxRed else if (isFocused) Color.White.copy(alpha = 0.9f) else Color(0xFFA1A1AA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MovieBoxRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TvNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.White else Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * TV Hero Slider matching user's Screenshot 1
 * - High-res backdrop with smooth gradient
 * - Left: Large Title, [TV] badge, Star rating, Year, Genre, 2-line Synopsis, Play & Info buttons
 * - Bottom/Right: Carousel of drama cards with focused white border & circular play button overlay
 */
@Composable
private fun TvHeroSlider(
    currentBanner: HeroBanner,
    banners: List<HeroBanner>,
    onBannerFocused: (HeroBanner) -> Unit,
    onPlayBanner: (HeroBanner) -> Unit,
    onDetailsClick: (HeroBanner) -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val carouselListState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // High-res Backdrop with Fade & Vignette
        AnimatedContent(
            targetState = currentBanner,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            label = "tv_hero_backdrop"
        ) { banner ->
            val bgUrl = banner.backdropUrl.ifBlank { banner.posterUrl }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bgUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Left & Bottom gradient overlay for maximum readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    DarkBackground.copy(alpha = 0.95f),
                                    DarkBackground.copy(alpha = 0.8f),
                                    DarkBackground.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DarkBackground.copy(alpha = 0.6f),
                                    DarkBackground
                                )
                            )
                        )
                )
            }
        }

        // Content Row: Left details, Right/Bottom cards
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left: Title, Metadata, Synopsis, Buttons
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .padding(end = 24.dp)
            ) {
                // Title
                Text(
                    text = currentBanner.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata Line: [TV] badge, ★ Rating, Year, Genre (matching Screenshot 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF6C5CE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "[TV]",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (currentBanner.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = currentBanner.rating,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val yr = currentBanner.releaseYear.ifBlank { "2024" }
                    Text(
                        text = yr,
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    val genre = currentBanner.genre.ifBlank { "Drama" }
                    Text(
                        text = genre,
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Synopsis / Description
                if (currentBanner.description.isNotBlank()) {
                    Text(
                        text = currentBanner.description,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Play Now & Details
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        text = "▶ Play Now",
                        isPrimary = true,
                        focusRequester = firstFocusRequester,
                        onClick = { onPlayBanner(currentBanner) }
                    )
                    TvActionButton(
                        text = "ℹ Info",
                        isPrimary = false,
                        onClick = { onDetailsClick(currentBanner) }
                    )
                }
            }

            // Right: Horizontal Carousel of Cards (matching Screenshot 1)
            Column(
                modifier = Modifier
                    .weight(0.9f)
            ) {
                LazyRow(
                    state = carouselListState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    itemsIndexed(banners, key = { _, b -> b.id }) { index, banner ->
                        TvHeroCard(
                            banner = banner,
                            isSelected = banner.id == currentBanner.id,
                            onFocused = {
                                onBannerFocused(banner)
                                coroutineScope.launch {
                                    carouselListState.animateScrollToItem(index)
                                }
                            },
                            onClick = { onPlayBanner(banner) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * TV Action Button with high-contrast white focus indicator
 */
@Composable
private fun TvActionButton(
    text: String,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) {
            Color.White
        } else if (isPrimary) {
            MovieBoxRed
        } else {
            Color.White.copy(alpha = 0.15f)
        }
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * TV Hero Card for Carousel (matching Screenshot 1)
 * - Focused: Thick white border (`border(2.5.dp, Color.White, RoundedCornerShape(8.dp))`)
 * - Scale 1.06f
 * - Circular Play button overlay in center
 * - Top-left language tag ("Hindi")
 */
@Composable
private fun TvHeroCard(
    banner: HeroBanner,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(150),
        label = "hero_card_scale"
    )

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .border(
                width = if (isFocused || isSelected) 2.5.dp else 0.5.dp,
                color = if (isFocused || isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(banner.posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = banner.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top-left language tag: "Hindi" (from Screenshot 1)
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(bottomEnd = 6.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            val lang = if (banner.title.contains("Hindi", ignoreCase = true)) "Hindi"
                else banner.genre.ifBlank { "Drama" }
            Text(
                text = lang,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Circular Play Button Overlay when focused or selected (from Screenshot 1)
        if (isFocused || isSelected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * TV Category Row matching user's Screenshot 2:
 * - Header: e.g. "🔥 Cinema" with a focusable "View More" button on the right
 * - Horizontal list of movie cards:
 *   - Portrait cards for Cinema / Series
 *   - Top-left language badge: "Hindi"
 *   - Title below card: "Hanuman Ans..", "Moana [Hindi]", etc.
 *   - Focused card gets bold white border, slight scale, and auto-scrolls into view!
 */
@Composable
private fun TvCategoryRow(
    section: CategorySection,
    movies: List<MovieItem>,
    onMovieClick: (MovieItem) -> Unit,
    onViewMoreClick: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header with "View More"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            TvViewMoreButton(onClick = onViewMoreClick)
        }

        // Horizontal Row of Movie Cards
        LazyRow(
            state = rowListState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)
        ) {
            itemsIndexed(movies, key = { index, movie -> "${movie.id}_$index" }) { index, movie ->
                TvMovieCard(
                    movie = movie,
                    isLandscape = section.isLandscapeDetected,
                    onFocused = {
                        coroutineScope.launch {
                            rowListState.animateScrollToItem(index)
                        }
                    },
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}

/**
 * TV "View More" Button with D-Pad focus indicator (matching Screenshot 2)
 */
@Composable
private fun TvViewMoreButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "View More",
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "View More",
                tint = if (isFocused) Color.White else Color.LightGray,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * Individual Movie Card for TV (matching Screenshot 2)
 * - Portrait (or landscape) with rounded corners
 * - Top-left language tag: "Hindi"
 * - Title text underneath the card
 * - Focused: Bold white border, scale 1.06f, elevated focus state
 */
@Composable
private fun TvMovieCard(
    movie: MovieItem,
    isLandscape: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(150),
        label = "tv_card_scale"
    )

    val cardWidth = if (isLandscape) 180.dp else 125.dp
    val cardHeight = if (isLandscape) 105.dp else 175.dp

    Column(
        modifier = Modifier
            .width(cardWidth)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .border(
                    width = if (isFocused) 2.5.dp else 0.5.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            val img = if (isLandscape && movie.backdropUrl.isNotBlank()) movie.backdropUrl else movie.coverUrl
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(img)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left language badge: "Hindi" (from Screenshot 2)
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                val langTag = if (movie.title.contains("Hindi", ignoreCase = true)) "Hindi"
                    else if (movie.country.isNotBlank() && movie.country != "Story TV") movie.country
                    else movie.genre.split("•").firstOrNull()?.trim() ?: "Drama"
                Text(
                    text = langTag,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Top-right corner tag (e.g. "HD", "4K", or episode count)
            if (movie.corner.isNotBlank()) {
                Surface(
                    color = MovieBoxRed.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = movie.corner,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title below card (matching Screenshot 2: "Hanuman Ans..", "Moana [Hindi]")
        Text(
            text = movie.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
