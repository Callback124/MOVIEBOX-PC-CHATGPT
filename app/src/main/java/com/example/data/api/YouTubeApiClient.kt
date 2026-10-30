package com.example.data.api

import android.util.Log
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.data.model.StreamPlayResult
import com.example.data.model.SubjectDetailResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class YouTubeCategory(
    val id: String,
    val title: String,
    val query: String,
    val isShorts: Boolean = false
)

object YouTubeApiClient {
    private const val TAG = "YouTubeApiClient"

    // 1. Search Suggestions API (Preserved)
    private const val SUGGEST_URL =
        "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&hl=en&gl=IN&q="

    // 2. Homepage API (browse FEwhat_to_watch with new API key & visitorData)
    private const val BROWSE_URL =
        "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
    private const val BROWSE_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.33 (KHTML, like Gecko) Chrome/87.0.4064.189 Safari/549.33,gzip(gfe)"
    private const val BROWSE_VISITOR_ID =
        "Cgs2N1dCeTVDMThFWSjZ_bbWBjIKCgJJThIEGgAgWGLPAgrMAkN2WUJDQVVTcUFFQjhRWnVaazQ0UmNtanNKM0hka3BrSHNlOHU5bEM0dnhKQ3I1c09TQzE0SHpId2UyUEdfZ3ZSVzRVb21nTEpqZTFJVWZtcnpUU3cxLXdVS1dUOEM1SkdlOGtCMklSQWZEdmNDM2dUWU5tcFlQbWpQaTBuRXNhZG80NHFnWktQOHNfNlEtRUwyMmdyRUlsZjdDSFZBcVk3T0poOEplVTJVckpmZmp1bUxWTkctQmhiLTBkT0xteWJ3R3F1YXl2NXU0ZDJvS3dzbUxrQUg1RFJXcHNGY3lUbUNnbllRbE5CTmdvQlRKRkFkNnBuVTg1WnNfR016MzlELWtQbEVqUmhDSDF0Y2tuSWlCNmlqdHhUNC1oUHpQX01TckxsVlhiY0FVN1poQTFlU2VBMlRzRnBHWmwtNVRDVno2QjFyZ0NzS2dH"

    // 3. Player API (VISIONOS)
    private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player"
    private const val PLAYER_USER_AGENT =
        "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)"
    private const val PLAYER_VISITOR_DATA =
        "CgtxbTRtMXdXWHVwOCjUz6zWBjIKCgJJThIEGgAgIGLfAgrcAjIyLllUPW5SQzJwUkxVcnNrRHduUlZHUEVwaElZMmZzZGo0NHp1eGtxS2FVRWlnREpPM21ya2kxaVN3RnU0cmdLeXpYWVVMYUxvV2ZkN20tQmpiMTRad09PRndkbm9sb2tiUVJvd2hMSF90T2EyU29qSlZxdW9PcjhiZmgxTkVVUGY0WWxrc0UzWS1kcnE5M2FNVnNaUHR5Wl9sZ3F0SkxicW5oMjR3YTI4MVNBNG1LMDV6dDZ6OUxEZE90dlpnMG81OU5WMm1IRVUxUWxEcmZuX2NlXzluZ1RtMHlTdGFfbzBSTmJja0x0Z1lVTDd3TWpvQ1VPdzFNWEVLYmphakJfbXhybF9yNWhkaU1QLUJEVDY3UGtNZ1FNUUJiTEttZHFFVHc2Z0tydTFIbjAyemJ0RHo2Q2RTelRHZldIamxfaFZ1U1lTR1VycFZ1NXh3c25EQVhHU0tDdzRoUQ%3D%3D"

    // 4. Search Results API (wefeed-fm-bff/search/searchV2)
    private const val SEARCH_URL = "https://api.fmonline.app/wefeed-fm-bff/search/searchV2"
    private const val SEARCH_USER_AGENT =
        "com.funbase.xradio/51000003 (Linux; U; Android 14; en_IN; Infinix X6711; Build/UP1A.231005.007; Cronet/153.0.8010.18)"
    private const val SEARCH_CLIENT_INFO =
        "{\"package_name\":\"com.funbase.xradio\",\"version_name\":\"5.2.04.0923.01\",\"version_code\":51000003,\"os\":\"android\",\"os_version\":\"14\",\"install_ch\":\"google-play\",\"device_id\":\"07c89fc911f2d7013e746f1a5c9cb2b8\",\"install_store\":\"gp\",\"gaid\":\"dfa2b45c-0134-49be-87b0-47203e73a707\",\"brand\":\"Infinix\",\"model\":\"Infinix X6711\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"40410\",\"X-Client-Vpn\":\"true\"}"
    private const val SEARCH_TR_SIGNATURE = "1791697829461|2|THbNNq6wD+xQIRe3Uu6ZAQ=="
    private const val SEARCH_AUTHORIZATION =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjY2MjE1MzIxNTUwMDcwOTI5ODQsImV4cCI6MTc5ODc4MTQzMCwiaWF0IjoxNzkxMDA1MTMwfQ.6BQxSttGKMYAS06mbEnDP-YuiShZN8rMqPogwbQFebY"

