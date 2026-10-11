package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.WatchHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WatchHistoryManager {
    private const val PREFS_NAME = "moviebox_watch_history_prefs"
    private const val KEY_HISTORY = "watch_history_items"
    private const val MAX_HISTORY_ITEMS = 100

    private var prefs: SharedPreferences? = null
    private val _historyFlow = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<WatchHistoryItem>> = _historyFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _historyFlow.value = loadFromPrefs()
        }
    }

    fun recordProgress(item: WatchHistoryItem) {
        if (item.movieId.isBlank() && item.title.isBlank()) return

        val currentList = _historyFlow.value.toMutableList()
        // For shorts: only keep the single LAST WATCHED episode in history for this short/drama
        if (item.isShortsPlayer || item.subjectType == 7) {
            currentList.removeAll {
                it.id == item.id ||
                (item.movieId.isNotBlank() && it.movieId == item.movieId) ||
                (item.detailPath.isNotBlank() && it.detailPath == item.detailPath) ||
                (item.title.isNotBlank() && it.title.equals(item.title, ignoreCase = true) && (it.isShortsPlayer || it.subjectType == 7))
            }
        } else {
            // For regular movies/series: deduplicate same episode or movie
            currentList.removeAll {
                it.id == item.id ||
                (!item.isSeries && item.movieId.isNotBlank() && it.movieId == item.movieId) ||
                (it.movieId == item.movieId && it.season == item.season && it.episode == item.episode)
            }
        }
        // Add to top with latest timestamp
        val updated = item.copy(timestamp = System.currentTimeMillis())
        currentList.add(0, updated)

        val limited = currentList.take(MAX_HISTORY_ITEMS)
        _historyFlow.value = limited
        saveToPrefs(limited)
    }

    fun deleteItem(id: String) {
        val currentList = _historyFlow.value.toMutableList()
        currentList.removeAll { it.id == id }
        _historyFlow.value = currentList
        saveToPrefs(currentList)
    }

    fun clearAll() {
        _historyFlow.value = emptyList()
        saveToPrefs(emptyList())
    }

    /**
     * Groups history items by date: "Today", "Yesterday", or date formatted string like "28 Sep".
     */
    fun getGroupedHistory(items: List<WatchHistoryItem>): Map<String, List<WatchHistoryItem>> {
        val result = mutableMapOf<String, MutableList<WatchHistoryItem>>()
        val todayCal = Calendar.getInstance()
        val itemCal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        for (item in items) {
            itemCal.timeInMillis = item.timestamp
            val isToday = todayCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                todayCal.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)

            val isYesterday = run {
                val yCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                yCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                    yCal.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)
            }

            val groupKey = when {
                isToday -> "Today"
                isYesterday -> "Yesterday"
                else -> dateFormat.format(Date(item.timestamp))
            }

            result.getOrPut(groupKey) { mutableListOf() }.add(item)
        }
        return result
    }

    private fun loadFromPrefs(): List<WatchHistoryItem> {
        val jsonString = prefs?.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<WatchHistoryItem>()
            val seenShortsKeys = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val parsed = WatchHistoryItem.fromJsonObject(obj)
                if (parsed.isShortsPlayer || parsed.subjectType == 7) {
                    val shortKey = parsed.movieId.ifBlank { parsed.detailPath.ifBlank { parsed.title.lowercase() } }
                    if (shortKey.isNotBlank() && !seenShortsKeys.add(shortKey)) {
                        continue
                    }
                }
                list.add(parsed)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToPrefs(items: List<WatchHistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { jsonArray.put(it.toJsonObject()) }
        prefs?.edit()?.putString(KEY_HISTORY, jsonArray.toString())?.apply()
    }
}
