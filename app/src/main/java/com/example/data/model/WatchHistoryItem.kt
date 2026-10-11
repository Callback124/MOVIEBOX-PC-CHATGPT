package com.example.data.model

import org.json.JSONObject

data class WatchHistoryItem(
    val id: String,
    val movieId: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0,
    val isSeries: Boolean = false,
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val durationMs: Long = 0L,
    val durationFormatted: String = "",
    val lastPositionMs: Long = 0L,
    val progressPercent: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "moviebox",
    val detailPath: String = "",
    val subjectType: Int = 1,
    val genre: String = "",
    val isShortsPlayer: Boolean = false
) {
    val displaySubtitle: String
        get() = if (isShortsPlayer || subjectType == 7) {
            if (episode > 0) "Episode $episode" else ""
        } else if (isSeries && (season > 0 || episode > 0)) {
            val s = if (season > 0) String.format("S%02d", season) else "S01"
            val e = if (episode > 0) String.format("E%02d", episode) else "E01"
            "$s $e"
        } else {
            ""
        }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("movieId", movieId)
            put("title", title)
            put("season", season)
            put("episode", episode)
            put("isSeries", isSeries)
            put("coverUrl", coverUrl)
            put("backdropUrl", backdropUrl)
            put("durationMs", durationMs)
            put("durationFormatted", durationFormatted)
            put("lastPositionMs", lastPositionMs)
            put("progressPercent", progressPercent)
            put("timestamp", timestamp)
            put("source", source)
            put("detailPath", detailPath)
            put("subjectType", subjectType)
            put("genre", genre)
            put("isShortsPlayer", isShortsPlayer)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): WatchHistoryItem {
            val sType = json.optInt("subjectType", 1)
            val src = json.optString("source", "moviebox")
            val idStr = json.optString("id", "")
            val seasonNum = json.optInt("season", 0)
            val epNum = json.optInt("episode", 0)
            val isSeriesFlag = json.optBoolean("isSeries", false)
            val inferredShorts = sType == 7 ||
                src.equals("vskit", true) ||
                src.equals("storytv", true) ||
                src.equals("freereels", true) ||
                idStr.endsWith("_shorts") ||
                (isSeriesFlag && seasonNum == 0 && epNum > 0)
            val isShorts = if (json.has("isShortsPlayer")) {
                json.optBoolean("isShortsPlayer", inferredShorts) || inferredShorts
            } else {
                inferredShorts
            }
            return WatchHistoryItem(
                id = idStr,
                movieId = json.optString("movieId", ""),
                title = json.optString("title", ""),
                season = seasonNum,
                episode = epNum,
                isSeries = isSeriesFlag,
                coverUrl = json.optString("coverUrl", ""),
                backdropUrl = json.optString("backdropUrl", ""),
                durationMs = json.optLong("durationMs", 0L),
                durationFormatted = json.optString("durationFormatted", ""),
                lastPositionMs = json.optLong("lastPositionMs", 0L),
                progressPercent = json.optInt("progressPercent", 0),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                source = src,
                detailPath = json.optString("detailPath", ""),
                subjectType = sType,
                genre = json.optString("genre", ""),
                isShortsPlayer = isShorts
            )
        }
    }
}
