package com.example.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.LookrApiClient
import com.example.data.api.LookrCategory
import com.example.data.api.LookrSubTag
import com.example.data.download.DownloadItem
import com.example.data.model.AppServer
import com.example.data.model.CategorySection
import com.example.data.model.HeroBanner
import com.example.data.model.HomeFeedData
import com.example.data.model.MovieItem
import com.example.data.repository.MovieBoxRepository
import com.example.data.model.toMovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object Home : AppScreen
    data object Search : AppScreen
    data object Downloads : AppScreen
    data object WatchHistory : AppScreen
    data class Genre(
        val section: CategorySection,
        val isShortsPage: Boolean = false,
        val isLandscape: Boolean = false
    ) : AppScreen
    data class MovieDetail(
        val movie: MovieItem,
        val playlist: List<MovieItem> = emptyList(),
        val isFromShortsPage: Boolean = false,
        val isDownloaded: Boolean = false,
        val downloadedSeason: Int = 0,
        val downloadedEpisode: Int = 0,
        val initialSeekMs: Long = 0L
    ) : AppScreen
    data class Shorts(
        val movie: MovieItem,
        val playlist: List<MovieItem> = emptyList(),
        val initialEpisode: Int = 1
    ) : AppScreen
}

data class HomeUiState(
    val feedData: HomeFeedData = HomeFeedData(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategoryFilter: String = "All",
    val selectedPlatform: String? = null,
    val searchQuery: String = "",
    val committedSearchQuery: String = "",
    val searchSuggestions: List<String> = emptyList(),
    val isSearchingSuggestions: Boolean = false,
    val isSearchingMovies: Boolean = false,
    val isMovieBoxSearchAutoLoading: Boolean = false,
    val hasMoreSearchResults: Boolean = false,
    val isSearchOpen: Boolean = false,
    val selectedMovie: MovieItem? = null,
    val playingMovie: MovieItem? = null,
    val watchlistIds: Set<String> = emptySet(),
    val filteredSections: List<CategorySection> = emptyList(),
    val searchResults: List<MovieItem> = emptyList(),
    val selectedSectionPage: CategorySection? = null,
    val isGenrePageShorts: Boolean = false,
    val isGenrePageLandscape: Boolean = false,
    val genrePageMovies: List<MovieItem> = emptyList(),
    val isGenrePageLoading: Boolean = false,
    val isGenrePageLoadingMore: Boolean = false,
    val genrePageHasMore: Boolean = false,
    val genrePageCurrentPage: Int = 1,
    val screenStack: List<AppScreen> = listOf(AppScreen.Home),
    val activeServer: AppServer = AppServer.SERVER_1,
    val shortsTvFeedData: HomeFeedData = HomeFeedData(),
    val shortsTvRecommendList: List<MovieItem> = emptyList(),
    val isShortsTvLoading: Boolean = false,
    val isShortsTvRecommendLoadingMore: Boolean = false,
    val shortsTvRecommendHasMore: Boolean = true,
    val shortsTvRecommendPage: Int = 1,
    val vskitFilterShortsList: List<MovieItem> = emptyList(),
    val vskitFilterPage: Int = 1,
    val vskitFilterHasMore: Boolean = true,
    val isVskitFilterLoadingMore: Boolean = false,
    val lookrCategories: List<LookrCategory> = LookrApiClient.CATEGORIES,
    val selectedLookrCategory: LookrCategory = LookrApiClient.CATEGORIES.first(),
    val selectedLookrSubTag: LookrSubTag = LookrApiClient.CATEGORIES.first().subTags.firstOrNull() ?: LookrSubTag("", "", "0"),
    val lookrItems: List<MovieItem> = emptyList(),
    val isLookrLoading: Boolean = false,
    val isLookrLoadingMore: Boolean = false,
    val lookrHasMore: Boolean = true,
    val lookrPage: Int = 1,
    val storyTvItems: List<MovieItem> = emptyList(),
    val isStoryTvLoading: Boolean = false,
    val isStoryTvLoadingMore: Boolean = false,
    val storyTvHasMore: Boolean = true,
    val storyTvPage: Int = 0,
    val storyTvLanguages: List<com.example.data.api.StoryTvLanguage> = emptyList(),
    val selectedStoryTvLanguage: com.example.data.api.StoryTvLanguage? = null,
    val freeReelsItems: List<MovieItem> = emptyList(),
    val isFreeReelsLoading: Boolean = false,
    val isFreeReelsLoadingMore: Boolean = false,
    val freeReelsHasMore: Boolean = true,
    val freeReelsNextCursor: String = ""
) {
    val currentScreen: AppScreen get() = screenStack.lastOrNull() ?: AppScreen.Home
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieBoxRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var vskitAutoLoadJob: Job? = null
    private var lookrLoadJob: Job? = null
    private var movieBoxSearchPage = 1
    private var movieBoxSearchHasMore = false
    private var isMovieBoxSearchLoadingNextPage = false

    init {
        com.example.data.repository.WatchHistoryManager.init(application)
        com.example.data.repository.RecentSearchesManager.init(application)
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getHomeFeedStream().collectLatest { feed ->
                val currentCategory = _uiState.value.selectedCategoryFilter
                val currentPlatform = _uiState.value.selectedPlatform
                val filtered = computeFilteredSections(
                    feed.sections,
                    currentCategory,
                    currentPlatform
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            feedData = feed,
                            isLoading = false,
                            filteredSections = filtered
                        )
                    }
                }
            }
        }
    }

    fun switchServer(server: AppServer) {
        if (_uiState.value.activeServer == server) return
        _uiState.update { it.copy(activeServer = server) }
        if (server == AppServer.SERVER_2 && _uiState.value.shortsTvFeedData.sections.isEmpty()) {
            loadShortsTvData()
        }
        if (server == AppServer.SERVER_3 && _uiState.value.lookrItems.isEmpty()) {
            loadLookrFeed()
        }
        if (server == AppServer.SERVER_4 && _uiState.value.storyTvItems.isEmpty()) {
            loadStoryTvFeed()
        }
        if (server == AppServer.SERVER_5 && _uiState.value.freeReelsItems.isEmpty()) {
            loadFreeReelsFeed()
        }
    }

    fun selectLookrCategory(category: LookrCategory) {
        if (_uiState.value.selectedLookrCategory.tagName == category.tagName) return
        val defaultSubTag = category.subTags.firstOrNull() ?: LookrSubTag("", "", "0")
        lookrLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedLookrCategory = category,
                selectedLookrSubTag = defaultSubTag,
                lookrItems = emptyList(),
                lookrPage = 1,
                lookrHasMore = true,
                isLookrLoading = true,
                isLookrLoadingMore = false
            )
        }
        loadLookrFeed(isRefresh = true)
    }

    fun selectLookrSubTag(subTag: LookrSubTag) {
        if (_uiState.value.selectedLookrSubTag.tagName == subTag.tagName) return
        lookrLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedLookrSubTag = subTag,
                lookrItems = emptyList(),
                lookrPage = 1,
                lookrHasMore = true,
                isLookrLoading = true,
                isLookrLoadingMore = false
            )
        }
        loadLookrFeed(isRefresh = true)
    }

    fun loadLookrFeed(isRefresh: Boolean = false) {
        lookrLoadJob?.cancel()
        lookrLoadJob = viewModelScope.launch {
            val currentCategory = _uiState.value.selectedLookrCategory
            val currentSubTag = _uiState.value.selectedLookrSubTag

            _uiState.update {
                it.copy(
                    isLookrLoading = true,
                    isLookrLoadingMore = false,
                    lookrItems = if (isRefresh) emptyList() else it.lookrItems
                )
            }

            try {
                // Pre-fetching Initial Pages: Shuru me page 1 ke sath-sath page 2 ka data bhi fetch kiya jata hai
                val (page1Items, hasMore1) = LookrApiClient.fetchRecommend(
                    tag = currentCategory.tagName,
                    subTag = currentSubTag.tagName,
                    opId = currentSubTag.opId,
                    page = 1,
                    pageSize = 10
                )

                var combinedItems = page1Items
                var finalPage = 1
                var finalHasMore = hasMore1

                if (hasMore1 && page1Items.isNotEmpty()) {
                    try {
                        val (page2Items, hasMore2) = LookrApiClient.fetchRecommend(
                            tag = currentCategory.tagName,
                            subTag = currentSubTag.tagName,
                            opId = currentSubTag.opId,
                            page = 2,
                            pageSize = 10
                        )
                        if (page2Items.isNotEmpty()) {
                            val existingIds = page1Items.map { it.id }.toSet()
                            val uniquePage2 = page2Items.filter { it.id !in existingIds }
                            combinedItems = page1Items + uniquePage2
                            finalPage = 2
                            finalHasMore = hasMore2
                        }
                    } catch (e: Exception) {
                        Log.w("HomeViewModel", "Error pre-fetching Lookr page 2", e)
                    }
                }

                _uiState.update {
                    it.copy(
                        lookrItems = combinedItems,
                        lookrPage = finalPage,
                        lookrHasMore = finalHasMore,
                        isLookrLoading = false,
                        isLookrLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading Lookr feed", e)
                _uiState.update {
                    it.copy(isLookrLoading = false, isLookrLoadingMore = false)
                }
            }
        }
    }

    fun loadMoreLookrItems() {
        if (_uiState.value.isLookrLoading || _uiState.value.isLookrLoadingMore || !_uiState.value.lookrHasMore) return
        _uiState.update { it.copy(isLookrLoadingMore = true) }
        viewModelScope.launch {
            val nextPage = _uiState.value.lookrPage + 1
            val currentCategory = _uiState.value.selectedLookrCategory
            val currentSubTag = _uiState.value.selectedLookrSubTag
            try {
                val (newItems, hasMore) = LookrApiClient.fetchRecommend(
                    tag = currentCategory.tagName,
                    subTag = currentSubTag.tagName,
                    opId = currentSubTag.opId,
                    page = nextPage,
                    pageSize = 10
                )
                _uiState.update { state ->
                    val existingIds = state.lookrItems.map { it.id }.toSet()
                    val filtered = newItems.filter { it.id !in existingIds }
                    state.copy(
                        lookrItems = state.lookrItems + filtered,
                        lookrHasMore = hasMore && newItems.isNotEmpty(),
                        lookrPage = nextPage,
                        isLookrLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading more Lookr items page=$nextPage", e)
                _uiState.update { it.copy(isLookrLoadingMore = false) }
            }
        }
    }

    fun toggleServer() {
        val nextServer = when (_uiState.value.activeServer) {
            AppServer.SERVER_1 -> AppServer.SERVER_2
            AppServer.SERVER_2 -> AppServer.SERVER_3
            AppServer.SERVER_3 -> AppServer.SERVER_4
            AppServer.SERVER_4 -> AppServer.SERVER_5
            AppServer.SERVER_5 -> AppServer.SERVER_6
            AppServer.SERVER_6 -> AppServer.SERVER_1
        }
        switchServer(nextServer)
    }

    /**
     * Server 4: Story TV Feed Loader
     */
    fun loadStoryTvFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isStoryTvLoading = true, storyTvPage = 0, storyTvHasMore = true) }
            } else {
                _uiState.update { it.copy(isStoryTvLoading = it.storyTvItems.isEmpty()) }
            }
            try {
                if (_uiState.value.storyTvLanguages.isEmpty()) {
                    val langs = repository.fetchStoryTvLanguages()
                    val preSelected = langs.find { it.isPreSelected } ?: langs.firstOrNull()
                    _uiState.update { it.copy(storyTvLanguages = langs, selectedStoryTvLanguage = preSelected) }
                }

                val (items, hasMore) = repository.fetchStoryTvShows(page = 0, size = 50)
                _uiState.update {
                    it.copy(
                        storyTvItems = items,
                        storyTvHasMore = hasMore,
                        storyTvPage = 0,
                        isStoryTvLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading Story TV feed", e)
                _uiState.update { it.copy(isStoryTvLoading = false) }
            }
        }
    }

    fun loadMoreStoryTvItems() {
        val state = _uiState.value
        if (state.isStoryTvLoading || state.isStoryTvLoadingMore || !state.storyTvHasMore) return
        val nextPage = state.storyTvPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isStoryTvLoadingMore = true) }
            try {
                val (items, hasMore) = repository.fetchStoryTvShows(page = nextPage, size = 50)
                _uiState.update {
                    val existingIds = it.storyTvItems.map { item -> item.id }.toSet()
                    val filtered = items.filter { item -> item.id !in existingIds }
                    it.copy(
                        storyTvItems = it.storyTvItems + filtered,
                        storyTvHasMore = hasMore && items.isNotEmpty(),
                        storyTvPage = nextPage,
                        isStoryTvLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading more Story TV items page=$nextPage", e)
                _uiState.update { it.copy(isStoryTvLoadingMore = false) }
            }
        }
    }

    fun selectStoryTvLanguage(language: com.example.data.api.StoryTvLanguage) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedStoryTvLanguage = language, isStoryTvLoading = true, storyTvItems = emptyList()) }
            try {
                repository.selectStoryTvLanguage(language.langId)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error selecting Story TV language", e)
            }
            loadStoryTvFeed(isRefresh = true)
        }
    }

    /**
     * Server 5: FreeReels Feed Loader
     */
    fun loadFreeReelsFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isFreeReelsLoading = true, freeReelsNextCursor = "", freeReelsHasMore = true) }
            } else {
                _uiState.update { it.copy(isFreeReelsLoading = it.freeReelsItems.isEmpty()) }
            }
            try {
                val (items, nextCursor) = repository.fetchFreeReelsHome("503")
                _uiState.update {
                    it.copy(
                        freeReelsItems = items,
                        freeReelsNextCursor = nextCursor ?: "",
                        freeReelsHasMore = !nextCursor.isNullOrBlank(),
                        isFreeReelsLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading FreeReels feed", e)
                _uiState.update { it.copy(isFreeReelsLoading = false) }
            }
        }
    }

    fun loadMoreFreeReelsItems() {
        val state = _uiState.value
        if (state.isFreeReelsLoading || state.isFreeReelsLoadingMore || !state.freeReelsHasMore || state.freeReelsNextCursor.isBlank()) return
        val cursor = state.freeReelsNextCursor
        viewModelScope.launch {
            _uiState.update { it.copy(isFreeReelsLoadingMore = true) }
            try {
                val (items, pageInfo) = repository.fetchFreeReelsFeed(cursor)
                val (newCursor, hasMore) = pageInfo
                _uiState.update {
                    val existingIds = it.freeReelsItems.map { item -> item.id }.toSet()
                    val filtered = items.filter { item -> item.id !in existingIds }
                    it.copy(
                        freeReelsItems = it.freeReelsItems + filtered,
                        freeReelsNextCursor = newCursor ?: "",
                        freeReelsHasMore = hasMore && !newCursor.isNullOrBlank(),
                        isFreeReelsLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading more FreeReels items", e)
                _uiState.update { it.copy(isFreeReelsLoadingMore = false) }
            }
        }
    }

    fun loadShortsTvData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isShortsTvLoading = it.shortsTvFeedData.sections.isEmpty() && it.vskitFilterShortsList.isEmpty()) }
            try {
                val feed = repository.refreshShortsTvFeed()
                val (filterList, filterHasMore) = repository.fetchFilterShortsList(page = 1, perPage = 24, channelId = 1012)
                _uiState.update {
                    it.copy(
                        shortsTvFeedData = feed,
                        vskitFilterShortsList = filterList,
                        vskitFilterHasMore = filterHasMore,
                        vskitFilterPage = 1,
                        isShortsTvLoading = false
                    )
                }
                // Automatic background load without requiring scroll
                startVskitAutoLoad()
            } catch (_: Exception) {
                _uiState.update { it.copy(isShortsTvLoading = false) }
            }
        }
    }

    /**
     * Automatically loads all available pages sequentially in background
     * without requiring user to scroll, keeping UI smooth.
     */
    fun startVskitAutoLoad() {
        vskitAutoLoadJob?.cancel()
        vskitAutoLoadJob = viewModelScope.launch {
            var currentPage = 2
            var hasMore = true
            // Pre-load up to 20 pages automatically in background
            while (hasMore && currentPage <= 20 && isActive) {
                try {
                    delay(350)
                    val (moreList, more) = repository.fetchFilterShortsList(
                        page = currentPage,
                        perPage = 24,
                        channelId = 1012
                    )
                    hasMore = more && moreList.isNotEmpty()
                    if (moreList.isNotEmpty()) {
                        _uiState.update { state ->
                            val combined = (state.vskitFilterShortsList + moreList).distinctBy { m -> m.id }
                            state.copy(
                                vskitFilterShortsList = combined,
                                vskitFilterPage = currentPage,
                                vskitFilterHasMore = hasMore,
                                isVskitFilterLoadingMore = false
                            )
                        }
                    } else {
                        hasMore = false
                        break
                    }
                    currentPage++
                } catch (_: Exception) {
                    hasMore = false
                    break
                }
            }
            // When auto-loading completes, mark hasMore to false and clear loading state so bottom indicator never lingers
            _uiState.update { state ->
                state.copy(
                    vskitFilterHasMore = false,
                    isVskitFilterLoadingMore = false
                )
            }
        }
    }

    /**
     * Infinite loader for VSKit filter API when user reaches bottom
     */
    fun loadMoreVskitFilterShorts() {
        val current = _uiState.value
        if (current.isVskitFilterLoadingMore || !current.vskitFilterHasMore || vskitAutoLoadJob?.isActive == true) return
        val nextPage = current.vskitFilterPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isVskitFilterLoadingMore = true) }
            try {
                val (moreList, hasMore) = repository.fetchFilterShortsList(
                    page = nextPage,
                    perPage = 24,
                    channelId = 1012
                )
                val effectiveHasMore = hasMore && moreList.isNotEmpty()
                _uiState.update {
                    it.copy(
                        vskitFilterShortsList = (it.vskitFilterShortsList + moreList).distinctBy { m -> m.id },
                        vskitFilterHasMore = effectiveHasMore,
                        vskitFilterPage = nextPage,
                        isVskitFilterLoadingMore = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isVskitFilterLoadingMore = false, vskitFilterHasMore = false) }
            }
        }
    }

    fun loadMoreShortsTvRecommend() {
        val current = _uiState.value
        if (current.isShortsTvRecommendLoadingMore || !current.shortsTvRecommendHasMore) return
        val nextPage = current.shortsTvRecommendPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isShortsTvRecommendLoadingMore = true) }
            try {
                val (moreList, hasMore) = repository.fetchShortsRecommendList(page = nextPage)
                _uiState.update {
                    it.copy(
                        shortsTvRecommendList = (it.shortsTvRecommendList + moreList).distinctBy { m -> m.id },
                        shortsTvRecommendHasMore = hasMore,
                        shortsTvRecommendPage = nextPage,
                        isShortsTvRecommendLoadingMore = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isShortsTvRecommendLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            if (_uiState.value.activeServer == AppServer.SERVER_2) {
                try {
                    val feed = repository.refreshShortsTvFeed()
                    val (filterList, filterHasMore) = repository.fetchFilterShortsList(page = 1, perPage = 24, channelId = 1012)
                    _uiState.update {
                        it.copy(
                            shortsTvFeedData = feed,
                            vskitFilterShortsList = filterList,
                            vskitFilterHasMore = filterHasMore,
                            vskitFilterPage = 1,
                            isRefreshing = false
                        )
                    }
                    startVskitAutoLoad()
                } catch (_: Exception) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            } else {
                val updatedFeed = repository.refreshHomeFeed()
                _uiState.update { state ->
                    val filtered = computeFilteredSections(
                        updatedFeed.sections,
                        state.selectedCategoryFilter,
                        state.selectedPlatform
                    )
                    state.copy(
                        feedData = updatedFeed,
                        isRefreshing = false,
                        filteredSections = filtered
                    )
                }
            }
        }
    }

    fun selectCategoryFilter(filter: String) {
        _uiState.update { state ->
            val newFilter = if (state.selectedCategoryFilter == filter) "All" else filter
            val filtered = computeFilteredSections(
                state.feedData.sections,
                newFilter,
                state.selectedPlatform
            )
            state.copy(
                selectedCategoryFilter = newFilter,
                filteredSections = filtered
            )
        }
    }

    fun selectPlatform(platform: String?) {
        _uiState.update { state ->
            val newPlatform = if (state.selectedPlatform == platform) null else platform
            val filtered = computeFilteredSections(
                state.feedData.sections,
                state.selectedCategoryFilter,
                newPlatform
            )
            state.copy(
                selectedPlatform = newPlatform,
                filteredSections = filtered
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        // Only update typing input and query live search suggestions - do NOT navigate or show search results yet!
        _uiState.update {
            it.copy(
                searchQuery = query,
            )
        }

        suggestJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    searchSuggestions = emptyList(),
                    isSearchingSuggestions = false,
                    committedSearchQuery = "",
                    searchResults = emptyList()
                )
            }
            return
        }

        suggestJob = viewModelScope.launch {
            delay(150) // fast debounce typing
            _uiState.update { it.copy(isSearchingSuggestions = true) }
            val suggestions = when (_uiState.value.activeServer) {
                AppServer.SERVER_5 -> {
                    val remoteSuggestions = try {
                        com.example.data.api.FreeReelsApiClient.fetchSearchKeywords(trimmed)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val localSuggestions = _uiState.value.freeReelsItems.map { it.title }
                        .filter { it.contains(trimmed, ignoreCase = true) }
                        .distinct()
                    (remoteSuggestions + localSuggestions).distinct().take(10)
                }
                AppServer.SERVER_4 -> {
                    val remoteSuggestions = try {
                        repository.searchStoryTv(trimmed).map { it.title }.filter { it.isNotBlank() }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val localSuggestions = _uiState.value.storyTvItems.map { it.title }
                        .filter { it.contains(trimmed, ignoreCase = true) }
                    (remoteSuggestions + localSuggestions).distinct().take(10)
                }
                AppServer.SERVER_3 -> {
                    val remoteSuggestions = try {
                        LookrApiClient.fetchSearchSuggestions(trimmed)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val localSuggestions = _uiState.value.lookrItems.map { it.title }
                        .filter { it.contains(trimmed, ignoreCase = true) }
                        .distinct()
                    (remoteSuggestions + localSuggestions).distinct().take(10)
                }
                AppServer.SERVER_2 -> {
                    val remoteSuggestions = try {
                        repository.fetchShortsSearchSuggestions(trimmed)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val localItems = _uiState.value.shortsTvFeedData.sections.flatMap { it.items } + _uiState.value.shortsTvRecommendList
                    val localSuggestions = localItems.map { it.title }
                        .filter { it.contains(trimmed, ignoreCase = true) }
                        .distinct()
                    (remoteSuggestions + localSuggestions).distinct().take(10)
                }
                else -> {
                    val remoteSuggestions = try {
                        repository.fetchSearchSuggestions(trimmed)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val localSuggestions = if (remoteSuggestions.isEmpty()) {
                        _uiState.value.feedData.sections.flatMap { it.items }
                            .map { it.title }
                            .filter { it.contains(trimmed, ignoreCase = true) }
                            .distinct()
                            .take(8)
                    } else {
                        emptyList()
                    }
                    (remoteSuggestions + localSuggestions).distinct().take(8)
                }
            }
            _uiState.update {
                it.copy(
                    searchSuggestions = suggestions,
                    isSearchingSuggestions = false
                )
            }
        }
    }

    /**
     * Triggered ONLY when the user presses Enter on the keyboard OR selects a suggestion!
     * As explicitly requested: "jab tab search par type karke enter na kare ya suggetion select na kare search result search search par nhi jayega ok"
     */
    fun submitSearch(query: String? = null) {
        val q = (query ?: _uiState.value.searchQuery).trim()
        if (q.isBlank()) return

        com.example.data.repository.RecentSearchesManager.addSearchQuery(_uiState.value.activeServer, q)

        suggestJob?.cancel()
        searchJob?.cancel()

        _uiState.update {
            it.copy(
                searchQuery = q,
                committedSearchQuery = q,
                searchSuggestions = emptyList(),
                isSearchingSuggestions = false,
                isSearchingMovies = true
            )
        }

        searchJob = viewModelScope.launch {
            if (_uiState.value.activeServer == AppServer.SERVER_5) {
                // Server 5 (FreeReels) search
                val freeReelsMatches = try {
                    repository.searchFreeReels(q)
                } catch (_: Exception) {
                    emptyList()
                }
                val localMatches = _uiState.value.freeReelsItems
                    .distinctBy { it.id }
                    .filter {
                        it.title.contains(q, ignoreCase = true) ||
                                it.genre.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true)
                    }
                val combined = (freeReelsMatches + localMatches).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        searchResults = if (combined.isNotEmpty()) combined else localMatches,
                        isSearchingMovies = false,
                        hasMoreSearchResults = false,
                        isMovieBoxSearchAutoLoading = false
                    )
                }
            } else if (_uiState.value.activeServer == AppServer.SERVER_4) {
                // Server 4 (Story TV) search
                val storyMatches = try {
                    repository.searchStoryTv(q)
                } catch (_: Exception) {
                    emptyList()
                }
                val localMatches = _uiState.value.storyTvItems
                    .distinctBy { it.id }
                    .filter {
                        it.title.contains(q, ignoreCase = true) ||
                                it.genre.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true)
                    }
                val combined = (storyMatches + localMatches).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        searchResults = if (combined.isNotEmpty()) combined else localMatches,
                        isSearchingMovies = false,
                        hasMoreSearchResults = false,
                        isMovieBoxSearchAutoLoading = false
                    )
                }
            } else if (_uiState.value.activeServer == AppServer.SERVER_3) {
                // Server 3 (Lookr) search
                val lookrMatches = try {
                    LookrApiClient.searchMovies(q)
                } catch (_: Exception) {
                    emptyList()
                }
                val localMatches = _uiState.value.lookrItems
                    .distinctBy { it.id }
                    .filter {
                        it.title.contains(q, ignoreCase = true) ||
                                it.genre.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true)
                    }

                val combined = (lookrMatches + localMatches).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        searchResults = if (combined.isNotEmpty()) combined else localMatches,
                        isSearchingMovies = false
                    )
                }
            } else if (_uiState.value.activeServer == AppServer.SERVER_2) {
                // Server 2 search
                val vskitMatches = try {
                    repository.searchShorts(q)
                } catch (_: Exception) {
                    emptyList()
                }
                val localMatches = (_uiState.value.shortsTvFeedData.sections.flatMap { it.items } + _uiState.value.shortsTvRecommendList)
                    .distinctBy { it.id }
                    .filter {
                        it.title.contains(q, ignoreCase = true) ||
                                it.genre.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true)
                    }

                val combined = (vskitMatches + localMatches).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        searchResults = if (combined.isNotEmpty()) combined else localMatches,
                        isSearchingMovies = false
                    )
                }
            } else {
                // Server 1 search (MovieBox Server)
                val localMatches = _uiState.value.feedData.sections.flatMap { it.items }
                    .distinctBy { it.id.ifBlank { it.title } }
                    .filter {
                        it.title.contains(q, ignoreCase = true) ||
                                it.genre.contains(q, ignoreCase = true) ||
                                it.country.contains(q, ignoreCase = true)
                    }

                movieBoxSearchPage = 1
                movieBoxSearchHasMore = false
                isMovieBoxSearchLoadingNextPage = false

                // 1. Fetch Page 1 immediately
                val (page1Matches, initialHasMore) = try {
                    repository.searchMoviesPage(q, page = 1, perPage = 20)
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Remote search error page 1 for '$q'", e)
                    Pair(emptyList<MovieItem>(), false)
                }

                movieBoxSearchPage = 1
                movieBoxSearchHasMore = initialHasMore

                val initialCombined = if (page1Matches.isNotEmpty()) {
                    (page1Matches + localMatches).distinctBy { it.id.ifBlank { it.title } }
                } else {
                    localMatches
                }

                // Show page 1 results immediately so user has zero wait time
                _uiState.update {
                    it.copy(
                        searchResults = initialCombined,
                        isSearchingMovies = false,
                        isMovieBoxSearchAutoLoading = initialHasMore,
                        hasMoreSearchResults = initialHasMore
                    )
                }

                // 2. Auto-load all remaining pages continuously in background without needing scroll
                var currentPage = 1
                var hasMore = initialHasMore
                val accumulated = initialCombined.toMutableList()

                while (isActive && hasMore) {
                    currentPage++
                    movieBoxSearchPage = currentPage
                    isMovieBoxSearchLoadingNextPage = true

                    val (nextItems, nextHasMore) = try {
                        repository.searchMoviesPage(q, page = currentPage, perPage = 20)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Remote search error page $currentPage for '$q'", e)
                        Pair(emptyList<MovieItem>(), false)
                    }

                    isMovieBoxSearchLoadingNextPage = false
                    hasMore = nextHasMore && nextItems.isNotEmpty()
                    movieBoxSearchHasMore = hasMore

                    if (nextItems.isNotEmpty()) {
                        val existingKeys = accumulated.map { it.id.ifBlank { it.title } }.toSet()
                        val newUnique = nextItems.filter { it.id.ifBlank { it.title } !in existingKeys }
                        if (newUnique.isNotEmpty()) {
                            accumulated.addAll(newUnique)
                            _uiState.update {
                                it.copy(
                                    searchResults = accumulated.toList(),
                                    isMovieBoxSearchAutoLoading = hasMore,
                                    hasMoreSearchResults = hasMore
                                )
                            }
                        }
                    } else {
                        hasMore = false
                        movieBoxSearchHasMore = false
                    }

                    // Yield and brief pause between pages so UI stays responsive and updates smoothly
                    delay(60)
                }

                _uiState.update {
                    it.copy(
                        isMovieBoxSearchAutoLoading = false,
                        hasMoreSearchResults = false
                    )
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        if (_uiState.value.activeServer != AppServer.SERVER_1) return
        val q = _uiState.value.committedSearchQuery.trim()
        if (q.isBlank() || !movieBoxSearchHasMore || isMovieBoxSearchLoadingNextPage) return

        viewModelScope.launch {
            if (!movieBoxSearchHasMore || isMovieBoxSearchLoadingNextPage) return@launch
            isMovieBoxSearchLoadingNextPage = true
            _uiState.update { it.copy(isMovieBoxSearchAutoLoading = true) }

            val nextPage = movieBoxSearchPage + 1
            val (items, hasMore) = try {
                repository.searchMoviesPage(q, page = nextPage, perPage = 20)
            } catch (e: Exception) {
                Pair(emptyList<MovieItem>(), false)
            }

            movieBoxSearchPage = nextPage
            movieBoxSearchHasMore = hasMore && items.isNotEmpty()
            isMovieBoxSearchLoadingNextPage = false

            if (items.isNotEmpty()) {
                _uiState.update { state ->
                    val existingKeys = state.searchResults.map { it.id.ifBlank { it.title } }.toSet()
                    val newUnique = items.filter { it.id.ifBlank { it.title } !in existingKeys }
                    state.copy(
                        searchResults = state.searchResults + newUnique,
                        isMovieBoxSearchAutoLoading = movieBoxSearchHasMore,
                        hasMoreSearchResults = movieBoxSearchHasMore
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isMovieBoxSearchAutoLoading = false,
                        hasMoreSearchResults = false
                    )
                }
            }
        }
    }

    fun clearSearch() {
        suggestJob?.cancel()
        searchJob?.cancel()
        movieBoxSearchPage = 1
        movieBoxSearchHasMore = false
        isMovieBoxSearchLoadingNextPage = false
        _uiState.update {
            it.copy(
                searchQuery = "",
                committedSearchQuery = "",
                searchSuggestions = emptyList(),
                searchResults = emptyList(),
                isSearchingMovies = false,
                isSearchingSuggestions = false,
                isMovieBoxSearchAutoLoading = false,
                hasMoreSearchResults = false
            )
        }
    }

    fun openSearch() {
        clearSearch()
        _uiState.update {
            it.copy(
                isSearchOpen = true,
                screenStack = it.screenStack + AppScreen.Search
            )
        }
    }

    fun openSearchWithQuery(query: String) {
        val q = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = q,
                committedSearchQuery = q,
                isSearchOpen = true,
                screenStack = it.screenStack + AppScreen.Search
            )
        }
    }

    fun toggleSearch(open: Boolean? = null) {
        val currentlyOpen = _uiState.value.currentScreen is AppScreen.Search
        val targetOpen = open ?: !currentlyOpen
        if (targetOpen) {
            openSearch()
        } else {
            navigateBack()
        }
    }

    fun openDownloads() {
        _uiState.update {
            it.copy(
                screenStack = it.screenStack + AppScreen.Downloads
            )
        }
    }

    fun openWatchHistory() {
        _uiState.update {
            it.copy(
                screenStack = it.screenStack + AppScreen.WatchHistory
            )
        }
    }

    fun clearRecentSearches() {
        com.example.data.repository.RecentSearchesManager.clearRecentSearches(_uiState.value.activeServer)
    }

    fun removeRecentSearch(query: String) {
        com.example.data.repository.RecentSearchesManager.removeSearchQuery(_uiState.value.activeServer, query)
    }

    fun playHistoryItem(item: com.example.data.model.WatchHistoryItem) {
        val isStorySource = item.source.equals("storytv", ignoreCase = true)
        val isFreeReelsSource = item.source.equals("freereels", ignoreCase = true)
        val isVskitSource = item.source.equals("vskit", ignoreCase = true)
        val shouldPlayInShorts = item.isShortsPlayer ||
            item.subjectType == 7 ||
            isVskitSource ||
            isStorySource ||
            isFreeReelsSource ||
            (item.isSeries && item.season == 0 && item.episode > 0)

        val resolvedSubjectType = when {
            shouldPlayInShorts && item.subjectType <= 1 -> 7
            item.isSeries && !shouldPlayInShorts && item.subjectType <= 1 -> 2
            else -> item.subjectType
        }

        val movie = MovieItem(
            id = item.movieId,
            title = item.title,
            description = item.genre,
            coverUrl = item.coverUrl,
            backdropUrl = item.backdropUrl,
            rating = "",
            genre = item.genre,
            releaseDate = "",
            releaseYear = "",
            country = "",
            corner = "",
            detailPath = item.detailPath,
            isShort = shouldPlayInShorts,
            subjectType = resolvedSubjectType,
            source = item.source,
            isSeries = item.isSeries,
            isVskitServer = isVskitSource,
            isStoryTvServer = isStorySource,
            isFreeReelsServer = isFreeReelsSource
        )

        if (shouldPlayInShorts) {
            val targetEp = if (item.episode > 0) item.episode else 1
            openShortsPlayer(movie, initialEpisode = targetEp)
        } else {
            _uiState.update {
                it.copy(
                    selectedMovie = movie,
                    playingMovie = movie,
                    screenStack = it.screenStack + AppScreen.MovieDetail(
                        movie = movie,
                        playlist = emptyList(),
                        isFromShortsPage = false,
                        isDownloaded = false,
                        downloadedSeason = if (item.isSeries) item.season.coerceAtLeast(1) else 0,
                        downloadedEpisode = if (item.isSeries) item.episode.coerceAtLeast(1) else 0,
                        initialSeekMs = item.lastPositionMs
                    )
                )
            }
        }
    }

    fun playOfflineMovie(downloadItem: DownloadItem) {
        val movie = MovieItem(
            id = downloadItem.movieId,
            title = downloadItem.title,
            description = "Downloaded Offline Video (${downloadItem.quality})",
            coverUrl = downloadItem.coverUrl,
            backdropUrl = downloadItem.backdropUrl,
            rating = "10",
            genre = downloadItem.dubLabel.ifBlank { "Offline" },
            releaseDate = "",
            releaseYear = "",
            country = "",
            corner = downloadItem.quality,
            detailPath = "",
            directUrl = downloadItem.localFilePath,
            isShort = false,
            dubs = if (downloadItem.dubLabel.isNotBlank()) listOf(com.example.data.model.DubLanguage(lanName = downloadItem.dubLabel)) else emptyList()
        )
        _uiState.update {
            it.copy(
                selectedMovie = movie,
                playingMovie = movie,
                screenStack = it.screenStack + AppScreen.MovieDetail(
                    movie = movie,
                    isDownloaded = true,
                    downloadedSeason = downloadItem.seasonNumber,
                    downloadedEpisode = downloadItem.episodeNumber
                )
            )
        }
    }

    /**
     * Opens dedicated page for a row, displaying its items immediately,
     * and fetching more items via genreTopId from API.
     */
    fun openSectionPage(
        section: CategorySection,
        isShortsSection: Boolean = false,
        isLandscape: Boolean = false
    ) {
        val effectiveIsShorts = isShortsSection || section.isHotShortTvSection
        val effectiveIsLandscape = isLandscape || section.isLandscapeDetected

        _uiState.update {
            it.copy(
                selectedSectionPage = section,
                isGenrePageShorts = effectiveIsShorts,
                isGenrePageLandscape = effectiveIsLandscape,
                genrePageMovies = section.items,
                genrePageCurrentPage = 1,
                genrePageHasMore = section.genreTopId.isNotBlank(),
                isGenrePageLoading = section.genreTopId.isNotBlank(),
                screenStack = it.screenStack + AppScreen.Genre(section, effectiveIsShorts, effectiveIsLandscape)
            )
        }

        if (section.genreTopId.isNotBlank()) {
            viewModelScope.launch {
                val (fetchedMovies, hasMore) = try {
                    repository.fetchGenreRanking(section.genreTopId, page = 1, perPage = 20)
                } catch (_: Exception) {
                    Pair(emptyList(), false)
                }

                _uiState.update { state ->
                    if (state.selectedSectionPage?.id == section.id) {
                        val merged = if (fetchedMovies.isNotEmpty()) {
                            (fetchedMovies + section.items).distinctBy { it.id.ifBlank { it.title } }
                        } else {
                            section.items
                        }
                        state.copy(
                            genrePageMovies = merged,
                            genrePageHasMore = hasMore,
                            isGenrePageLoading = false
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun loadMoreGenreMovies() {
        val state = _uiState.value
        val section = state.selectedSectionPage ?: return
        if (section.genreTopId.isBlank() || !state.genrePageHasMore || state.isGenrePageLoadingMore) return

        val nextPage = state.genrePageCurrentPage + 1
        _uiState.update { it.copy(isGenrePageLoadingMore = true) }

        viewModelScope.launch {
            val (newMovies, hasMore) = try {
                repository.fetchGenreRanking(section.genreTopId, page = nextPage, perPage = 20)
            } catch (_: Exception) {
                Pair(emptyList(), false)
            }

            _uiState.update { current ->
                if (current.selectedSectionPage?.id == section.id) {
                    val updatedList = (current.genrePageMovies + newMovies).distinctBy { it.id.ifBlank { it.title } }
                    current.copy(
                        genrePageMovies = updatedList,
                        genrePageCurrentPage = nextPage,
                        genrePageHasMore = hasMore,
                        isGenrePageLoadingMore = false
                    )
                } else {
                    current.copy(isGenrePageLoadingMore = false)
                }
            }
        }
    }

    fun closeSectionPage() {
        navigateBack()
    }

    fun openMovieDetail(
        movie: MovieItem,
        playlist: List<MovieItem> = emptyList(),
        isFromShortsPage: Boolean = false
    ) {
        if (_uiState.value.activeServer == AppServer.SERVER_2 ||
            _uiState.value.activeServer == AppServer.SERVER_4 ||
            _uiState.value.activeServer == AppServer.SERVER_5 ||
            movie.isStoryTvServer ||
            movie.isFreeReelsServer ||
            movie.source.equals("storytv", ignoreCase = true) ||
            movie.source.equals("freereels", ignoreCase = true)
        ) {
            openShortsPlayer(movie, playlist)
            return
        }
        _uiState.update {
            it.copy(
                selectedMovie = movie,
                screenStack = it.screenStack + AppScreen.MovieDetail(movie, playlist, isFromShortsPage = false)
            )
        }
    }

    fun openShortsPlayer(
        movie: MovieItem,
        playlist: List<MovieItem> = emptyList(),
        initialEpisode: Int = 1
    ) {
        _uiState.update {
            it.copy(
                selectedMovie = movie,
                screenStack = it.screenStack + AppScreen.Shorts(movie, playlist, initialEpisode = initialEpisode)
            )
        }
    }

    fun openBannerDetail(banner: HeroBanner) {
        val movie = banner.toMovieItem()
        if (_uiState.value.activeServer == AppServer.SERVER_2 ||
            _uiState.value.activeServer == AppServer.SERVER_4 ||
            _uiState.value.activeServer == AppServer.SERVER_5 ||
            movie.isStoryTvServer ||
            movie.isFreeReelsServer
        ) {
            openShortsPlayer(movie)
        } else {
            openMovieDetail(movie)
        }
    }

    fun closeMovieDetail() {
        navigateBack()
    }

    fun navigateBack(): Boolean {
        val currentStack = _uiState.value.screenStack
        if (currentStack.size > 1) {
            val newStack = currentStack.dropLast(1)
            val newScreen = newStack.lastOrNull() ?: AppScreen.Home
            _uiState.update { state ->
                state.copy(
                    screenStack = newStack,
                    isSearchOpen = newScreen is AppScreen.Search,
                    selectedMovie = when (newScreen) {
                        is AppScreen.MovieDetail -> newScreen.movie
                        is AppScreen.Shorts -> newScreen.movie
                        else -> null
                    },
                    selectedSectionPage = if (newScreen is AppScreen.Genre) newScreen.section else null,
                    isGenrePageShorts = if (newScreen is AppScreen.Genre) newScreen.isShortsPage else false
                )
            }
            return true
        }
        return false
    }

    fun playMovie(movie: MovieItem) {
        openMovieDetail(movie)
    }

    fun closePlayer() {
        _uiState.update { it.copy(playingMovie = null) }
    }

    fun toggleWatchlist(movieId: String) {
        _uiState.update { state ->
            val set = state.watchlistIds.toMutableSet()
            if (set.contains(movieId)) {
                set.remove(movieId)
            } else {
                set.add(movieId)
            }
            state.copy(watchlistIds = set)
        }
    }

    private fun computeFilteredSections(
        sections: List<CategorySection>,
        categoryFilter: String,
        platform: String?
    ): List<CategorySection> {
        var result = sections

        // Platform filter if active
        if (!platform.isNullOrBlank()) {
            result = result.filter { section ->
                section.title.contains(platform, ignoreCase = true) ||
                        section.items.any { it.title.contains(platform, ignoreCase = true) }
            }
            if (result.isEmpty()) {
                // If no exact match for platform in title, return all with platform badge highlight
                result = sections
            }
        }

        // Category tabs: All, Movies, TV Series, Shorts, Anime, K-Drama
        return when (categoryFilter) {
            "Movies" -> result.filter {
                it.title.contains("Movie", ignoreCase = true) ||
                        it.title.contains("Film", ignoreCase = true)
            }
            "TV Series" -> result.filter {
                it.title.contains("Series", ignoreCase = true) ||
                        it.title.contains("Sitcom", ignoreCase = true) ||
                        it.title.contains("Show", ignoreCase = true)
            }
            "Shorts" -> result.filter {
                it.title.contains("Short", ignoreCase = true) ||
                        it.title.contains("Fight Zone", ignoreCase = true) ||
                        it.items.any { item -> item.isShort }
            }
            "Anime" -> result.filter {
                it.title.contains("Anime", ignoreCase = true) ||
                        it.title.contains("Animation", ignoreCase = true)
            }
            "K-Drama" -> result.filter {
                it.title.contains("K-Drama", ignoreCase = true) ||
                        it.title.contains("C-Drama", ignoreCase = true)
            }
            else -> result
        }
    }
}
