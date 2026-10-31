package com.example.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val releaseNotes: String
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class DownloadCompleted(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    
    // GitHub Releases API for tag "release"
    private const val GITHUB_RELEASE_API =
        "https://api.github.com/repos/ananscommunication08/ALL-MOVIEBOX/releases/tags/release"
    
    // Fallback direct URL if API rate limit or error occurs
    private const val FALLBACK_RELEASE_PAGE =
        "https://github.com/ananscommunication08/ALL-MOVIEBOX/releases/tag/release"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun getCurrentVersionName(): String {
        return BuildConfig.VERSION_NAME.ifBlank { "1.2" }
    }

    /**
     * Parse version string (e.g., "1.2", "1.3.1", "V-1.2", "v1.3") into a list of integers
     */
    fun parseVersionNumbers(versionStr: String): List<Int> {
        val clean = versionStr.replace(Regex("[^0-9.]"), "")
        if (clean.isBlank()) return listOf(0)
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Compare two versions.
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = parseVersionNumbers(v1)
        val v2Parts = parseVersionNumbers(v2)
        val maxLen = maxOf(v1Parts.size, v2Parts.size)
        for (i in 0 until maxLen) {
            val p1 = v1Parts.getOrElse(i) { 0 }
            val p2 = v2Parts.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    /**
     * Extracts version string from file name or string
     * e.g., "MovieBox.V-1.3.apk" -> "1.3", "MovieBox_v2.0.apk" -> "2.0"
     */
    fun extractVersionFromText(text: String): String? {
        val pattern = Pattern.compile("(?i)(?:v|v-|v_|version)[-_ ]*([0-9]+(?:\\.[0-9]+)+)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val fallbackPattern = Pattern.compile("([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)")
        val fbMatcher = fallbackPattern.matcher(text)
        if (fbMatcher.find()) {
            return fbMatcher.group(1)
        }
        return null
    }

    /**
     * Check GitHub release assets for a new APK version
     */
    suspend fun checkForUpdates(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName()
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASE_API)
                .header("User-Agent", "MovieBoxAndroidApp/${currentVersion}")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub release check failed: HTTP ${response.code}")
                return@withContext AppUpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    downloadUrl = "",
                    fileName = "",
                    fileSize = 0L,
                    releaseNotes = ""
                )
            }

            val jsonBody = response.body?.string() ?: ""
            if (jsonBody.isBlank()) {
                return@withContext AppUpdateInfo(false, currentVersion, currentVersion, "", "", 0L, "")
            }

            val releaseObj = JSONObject(jsonBody)
            val releaseNotes = releaseObj.optString("body", "")
            val assetsArray = releaseObj.optJSONArray("assets")

            if (assetsArray != null && assetsArray.length() > 0) {
                var bestApkUrl = ""
                var bestFileName = ""
                var bestFileSize = 0L
                var bestVersion = currentVersion

                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    val size = asset.optLong("size", 0L)

                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        val extractedVer = extractVersionFromText(name) ?: ""
                        if (extractedVer.isNotBlank()) {
                            if (compareVersions(extractedVer, bestVersion) > 0) {
                                bestVersion = extractedVer
                                bestApkUrl = downloadUrl
                                bestFileName = name
                                bestFileSize = size
                            }
                        } else if (bestApkUrl.isBlank()) {
                            bestApkUrl = downloadUrl
                            bestFileName = name
                            bestFileSize = size
                        }
                    }
                }

                val hasUpdate = compareVersions(bestVersion, currentVersion) > 0

                return@withContext AppUpdateInfo(
                    hasUpdate = hasUpdate,
                    currentVersion = currentVersion,
                    latestVersion = bestVersion,
                    downloadUrl = bestApkUrl,
                    fileName = bestFileName,
                    fileSize = bestFileSize,
                    releaseNotes = releaseNotes
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update: ${e.message}", e)
        }

        AppUpdateInfo(
            hasUpdate = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            downloadUrl = "",
            fileName = "",
            fileSize = 0L,
            releaseNotes = ""
        )
    }

    /**
     * Download the latest APK file with real-time percentage progress
     */
    suspend fun downloadApk(context: Context, downloadUrl: String, fileName: String): File? = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) {
            _downloadState.value = DownloadState.Error("Invalid download URL")
            return@withContext null
        }

        try {
            _downloadState.value = DownloadState.Downloading(0, 0, 0)
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MovieBoxAndroidApp")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = DownloadState.Error("Server returned code ${response.code}")
                return@withContext null
            }

            val body = response.body ?: run {
                _downloadState.value = DownloadState.Error("Response body is empty")
                return@withContext null
            }

            val totalBytes = body.contentLength()
            val downloadsDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
            val cleanName = if (fileName.endsWith(".apk")) fileName else "MovieBox_Update.apk"
            val targetFile = File(downloadsDir, cleanName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedBytes: Long = 0
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val progressPercent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        50
                    }

                    if (progressPercent != lastProgress) {
                        lastProgress = progressPercent
                        _downloadState.value = DownloadState.Downloading(progressPercent, downloadedBytes, totalBytes)
                    }
                }
                outputStream.flush()
                _downloadState.value = DownloadState.DownloadCompleted(targetFile)
                return@withContext targetFile
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            return@withContext null
        }
    }

    /**
     * First trigger uninstallation of current old version, and prepare APK for installation
     */
    fun uninstallAndInstallApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }

            // Save APK path to SharedPreferences so when user or receiver launches next time, it can resume
            val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pending_apk_path", apkFile.absolutePath).apply()

            // Trigger uninstall intent for current package
            val packageUri = Uri.parse("package:${context.packageName}")
            val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // In Android, uninstallation will remove current app and then package installer installs the new APK.
            // Also launch installer prompt so user can either clean replace or install after uninstall.
            try {
                context.startActivity(uninstallIntent)
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_UNINSTALL_PACKAGE intent failed, falling back to direct package install: ${e.message}")
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uninstallAndInstallApk: ${e.message}", e)
            installApk(context, apkFile)
        }
    }

    /**
     * Trigger Android system package installer to install the downloaded APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            // Fallback: Open browser download URL directly
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(FALLBACK_RELEASE_PAGE)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {}
        }
    }
}