    val CATEGORIES = listOf(
        YouTubeCategory("trending", "🔥 Trending", "trending videos shorts hindi india"),
        YouTubeCategory("shorts", "⚡ Shorts", "trending youtube shorts hindi viral #shorts", isShorts = true),
        YouTubeCategory("music", "🎵 Music Hits", "new bollywood songs official music video"),
        YouTubeCategory("movies", "🎬 Full Movies", "superhit hindi dubbed full movie"),
        YouTubeCategory("trailers", "🍿 Trailers & Teasers", "official trailer hindi"),
        YouTubeCategory("comedy", "😂 Comedy", "standup comedy hindi funny videos"),
        YouTubeCategory("gaming", "🎮 Gaming", "gaming highlights hindi"),
        YouTubeCategory("tech", "📱 Tech & Gadgets", "tech review hindi gadgets")
    )

    val YOUTUBE_VIDEO_HEADERS = mapOf(
        "User-Agent" to BROWSE_USER_AGENT,
        "Accept" to "*/*",
        "Origin" to "https://www.youtube.com",
        "Referer" to "https://www.youtube.com/"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 1. Search Suggestions API (PRESERVED)
     * Fetches live YouTube search query completions.
     */
    suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val req = Request.Builder()
                .url("$SUGGEST_URL$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                val startIdx = body.indexOf("[[")
                val endIdx = body.lastIndexOf("]]")
                if (startIdx != -1 && endIdx > startIdx) {
                    val arrStr = body.substring(startIdx, endIdx + 2)
                    val arr = JSONArray(arrStr)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val itemArr = arr.optJSONArray(i) ?: continue
                        val word = itemArr.optString(0).trim()
                        if (word.isNotBlank() && !list.contains(word)) {
                            list.add(word)
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list.take(10)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching YouTube suggestions", e)
        }
        emptyList()
    }

    /**
     * 2. NEW HOMEPAGE API (FEwhat_to_watch)
     * Calls YouTube browse endpoint to fetch home videos and trending shorts shelf.
     */
    suspend fun fetchHomepageFeed(continuation: String? = null): Triple<List<MovieItem>, List<MovieItem>, String?> =
        withContext(Dispatchers.IO) {
            val videos = mutableListOf<MovieItem>()
            val shorts = mutableListOf<MovieItem>()
            var nextContinuation: String? = null

            try {
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("hl", "en")
                            put("gl", "IN")
                            put("remoteHost", "157.15.35.146")
                            put("deviceMake", "Apple")
                            put("deviceModel", "")
                            put("visitorData", BROWSE_VISITOR_ID)
                            put("userAgent", BROWSE_USER_AGENT)
                            put("clientName", "WEB")
                            put("clientVersion", "2.20261012.01.00")
                            put("osName", "Macintosh")
                            put("osVersion", "10_12_1")
                            put("originalUrl", "https://www.youtube.com/?gl=IN")
                            put("screenPixelDensity", 2)
                            put("platform", "DESKTOP")
                            put("clientFormFactor", "UNKNOWN_FORM_FACTOR")
                            put("screenDensityFloat", 2.0)
                            put("userInterfaceTheme", "USER_INTERFACE_THEME_LIGHT")
                            put("timeZone", "Asia/Kolkata")
                            put("browserName", "Chrome")
                            put("browserVersion", "87.0.4064.189")
                            put("screenHeightPoints", 2249)
                            put("screenWidthPoints", 1080)
                            put("utcOffsetMinutes", 330)
                        })
                        put("request", JSONObject().apply {
                            put("useSsl", true)
                        })
                        put("user", JSONObject().apply {
                            put("lockedSafetyMode", false)
                        })
                    })
                    if (!continuation.isNullOrBlank()) {
                        put("continuation", continuation)
                    } else {
                        put("browseId", "FEwhat_to_watch")
                    }
                }

                val req = Request.Builder()
                    .url(BROWSE_URL)
                    .header("User-Agent", BROWSE_USER_AGENT)
                    .header("origin", "https://www.youtube.com")
                    .header("referer", "https://www.youtube.com/?gl=IN")
                    .header("content-type", "application/json; charset=utf-8")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = JSONObject(body)

                    // Check continuation
                    val actions = root.optJSONArray("onResponseReceivedActions")
                    val gridContents = if (actions != null && actions.length() > 0) {
                        actions.optJSONObject(0)
                            ?.optJSONObject("appendContinuationItemsAction")
                            ?.optJSONArray("continuationItems")
                    } else {
                        val tabs = root.optJSONObject("contents")
                            ?.optJSONObject("twoColumnBrowseResultsRenderer")
                            ?.optJSONArray("tabs")
                        val tabContent = tabs?.optJSONObject(0)
                            ?.optJSONObject("tabRenderer")
                            ?.optJSONObject("content")
                        tabContent?.optJSONObject("richGridRenderer")?.optJSONArray("contents")
                    }

                    if (gridContents != null) {
                        for (i in 0 until gridContents.length()) {
                            val item = gridContents.optJSONObject(i) ?: continue

                            // 1. Regular 16:9 videos in richItemRenderer
                            val richItem = item.optJSONObject("richItemRenderer")?.optJSONObject("content")
                            if (richItem != null) {
                                if (richItem.has("lockupViewModel")) {
                                    val lvm = richItem.optJSONObject("lockupViewModel")
                                    val vid = lvm?.optString("contentId")?.trim() ?: ""
                                    val meta = lvm?.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                                    val title = meta?.optJSONObject("title")?.optString("content") ?: ""
                                    if (vid.isNotBlank() && title.isNotBlank()) {
                                        var channel = "YouTube"
                                        var views = ""
                                        val rows = meta?.optJSONObject("metadata")
                                            ?.optJSONObject("contentMetadataViewModel")
                                            ?.optJSONArray("metadataRows")
                                        if (rows != null && rows.length() > 0) {
                                            val p0 = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                                            if (p0 != null && p0.length() > 0) {
                                                channel = p0.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: "YouTube"
                                            }
                                        }
                                        if (rows != null && rows.length() > 1) {
                                            val p1 = rows.optJSONObject(1)?.optJSONArray("metadataParts")
                                            if (p1 != null && p1.length() > 0) {
                                                views = p1.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                                            }
                                        }
                                        val thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                        videos.add(
                                            MovieItem(
                                                id = vid,
                                                title = title,
                                                description = if (views.isNotBlank()) "$channel • $views" else channel,
                                                coverUrl = thumb,
                                                backdropUrl = thumb,
                                                rating = "VIDEO",
                                                releaseDate = "2025",
                                                releaseYear = "2025",
                                                genre = channel,
                                                country = views.ifBlank { "YouTube" },
                                                corner = "VIDEO",
                                                duration = "VIDEO",
                                                detailPath = vid,
                                                source = "youtube",
                                                uploadBy = channel,
                                                coverWidth = 1280,
                                                coverHeight = 720,
                                                isShort = false,
                                                isSeries = false,
                                                subjectType = 1,
                                                customHeaders = YOUTUBE_VIDEO_HEADERS
                                            )
                                        )
                                    }
                                } else if (richItem.has("videoRenderer")) {
                                    val vr = richItem.optJSONObject("videoRenderer")
                                    val vid = vr?.optString("videoId")?.trim() ?: ""
                                    val title = extractRunsText(vr?.optJSONObject("title"))
                                    val author = extractRunsText(vr?.optJSONObject("ownerText")).ifBlank { "YouTube" }
                                    val views = extractRunsText(vr?.optJSONObject("viewCountText"))
                                    val length = vr?.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                                    if (vid.isNotBlank() && title.isNotBlank()) {
                                        val thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                        videos.add(
                                            MovieItem(
                                                id = vid,
                                                title = title,
                                                description = if (views.isNotBlank()) "$author • $views" else author,
                                                coverUrl = thumb,
                                                backdropUrl = thumb,
                                                rating = "VIDEO",
                                                releaseDate = "2025",
                                                releaseYear = "2025",
                                                genre = author,
                                                country = views.ifBlank { "YouTube" },
                                                corner = length.ifBlank { "VIDEO" },
                                                duration = length.ifBlank { "VIDEO" },
                                                detailPath = vid,
                                                source = "youtube",
                                                uploadBy = author,
                                                coverWidth = 1280,
                                                coverHeight = 720,
                                                isShort = false,
                                                isSeries = false,
                                                subjectType = 1,
                                                customHeaders = YOUTUBE_VIDEO_HEADERS
                                            )
                                        )
                                    }
                                }
                            }

                            // 2. Shorts shelf in richSectionRenderer
                            val richSection = item.optJSONObject("richSectionRenderer")?.optJSONObject("content")
                            val shelf = richSection?.optJSONObject("richShelfRenderer")
                            if (shelf != null) {
                                val shelfItems = shelf.optJSONArray("contents")
                                if (shelfItems != null) {
                                    for (s in 0 until shelfItems.length()) {
                                        val sContent = shelfItems.optJSONObject(s)
                                            ?.optJSONObject("richItemRenderer")
                                            ?.optJSONObject("content") ?: continue

                                        if (sContent.has("shortsLockupViewModel")) {
                                            val slvm = sContent.optJSONObject("shortsLockupViewModel")
                                            val url = slvm?.optJSONObject("onTap")
                                                ?.optJSONObject("innertubeCommand")
                                                ?.optJSONObject("commandMetadata")
                                                ?.optJSONObject("webCommandMetadata")
                                                ?.optString("url") ?: ""
                                            val vid = if (url.contains("/shorts/")) {
                                                url.substringAfter("/shorts/").substringBefore("?").trim()
                                            } else {
                                                slvm?.optString("entityId")?.replace("shorts-shelf-item-", "")?.trim() ?: ""
                                            }
                                            val om = slvm?.optJSONObject("overlayMetadata")
                                            val title = om?.optJSONObject("primaryText")?.optString("content") ?: ""
                                            val views = om?.optJSONObject("secondaryText")?.optString("content") ?: "Shorts"
                                            if (vid.isNotBlank() && title.isNotBlank()) {
                                                val thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                                shorts.add(
                                                    MovieItem(
                                                        id = vid,
                                                        title = title,
                                                        description = "YouTube Shorts • $views",
                                                        coverUrl = thumb,
                                                        backdropUrl = thumb,
                                                        rating = "SHORTS",
                                                        releaseDate = "Shorts",
                                                        releaseYear = "2025",
                                                        genre = "YouTube Shorts",
                                                        country = views,
                                                        corner = "SHORTS",
                                                        duration = "SHORTS",
                                                        detailPath = vid,
                                                        source = "youtube",
                                                        uploadBy = "YouTube Shorts",
                                                        coverWidth = 720,
                                                        coverHeight = 1280,
                                                        isShort = true,
                                                        isSeries = false,
                                                        subjectType = 7,
                                                        customHeaders = YOUTUBE_VIDEO_HEADERS
                                                    )
                                                )
                                            }
                                        } else if (sContent.has("reelItemRenderer")) {
                                            val rr = sContent.optJSONObject("reelItemRenderer")
                                            val vid = rr?.optString("videoId")?.trim() ?: ""
                                            val title = rr?.optJSONObject("headline")?.optString("simpleText") ?: ""
                                            if (vid.isNotBlank() && title.isNotBlank()) {
                                                val thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                                shorts.add(
                                                    MovieItem(
                                                        id = vid,
                                                        title = title,
                                                        description = "YouTube Shorts",
                                                        coverUrl = thumb,
                                                        backdropUrl = thumb,
                                                        rating = "SHORTS",
                                                        releaseDate = "Shorts",
                                                        releaseYear = "2025",
                                                        genre = "YouTube Shorts",
                                                        country = "Shorts",
                                                        corner = "SHORTS",
                                                        duration = "SHORTS",
                                                        detailPath = vid,
                                                        source = "youtube",
                                                        uploadBy = "YouTube Shorts",
                                                        coverWidth = 720,
                                                        coverHeight = 1280,
                                                        isShort = true,
                                                        isSeries = false,
                                                        subjectType = 7,
                                                        customHeaders = YOUTUBE_VIDEO_HEADERS
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Continuation item renderer
                            val continuationItem = item.optJSONObject("continuationItemRenderer")
                            if (continuationItem != null) {
                                val token = continuationItem.optJSONObject("continuationEndpoint")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token")
                                if (!token.isNullOrBlank()) {
                                    nextContinuation = token
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching YouTube browse feed", e)
            }

            Triple(videos.distinctBy { it.id }, shorts.distinctBy { it.id }, nextContinuation)
        }

    /**
     * Homepage trending shorts helper.
     */
    suspend fun fetchTrendingShorts(): List<MovieItem> = withContext(Dispatchers.IO) {
        val (_, shorts, _) = fetchHomepageFeed()
        shorts
    }

    /**
     * 3. NEW SEARCH RESULTS API (wefeed-fm-bff/search/searchV2)
     * Returns VIDEO, SHORTS, and AUDIO MUSIC as specified by user:
     * "AND SEARCH RESULT ME VIDEO , SHORT , AUDIO MUSIC SAB TRAH KA DATA AATA HAI"
     */
    suspend fun searchVideosWithContinuation(
        query: String,
        continuationToken: String? = null,
        isShorts: Boolean = false
    ): Pair<List<MovieItem>, String?> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank() && continuationToken.isNullOrBlank()) {
            return@withContext Pair(emptyList(), null)
        }

        // If category is "trending" and no query was typed, fetch homepage feed
        if (trimmed.equals("trending", ignoreCase = true) || trimmed.contains("trending videos", ignoreCase = true)) {
            val (videos, shorts, cont) = fetchHomepageFeed(continuationToken)
            val combined = (shorts + videos).distinctBy { it.id }
            return@withContext Pair(combined, cont)
        }

        val page = continuationToken?.toIntOrNull() ?: 1
        val items = mutableListOf<MovieItem>()
        var nextPageToken: String? = null

        try {
            val encodedKeyword = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$SEARCH_URL?keyword=$encodedKeyword&entranceType=HomeTab&homeTabCode=YoutubeSubAll&page=$page&perPage=15"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SEARCH_USER_AGENT)
                .header("x-client-vpn", "true")
                .header("x-client-info", SEARCH_CLIENT_INFO)
                .header("x-client-status", "1")
                .header("x-tr-signature", SEARCH_TR_SIGNATURE)
                .header("authorization", SEARCH_AUTHORIZATION)
                .header("priority", "u=1, i")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data") ?: return@use
                val resultsArr = dataObj.optJSONArray("results") ?: JSONArray()

                for (rIdx in 0 until resultsArr.length()) {
                    val r = resultsArr.optJSONObject(rIdx) ?: continue

                    // 1. UGC Videos & Clips (VIDEOS & SHORTS)
                    val ugcList = r.optJSONArray("ugcList")
                    if (ugcList != null) {
                        for (u in 0 until ugcList.length()) {
                            val ugcItem = ugcList.optJSONObject(u) ?: continue
                            val v = ugcItem.optJSONObject("video") ?: continue
                            val vid = v.optString("ugcVideoId").ifBlank { v.optString("subjectId") }
                            val title = v.optString("title")
                            if (vid.isBlank() || title.isBlank()) continue

                            val style = v.optString("videoStyle", "Horizontal")
                            val durationSec = v.optLong("duration", 0L)
                            val isShortDetected = isShorts ||
                                style.equals("Vertical", ignoreCase = true) ||
                                (durationSec in 1..120) ||
                                title.contains("#shorts", ignoreCase = true) ||
                                title.contains("#short", ignoreCase = true)

                            val coverUrl = v.optJSONObject("cover")?.optString("url")
                                ?: v.optJSONObject("coverVertical")?.optString("url")
                                ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"

                            val creator = v.optJSONObject("creator")?.optString("nickname") ?: "Creator"
                            val desc = v.optString("description")
                            val watchNum = v.optString("watchNum")
                            val durText = if (isShortDetected) "SHORTS" else formatDurationSeconds(durationSec)

                            items.add(
                                MovieItem(
                                    id = vid,
                                    title = title,
                                    description = if (watchNum.isNotBlank()) "$creator • $watchNum views\n\n$desc" else "$creator\n\n$desc",
                                    coverUrl = coverUrl,
                                    backdropUrl = coverUrl,
                                    rating = if (isShortDetected) "SHORTS" else "VIDEO",
                                    releaseDate = v.optString("releaseDate", "2025"),
                                    releaseYear = v.optString("releaseDate", "2025").take(4),
                                    genre = v.optString("category", if (isShortDetected) "Shorts" else "Video"),
                                    country = creator,
                                    corner = durText,
                                    duration = durText,
                                    detailPath = vid,
                                    source = "youtube",
                                    uploadBy = creator,
                                    coverWidth = if (isShortDetected) 720 else 1280,
                                    coverHeight = if (isShortDetected) 1280 else 720,
                                    isShort = isShortDetected,
                                    isSeries = false,
                                    subjectType = if (isShortDetected) 7 else 1,
                                    customHeaders = YOUTUBE_VIDEO_HEADERS
                                )
                            )
                        }
                    }

                    // 2. Music & Audio Tracks (AUDIO MUSIC & PODCAST)
                    val contentsArr = r.optJSONArray("contents")
                    if (contentsArr != null) {
                        for (c in 0 until contentsArr.length()) {
                            val cItem = contentsArr.optJSONObject(c) ?: continue
                            val snd = cItem.optJSONObject("sound")
                            val alb = cItem.optJSONObject("album")

                            if (snd != null) {
                                val sid = snd.optString("soundId")
                                val title = snd.optString("title")
                                if (sid.isBlank() || title.isBlank()) continue

                                val coverUrl = snd.optJSONObject("cover")?.optString("url") ?: ""
                                val artist = snd.optJSONArray("artists")?.optJSONObject(0)?.optString("name")
                                    ?: snd.optJSONObject("author")?.optString("nickname")
                                    ?: "Music Artist"
                                val durationSec = snd.optLong("duration", 0L)
                                val audios = snd.optJSONObject("resources")?.optJSONArray("audioList")
                                val streamUrl = audios?.optJSONObject(0)?.optString("url") ?: ""
                                val ytVideoId = snd.optString("youtubeVideoId")
                                val durText = formatDurationSeconds(durationSec)

                                val headersMap = mutableMapOf<String, String>().apply {
                                    putAll(YOUTUBE_VIDEO_HEADERS)
                                    if (streamUrl.isNotBlank()) put("audio_stream_url", streamUrl)
                                    if (ytVideoId.isNotBlank()) put("youtube_video_id", ytVideoId)
                                }

                                items.add(
                                    MovieItem(
                                        id = if (ytVideoId.isNotBlank()) ytVideoId else sid,
                                        title = title,
                                        description = "🎵 Music Track • $artist • $durText",
                                        coverUrl = coverUrl.ifBlank { "https://i.ytimg.com/vi/$sid/hqdefault.jpg" },
                                        backdropUrl = coverUrl,
                                        rating = "AUDIO",
                                        releaseDate = "Music",
                                        releaseYear = "2025",
                                        genre = "Music",
                                        country = artist,
                                        corner = durText,
                                        duration = durText,
                                        detailPath = sid,
                                        source = "youtube",
                                        uploadBy = artist,
                                        directUrl = streamUrl,
                                        coverWidth = 720,
                                        coverHeight = 720,
                                        isShort = false,
                                        isSeries = false,
                                        subjectType = 1,
                                        customHeaders = headersMap
                                    )
                                )
                            } else if (alb != null) {
                                val aid = alb.optString("albumId")
                                val title = alb.optString("title")
                                if (aid.isBlank() || title.isBlank()) continue

                                val coverUrl = alb.optJSONObject("cover")?.optString("url") ?: ""
                                val author = alb.optJSONObject("author")?.optString("nickname") ?: "Artist"
                                val soundNum = alb.optInt("soundNum", 0)

                                items.add(
                                    MovieItem(
                                        id = aid,
                                        title = title,
                                        description = "💿 Music Album • $author ($soundNum tracks)",
                                        coverUrl = coverUrl,
                                        backdropUrl = coverUrl,
                                        rating = "MUSIC",
                                        releaseDate = "Album",
                                        releaseYear = "2025",
                                        genre = "Music Album",
                                        country = author,
                                        corner = "ALBUM",
                                        duration = "$soundNum tracks",
                                        detailPath = aid,
                                        source = "youtube",
                                        uploadBy = author,
                                        coverWidth = 720,
                                        coverHeight = 720,
                                        isShort = false,
                                        isSeries = false,
                                        subjectType = 1,
                                        customHeaders = YOUTUBE_VIDEO_HEADERS
                                    )
                                )
                            }
                        }
                    }

                    // 3. Regular Video List (if present)
                    val videoList = r.optJSONArray("videoList")
                    if (videoList != null) {
                        for (vIdx in 0 until videoList.length()) {
                            val vItem = videoList.optJSONObject(vIdx) ?: continue
                            val vid = vItem.optString("videoId").ifBlank { vItem.optString("id") }
                            val title = vItem.optString("title")
                            if (vid.isBlank() || title.isBlank()) continue
                            val thumb = vItem.optString("coverUrl").ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }
                            items.add(
                                MovieItem(
                                    id = vid,
                                    title = title,
                                    description = vItem.optString("description", "YouTube Video"),
                                    coverUrl = thumb,
                                    backdropUrl = thumb,
                                    rating = "VIDEO",
                                    releaseDate = "2025",
                                    releaseYear = "2025",
                                    genre = "Video",
                                    country = "YouTube",
                                    corner = "VIDEO",
                                    duration = "VIDEO",
                                    detailPath = vid,
                                    source = "youtube",
                                    uploadBy = "YouTube",
                                    coverWidth = 1280,
                                    coverHeight = 720,
                                    isShort = false,
                                    isSeries = false,
                                    subjectType = 1,
                                    customHeaders = YOUTUBE_VIDEO_HEADERS
                                )
                            )
                        }
                    }

                    // 4. Shorts List (if present)
                    val shortList = r.optJSONArray("shortList")
                    if (shortList != null) {
                        for (sIdx in 0 until shortList.length()) {
                            val sItem = shortList.optJSONObject(sIdx) ?: continue
                            val sid = sItem.optString("videoId").ifBlank { sItem.optString("id") }
                            val title = sItem.optString("title")
                            if (sid.isBlank() || title.isBlank()) continue
                            val thumb = sItem.optString("coverUrl").ifBlank { "https://i.ytimg.com/vi/$sid/hqdefault.jpg" }
                            items.add(
                                MovieItem(
                                    id = sid,
                                    title = title,
                                    description = "YouTube Shorts",
                                    coverUrl = thumb,
                                    backdropUrl = thumb,
                                    rating = "SHORTS",
                                    releaseDate = "Shorts",
                                    releaseYear = "2025",
                                    genre = "Shorts",
                                    country = "Shorts",
                                    corner = "SHORTS",
                                    duration = "SHORTS",
                                    detailPath = sid,
                                    source = "youtube",
                                    uploadBy = "YouTube Shorts",
                                    coverWidth = 720,
                                    coverHeight = 1280,
                                    isShort = true,
                                    isSeries = false,
                                    subjectType = 7,
                                    customHeaders = YOUTUBE_VIDEO_HEADERS
                                )
                            )
                        }
                    }
                }

                // Check pagination
                val pager = dataObj.optJSONObject("pager")
                val hasMore = pager?.optBoolean("hasMore", false) ?: false
                if (hasMore) {
                    val nextPage = pager?.optString("nextPage") ?: "${page + 1}"
                    nextPageToken = nextPage
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchV2 for '$query'", e)
        }

        Pair(items.distinctBy { it.id }, nextPageToken)
    }

    suspend fun searchVideos(query: String, isShorts: Boolean = false): List<MovieItem> {
        return searchVideosWithContinuation(query, null, isShorts).first
    }

    suspend fun fetchCategoryVideos(category: YouTubeCategory): List<MovieItem> = withContext(Dispatchers.IO) {
        if (category.id == "trending") {
            val (videos, _, _) = fetchHomepageFeed()
            videos
        } else if (category.id == "shorts") {
            fetchTrendingShorts()
        } else {
            searchVideos(category.query, isShorts = category.isShorts)
        }
    }

    /**
     * 4. NEW PLAYER API (VISIONOS)
     * POST https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false&t=aEKR47HilHIX&id=$videoId
     */
    suspend fun fetchPlayStreams(videoId: String): StreamPlayResult = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
            .substringAfter("v=", videoId.trim())
            .substringBefore("&")
            .substringAfter("/shorts/", videoId.trim())
            .substringBefore("?")
            .trim()
        if (cleanId.isBlank()) return@withContext StreamPlayResult(hasResource = false)

        val playerUrlWithParams = "$PLAYER_URL?prettyPrint=false&t=aEKR47HilHIX&id=$cleanId"

        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("clientScreen", "WATCH")
                        put("platform", "MOBILE")
                        put("visitorData", PLAYER_VISITOR_DATA)
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice14,1")
                        put("osName", "visionOS")
                        put("osVersion", "25.6.0.23O471")
                        put("hl", "en-GB")
                        put("gl", "IN")
                        put("utcOffsetMinutes", 0)
                    })
                    put("request", JSONObject().apply {
                        put("internalExperimentFlags", JSONArray())
                        put("useSsl", true)
                    })
                    put("user", JSONObject().apply {
                        put("lockedSafetyMode", false)
                    })
                })
                put("videoId", cleanId)
                put("cpn", "WtMVm1pOwcS_8mc-")
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val req = Request.Builder()
                .url(playerUrlWithParams)
                .header("User-Agent", PLAYER_USER_AGENT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-format-version", "2")
                .header("accept-language", "en-GB, en;q=0.9")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val streams = parseStreamingData(body)
                        if (streams.isNotEmpty()) {
                            val sorted = sortStreams(streams)
                            return@withContext StreamPlayResult(
                                streams = sorted,
                                hasResource = true,
                                defaultStream = sorted.firstOrNull(),
                                rawJson = body,
                                requestUrl = playerUrlWithParams,
                                httpCode = resp.code
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VISIONOS Player API error for $cleanId: ${e.message}")
        }

        // Return empty so YouTubeWebPlayerSurface takes over embedded playback smoothly
        StreamPlayResult(hasResource = false, errorMessage = "Playback via WebPlayer")
    }

    /**
     * Video Details helper from VISIONOS player API.
     */
    suspend fun fetchVideoDetail(videoId: String): SubjectDetailResult? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) return@withContext null

        try {
            val playerUrl = "$PLAYER_URL?prettyPrint=false&t=aEKR47HilHIX&id=$cleanId"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("clientScreen", "WATCH")
                        put("platform", "MOBILE")
                        put("visitorData", PLAYER_VISITOR_DATA)
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice14,1")
                        put("osName", "visionOS")
                        put("osVersion", "25.6.0.23O471")
                        put("hl", "en-GB")
                        put("gl", "IN")
                        put("utcOffsetMinutes", 0)
                    })
                })
                put("videoId", cleanId)
                put("cpn", "WtMVm1pOwcS_8mc-")
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val req = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", PLAYER_USER_AGENT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-format-version", "2")
                .header("accept-language", "en-GB, en;q=0.9")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val root = JSONObject(body)
                        val vd = root.optJSONObject("videoDetails")
                        val micro = root.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
                        if (vd != null) {
                            val title = vd.optString("title")
                            val author = vd.optString("author", "YouTube")
                            val shortDesc = vd.optString("shortDescription")
                            val lengthSec = vd.optLong("lengthSeconds", 0L)
                            val viewCount = vd.optLong("viewCount", 0L)
                            val publishDate = micro?.optString("publishDate", "2025") ?: "2025"
                            val category = micro?.optString("category", author) ?: author
                            val highResThumb = "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
                            val isShortVideo = lengthSec in 1..120 || title.contains("#shorts", ignoreCase = true)

                            if (title.isNotBlank()) {
                                return@withContext SubjectDetailResult(
                                    subjectId = cleanId,
                                    subjectType = if (isShortVideo) 7 else 1,
                                    title = title,
                                    description = buildString {
                                        append("Channel: $author  •  ${formatViews(viewCount)} views  •  $publishDate")
                                        if (shortDesc.isNotBlank()) {
                                            append("\n\n")
                                            append(shortDesc)
                                        }
                                    },
                                    coverUrl = highResThumb,
                                    backdropUrl = highResThumb,
                                    releaseDate = publishDate,
                                    releaseYear = publishDate.take(4).ifBlank { "2025" },
                                    genre = category,
                                    country = "${formatViews(viewCount)} views",
                                    rating = if (isShortVideo) "SHORTS" else "9.2",
                                    durationFormatted = formatDurationSeconds(lengthSec),
                                    corner = formatDurationSeconds(lengthSec),
                                    isSeries = false,
                                    detailPath = cleanId,
                                    source = "youtube",
                                    uploadBy = author
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching YouTube video detail for $cleanId", e)
        }
        null
    }

    private fun parseStreamingData(jsonStr: String): List<MovieStream> {
        val list = mutableListOf<MovieStream>()
        try {
            val root = JSONObject(jsonStr)
            val streamingData = root.optJSONObject("streamingData") ?: return emptyList()

            // 1. HLS Manifest
            val hlsManifestUrl = streamingData.optString("hlsManifestUrl")
            if (hlsManifestUrl.isNotBlank()) {
                list.add(
                    MovieStream(
                        id = "yt_hls_auto",
                        resolution = "1080",
                        format = "HLS",
                        url = hlsManifestUrl
                    )
                )
            }

            // 2. Direct muxed formats
            val formats = streamingData.optJSONArray("formats") ?: JSONArray()
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val url = f.optString("url")
                if (url.isBlank()) continue
                val height = f.optInt("height", 0)
                val width = f.optInt("width", 0)
                val qualityLabel = f.optString("qualityLabel", "")
                val shortSide = if (height > 0 && width > 0) minOf(height, width) else height
                val res = if (shortSide > 0) shortSide.toString() else qualityLabel.filter { it.isDigit() }.ifBlank { "720" }
                val contentLen = f.optString("contentLength", "0").toLongOrNull() ?: 0L
                val approxDurationMs = f.optString("approxDurationMs", "0").toLongOrNull() ?: 0L
                list.add(
                    MovieStream(
                        id = "yt_fmt_${f.optInt("itag", i)}",
                        resolution = res,
                        format = "MP4",
                        url = url,
                        size = contentLen,
                        duration = approxDurationMs / 1000L
                    )
                )
            }

            // 3. Adaptive formats (with URL)
            val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
            for (i in 0 until adaptive.length()) {
                val af = adaptive.optJSONObject(i) ?: continue
                val url = af.optString("url")
                val mime = af.optString("mimeType", "")
                if (url.isBlank() || !mime.startsWith("video/")) continue
                val height = af.optInt("height", 0)
                if (height < 360) continue
                list.add(
                    MovieStream(
                        id = "yt_adapt_${af.optInt("itag", i)}",
                        resolution = "$height",
                        format = if (mime.contains("webm")) "WEBM" else "MP4",
                        url = url
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing streamingData", e)
        }
        return list
    }

    private fun sortStreams(streams: List<MovieStream>): List<MovieStream> {
        return streams.sortedWith(
            compareByDescending<MovieStream> {
                it.resolution.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
            }.thenByDescending {
                if (it.format.equals("MP4", ignoreCase = true)) 1 else 0
            }
        )
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val content = obj.optString("content")
        if (content.isNotBlank()) return content
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            sb.append(r.optString("text"))
        }
        return sb.toString().trim()
    }

    fun formatDurationSeconds(seconds: Long): String {
        if (seconds <= 0L) return "LIVE"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun formatViews(views: Long): String {
        if (views <= 0L) return ""
        return when {
            views >= 1_000_000_000L -> String.format("%.1fB", views / 1_000_000_000.0)
            views >= 1_000_000L -> String.format("%.1fM", views / 1_000_000.0)
            views >= 1_000L -> String.format("%.1fK", views / 1_000.0)
            else -> views.toString()
        }
    }
}
