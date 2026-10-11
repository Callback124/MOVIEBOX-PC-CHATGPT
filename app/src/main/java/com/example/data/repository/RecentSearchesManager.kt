package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Manages search history per server (Server 1 to Server 6).
 * Persists recent search queries in SharedPreferences so each server
 * maintains its own independent list of recent searches.
 */
object RecentSearchesManager {
    private const val PREFS_NAME = "moviebox_recent_searches_prefs"
    private const val MAX_RECENT_SEARCHES = 20

    private var prefs: SharedPreferences? = null

    // StateFlow map for each server's recent searches
    private val _recentSearchesMap = mutableMapOf<AppServer, MutableStateFlow<List<String>>>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            AppServer.entries.forEach { server ->
                val list = loadFromPrefs(server)
                _recentSearchesMap.getOrPut(server) { MutableStateFlow(emptyList()) }.value = list
            }
        }
    }

    fun getRecentSearchesFlow(server: AppServer): StateFlow<List<String>> {
        return _recentSearchesMap.getOrPut(server) {
            MutableStateFlow(loadFromPrefs(server))
        }.asStateFlow()
    }

    fun getRecentSearches(server: AppServer): List<String> {
        return _recentSearchesMap[server]?.value ?: loadFromPrefs(server)
    }

    fun addSearchQuery(server: AppServer, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val currentList = getRecentSearches(server).toMutableList()
        // Remove if already exists so it moves to top
        currentList.removeAll { it.equals(trimmed, ignoreCase = true) }
        currentList.add(0, trimmed)

        val limited = currentList.take(MAX_RECENT_SEARCHES)
        saveToPrefs(server, limited)
        _recentSearchesMap.getOrPut(server) { MutableStateFlow(emptyList()) }.value = limited
    }

    fun removeSearchQuery(server: AppServer, query: String) {
        val currentList = getRecentSearches(server).toMutableList()
        currentList.removeAll { it.equals(query.trim(), ignoreCase = true) }
        saveToPrefs(server, currentList)
        _recentSearchesMap.getOrPut(server) { MutableStateFlow(emptyList()) }.value = currentList
    }

    fun clearRecentSearches(server: AppServer) {
        saveToPrefs(server, emptyList())
        _recentSearchesMap.getOrPut(server) { MutableStateFlow(emptyList()) }.value = emptyList()
    }

    private fun loadFromPrefs(server: AppServer): List<String> {
        val key = "recent_${server.name.lowercase()}"
        val jsonString = prefs?.getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToPrefs(server: AppServer, list: List<String>) {
        val key = "recent_${server.name.lowercase()}"
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        prefs?.edit()?.putString(key, jsonArray.toString())?.apply()
    }
}
