package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.CastActor
import com.example.data.model.CategorySection
import com.example.data.model.DubLanguage
import com.example.data.model.HeroBanner
import com.example.data.model.HomeFeedData
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.data.model.PlatformItem
import com.example.data.model.SeasonInfo
import com.example.data.model.StreamPlayResult
import com.example.data.model.SubjectDetailResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object MovieBoxApiClient {
    private const val TAG = "MovieBoxApiClient"
    private const val HOME_API_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/home"
    private const val SEARCH_SUGGEST_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search-suggest"
    private const val RANKING_CONTENT_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/ranking-list/content"
    private const val SEARCH_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search"
    private const val DETAIL_API_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/detail"
    private const val DETAIL_API_FALLBACK_URL = "https://movieboxph.org/wefeed-h5api-bff/detail"
    private const val PLAY_URL = "https://movieboxph.org/wefeed-h5api-bff/subject/play"
    private const val PLAY_URL_FALLBACK = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play"

    private const val AUTH_TOKEN =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
    private const val ORIGIN = "https://movieboxph.org"
    private const val REFERER = "https://movieboxph.org/"
    private const val CLIENT_INFO = "{\"timezone\":\"Asia/Calcutta\"}"
    private const val REQUEST_LANG = "en"

    // Dynamic session token cache so Search and Detail APIs always succeed
    @Volatile
    private var cachedSessionToken: String = ""
    @Volatile
    private var tokenLastUpdatedTime: Long = 0L

    private fun generateClientToken(): String {
        val now = System.currentTimeMillis() / 1000L
        val reversed = now.toString().reversed()
        val md5Hash = try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(reversed.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "9fc3edc44ea88a1f6b8fcfd149a742da"
        }
        return "$now,$md5Hash"
    }

    suspend fun getOrFetchFreshToken(context: Context): String = withContext(Dispatchers.IO) {
        val currentToken = cachedSessionToken
        val now = System.currentTimeMillis()
        if (currentToken.isNotBlank() && (now - tokenLastUpdatedTime) < 30 * 60 * 1000L) {
            return@withContext currentToken
        }
        val client = getPlayClient()

        // 1. Try search-suggest endpoint (returns fresh x-user token header immediately)
        try {
            val suggestReq = Request.Builder()
                .url(SEARCH_SUGGEST_URL)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .post("{\"keyword\":\"a\"}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(suggestReq).execute().use { resp ->
                val xUser = resp.header("x-user")
                if (!xUser.isNullOrBlank()) {
                    val token = JSONObject(xUser).optString("token")
                    if (token.isNotBlank()) {
                        cachedSessionToken = token
                        tokenLastUpdatedTime = System.currentTimeMillis()
                        Log.d(TAG, "Successfully renewed MovieBox session token from search-suggest")
                        return@withContext token
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch token from search-suggest: ${e.message}")
        }

        // 2. Try home endpoint
        try {
            val homeReq = Request.Builder()
                .url("$HOME_API_URL?page=1&perPage=1")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .get()
                .build()
            client.newCall(homeReq).execute().use { resp ->
                val xUser = resp.header("x-user")
                if (!xUser.isNullOrBlank()) {
                    val token = JSONObject(xUser).optString("token")
                    if (token.isNotBlank()) {
                        cachedSessionToken = token
                        tokenLastUpdatedTime = System.currentTimeMillis()
                        Log.d(TAG, "Successfully renewed MovieBox session token from home")
                        return@withContext token
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch token from home: ${e.message}")
        }

        return@withContext cachedSessionToken
    }

    private var okHttpClient: OkHttpClient? = null
    private var playOkHttpClient: OkHttpClient? = null

    private fun getPlayClient(): OkHttpClient {
        return playOkHttpClient ?: synchronized(this) {
            playOkHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build().also { playOkHttpClient = it }
        }
    }

    private fun getClient(context: Context): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: run {
                val cacheDir = File(context.cacheDir, "moviebox_http_cache")
                val cache = Cache(cacheDir, 20L * 1024 * 1024) // 20 MB cache
                OkHttpClient.Builder()
                    .cache(cache)
                    .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val requestUrl = request.url.toString()
                        val isSearchEndpoint = requestUrl.contains("/subject/search") && !requestUrl.contains("search-suggest")

                        val token = cachedSessionToken
                        val reqBuilder = request.newBuilder()
                            .header("Accept", "*/*")
                            .header("Content-Type", "application/json")
                            .header("Origin", ORIGIN)
                            .header("Referer", REFERER)
                            .header("User-Agent", USER_AGENT)
                            .header("x-client-info", CLIENT_INFO)
                            .header("x-request-lang", REQUEST_LANG)
                            .header("sec-ch-ua", "\"Chromium\";v=\"152\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"152\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("sec-ch-ua-platform", "\"Windows\"")
                            .header("sec-fetch-dest", "empty")
                            .header("sec-fetch-mode", "cors")
                            .header("sec-fetch-site", "cross-site")
                            .header("accept-language", "en-US,en;q=0.9,hi;q=0.8")

                        if (!isSearchEndpoint) {
                            reqBuilder.header("x-client-token", generateClientToken())
                        }

                        if (token.isNotBlank()) {
                            reqBuilder.header("Authorization", "Bearer $token")
                            val userJson = "{\"token\":\"$token\",\"userType\":0,\"appType\":3}"
                            reqBuilder.header("x-user", userJson)
                            reqBuilder.header("Cookie", "token=$token; moviebox_web_tk=$token; NEXT_LOCALE=en")
                        } else {
                            reqBuilder.header("Authorization", AUTH_TOKEN)
                        }

                        val response = chain.proceed(reqBuilder.build())
                        // Capture fresh x-user token if server returns one
                        val returnedUser = response.header("x-user")
                        if (!returnedUser.isNullOrBlank()) {
                            try {
                                val freshTok = JSONObject(returnedUser).optString("token")
                                if (freshTok.isNotBlank()) {
                                    cachedSessionToken = freshTok
                                    tokenLastUpdatedTime = System.currentTimeMillis()
                                }
                            } catch (_: Exception) {}
                        }
                        response
                    }
                    .build().also { okHttpClient = it }
            }
        }
    }

    suspend fun fetchHomeFeed(context: Context): HomeFeedData = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            HOME_API_URL,
            "https://movieboxph.org/wefeed-h5api-bff/home"
        )
        for (apiUrl in endpoints) {
            try {
                val client = getClient(context)
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (!jsonString.isNullOrBlank()) {
                            val parsed = parseHomeFeedJson(jsonString)
                            if (parsed.sections.isNotEmpty() || parsed.heroBanners.isNotEmpty()) {
                                Log.d(TAG, "Successfully loaded live home feed from $apiUrl with ${parsed.sections.size} sections")
                                return@withContext parsed
                            }
                        }
                    }
                    Log.w(TAG, "Network response unsuccessful from $apiUrl code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching feed from $apiUrl", e)
            }
        }

        // Fallback to bundled cache to guarantee 100% reliability and 0% lag
        loadBundledHomeFeed(context)
    }

    @Volatile
    private var cachedBundledFeed: HomeFeedData? = null

    fun loadBundledHomeFeed(context: Context): HomeFeedData {
        cachedBundledFeed?.let { return it }
        return try {
            val inputStream = context.resources.openRawResource(R.raw.default_home)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val parsed = parseHomeFeedJson(jsonString)
            cachedBundledFeed = parsed
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled cache", e)
            HomeFeedData()
        }
    }

    fun parseHomeFeedJson(jsonString: String): HomeFeedData {
        val root = JSONObject(jsonString)
        val dataObj = root.optJSONObject("data") ?: return HomeFeedData()

        // 1. Platforms
        val platformArray = dataObj.optJSONArray("platformList") ?: JSONArray()
        val platforms = mutableListOf<PlatformItem>()
        for (i in 0 until platformArray.length()) {
            val pObj = platformArray.optJSONObject(i) ?: continue
            val name = pObj.optString("name")
            if (name.isNotBlank()) {
                platforms.add(
                    PlatformItem(
                        name = name,
                        uploadBy = pObj.optString("uploadBy")
                    )
                )
            }
        }

        val opArray = dataObj.optJSONArray("operatingList") ?: JSONArray()
        val heroBanners = mutableListOf<HeroBanner>()
        val sections = mutableListOf<CategorySection>()

        for (i in 0 until opArray.length()) {
            val opObj = opArray.optJSONObject(i) ?: continue
            val type = opObj.optString("type")
            val title = opObj.optString("title").trim()
            val opId = opObj.optString("opId", i.toString())
            val genreTopId = opObj.optString("genreTopId")
            val detailPath = opObj.optString("detailPath")

            when (type) {
                "BANNER" -> {
                    val bannerObj = opObj.optJSONObject("banner")
                    val itemsArray = bannerObj?.optJSONArray("items") ?: JSONArray()
                    for (bIndex in 0 until itemsArray.length()) {
                        val bItem = itemsArray.optJSONObject(bIndex) ?: continue
                        val bSubject = bItem.optJSONObject("subject")
                        val bImage = bItem.optJSONObject("image")

                        val bannerTitle = bItem.optString("title").ifBlank {
                            bSubject?.optString("title") ?: "MovieBox Feature"
                        }
                        val backdropUrl = bImage?.optString("url") ?: bSubject?.optJSONObject("stills")?.optString("url") ?: ""
                        val posterUrl = bSubject?.optJSONObject("cover")?.optString("url") ?: backdropUrl
                        val rDate = bSubject?.optString("releaseDate") ?: ""
                        val rYear = if (rDate.length >= 4) rDate.substring(0, 4) else "2026"
                        val bGenre = bSubject?.optString("genre") ?: "Action"
                        val bCorner = bSubject?.optString("corner") ?: ""
                        val bSubjectType = bSubject?.optInt("subjectType", 0)?.takeIf { it > 0 }
                            ?: bItem.optInt("subjectType", 0)
                        val effectiveBannerSubjectType = when {
                            bSubjectType > 0 -> bSubjectType
                            bCorner.contains("Short", ignoreCase = true) -> 7
                            bGenre.contains("TV Series", ignoreCase = true) || bGenre.contains("TV Show", ignoreCase = true) || bGenre.equals("Series", ignoreCase = true) -> 2
                            else -> 1
                        }
                        val isSeries = when {
                            effectiveBannerSubjectType == 6 -> false // Music Video Song { no episode and season }
                            effectiveBannerSubjectType == 1 -> false // Movie
                            effectiveBannerSubjectType == 2 || effectiveBannerSubjectType == 7 -> true
                            Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(bCorner) -> true
                            bCorner.contains("Season", ignoreCase = true) -> true
                            bGenre.contains("TV Series", ignoreCase = true) ||
                                bGenre.contains("TV Show", ignoreCase = true) ||
                                bGenre.contains("Drama Series", ignoreCase = true) ||
                                bGenre.equals("Series", ignoreCase = true) -> true
                            bannerTitle.contains("Season ", ignoreCase = true) -> true
                            else -> false
                        }

                        val rawSubjectId = bSubject?.optString("subjectId")?.takeIf { it.isNotBlank() && it != "0" }
                            ?: bItem.optString("subjectId").takeIf { it.isNotBlank() && it != "0" }
                            ?: bSubject?.optString("id")?.takeIf { it.isNotBlank() && it != "0" }
                            ?: bItem.optString("id").takeIf { it.isNotBlank() && it != "0" }
                            ?: ""

                        val rawDetailPath = bSubject?.optString("detailPath")?.trim()?.ifBlank { null }
                            ?: bItem.optString("detailPath")?.trim()?.ifBlank { null }
                            ?: ""

                        val heroBanner = HeroBanner(
                            id = rawSubjectId.ifBlank { bItem.optString("id", bIndex.toString()) },
                            title = bannerTitle,
                            description = bSubject?.optString("description") ?: "",
                            backdropUrl = backdropUrl.ifBlank { posterUrl },
                            posterUrl = posterUrl.ifBlank { backdropUrl },
                            subjectId = rawSubjectId,
                            rating = bSubject?.optString("imdbRatingValue") ?: "8.2",
                            genre = bGenre,
                            releaseDate = rDate,
                            releaseYear = rYear,
                            country = bSubject?.optString("countryName") ?: "",
                            corner = bCorner,
                            detailPath = rawDetailPath,
                            isSeries = isSeries,
                            subjectType = effectiveBannerSubjectType
                        )
                        heroBanners.add(heroBanner)
                    }
                }
                "SUBJECTS_MOVIE", "APPOINTMENT_LIST" -> {
                    val subjectsArray = opObj.optJSONArray("subjects") ?: JSONArray()
                    val movies = parseSubjectArray(subjectsArray, isShortCategory = title.contains("Short", ignoreCase = true))
                    if (movies.isNotEmpty()) {
                        sections.add(
                            CategorySection(
                                id = opId,
                                title = cleanCategoryTitle(title),
                                type = type,
                                items = movies,
                                genreTopId = genreTopId,
                                detailPath = detailPath,
                                opId = opId
                            )
                        )
                    }
                }
                "CUSTOM" -> {
                    val customData = opObj.optJSONObject("customData")
                    val customItemsArray = customData?.optJSONArray("items") ?: JSONArray()
                    val movies = mutableListOf<MovieItem>()
                    for (cIndex in 0 until customItemsArray.length()) {
                        val cItem = customItemsArray.optJSONObject(cIndex) ?: continue
                        val cSubject = cItem.optJSONObject("subject")
                        val itemMovie = if (cSubject != null) {
                            parseMovieItem(cSubject, isShort = title.contains("Short", ignoreCase = true))
                        } else {
                            val cTitle = cItem.optString("title")
                            val cImgObj = cItem.optJSONObject("image")
                            val cImg = cImgObj?.optString("url") ?: ""
                            val cWidth = cImgObj?.optInt("width", 0) ?: 0
                            val cHeight = cImgObj?.optInt("height", 0) ?: 0
                            MovieItem(
                                id = cItem.optString("id", cIndex.toString()),
                                title = cTitle,
                                coverUrl = cImg,
                                backdropUrl = cImg,
                                isShort = title.contains("Short", ignoreCase = true),
                                coverWidth = cWidth,
                                coverHeight = cHeight
                            )
                        }
                        if (itemMovie.title.isNotBlank() && itemMovie.coverUrl.isNotBlank()) {
                            movies.add(itemMovie)
                        }
                    }
                    if (movies.isNotEmpty()) {
                        sections.add(
                            CategorySection(
                                id = opId,
                                title = cleanCategoryTitle(title),
                                type = type,
                                items = movies,
                                genreTopId = genreTopId,
                                detailPath = detailPath,
                                opId = opId
                            )
                        )
                    }
                }
            }
        }

        return HomeFeedData(
            heroBanners = heroBanners,
            platforms = platforms,
            sections = sections
        )
    }

    private fun parseSubjectArray(array: JSONArray, isShortCategory: Boolean): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        for (i in 0 until array.length()) {
            val sObj = array.optJSONObject(i) ?: continue
            val movie = parseMovieItem(sObj, isShort = isShortCategory)
            if (movie.title.isNotBlank() && movie.coverUrl.isNotBlank()) {
                list.add(movie)
            }
        }
        return list
    }

    private fun parseMovieItem(sObj: JSONObject, isShort: Boolean): MovieItem {
        val subjectId = sObj.optString("subjectId")
        val title = sObj.optString("title")
        val description = sObj.optString("description")
        val releaseDate = sObj.optString("releaseDate")
        val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "2025"
        val rawRating = sObj.optString("imdbRatingValue", "7.5")
        val rating = if (rawRating.isBlank() || rawRating == "0") "7.8" else rawRating
        val ratingCount = sObj.optInt("imdbRatingCount", 0)

        val coverObj = sObj.optJSONObject("cover")
        val stillsObj = sObj.optJSONObject("stills")
        val imageObj = sObj.optJSONObject("image")
        val coverUrl = coverObj?.optString("url") ?: imageObj?.optString("url") ?: ""
        val stillsUrl = stillsObj?.optString("url") ?: coverUrl
        val coverWidth = coverObj?.optInt("width", 0) ?: imageObj?.optInt("width", 0) ?: 0
        val coverHeight = coverObj?.optInt("height", 0) ?: imageObj?.optInt("height", 0) ?: 0
        val genre = sObj.optString("genre", "Drama, Action")
        val country = sObj.optString("countryName", "")
        val corner = sObj.optString("corner")
        val durationSec = sObj.optLong("duration", 0L)
        val durationFormatted = formatDuration(durationSec)
        val source = sObj.optString("source")
        val uploadBy = sObj.optString("uploadBy")
        val isShortMovie = isShort ||
            (source.equals("ugc-anime.com", ignoreCase = true) && uploadBy.equals("MiniTV", ignoreCase = true)) ||
            (sObj.toString().contains("ugc-anime.com", ignoreCase = true) && sObj.toString().contains("MiniTV", ignoreCase = true)) ||
            title.contains("Short", ignoreCase = true) || genre.contains("Short", ignoreCase = true)

        val dubsArray = sObj.optJSONArray("dubs")
        val parsedDubs = mutableListOf<com.example.data.model.DubLanguage>()
        if (dubsArray != null) {
            for (d in 0 until dubsArray.length()) {
                val dObj = dubsArray.optJSONObject(d) ?: continue
                val orig = dObj.optBoolean("original", false)
                val dSubjId = dObj.optString("subjectId").ifBlank { dObj.optString("id") }.ifBlank { if (orig) subjectId else "" }
                val lanName = dObj.optString("lanName")
                val lanCode = dObj.optString("lanCode")
                val dDetailPath = dObj.optString("detailPath")
                if (lanName.isNotBlank() || dSubjId.isNotBlank()) {
                    parsedDubs.add(
                        com.example.data.model.DubLanguage(
                            subjectId = dSubjId,
                            lanName = lanName,
                            lanCode = lanCode,
                            original = orig,
                            detailPath = dDetailPath
                        )
                    )
                }
            }
        }

        val rawSubjectType = sObj.optInt("subjectType", 0)
        val totalEpCount = Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").find(corner)?.groupValues?.get(1)?.toIntOrNull()
            ?: sObj.optInt("totalEpisodes", 0).takeIf { it > 0 }
            ?: sObj.optInt("maxEp", 0).takeIf { it > 0 }
            ?: 0

        val effectiveSubjectType = when {
            rawSubjectType > 0 -> rawSubjectType
            isShortMovie || corner.contains("Short", ignoreCase = true) -> 7
            genre.contains("Music", ignoreCase = true) || genre.contains("Song", ignoreCase = true) -> 6
            genre.contains("Kids", ignoreCase = true) || genre.contains("Cartoon", ignoreCase = true) || corner.contains("Kids", ignoreCase = true) -> 5
            genre.contains("Sport", ignoreCase = true) || genre.contains("Wrestling", ignoreCase = true) || genre.contains("Live", ignoreCase = true) -> 9
            totalEpCount > 1 || corner.contains("Season", ignoreCase = true) || genre.contains("TV Series", ignoreCase = true) -> 2
            else -> 1
        }

        val isSeriesItem = when {
            effectiveSubjectType == 6 -> false // Music Video Song { no episode and season }
            effectiveSubjectType == 1 -> false // Movie
            effectiveSubjectType == 2 || effectiveSubjectType == 7 -> true
            totalEpCount > 1 -> true
            Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(corner) -> true
            corner.contains("Season", ignoreCase = true) || corner.contains("Series", ignoreCase = true) -> true
            genre.contains("TV Series", ignoreCase = true) ||
                genre.contains("TV Show", ignoreCase = true) ||
                genre.contains("Drama Series", ignoreCase = true) ||
                genre.equals("Series", ignoreCase = true) -> true
            title.contains("Season ", ignoreCase = true) -> true
            else -> false
        }

        return MovieItem(
            id = subjectId,
            title = title,
            description = description,
            coverUrl = coverUrl,
            backdropUrl = stillsUrl,
            rating = rating,
            ratingCount = ratingCount,
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            genre = genre,
            country = country,
            corner = corner,
            duration = durationFormatted,
            isShort = isShortMovie || effectiveSubjectType == 7,
            detailPath = sObj.optString("detailPath"),
            source = source,
            uploadBy = uploadBy,
            coverWidth = coverWidth,
            coverHeight = coverHeight,
            dubs = parsedDubs,
            totalEpisodes = totalEpCount,
            isSeries = isSeriesItem,
            subjectType = effectiveSubjectType
        )
    }

    private fun formatDuration(durationMillisOrSeconds: Long): String {
        if (durationMillisOrSeconds <= 0) return ""
        val seconds = if (durationMillisOrSeconds > 100000) durationMillisOrSeconds / 1000 else durationMillisOrSeconds
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private fun cleanCategoryTitle(title: String): String {
        return title
            .replace("Banner_Africa", "Featured Releases")
            .trim()
    }

    suspend fun fetchSearchSuggestions(context: Context, keyword: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val client = getClient(context)
            val jsonBody = JSONObject().apply {
                put("keyword", trimmed)
            }.toString()

            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(SEARCH_SUGGEST_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        val dataObj = root.optJSONObject("data")
                        val itemsArray = dataObj?.optJSONArray("items")
                        if (itemsArray != null) {
                            val list = mutableListOf<String>()
                            for (i in 0 until itemsArray.length()) {
                                val itm = itemsArray.optJSONObject(i) ?: continue
                                val word = itm.optString("word").trim()
                                if (word.isNotBlank() && !list.contains(word)) {
                                    list.add(word)
                                }
                            }
                            return@withContext list
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching suggestions for: $keyword", e)
        }
        emptyList()
    }

    suspend fun fetchRankingListContent(
        context: Context,
        genreTopId: String,
        page: Int = 1,
        perPage: Int = 20
    ): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        if (genreTopId.isBlank()) return@withContext Pair(emptyList(), false)
        try {
            val client = getClient(context)
            val url = "$RANKING_CONTENT_URL?id=$genreTopId&page=$page&perPage=$perPage"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
                        val subjectList = dataObj.optJSONArray("subjectList") ?: JSONArray()
                        val movies = parseSubjectArray(subjectList, isShortCategory = false)
                        val pager = dataObj.optJSONObject("pager")
                        val hasMore = pager?.optBoolean("hasMore", false) ?: (movies.size >= perPage)
                        return@withContext Pair(movies, hasMore)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ranking list for: $genreTopId", e)
        }
        Pair(emptyList(), false)
    }

    suspend fun searchMoviesPage(
        context: Context,
        keyword: String,
        page: Int = 1,
        perPage: Int = 20
    ): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@withContext Pair(emptyList(), false)

        // Ensure token is fresh before making search request
        getOrFetchFreshToken(context)

        for (attempt in 0..1) {
            try {
                val client = getClient(context)
                val jsonBody = JSONObject().apply {
                    put("keyword", trimmed)
                    put("page", page)
                    put("perPage", perPage)
                }.toString()

                val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(SEARCH_URL).post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonStr = response.body?.string()
                        if (!jsonStr.isNullOrBlank()) {
                            val root = JSONObject(jsonStr)
                            val code = root.optInt("code", 0)
                            if (code == 0) {
                                val dataObj = root.optJSONObject("data")
                                val itemsArray = dataObj?.optJSONArray("items") ?: JSONArray()
                                val pagerObj = dataObj?.optJSONObject("pager")
                                val hasMore = pagerObj?.optBoolean("hasMore", false) ?: false
                                val list = mutableListOf<MovieItem>()
                                for (i in 0 until itemsArray.length()) {
                                    val sObj = itemsArray.optJSONObject(i) ?: continue
                                    val movie = parseMovieItem(sObj, isShort = false)
                                    if (movie.title.isNotBlank()) {
                                        list.add(movie)
                                    }
                                }
                                return@withContext Pair(list, hasMore)
                            } else if (attempt == 0) {
                                // Token might have expired, invalidate and retry
                                cachedSessionToken = ""
                                tokenLastUpdatedTime = 0L
                                getOrFetchFreshToken(context)
                                return@use
                            }
                        }
                    } else if (attempt == 0 && (response.code == 400 || response.code == 401 || response.code == 403)) {
                        cachedSessionToken = ""
                        tokenLastUpdatedTime = 0L
                        getOrFetchFreshToken(context)
                        return@use
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching movies page $page for: $keyword (attempt $attempt)", e)
            }
        }
        Pair(emptyList(), false)
    }

    suspend fun searchMoviesApi(
        context: Context,
        keyword: String,
        page: Int = 1,
        perPage: Int = 20
    ): List<MovieItem> {
        return searchMoviesPage(context, keyword, page, perPage).first
    }

    suspend fun fetchSubjectDetail(
        context: Context,
        detailPath: String,
        fallbackSubjectId: String = ""
    ): SubjectDetailResult? = withContext(Dispatchers.IO) {
        val path = detailPath.trim()
        if (path.isBlank() && fallbackSubjectId.isBlank()) return@withContext null

        val urls = mutableListOf<String>()
        if (path.isNotBlank()) {
            urls.add("$DETAIL_API_URL?detailPath=$path")
            urls.add("$DETAIL_API_FALLBACK_URL?detailPath=$path")
        }
        if (fallbackSubjectId.isNotBlank()) {
            urls.add("$DETAIL_API_URL?subjectId=$fallbackSubjectId")
            urls.add("$DETAIL_API_FALLBACK_URL?subjectId=$fallbackSubjectId")
        }

        val client = getClient(context)
        val refererUrl = if (path.isNotBlank()) "https://movieboxph.org/play/$path" else "https://movieboxph.org/"
        val currentToken = getOrFetchFreshToken(context)
        val authHeader = if (currentToken.isNotBlank()) "Bearer $currentToken" else AUTH_TOKEN

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader)
                    .header("Origin", ORIGIN)
                    .header("Referer", refererUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("x-client-info", CLIENT_INFO)
                    .header("x-request-lang", REQUEST_LANG)
                    .header("Accept", "*/*")
                    .get()
                    .build()

                val jsonStr = try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body?.string()
                    }
                } catch (e: Exception) {
                    null
                } ?: continue
                if (jsonStr.isBlank()) continue

                val prettyDetailJson = try {
                    JSONObject(jsonStr).toString(2)
                } catch (e: Exception) {
                    jsonStr
                }

                val root = JSONObject(jsonStr)
                if (root.optInt("code", -1) != 0) continue
                val dataObj = root.optJSONObject("data") ?: continue
                val subj = dataObj.optJSONObject("subject") ?: continue

                val subjectId = subj.optString("subjectId").ifBlank { fallbackSubjectId }
                val subjectType = subj.optInt("subjectType", 1) // 1=Movie, 2=Series
                val title = subj.optString("title")
                val description = subj.optString("description")
                val releaseDate = subj.optString("releaseDate")
                val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                val durationSec = subj.optLong("duration", 0L)
                val durationFormatted = formatDuration(durationSec)
                val genre = subj.optString("genre")
                val country = subj.optString("countryName")
                val rating = subj.optString("imdbRatingValue").ifBlank { "7.8" }
                val ratingCount = subj.optInt("imdbRatingCount", 0)
                val subtitles = subj.optString("subtitles")
                val corner = subj.optString("corner")
                val hasResource = subj.optBoolean("hasResource", true)
                val source = subj.optString("source")
                    .ifBlank { dataObj.optString("source") }
                    .ifBlank { dataObj.optJSONObject("resource")?.optString("source") ?: "" }
                val uploadBy = subj.optString("uploadBy")
                    .ifBlank { dataObj.optString("uploadBy") }
                    .ifBlank { dataObj.optJSONObject("resource")?.optString("uploadBy") ?: "" }

                val coverUrl = subj.optJSONObject("cover")?.optString("url") ?: ""
                val stillsUrl = subj.optJSONObject("stills")?.optString("url") ?: coverUrl
                val trailerUrl = subj.optJSONObject("trailer")?.optJSONObject("videoAddress")?.optString("url") ?: ""

                // Parse real seasons from resource.seasons
                val resourceObj = dataObj.optJSONObject("resource")
                val seasonsArray = resourceObj?.optJSONArray("seasons") ?: JSONArray()
                val rawSeasonsList = mutableListOf<SeasonInfo>()
                var hasExplicitMultiEpisodesOrSeasons = false

                for (sIdx in 0 until seasonsArray.length()) {
                    val sObj = seasonsArray.optJSONObject(sIdx) ?: continue
                    val se = sObj.optInt("se", 0)
                    val maxEp = sObj.optInt("maxEp", 0)
                    val resArr = sObj.optJSONArray("resolutions") ?: JSONArray()
                    val resList = mutableListOf<Int>()
                    for (rIdx in 0 until resArr.length()) {
                        val rObj = resArr.optJSONObject(rIdx) ?: continue
                        val resVal = rObj.optInt("resolution", 0)
                        if (resVal > 0) resList.add(resVal)
                    }
                    if (se > 0 || maxEp > 1) {
                        hasExplicitMultiEpisodesOrSeasons = true
                        rawSeasonsList.add(
                            SeasonInfo(
                                seasonNumber = if (se > 0) se else 1,
                                maxEp = if (maxEp > 0) maxEp else 1,
                                resolutions = resList
                            )
                        )
                    }
                }

                // Parse actors from stars
                val starsArray = dataObj.optJSONArray("stars") ?: JSONArray()
                val castList = mutableListOf<CastActor>()
                for (cIdx in 0 until starsArray.length()) {
                    val starObj = starsArray.optJSONObject(cIdx) ?: continue
                    val name = starObj.optString("name")
                    if (name.isNotBlank()) {
                        castList.add(
                            CastActor(
                                staffId = starObj.optString("staffId"),
                                name = name,
                                character = starObj.optString("character"),
                                avatarUrl = starObj.optString("avatarUrl")
                            )
                        )
                    }
                }

                // Parse dub languages
                val dubsArray = subj.optJSONArray("dubs") ?: dataObj.optJSONArray("dubs") ?: JSONArray()
                val dubList = mutableListOf<DubLanguage>()
                for (dIdx in 0 until dubsArray.length()) {
                    val dObj = dubsArray.optJSONObject(dIdx) ?: continue
                    val original = dObj.optBoolean("original", false)
                    val dubSubjId = dObj.optString("subjectId")
                        .ifBlank { dObj.optString("id") }
                        .ifBlank { dObj.optString("targetId") }
                        .ifBlank { if (original) subjectId else "" }
                    val lanName = dObj.optString("lanName")
                    val lanCode = dObj.optString("lanCode")
                    val type = dObj.optInt("type", 0)
                    val dubDetailPath = dObj.optString("detailPath")
                    if (lanName.isNotBlank() || dubSubjId.isNotBlank()) {
                        dubList.add(
                            DubLanguage(
                                subjectId = dubSubjId,
                                lanName = lanName,
                                lanCode = lanCode,
                                original = original,
                                type = type,
                                detailPath = dubDetailPath
                            )
                        )
                    }
                }

                val cornerHasEpisodes = Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(corner) ||
                    corner.contains("Season", ignoreCase = true) ||
                    corner.contains("Series", ignoreCase = true)

                val titleHasEpisodesOrSeason = Regex("""\b(Season\s*\d+|Episode\s*\d+|S\d{1,2}\s*E\d{1,2}|S\d{1,2})\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)

                val genreHasSeries = genre.contains("TV Series", ignoreCase = true) ||
                    genre.contains("TV Show", ignoreCase = true) ||
                    genre.contains("Drama Series", ignoreCase = true) ||
                    genre.contains("Anime Series", ignoreCase = true) ||
                    genre.contains("Mini-Series", ignoreCase = true) ||
                    genre.equals("Series", ignoreCase = true)

                val effectiveDetailSubjectType = when {
                    subjectType > 0 -> subjectType
                    corner.contains("Short", ignoreCase = true) -> 7
                    genre.contains("Music", ignoreCase = true) || genre.contains("Song", ignoreCase = true) -> 6
                    genre.contains("Kids", ignoreCase = true) || genre.contains("Cartoon", ignoreCase = true) || corner.contains("Kids", ignoreCase = true) -> 5
                    genre.contains("Sport", ignoreCase = true) || genre.contains("Wrestling", ignoreCase = true) || genre.contains("Live", ignoreCase = true) -> 9
                    genreHasSeries || cornerHasEpisodes || titleHasEpisodesOrSeason || hasExplicitMultiEpisodesOrSeasons -> 2
                    else -> 1
                }

                val isSeries = when {
                    effectiveDetailSubjectType == 6 -> false // Music Video Song { no episode and season }
                    effectiveDetailSubjectType == 1 -> false // EXPLICIT MOVIE!
                    effectiveDetailSubjectType == 2 || effectiveDetailSubjectType == 7 -> true
                    hasExplicitMultiEpisodesOrSeasons -> true
                    cornerHasEpisodes -> true
                    titleHasEpisodesOrSeason -> true
                    genreHasSeries -> true
                    else -> false
                }

                val finalSeasonsList = if (isSeries) {
                    if (rawSeasonsList.isNotEmpty()) {
                        rawSeasonsList.sortedBy { it.seasonNumber }
                    } else {
                        val directMaxEp = resourceObj?.optInt("maxEp", 0)?.takeIf { it > 0 }
                            ?: dataObj.optInt("maxEp", 0).takeIf { it > 0 }
                            ?: subj.optInt("maxEp", 0).takeIf { it > 0 }
                            ?: subj.optInt("episodeCount", 0).takeIf { it > 0 }
                            ?: subj.optInt("totalEpisodes", 0).takeIf { it > 0 }
                            ?: resourceObj?.optInt("episodeCount", 0)?.takeIf { it > 0 }
                            ?: Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").find(corner)?.groupValues?.get(1)?.toIntOrNull()
                            ?: 12
                        listOf(
                            SeasonInfo(
                                seasonNumber = 1,
                                maxEp = directMaxEp,
                                resolutions = emptyList()
                            )
                        )
                    }
                } else {
                    emptyList()
                }

                val parsedDetailPath = subj.optString("detailPath")
                    .ifBlank { dataObj.optString("detailPath") }
                    .ifBlank { path }

                return@withContext SubjectDetailResult(
                        subjectId = subjectId,
                        subjectType = effectiveDetailSubjectType,
                        title = title,
                        description = description,
                        coverUrl = coverUrl,
                        backdropUrl = stillsUrl,
                        releaseDate = releaseDate,
                        releaseYear = releaseYear,
                        genre = genre,
                        country = country,
                        rating = rating,
                        ratingCount = ratingCount,
                        durationFormatted = durationFormatted,
                        corner = corner,
                        subtitles = subtitles,
                        isSeries = isSeries,
                        totalEpisodes = if (isSeries) finalSeasonsList.sumOf { it.maxEp } else 0,
                        seasons = finalSeasonsList,
                        cast = castList,
                        detailPath = parsedDetailPath,
                        hasResource = hasResource,
                        trailerUrl = trailerUrl,
                        source = source,
                        uploadBy = uploadBy,
                        dubs = dubList,
                        rawJson = prettyDetailJson
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching subject detail from $url", e)
                }
            }
            null
        }

    suspend fun fetchPlayStreams(
        context: Context,
        subjectId: String,
        detailPath: String = "",
        isShort: Boolean = false,
        season: Int? = null,
        episode: Int? = null,
        customHeaders: Map<String, String> = emptyMap()
    ): StreamPlayResult = withContext(Dispatchers.IO) {
        if (subjectId.isBlank() && detailPath.isBlank()) return@withContext StreamPlayResult()

        // 1. Normalize subjectId and detailPath
        var effectiveDetailPath = detailPath.trim()
        var realSubjectId = subjectId.trim()
        if (realSubjectId == "0") realSubjectId = ""

        if (effectiveDetailPath.isBlank() && realSubjectId.isNotBlank()) {
            try {
                val detail = fetchSubjectDetail(context, "", realSubjectId)
                if (detail != null && detail.detailPath.isNotBlank()) {
                    effectiveDetailPath = detail.detailPath.trim()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve detailPath for subjectId: $realSubjectId", e)
            }
        } else if (realSubjectId.isBlank() && effectiveDetailPath.isNotBlank()) {
            try {
                val detail = fetchSubjectDetail(context, effectiveDetailPath, "")
                if (detail != null && detail.subjectId.isNotBlank() && detail.subjectId != "0") {
                    realSubjectId = detail.subjectId.trim()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve subjectId for detailPath: $effectiveDetailPath", e)
            }
        }

        // 2. Determine primary season and episode
        val reqSeason = season ?: if (isShort) 1 else 0
        val reqEpisode = episode ?: if (isShort) 1 else 0

        val firstResult = executePlayRequest(context, realSubjectId, effectiveDetailPath, reqSeason, reqEpisode, customHeaders)
        if (firstResult.hasResource && firstResult.streams.isNotEmpty()) {
            return@withContext firstResult
        }

        // 3. Fallback strategy:
        // If first attempt used se != 0 or ep != 0 (e.g. series or mistakenly flagged as series),
        // try movie format: se=0&ep=0
        if (reqSeason != 0 || reqEpisode != 0) {
            val movieResult = executePlayRequest(context, realSubjectId, effectiveDetailPath, 0, 0, customHeaders)
            if (movieResult.hasResource && movieResult.streams.isNotEmpty()) {
                return@withContext movieResult
            }
        } else {
            // If first attempt used se=0&ep=0, but had no stream resources, try se=1&ep=1
            val seriesResult = executePlayRequest(context, realSubjectId, effectiveDetailPath, 1, 1, customHeaders)
            if (seriesResult.hasResource && seriesResult.streams.isNotEmpty()) {
                return@withContext seriesResult
            }
        }

        // Return firstResult so full response metadata is preserved for debugging
        return@withContext firstResult
    }

    private fun executePlayRequest(
        context: Context,
        subjectId: String,
        detailPath: String,
        season: Int,
        episode: Int,
        customHeaders: Map<String, String> = emptyMap()
    ): StreamPlayResult {
        val baseUrls = listOf(PLAY_URL, PLAY_URL_FALLBACK)
        var lastResult = StreamPlayResult()
        for (baseUrl in baseUrls) {
            val trimmedPath = detailPath.trim()
            val validSubjectId = subjectId.trim().takeIf { it.isNotBlank() && it != "0" }

            var requestUrl = if (validSubjectId != null) {
                "$baseUrl?subjectId=$validSubjectId&se=$season&ep=$episode"
            } else {
                "$baseUrl?detailPath=$trimmedPath&se=$season&ep=$episode"
            }
            if (trimmedPath.isNotBlank() && validSubjectId != null) {
                requestUrl += "&detailPath=$trimmedPath"
            }

            // Attempt 1: Clean guest request (highest reliability for direct streams without token expiry limits)
            // Attempt 2: Request with user authorization and cookies
            val authOptions = listOf(false, true)
            for (useAuth in authOptions) {
                try {
                    val client = getPlayClient()
                    val refererUrl = if (trimmedPath.isNotBlank()) "https://movieboxph.org/play/$trimmedPath" else "https://movieboxph.org/"

                    val reqBuilder = Request.Builder()
                        .url(requestUrl)
                        .header("accept", "*/*")
                        .header("accept-language", "en-US,en;q=0.9,hi;q=0.8")
                        .header("content-type", "application/json")
                        .header("priority", "u=1, i")
                        .header("referer", refererUrl)
                        .header("sec-ch-ua", "\"Chromium\";v=\"152\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"152\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-fetch-dest", "empty")
                        .header("sec-fetch-mode", "cors")
                        .header("sec-fetch-site", "same-origin")
                        .header("user-agent", USER_AGENT)
                        .header("x-client-info", CLIENT_INFO)
                        .header("x-request-lang", REQUEST_LANG)
                        .header("x-source", "")

                    if (customHeaders.isNotEmpty()) {
                        for ((k, v) in customHeaders) {
                            reqBuilder.header(k, v)
                        }
                    }
                    val isVskit = customHeaders.containsKey("X-Site-Domain") ||
                        customHeaders["X-Site-Type"] == "VskitWeb" ||
                        trimmedPath.contains("vskit", ignoreCase = true)
                    if (isVskit) {
                        if (!customHeaders.containsKey("X-Site-Domain")) reqBuilder.header("X-Site-Domain", "https://vskit.online")
                        if (!customHeaders.containsKey("X-Site-Type")) reqBuilder.header("X-Site-Type", "VskitWeb")
                        reqBuilder.header("X-PM-Level", "3")
                        reqBuilder.header("X-PM-Active", "true")
                    }

                    if (useAuth) {
                        val playTok = cachedSessionToken
                        if (playTok.isNotBlank()) {
                            reqBuilder.header("authorization", "Bearer $playTok")
                            reqBuilder.header("cookie", "token=$playTok; moviebox_web_tk=$playTok; NEXT_LOCALE=en")
                        } else {
                            reqBuilder.header("authorization", AUTH_TOKEN)
                        }
                    }

                    val request = reqBuilder.get().build()
                    val response = client.newCall(request).execute()
                    val jsonStr = response.body?.string() ?: ""
                    val prettyJson = try {
                        if (jsonStr.isNotBlank()) JSONObject(jsonStr).toString(2) else ""
                    } catch (e: Exception) {
                        jsonStr
                    }

                    if (!response.isSuccessful) {
                        lastResult = StreamPlayResult(
                            hasResource = false,
                            rawJson = prettyJson,
                            requestUrl = requestUrl,
                            httpCode = response.code,
                            errorMessage = "HTTP ${response.code}: ${response.message}"
                        )
                        continue
                    }

                    if (jsonStr.isNotBlank()) {
                        val root = JSONObject(jsonStr)
                        val dataObj = root.optJSONObject("data")
                        if (dataObj != null) {
                            val hasResource = dataObj.optBoolean("hasResource", false)
                            val streamList = mutableListOf<MovieStream>()

                            // 1. Direct MP4 Streams (e.g. 2160, 1080, 720, 480)
                            val streamsArray = dataObj.optJSONArray("streams") ?: JSONArray()
                            for (i in 0 until streamsArray.length()) {
                                val sObj = streamsArray.optJSONObject(i) ?: continue
                                val sUrl = sObj.optString("url")
                                if (sUrl.isNotBlank()) {
                                    val resRaw = sObj.optString("resolution")
                                        .ifBlank { sObj.optString("resolutions") }
                                        .ifBlank { sObj.optInt("resolution", 0).takeIf { it > 0 }?.toString() ?: "720" }
                                    val cleanRes = resRaw.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "720" } ?: "720"
                                    streamList.add(
                                        MovieStream(
                                            id = sObj.optString("id", "stream_$i"),
                                            resolution = cleanRes,
                                            format = sObj.optString("format", "MP4"),
                                            url = sUrl,
                                            size = sObj.optLong("size", 0L),
                                            duration = sObj.optLong("duration", 0L),
                                            codecName = sObj.optString("codecName", "h264")
                                        )
                                    )
                                }
                            }

                            // 2. DASH Streams (.mpd)
                            val dashArray = dataObj.optJSONArray("dash") ?: JSONArray()
                            for (i in 0 until dashArray.length()) {
                                val dObj = dashArray.optJSONObject(i) ?: continue
                                val dUrl = dObj.optString("url")
                                if (dUrl.isNotBlank()) {
                                    val resRaw = dObj.optString("resolution")
                                        .ifBlank { dObj.optString("resolutions") }
                                        .ifBlank { dObj.optInt("resolution", 0).takeIf { it > 0 }?.toString() ?: "1080" }
                                    val cleanRes = resRaw.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "1080" } ?: "1080"
                                    streamList.add(
                                        MovieStream(
                                            id = dObj.optString("id", "dash_$i"),
                                            resolution = cleanRes,
                                            format = dObj.optString("format", "DASH"),
                                            url = dUrl,
                                            size = dObj.optLong("size", 0L),
                                            duration = dObj.optLong("duration", 0L),
                                            codecName = dObj.optString("codecName", "hevc")
                                        )
                                    )
                                }
                            }

                            // 3. HLS Streams (.m3u8)
                            val hlsArray = dataObj.optJSONArray("hls") ?: JSONArray()
                            for (i in 0 until hlsArray.length()) {
                                val hObj = hlsArray.optJSONObject(i) ?: continue
                                val hUrl = hObj.optString("url")
                                if (hUrl.isNotBlank()) {
                                    val resRaw = hObj.optString("resolution")
                                        .ifBlank { hObj.optString("resolutions") }
                                        .ifBlank { hObj.optInt("resolution", 0).takeIf { it > 0 }?.toString() ?: "1080" }
                                    val cleanRes = resRaw.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "1080" } ?: "1080"
                                    streamList.add(
                                        MovieStream(
                                            id = hObj.optString("id", "hls_$i"),
                                            resolution = cleanRes,
                                            format = hObj.optString("format", "HLS"),
                                            url = hUrl,
                                            size = hObj.optLong("size", 0L),
                                            duration = hObj.optLong("duration", 0L),
                                            codecName = hObj.optString("codecName", "h264")
                                        )
                                    )
                                }
                            }

                            val sorted = if (streamList.isNotEmpty()) {
                                streamList.sortedWith(
                                    compareByDescending<MovieStream> {
                                        it.resolution.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                                    }.thenBy {
                                        if (it.format.equals("MP4", ignoreCase = true)) 0 else 1
                                    }
                                )
                            } else emptyList()

                            // Default choose highest quality stream!
                            val defaultStream = sorted.firstOrNull { it.url.isNotBlank() }

                            val res = StreamPlayResult(
                                streams = sorted,
                                hasResource = hasResource || sorted.isNotEmpty(),
                                defaultStream = defaultStream,
                                rawJson = prettyJson,
                                requestUrl = requestUrl,
                                httpCode = response.code
                            )
                            if (res.streams.isNotEmpty()) {
                                return res
                            } else {
                                lastResult = res
                            }
                        } else {
                            lastResult = StreamPlayResult(
                                hasResource = false,
                                rawJson = prettyJson,
                                requestUrl = requestUrl,
                                httpCode = response.code,
                                errorMessage = root.optString("message", "No 'data' object in response")
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching play streams from $baseUrl for subjectId: $subjectId", e)
                    lastResult = StreamPlayResult(
                        hasResource = false,
                        rawJson = "",
                        requestUrl = requestUrl,
                        httpCode = 0,
                        errorMessage = "${e::class.java.simpleName}: ${e.message}"
                    )
                }
            }
        }
        return lastResult
    }
}
