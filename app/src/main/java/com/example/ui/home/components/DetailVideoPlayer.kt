package com.example.ui.home.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import com.example.util.DeviceUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.widget.Toast
import com.example.data.api.MovieBoxApiClient

import com.example.data.download.MovieDownloadManager
import com.example.data.model.DubLanguage
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.data.model.SeasonInfo
import com.example.data.model.StreamPlayResult
import com.example.data.model.SubjectDetailResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TvPlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1.0f,
        animationSpec = tween(120),
        label = "tv_btn_scale"
    )

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(size)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    onClick()
                    true
                } else false
            }
            .background(
                color = when {
                    isFocused && isPrimary -> Color.White
                    isFocused -> Color.White.copy(alpha = 0.35f)
                    isPrimary -> Color.White
                    else -> Color.Black.copy(alpha = 0.5f)
                },
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = when {
                    isFocused && isPrimary -> Color(0xFFE50914)
                    isFocused -> Color.White
                    else -> Color.White.copy(alpha = 0.2f)
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                isPrimary -> Color.Black
                else -> Color.White
            },
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun Modifier.tvControlFocusable(
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    borderColor: Color = Color.White
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.22f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "tv_ctrl_scale"
    )
    this
        .scale(scale)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .then(
            if (isFocused) {
                Modifier
                    .border(3.dp, borderColor, shape)
                    .background(Color.White.copy(alpha = 0.35f), shape)
            } else {
                Modifier
            }
        )
}

private const val FALLBACK_STREAM =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

private const val STREAM_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
private const val STREAM_REFERER = "https://movieboxph.org/"
private const val STREAM_ORIGIN = "https://movieboxph.org"

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun hideSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }
}

private fun showSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = (millis / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
private fun createMediaSource(
    context: Context,
    streamUrl: String,
    format: String,
    customHeaders: Map<String, String> = emptyMap()
): MediaSource {
    val isLocal = streamUrl.startsWith("/") || streamUrl.startsWith("file:")
    val uri = if (streamUrl.startsWith("/")) Uri.fromFile(java.io.File(streamUrl)) else Uri.parse(streamUrl)

    val isGoogleVideo = streamUrl.contains("googlevideo.com", ignoreCase = true) ||
        customHeaders["Origin"]?.contains("youtube.com", ignoreCase = true) == true
    val defaultHeaders = if (isGoogleVideo) {
        mapOf("Accept" to "*/*")
    } else {
        mapOf(
            "Referer" to STREAM_REFERER,
            "Origin" to STREAM_ORIGIN,
            "Accept" to "*/*"
        )
    }
    val requestHeaders = if (customHeaders.isNotEmpty()) {
        defaultHeaders + customHeaders
    } else {
        defaultHeaders
    }
    val userAgent = customHeaders["User-Agent"] ?: customHeaders["user-agent"] ?: STREAM_USER_AGENT

    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(requestHeaders)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val mediaItem = MediaItem.Builder()
        .setUri(uri)
        .build()

    val lower = format.lowercase()
    val path = uri.path?.lowercase() ?: ""
    return when {
        !isLocal && (lower.contains("dash") || path.endsWith(".mpd")) -> {
            DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        !isLocal && (lower.contains("hls") || path.endsWith(".m3u8")) -> {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        else -> {
            val extractorsFactory = DefaultExtractorsFactory()
                .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
        }
    }
}

private data class AudioTrackItem(
    val id: String,
    val label: String,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isDub: Boolean = false,
    val dubSubjectId: String = "",
    val dubDetailPath: String = "",
    val lanCode: String = "",
    val isOriginal: Boolean = false,
    val dub: DubLanguage? = null
)

private val LANGUAGE_MAP = mapOf(
    "hi" to "Hindi", "hin" to "Hindi",
    "en" to "English", "eng" to "English",
    "ta" to "Tamil", "tam" to "Tamil",
    "te" to "Telugu", "tel" to "Telugu",
    "ml" to "Malayalam", "mal" to "Malayalam",
    "kn" to "Kannada", "kan" to "Kannada",
    "bn" to "Bengali", "ben" to "Bengali",
    "mr" to "Marathi", "mar" to "Marathi",
    "pa" to "Punjabi", "pan" to "Punjabi",
    "gu" to "Gujarati", "guj" to "Gujarati",
    "ur" to "Urdu", "urd" to "Urdu",
    "or" to "Odia", "ori" to "Odia",
    "bho" to "Bhojpuri",
    "ko" to "Korean", "kor" to "Korean",
    "ja" to "Japanese", "jpn" to "Japanese",
    "zh" to "Chinese", "zho" to "Chinese", "chi" to "Chinese",
    "es" to "Spanish", "spa" to "Spanish",
    "fr" to "French", "fra" to "French", "fre" to "French",
    "de" to "German", "deu" to "German", "ger" to "German",
    "ru" to "Russian", "rus" to "Russian",
    "pt" to "Portuguese", "por" to "Portuguese",
    "ar" to "Arabic", "ara" to "Arabic",
    "id" to "Indonesian", "ind" to "Indonesian",
    "th" to "Thai", "tha" to "Thai",
    "vi" to "Vietnamese", "vie" to "Vietnamese",
    "tr" to "Turkish", "tur" to "Turkish",
    "it" to "Italian", "ita" to "Italian"
)

private fun resolveDubLabel(lanName: String, lanCode: String, isOriginal: Boolean): String {
    val cleanName = lanName.trim()
    val code = lanCode.trim().lowercase()
    val mappedName = LANGUAGE_MAP[code]

    val baseName = when {
        cleanName.isNotBlank() && !cleanName.equals("Original", ignoreCase = true) && !cleanName.equals("null", ignoreCase = true) -> cleanName
        mappedName != null -> mappedName
        cleanName.isNotBlank() -> cleanName
        code.isNotBlank() -> code.uppercase()
        else -> "Original"
    }

    return if (isOriginal && !baseName.contains("Original", ignoreCase = true)) {
        "$baseName (Original)"
    } else {
        baseName
    }
}

private fun extractResolutionHeight(qualityStr: String): String {
    val lower = qualityStr.lowercase()
    return when {
        lower.contains("2160") || lower.contains("4k") -> "2160"
        lower.contains("1440") || lower.contains("2k") -> "1440"
        lower.contains("1080") -> "1080"
        lower.contains("720") -> "720"
        lower.contains("480") -> "480"
        lower.contains("360") -> "360"
        else -> qualityStr.split("x", "×", "p", " ").lastOrNull { part ->
            part.filter { it.isDigit() }.isNotBlank()
        }?.filter { it.isDigit() } ?: qualityStr.filter { it.isDigit() }
    }
}

private fun formatResolutionDisplay(heightStr: String): String {
    val clean = heightStr.filter { it.isDigit() }
    return when (clean) {
        "2160" -> "3840 × 2160"
        "1440" -> "2560 × 1440"
        "1080" -> "1920 × 1080"
        "720" -> "1280 × 720"
        "480" -> "854 × 480"
        "360" -> "640 × 360"
        else -> if (clean.isNotBlank()) "${clean}p" else "1920 × 1080"
    }
}

private fun findBestMatchingAudioTrack(
    availableAudioTracks: List<AudioTrackItem>,
    movie: MovieItem
): AudioTrackItem? {
    if (availableAudioTracks.isEmpty()) return null

    // 1. Direct match by dubSubjectId (exact match for clicked dub subject)
    if (movie.id.isNotBlank()) {
        val bySubj = availableAudioTracks.firstOrNull { it.isDub && it.dubSubjectId == movie.id }
        if (bySubj != null) return bySubj
    }

    // 2. Direct match by dubDetailPath
    if (movie.detailPath.isNotBlank()) {
        val byPath = availableAudioTracks.firstOrNull { it.isDub && it.dubDetailPath == movie.detailPath }
        if (byPath != null) return byPath
    }

    // 3. Search keywords across title, genre, corner, and description
    val searchTarget = "${movie.title} ${movie.genre} ${movie.corner} ${movie.description}".lowercase()

    val languageKeywords = listOf(
        "malayalam" to listOf("malayalam", "mal", "ml", "മലയാളം"),
        "kannada" to listOf("kannada", "kan", "kn", "ಕನ್ನಡ"),
        "telugu" to listOf("telugu", "tel", "te", "తెలుగు"),
        "tamil" to listOf("tamil", "tam", "ta", "தமிழ்"),
        "hindi" to listOf("hindi", "hin", "hi", "हिंदी"),
        "english" to listOf("english", "eng", "en"),
        "bengali" to listOf("bengali", "ben", "bn", "বাংলা"),
        "marathi" to listOf("marathi", "mar", "mr", "मराठी"),
        "punjabi" to listOf("punjabi", "pan", "pa", "ਪੰਜਾਬੀ"),
        "gujarati" to listOf("gujarati", "guj", "gu", "ગુજરાતી"),
        "urdu" to listOf("urdu", "urd", "ur"),
        "bhojpuri" to listOf("bhojpuri", "bho"),
        "odia" to listOf("odia", "oriya", "ori"),
        "korean" to listOf("korean", "kor", "ko"),
        "japanese" to listOf("japanese", "jpn", "ja"),
        "chinese" to listOf("chinese", "chi", "zho", "zh"),
        "spanish" to listOf("spanish", "spa", "es"),
        "french" to listOf("french", "fra", "fre", "fr"),
        "german" to listOf("german", "deu", "ger", "de"),
        "russian" to listOf("russian", "rus", "ru"),
        "portuguese" to listOf("portuguese", "por", "pt"),
        "arabic" to listOf("arabic", "ara", "ar"),
        "indonesian" to listOf("indonesian", "ind", "id"),
        "thai" to listOf("thai", "tha", "th"),
        "vietnamese" to listOf("vietnamese", "vie", "vi"),
        "turkish" to listOf("turkish", "tur", "tr"),
        "italian" to listOf("italian", "ita", "it")
    )

    for ((langName, aliases) in languageKeywords) {
        val matchesSearch = aliases.any { alias ->
            if (alias.length <= 2) {
                searchTarget.contains("($alias)") || searchTarget.contains("[$alias]") ||
                searchTarget.contains(" $alias ") || searchTarget.startsWith("$alias ") || searchTarget.endsWith(" $alias")
            } else {
                searchTarget.contains(alias)
            }
        }

        if (matchesSearch) {
            val matchingTrack = availableAudioTracks.firstOrNull { track ->
                track.label.contains(langName, ignoreCase = true) ||
                aliases.any { alias ->
                    track.label.contains(alias, ignoreCase = true) ||
                    track.lanCode.equals(alias, ignoreCase = true)
                }
            }
            if (matchingTrack != null) {
                return matchingTrack
            }
        }
    }

    // 4. Direct label match in title or genre
    val directLabelMatch = availableAudioTracks.firstOrNull { track ->
        track.label.isNotBlank() && track.label != "Default Audio" &&
        (movie.title.contains(track.label, ignoreCase = true) || movie.genre.contains(track.label, ignoreCase = true))
    }
    if (directLabelMatch != null) return directLabelMatch

    // 5. If corner indicates Dub, prefer the first dub
    if (movie.corner.contains("Dub", ignoreCase = true)) {
        val firstDub = availableAudioTracks.firstOrNull { it.isDub }
        if (firstDub != null) return firstDub
    }

    // 6. Original dub if present
    val originalTrack = availableAudioTracks.firstOrNull { it.isOriginal }
    if (originalTrack != null) return originalTrack

    // 7. Fallback to first dub track or first track
    return availableAudioTracks.firstOrNull { it.isDub } ?: availableAudioTracks.firstOrNull()
}

@Composable
fun NormalPlayerSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekingChange: (Boolean, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var isFocused by remember { mutableStateOf(false) }

    val actualProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayProgress = if (isDragging) dragProgress else actualProgress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                            onSeekTo(target)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val maxDur = if (durationMs > 0) durationMs else Long.MAX_VALUE
                            val target = (currentPositionMs + 10000L).coerceAtMost(maxDur)
                            onSeekTo(target)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (size.width > 0 && durationMs > 0) {
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (progress * durationMs).toLong()
                        onSeekTo(targetMs)
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0 && durationMs > 0) {
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeekingChange(true, (dragProgress * durationMs).toLong())
                        }
                    },
                    onDragEnd = {
                        if (durationMs > 0) {
                            val targetMs = (dragProgress * durationMs).toLong()
                            onSeekTo(targetMs)
                        }
                        isDragging = false
                        onSeekingChange(false, 0L)
                    },
                    onDragCancel = {
                        isDragging = false
                        onSeekingChange(false, 0L)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0 && durationMs > 0) {
                            val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragProgress = newProgress
                            onSeekingChange(true, (newProgress * durationMs).toLong())
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbRadius = if (isDragging || isFocused) 10.dp else 5.dp
        val trackHeight = if (isFocused) 5.dp else 2.5.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val progressX = (displayProgress * size.width).coerceIn(0f, size.width)

            // Inactive track (thin translucent normal line)
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight.toPx(),
                cap = StrokeCap.Round
            )

            // Active track (thin solid white normal line)
            if (progressX > 0f) {
                drawLine(
                    color = if (isFocused) Color(0xFFE50914) else Color.White,
                    start = Offset(0f, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = trackHeight.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Circular thumb with glowing red ring when focused for TV remote
            if (isFocused) {
                drawCircle(
                    color = Color.White,
                    radius = (thumbRadius + 3.dp).toPx(),
                    center = Offset(progressX, centerY)
                )
            }
            drawCircle(
                color = if (isFocused) Color(0xFFE50914) else Color.White,
                radius = thumbRadius.toPx(),
                center = Offset(progressX, centerY)
            )
        }
    }
}

/**
 * AndroidX Media3 (ExoPlayer 1.5.1) Player matching the user's authentic screenshots:
 * - Clean white controls, white circular play/pause button with black icon
 * - Top Bar: Back button, Title, Volume/Mute, Lock Screen toggle
 * - Bottom Controls: Aspect Ratio (Fit/Fill/Zoom), PiP, Rewind (<<), Play/Pause, Fast Forward (>>), Settings, Fullscreen
 * - Timeline: Current time (00:00) | Scrubber line | Total time (00:00)
 * - Settings Dialog: VIDEO and AUDIO tabs, None, Auto, Resolution radio list, CANCEL and OK buttons
 */
@OptIn(UnstableApi::class)
@Composable
fun DetailVideoPlayer(
    movie: MovieItem,
    isSeries: Boolean = true,
    selectedSeason: Int = 1,
    selectedEpisode: Int = 1,
    selectedQuality: String = "1080p",
    dubs: List<DubLanguage> = emptyList(),
    selectedDub: DubLanguage? = null,
    onDubSelected: ((DubLanguage) -> Unit)? = null,
    onDubsListUpdated: ((List<DubLanguage>) -> Unit)? = null,
    onQualitySelected: (String) -> Unit = {},
    onAvailableQualitiesChanged: (List<String>) -> Unit = {},
    onStreamResultFetched: (StreamPlayResult) -> Unit = {},
    isFullscreen: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    isFromShortsPage: Boolean = false,
    isDownloaded: Boolean = false,
    isLastEpisodeOfLastSeason: Boolean = false,
    onPlayNext: (() -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    availableSeasons: List<Int> = emptyList(),
    seasonsInfo: List<SeasonInfo> = emptyList(),
    onSubjectDetailUpdated: ((SubjectDetailResult) -> Unit)? = null,
    initialSeekMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val scope = rememberCoroutineScope()

    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            playerFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    var resolvedStreams by remember { mutableStateOf<List<MovieStream>>(emptyList()) }
    var activeStreamUrl by remember { mutableStateOf("") }
    var activeFormat by remember { mutableStateOf("MP4") }
    var isFetchingStream by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var lastLoadedStreamKey by remember { mutableStateOf("") }
    var isVideoPortrait by remember { mutableStateOf(isFromShortsPage || movie.isShort) }
    val isYouTubeMovie = remember(movie.source) { movie.source.equals("youtube", ignoreCase = true) }
    var useYouTubeWebEmbed by remember(movie.id, movie.detailPath) { mutableStateOf(isYouTubeMovie) }
    var ytWebSeekRequestMs by remember { mutableLongStateOf(-1L) }

    // Dub languages fetched from details
    var currentDubs by remember(movie.id, dubs) { mutableStateOf(if (dubs.isNotEmpty()) dubs else movie.dubs) }
    LaunchedEffect(dubs) {
        if (dubs.isNotEmpty()) {
            currentDubs = dubs
        }
    }
    var internalDetailedInfo by remember { mutableStateOf<SubjectDetailResult?>(null) }
    var internalAudioTracks by remember { mutableStateOf<List<AudioTrackItem>>(emptyList()) }

    // Fetch details to retrieve available dub languages if not pre-populated
    LaunchedEffect(movie.id, movie.detailPath, isDownloaded) {
        if (!isDownloaded && (movie.detailPath.isNotBlank() || movie.id.isNotBlank())) {
            try {
                val detail = if (movie.source.equals("youtube", ignoreCase = true)) {
                    com.example.data.api.YouTubeApiClient.fetchVideoDetail(
                        videoId = movie.id.ifBlank { movie.detailPath }
                    )
                } else if (movie.source.equals("lookr", ignoreCase = true)) {
                    com.example.data.api.LookrApiClient.fetchSubjectDetail(
                        detailPath = movie.detailPath,
                        subjectId = movie.id
                    )
                } else {
                    MovieBoxApiClient.fetchSubjectDetail(
                        context = context,
                        detailPath = movie.detailPath,
                        fallbackSubjectId = movie.id
                    )
                }
                if (detail != null) {
                    internalDetailedInfo = detail
                    if (detail.dubs.isNotEmpty()) {
                        currentDubs = detail.dubs
                        onDubsListUpdated?.invoke(detail.dubs)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Resolved seasons & episodes mapping for batch download
    val resolvedSeasons = remember(availableSeasons, seasonsInfo, internalDetailedInfo, isSeries) {
        if (!isSeries) listOf(0)
        else {
            val list = when {
                availableSeasons.isNotEmpty() -> availableSeasons.filter { it > 0 }
                seasonsInfo.isNotEmpty() -> seasonsInfo.map { it.seasonNumber }.filter { it > 0 }
                internalDetailedInfo != null && internalDetailedInfo!!.seasons.isNotEmpty() ->
                    internalDetailedInfo!!.seasons.map { it.seasonNumber }.filter { it > 0 }
                else -> listOf(if (selectedSeason > 0) selectedSeason else 1)
            }
            if (list.isEmpty()) listOf(1) else list
        }
    }

    val resolvedSeasonEpisodesMap = remember(resolvedSeasons, seasonsInfo, internalDetailedInfo, isSeries) {
        if (!isSeries) emptyMap()
        else {
            val sInfoList = when {
                seasonsInfo.isNotEmpty() -> seasonsInfo
                internalDetailedInfo != null && internalDetailedInfo!!.seasons.isNotEmpty() ->
                    internalDetailedInfo!!.seasons
                else -> emptyList()
            }
            resolvedSeasons.associateWith { sNum ->
                val maxEp = sInfoList.find { it.seasonNumber == sNum }?.maxEp?.takeIf { it > 0 } ?: 12
                (1..maxEp).toList()
            }
        }
    }

    val isTv = remember { DeviceUtils.isAndroidTv(context) }

    // Fullscreen state - on TV directly opens in fullscreen
    var isFullscreenActive by remember { mutableStateOf(if (isTv) true else isFullscreen) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(!isTv) }
    val playControlFocusRequester = remember { FocusRequester() }

    // Lock screen state
    var isLocked by remember { mutableStateOf(false) }

    // Volume state
    var isMuted by remember { mutableStateOf(false) }

    // Audio Manager for volume gestures
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Gesture control states
    var activeGesture by remember { mutableStateOf(PlayerGestureType.NONE) }
    var seekGestureState by remember { mutableStateOf<PlayerSeekHudState?>(null) }
    var doubleTapFeedback by remember { mutableStateOf<PlayerDoubleTapFeedback?>(null) }
    var brightnessFraction by remember {
        val win = activity?.window
        val lp = win?.attributes
        val b = if (lp != null && lp.screenBrightness >= 0f) {
            lp.screenBrightness
        } else {
            try {
                val sysB = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
                (sysB / 255f).coerceIn(0.05f, 1f)
            } catch (_: Exception) { 0.5f }
        }
        mutableFloatStateOf(b)
    }
    var volumeFraction by remember {
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 15f
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 8f
        mutableFloatStateOf((curVol / maxVol).coerceIn(0f, 1f))
    }
    var isBrightnessHudVisible by remember { mutableStateOf(false) }
    var isVolumeHudVisible by remember { mutableStateOf(false) }

    // Drag tracking variables
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatedDx by remember { mutableFloatStateOf(0f) }
    var dragAccumulatedDy by remember { mutableFloatStateOf(0f) }
    var dragInitialSeekMs by remember { mutableLongStateOf(0L) }
    var dragInitialBrightness by remember { mutableFloatStateOf(0.5f) }
    var dragInitialVolumeIndex by remember { mutableIntStateOf(8) }

    // Aspect ratio resize mode (Fit -> Fill -> Zoom)
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Settings modal dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var settingsActiveTab by remember { mutableStateOf("VIDEO") } // "VIDEO" or "AUDIO"
    var tempSelectedVideoQuality by remember { mutableStateOf("1920 × 1080") }
    var currentActiveVideoQuality by remember { mutableStateOf("1920 × 1080") }
    var tempSelectedAudioTrackId by remember { mutableStateOf("auto") }
    var currentActiveAudioTrackId by remember { mutableStateOf("auto") }
    var lastActiveDubTrackId by remember { mutableStateOf("auto") }
    var noDubMessage by remember { mutableStateOf<String?>(null) }
    var hasTriggeredAutoPlayNext by remember { mutableStateOf(false) }

    // Auto-dismiss "No dub language available" notification
    LaunchedEffect(noDubMessage) {
        if (noDubMessage != null) {
            kotlinx.coroutines.delay(3500L)
            noDubMessage = null
        }
    }

    // Combine dubs and internal tracks into availableAudioTracks
    val availableAudioTracks = remember(currentDubs, internalAudioTracks) {
        val list = mutableListOf<AudioTrackItem>()
        if (currentDubs.isNotEmpty()) {
            for ((idx, dub) in currentDubs.withIndex()) {
                val label = resolveDubLabel(dub.lanName, dub.lanCode, dub.original)
                val safeSubjId = dub.subjectId.ifBlank { dub.lanCode.ifBlank { dub.lanName.ifBlank { "$idx" } } }
                val effectiveSubjId = dub.subjectId.ifBlank {
                    if (dub.original) movie.id else ""
                }
                list.add(
                    AudioTrackItem(
                        id = "dub_${safeSubjId}_${dub.lanCode.ifBlank { "$idx" }}",
                        label = label,
                        isDub = true,
                        dubSubjectId = effectiveSubjId,
                        dubDetailPath = dub.detailPath,
                        lanCode = dub.lanCode,
                        isOriginal = dub.original,
                        dub = dub
                    )
                )
            }
        }
        for (internal in internalAudioTracks) {
            if (internal.id != "auto" && internal.id != "default") {
                list.add(internal)
            }
        }
        if (list.isEmpty()) {
            list.add(AudioTrackItem("auto", "Default Audio"))
        }
        list
    }

    fun findTrackForDub(targetDub: DubLanguage?, tracks: List<AudioTrackItem>): AudioTrackItem? {
        if (targetDub == null) return null
        return tracks.firstOrNull { it.dub == targetDub }
            ?: tracks.firstOrNull { it.isDub && targetDub.subjectId.isNotBlank() && it.dubSubjectId == targetDub.subjectId }
            ?: tracks.firstOrNull { it.isDub && targetDub.detailPath.isNotBlank() && it.dubDetailPath == targetDub.detailPath }
            ?: tracks.firstOrNull { it.isDub && targetDub.lanCode.isNotBlank() && it.lanCode.equals(targetDub.lanCode, ignoreCase = true) && it.isOriginal == targetDub.original }
            ?: tracks.firstOrNull { it.isDub && targetDub.lanCode.isNotBlank() && it.lanCode.equals(targetDub.lanCode, ignoreCase = true) }
            ?: tracks.firstOrNull { it.isDub && targetDub.lanName.isNotBlank() && it.label.contains(targetDub.lanName, ignoreCase = true) }
    }

    // Ensure the correct audio track (e.g. Malayalam, Kannada, Hindi, Tamil, Telugu, etc.) is selected
    LaunchedEffect(availableAudioTracks, movie.id, movie.title, movie.genre, movie.corner) {
        if (availableAudioTracks.isNotEmpty()) {
            val isCurrentValid = availableAudioTracks.any { it.id == currentActiveAudioTrackId }
            val shouldSelect = !isCurrentValid ||
                currentActiveAudioTrackId == "auto" ||
                currentActiveAudioTrackId.isBlank() ||
                (!currentActiveAudioTrackId.startsWith("dub_") && availableAudioTracks.any { it.isDub })

            if (shouldSelect) {
                val targetFromProp = findTrackForDub(selectedDub, availableAudioTracks)
                val best = targetFromProp ?: findBestMatchingAudioTrack(availableAudioTracks, movie)
                if (best != null) {
                    currentActiveAudioTrackId = best.id
                    lastActiveDubTrackId = best.id
                    val matchingDub = best.dub ?: currentDubs.firstOrNull { d ->
                        (best.lanCode.isNotBlank() && d.lanCode.equals(best.lanCode, ignoreCase = true)) ||
                        (best.dubDetailPath.isNotBlank() && d.detailPath == best.dubDetailPath) ||
                        (best.dubSubjectId.isNotBlank() && d.subjectId == best.dubSubjectId)
                    }
                    if (matchingDub != null) {
                        onDubSelected?.invoke(matchingDub)
                    }
                }
            }
        }
    }

    // ExoPlayer instance
    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            playWhenReady = true
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    val currentIsSeries by rememberUpdatedState(isSeries)
    val currentIsLastEpisodeOfLastSeason by rememberUpdatedState(isLastEpisodeOfLastSeason)
    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)

    // Attach ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        playbackError = null
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                        durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                        hasTriggeredAutoPlayNext = false
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        if (currentIsSeries && !currentIsLastEpisodeOfLastSeason && !hasTriggeredAutoPlayNext) {
                            hasTriggeredAutoPlayNext = true
                            isPlaying = false
                            currentOnPlaybackEnded?.invoke()
                        } else {
                            isPlaying = false
                            exoPlayer.pause()
                        }
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val rot = videoSize.unappliedRotationDegrees
                val effW = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
                val effH = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
                if (effW > 0 && effH > 0) {
                    isVideoPortrait = effH > effW
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val list = mutableListOf<AudioTrackItem>()
                var selectedInternalTrackId: String? = null
                for ((groupIndex, group) in tracks.groups.withIndex()) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val langCode = format.language?.lowercase() ?: ""
                            val mappedLang = LANGUAGE_MAP[langCode]
                                ?: format.language?.uppercase()?.ifBlank { null }
                                ?: "Audio ${trackIndex + 1}"
                            val label = format.label ?: mappedLang
                            val trackId = "$groupIndex-$trackIndex"
                            list.add(
                                AudioTrackItem(
                                    id = trackId,
                                    label = label,
                                    groupIndex = groupIndex,
                                    trackIndex = trackIndex,
                                    lanCode = langCode
                                )
                            )
                            if (group.isTrackSelected(trackIndex)) {
                                selectedInternalTrackId = trackId
                            }
                        }
                    }
                }
                internalAudioTracks = list
                // Do NOT overwrite with internal track if dub tracks exist or dub is already active
                if (selectedInternalTrackId != null && !currentActiveAudioTrackId.startsWith("dub_") && currentDubs.isEmpty()) {
                    currentActiveAudioTrackId = selectedInternalTrackId
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (isYouTubeMovie) {
                    playbackError = null
                    useYouTubeWebEmbed = true
                    exoPlayer.stop()
                    return
                }
                playbackError = "Playback error: ${error.errorCodeName}"
                if (activeStreamUrl != FALLBACK_STREAM) {
                    scope.launch {
                        delay(500)
                        try {
                            val fallbackSource = createMediaSource(context, FALLBACK_STREAM, "MP4")
                            exoPlayer.setMediaSource(fallbackSource)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Timeline progress updater
    LaunchedEffect(isPlaying, useYouTubeWebEmbed) {
        while (isPlaying && !useYouTubeWebEmbed) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0) {
                durationMs = exoPlayer.duration
                // Auto-play next episode when stream finishes or reaches end in case STATE_ENDED event is delayed
                if (durationMs > 2000L && currentPositionMs >= durationMs - 400L) {
                    if (currentIsSeries && !currentIsLastEpisodeOfLastSeason && !hasTriggeredAutoPlayNext) {
                        hasTriggeredAutoPlayNext = true
                        isPlaying = false
                        currentOnPlaybackEnded?.invoke()
                    } else if (currentIsLastEpisodeOfLastSeason && currentPositionMs >= durationMs - 150L) {
                        isPlaying = false
                        exoPlayer.pause()
                    }
                }
            }
            delay(500)
        }
    }

    // Record watch history periodically while watching and when paused
    LaunchedEffect(isPlaying, selectedSeason, selectedEpisode, useYouTubeWebEmbed) {
        var lastRecordedSec = -1L
        while (isPlaying) {
            val cur = if (useYouTubeWebEmbed) currentPositionMs.coerceAtLeast(0L) else exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = if (useYouTubeWebEmbed) durationMs.coerceAtLeast(0L) else exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0L && cur > 1000L) {
                val secInterval = cur / 3000L
                if (secInterval != lastRecordedSec) {
                    lastRecordedSec = secInterval
                    val pct = ((cur.toFloat() / dur.toFloat()) * 100).toInt().coerceIn(1, 100)
                    val historyId = "${movie.id}_s${selectedSeason}_e${selectedEpisode}"
                    com.example.data.repository.WatchHistoryManager.recordProgress(
                        com.example.data.model.WatchHistoryItem(
                            id = historyId,
                            movieId = movie.id,
                            title = movie.title,
                            season = selectedSeason,
                            episode = selectedEpisode,
                            isSeries = isSeries,
                            coverUrl = movie.coverUrl.ifBlank { internalDetailedInfo?.coverUrl ?: "" },
                            backdropUrl = movie.backdropUrl.ifBlank { internalDetailedInfo?.backdropUrl ?: "" },
                            durationMs = dur,
                            durationFormatted = formatTime(dur),
                            lastPositionMs = cur,
                            progressPercent = pct,
                            source = movie.source,
                            detailPath = movie.detailPath.ifBlank { internalDetailedInfo?.detailPath ?: "" },
                            subjectType = if (isSeries && movie.subjectType <= 1) 2 else movie.subjectType,
                            genre = movie.genre,
                            isShortsPlayer = false
                        )
                    )
                }
            }
            delay(1000)
        }
    }

    // Auto-hide controls after 8 seconds on TV (4.5s on mobile)
    LaunchedEffect(controlsVisible, isPlaying, isLocked, isBuffering, isFetchingStream) {
        if (controlsVisible && isPlaying && !isLocked && !showSettingsDialog && !isBuffering && !isFetchingStream) {
            delay(if (isTv) 8000L else 4500L)
            controlsVisible = false
        }
    }

    // Keep controls visible when buffering starts on mobile so user sees the progress indicator around play button
    LaunchedEffect(isBuffering, isFetchingStream) {
        if (!isTv && (isBuffering || isFetchingStream) && !isLocked) {
            controlsVisible = true
        }
    }

    // Request focus on play button when controls become visible on TV
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && isTv) {
            delay(120)
            try {
                playControlFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (!controlsVisible && isTv) {
            delay(80)
            try {
                playerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-dismiss double-tap feedback after 800ms
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(800L)
            doubleTapFeedback = null
        }
    }

    // Auto-dismiss brightness HUD 1000ms after gesture ends
    LaunchedEffect(isBrightnessHudVisible, activeGesture) {
        if (isBrightnessHudVisible && activeGesture != PlayerGestureType.SWIPE_BRIGHTNESS) {
            delay(1000L)
            isBrightnessHudVisible = false
        }
    }

    // Auto-dismiss volume HUD 1000ms after gesture ends
    LaunchedEffect(isVolumeHudVisible, activeGesture) {
        if (isVolumeHudVisible && activeGesture != PlayerGestureType.SWIPE_VOLUME) {
            delay(1000L)
            isVolumeHudVisible = false
        }
    }

    // Auto-dismiss seek HUD 600ms after seek completes
    LaunchedEffect(seekGestureState, activeGesture) {
        if (seekGestureState != null && activeGesture != PlayerGestureType.SWIPE_SEEK) {
            delay(600L)
            seekGestureState = null
        }
    }

    fun exitFullscreen() {
        activity?.runOnUiThread {
            isFullscreenActive = false
            onFullscreenChanged(false)
            if (!isTv) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } catch (_: Exception) {}
                showSystemUI(activity)
            }
        }
    }

    fun enterFullscreen() {
        activity?.runOnUiThread {
            isFullscreenActive = true
            onFullscreenChanged(true)
            if (!isTv) {
                val currentVideoSize = exoPlayer.videoSize
                val rot = currentVideoSize.unappliedRotationDegrees
                val effW = if (rot == 90 || rot == 270) currentVideoSize.height else currentVideoSize.width
                val effH = if (rot == 90 || rot == 270) currentVideoSize.width else currentVideoSize.height
                val isPortrait = if (effW > 0 && effH > 0) {
                    effH > effW
                } else {
                    isVideoPortrait || isFromShortsPage || movie.isShort || movie.genre.contains("Short", ignoreCase = true)
                }

                try {
                    if (isPortrait) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } catch (_: Exception) {}
            }
            hideSystemUI(activity)
        }
    }

    // Sync fullscreen state
    LaunchedEffect(isFullscreen) {
        if (isFullscreen != isFullscreenActive) {
            isFullscreenActive = isFullscreen
            if (isFullscreen) {
                enterFullscreen()
            } else {
                exitFullscreen()
            }
        }
    }

    // Keep fullscreen orientation aligned if video aspect ratio is determined after playback starts (Mobile only)
    LaunchedEffect(isVideoPortrait, isFullscreenActive) {
        if (!isTv && isFullscreenActive && activity != null) {
            val targetOrientation = if (isVideoPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            try {
                if (activity.requestedOrientation != targetOrientation) {
                    activity.requestedOrientation = targetOrientation
                }
            } catch (_: Exception) {}
        }
    }

    // Back button handling in player:
    // Only intercept if screen is locked (to unlock) or in fullscreen (to exit fullscreen)
    BackHandler(enabled = isLocked || isFullscreenActive) {
        if (isLocked) {
            isLocked = false
        } else if (isFullscreenActive) {
            exitFullscreen()
        }
    }

    // Fetch stream URLs
    LaunchedEffect(movie.id, movie.detailPath, movie.directUrl, isSeries, selectedSeason, selectedEpisode, isDownloaded, currentActiveAudioTrackId) {
        val activeDub = availableAudioTracks.find { it.id == currentActiveAudioTrackId && it.isDub }
        val targetSubjectId = activeDub?.dubSubjectId?.takeIf { it.isNotBlank() } ?: movie.id
        val targetDetailPath = activeDub?.dubDetailPath ?: movie.detailPath
        val currentKey = "${targetSubjectId}_${targetDetailPath}_${selectedSeason}_${selectedEpisode}"
        if (currentKey == lastLoadedStreamKey && activeStreamUrl.isNotBlank()) {
            return@LaunchedEffect
        }
        lastLoadedStreamKey = currentKey
        hasTriggeredAutoPlayNext = false
        isFetchingStream = true
        playbackError = null
        activeStreamUrl = ""
        exoPlayer.stop()

        if (isDownloaded || (!movie.source.equals("lookr", ignoreCase = true) && movie.directUrl.isNotBlank())) {
            activeStreamUrl = movie.directUrl
            activeFormat = "MP4"
            val stream = MovieStream(
                id = "direct",
                resolution = "1080",
                format = "MP4",
                url = movie.directUrl
            )
            resolvedStreams = listOf(stream)
            onStreamResultFetched(
                StreamPlayResult(
                    streams = listOf(stream),
                    hasResource = true,
                    defaultStream = stream,
                    rawJson = "{ \"direct\": true }",
                    requestUrl = movie.directUrl
                )
            )
            onAvailableQualitiesChanged(listOf("1080p"))
            currentActiveVideoQuality = "1920 × 1080"
            tempSelectedVideoQuality = "1920 × 1080"
            isFetchingStream = false
        } else {
            val result = if (movie.source.equals("youtube", ignoreCase = true)) {
                com.example.data.api.YouTubeApiClient.fetchPlayStreams(
                    videoId = targetSubjectId.ifBlank { targetDetailPath }
                )
            } else if (movie.source.equals("lookr", ignoreCase = true)) {
                val lookrStreams = com.example.data.api.LookrApiClient.fetchStreams(
                    subjectId = targetSubjectId,
                    detailPath = targetDetailPath,
                    se = if (isSeries) selectedSeason else 0,
                    ep = if (isSeries) selectedEpisode else 0
                )
                com.example.data.model.StreamPlayResult(
                    streams = lookrStreams,
                    hasResource = lookrStreams.isNotEmpty(),
                    defaultStream = lookrStreams.firstOrNull { it.resolution.contains("720") } ?: lookrStreams.firstOrNull()
                )
            } else {
                MovieBoxApiClient.fetchPlayStreams(
                    context = context,
                    subjectId = targetSubjectId,
                    detailPath = targetDetailPath,
                    isShort = movie.isShort,
                    season = if (isSeries) selectedSeason else 0,
                    episode = if (isSeries) selectedEpisode else 0
                )
            }
            onStreamResultFetched(result)
            val validStreams = result.streams.filter { it.url.isNotBlank() }
            resolvedStreams = validStreams
            val qualities = if (validStreams.isNotEmpty()) {
                val sorted = validStreams.sortedByDescending { s ->
                    extractResolutionHeight(s.resolution).toIntOrNull() ?: 0
                }
                sorted.map { s ->
                    val clean = extractResolutionHeight(s.resolution).ifBlank { "720" }
                    when {
                        s.format.equals("DASH", ignoreCase = true) -> "${clean}p (DASH)"
                        s.format.equals("HLS", ignoreCase = true) -> "${clean}p (HLS)"
                        else -> "${clean}p"
                    }
                }.distinct()
            } else {
                listOf("2160p", "1080p", "720p", "480p")
            }
            onAvailableQualitiesChanged(qualities)

            // Default choose highest quality stream!
            val highestStream = validStreams.maxByOrNull { s ->
                extractResolutionHeight(s.resolution).toIntOrNull() ?: 0
            } ?: result.defaultStream ?: validStreams.firstOrNull()

            val targetRes = if (selectedQuality.isNotBlank() &&
                !selectedQuality.equals("Auto", ignoreCase = true) &&
                !selectedQuality.equals("None", ignoreCase = true)
            ) {
                extractResolutionHeight(selectedQuality)
            } else {
                extractResolutionHeight(highestStream?.resolution ?: "1080")
            }

            val matchedStream = validStreams.firstOrNull { s ->
                extractResolutionHeight(s.resolution) == targetRes
            } ?: highestStream

            if (matchedStream != null && matchedStream.url.isNotBlank()) {
                useYouTubeWebEmbed = false
                activeStreamUrl = matchedStream.url
                activeFormat = matchedStream.format
                val matchedRes = extractResolutionHeight(matchedStream.resolution)
                val displayOpt = formatResolutionDisplay(matchedRes)
                currentActiveVideoQuality = displayOpt
                tempSelectedVideoQuality = displayOpt
                onQualitySelected(displayOpt)
            } else if (isYouTubeMovie) {
                // Direct InnerTube stream was bot-gated; use official YouTube IFrame Player in WebView!
                useYouTubeWebEmbed = true
                activeStreamUrl = ""
                activeFormat = "YT_EMBED"
                currentActiveVideoQuality = "1920 × 1080"
                tempSelectedVideoQuality = "1920 × 1080"
                isBuffering = true
            } else {
                useYouTubeWebEmbed = false
                activeStreamUrl = FALLBACK_STREAM
                activeFormat = "MP4"
            }
            isFetchingStream = false
        }
    }

    val effectiveMediaHeaders = remember(movie) {
        when {
            movie.source.equals("youtube", ignoreCase = true) -> {
                com.example.data.api.YouTubeApiClient.YOUTUBE_VIDEO_HEADERS
            }
            movie.source.equals("lookr", ignoreCase = true) -> {
                com.example.data.api.LookrApiClient.LOOKR_VIDEO_HEADERS
            }
            else -> {
                movie.customHeaders
            }
        }
    }

    var pendingSeekPos by remember(movie.id) {
        mutableLongStateOf(if (initialSeekMs > 0L) initialSeekMs else -1L)
    }
    var lastEpisodeMarker by remember(movie.id) {
        mutableStateOf("${selectedSeason}_${selectedEpisode}")
    }
    LaunchedEffect(selectedSeason, selectedEpisode) {
        val currentEpMarker = "${selectedSeason}_${selectedEpisode}"
        if (currentEpMarker != lastEpisodeMarker) {
            lastEpisodeMarker = currentEpMarker
            // When user or auto-play changes episode/season, always start new episode from 00:00
            pendingSeekPos = -1L
            currentPositionMs = 0L
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }
    var lastMediaKey by remember { mutableStateOf("") }
    val activeDubForMedia = availableAudioTracks.find { it.id == currentActiveAudioTrackId && it.isDub }
    val effectiveSubjForMedia = activeDubForMedia?.dubSubjectId?.takeIf { it.isNotBlank() } ?: movie.id
    val effectivePathForMedia = activeDubForMedia?.dubDetailPath ?: movie.detailPath
    val currentMediaKey = "${effectiveSubjForMedia}_${effectivePathForMedia}_${selectedSeason}_${selectedEpisode}"

    LaunchedEffect(currentMediaKey) {
        hasTriggeredAutoPlayNext = false
    }

    // Load active stream into ExoPlayer seamlessly
    LaunchedEffect(activeStreamUrl) {
        if (activeStreamUrl.isNotBlank()) {
            val isNewMedia = (currentMediaKey != lastMediaKey)
            lastMediaKey = currentMediaKey
            val currentPos = if (isNewMedia) 0L else exoPlayer.currentPosition
            try {
                val mediaSource = createMediaSource(context, activeStreamUrl, activeFormat, effectiveMediaHeaders)
                if (pendingSeekPos >= 0L) {
                    val targetSeek = pendingSeekPos
                    pendingSeekPos = -1L
                    exoPlayer.setMediaSource(mediaSource, targetSeek)
                    exoPlayer.prepare()
                    exoPlayer.seekTo(targetSeek)
                } else if (isNewMedia) {
                    exoPlayer.setMediaSource(mediaSource, /* resetPosition = */ true)
                    exoPlayer.prepare()
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                } else if (currentPos > 0) {
                    exoPlayer.setMediaSource(mediaSource, currentPos)
                    exoPlayer.prepare()
                    exoPlayer.seekTo(currentPos)
                } else {
                    exoPlayer.setMediaSource(mediaSource, /* resetPosition = */ true)
                    exoPlayer.prepare()
                }
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                isPlaying = true
                playbackError = null
            } catch (e: Exception) {
                playbackError = e.message
            }
        }
    }

    // Apply quality selection (None & Auto removed, switches strictly between stream resolutions)
    fun applyQualityChange(qualityOption: String) {
        if (qualityOption.equals("None", ignoreCase = true) || qualityOption.equals("Auto", ignoreCase = true)) {
            return
        }
        currentActiveVideoQuality = qualityOption
        onQualitySelected(qualityOption)

        val targetHeight = extractResolutionHeight(qualityOption)
        val stream = resolvedStreams.firstOrNull { s ->
            extractResolutionHeight(s.resolution) == targetHeight
        } ?: resolvedStreams.maxByOrNull { s ->
            extractResolutionHeight(s.resolution).toIntOrNull() ?: 0
        } ?: resolvedStreams.firstOrNull()

        if (stream != null && stream.url.isNotBlank()) {
            val curPos = exoPlayer.currentPosition
            activeFormat = stream.format
            activeStreamUrl = stream.url
            try {
                val mediaSource = createMediaSource(context, stream.url, stream.format, effectiveMediaHeaders)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                if (curPos > 0) exoPlayer.seekTo(curPos)
                exoPlayer.play()
            } catch (_: Exception) {}
        }
    }

    // Centralized function for switching dub language stream and audio
    fun switchDubPlayback(selectedAudio: AudioTrackItem, preservePos: Boolean = true) {
        val prevActiveTrackId = if (lastActiveDubTrackId != "auto" && availableAudioTracks.any { it.id == lastActiveDubTrackId }) {
            lastActiveDubTrackId
        } else {
            currentActiveAudioTrackId
        }

        fun onNoDubAvailable() {
            Toast.makeText(context, "No dub language available", Toast.LENGTH_SHORT).show()
            noDubMessage = "No dub language available"
            currentActiveAudioTrackId = prevActiveTrackId
            lastActiveDubTrackId = prevActiveTrackId
        }

        val effectiveSubjId = selectedAudio.dubSubjectId.ifBlank {
            if (movie.id.isNotBlank()) movie.id else ""
        }
        if (effectiveSubjId.isBlank() && selectedAudio.dubDetailPath.isBlank()) {
            onNoDubAvailable()
            return
        }

        scope.launch {
            val curPos = if (preservePos) exoPlayer.currentPosition else 0L
            pendingSeekPos = curPos
            isFetchingStream = true
            try {
                val dubResultDeferred = async {
                    if (movie.source.equals("lookr", ignoreCase = true)) {
                        val lookrStreams = com.example.data.api.LookrApiClient.fetchStreams(
                            subjectId = effectiveSubjId,
                            detailPath = selectedAudio.dubDetailPath,
                            se = if (isSeries) selectedSeason else 0,
                            ep = if (isSeries) selectedEpisode else 0
                        )
                        com.example.data.model.StreamPlayResult(
                            streams = lookrStreams,
                            hasResource = lookrStreams.isNotEmpty(),
                            defaultStream = lookrStreams.firstOrNull { it.resolution.contains("720") } ?: lookrStreams.firstOrNull()
                        )
                    } else {
                        MovieBoxApiClient.fetchPlayStreams(
                            context = context,
                            subjectId = effectiveSubjId,
                            detailPath = selectedAudio.dubDetailPath,
                            isShort = movie.isShort,
                            season = if (isSeries) selectedSeason else 0,
                            episode = if (isSeries) selectedEpisode else 0
                        )
                    }
                }

                val dubDetailDeferred = async {
                    if (movie.source.equals("lookr", ignoreCase = true)) {
                        com.example.data.api.LookrApiClient.fetchSubjectDetail(
                            detailPath = selectedAudio.dubDetailPath,
                            subjectId = effectiveSubjId
                        )
                    } else {
                        MovieBoxApiClient.fetchSubjectDetail(
                            context = context,
                            detailPath = selectedAudio.dubDetailPath,
                            fallbackSubjectId = effectiveSubjId
                        )
                    }
                }

                val dubResult = dubResultDeferred.await()
                val dubDetail = dubDetailDeferred.await()

                if (dubDetail != null) {
                    internalDetailedInfo = dubDetail
                    if (dubDetail.dubs.isNotEmpty()) {
                        currentDubs = dubDetail.dubs
                        onDubsListUpdated?.invoke(dubDetail.dubs)
                    }
                    onSubjectDetailUpdated?.invoke(dubDetail)
                }

                val validStreams = dubResult.streams.filter { it.url.isNotBlank() }
                if (validStreams.isNotEmpty()) {
                    val targetHeight = extractResolutionHeight(currentActiveVideoQuality)
                    val matched = validStreams.firstOrNull { s ->
                        extractResolutionHeight(s.resolution) == targetHeight
                    } ?: validStreams.maxByOrNull { s ->
                        extractResolutionHeight(s.resolution).toIntOrNull() ?: 0
                    } ?: dubResult.defaultStream?.takeIf { it.url.isNotBlank() } ?: validStreams.firstOrNull()

                    if (matched != null && matched.url.isNotBlank()) {
                        resolvedStreams = validStreams
                        lastActiveDubTrackId = selectedAudio.id
                        currentActiveAudioTrackId = selectedAudio.id
                        lastLoadedStreamKey = "${effectiveSubjId}_${selectedAudio.dubDetailPath}_${selectedSeason}_${selectedEpisode}"
                        lastMediaKey = lastLoadedStreamKey
                        val matchedRes = extractResolutionHeight(matched.resolution)
                        val displayOpt = formatResolutionDisplay(matchedRes)
                        currentActiveVideoQuality = displayOpt
                        tempSelectedVideoQuality = displayOpt
                        onQualitySelected(displayOpt)
                        activeFormat = matched.format
                        activeStreamUrl = matched.url
                        val ms = createMediaSource(context, matched.url, matched.format, effectiveMediaHeaders)
                        exoPlayer.setMediaSource(ms)
                        exoPlayer.prepare()
                        if (curPos > 0) {
                            exoPlayer.seekTo(curPos)
                        }
                        exoPlayer.play()

                        val matchingDub = selectedAudio.dub ?: currentDubs.firstOrNull { d ->
                            (selectedAudio.lanCode.isNotBlank() && d.lanCode.equals(selectedAudio.lanCode, ignoreCase = true)) ||
                            (selectedAudio.dubDetailPath.isNotBlank() && d.detailPath == selectedAudio.dubDetailPath) ||
                            (selectedAudio.dubSubjectId.isNotBlank() && d.subjectId == selectedAudio.dubSubjectId)
                        } ?: dubDetail?.dubs?.firstOrNull { d ->
                            (selectedAudio.lanCode.isNotBlank() && d.lanCode.equals(selectedAudio.lanCode, ignoreCase = true)) ||
                            (selectedAudio.dubDetailPath.isNotBlank() && d.detailPath == selectedAudio.dubDetailPath) ||
                            (selectedAudio.dubSubjectId.isNotBlank() && d.subjectId == selectedAudio.dubSubjectId)
                        }
                        if (matchingDub != null) {
                            onDubSelected?.invoke(matchingDub)
                        }
                    } else {
                        onNoDubAvailable()
                    }
                } else {
                    onNoDubAvailable()
                }
            } catch (_: Exception) {
                onNoDubAvailable()
            } finally {
                isFetchingStream = false
            }
        }
    }

    // React to external dub change (e.g. user clicked dub language chip)
    LaunchedEffect(selectedDub) {
        if (selectedDub != null) {
            val matchingTrack = findTrackForDub(selectedDub, availableAudioTracks)
                ?: AudioTrackItem(
                    id = "dub_${selectedDub.subjectId.ifBlank { selectedDub.lanCode.ifBlank { selectedDub.lanName } }}_${selectedDub.lanCode}",
                    label = resolveDubLabel(selectedDub.lanName, selectedDub.lanCode, selectedDub.original),
                    isDub = true,
                    dubSubjectId = selectedDub.subjectId.ifBlank { if (selectedDub.original) movie.id else "" },
                    dubDetailPath = selectedDub.detailPath,
                    lanCode = selectedDub.lanCode,
                    isOriginal = selectedDub.original,
                    dub = selectedDub
                )
            if (matchingTrack.id != currentActiveAudioTrackId) {
                switchDubPlayback(matchingTrack, preservePos = true)
            }
        }
    }

    // Format display resolution options from streams (strictly NO "None" and NO "Auto")
    // Highest quality is listed first (2160p (4K) -> 1440p -> 1080p -> 720p...)
    val videoResolutionOptions = remember(resolvedStreams) {
        val list = mutableListOf<String>()
        if (resolvedStreams.isNotEmpty()) {
            val sortedStreams = resolvedStreams
                .filter { it.url.isNotBlank() }
                .sortedByDescending { s ->
                    extractResolutionHeight(s.resolution).toIntOrNull() ?: 0
                }
            sortedStreams.forEach { s ->
                val clean = extractResolutionHeight(s.resolution).ifBlank { "720" }
                val display = formatResolutionDisplay(clean)
                if (!list.contains(display)) {
                    list.add(display)
                }
            }
        }
        if (list.isEmpty()) {
            list.add("3840 × 2160")
            list.add("1920 × 1080")
            list.add("1280 × 720")
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val isRight = offset.x >= size.width / 2f
                            val dur = if (durationMs > 0) durationMs else exoPlayer.duration.coerceAtLeast(0L)
                            val basePos = if (useYouTubeWebEmbed) currentPositionMs else exoPlayer.currentPosition
                            if (isRight) {
                                val targetPos = (basePos + 10000L).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)
                                if (useYouTubeWebEmbed) ytWebSeekRequestMs = targetPos else exoPlayer.seekTo(targetPos)
                                currentPositionMs = targetPos
                                doubleTapFeedback = PlayerDoubleTapFeedback(isRight = true, timestamp = System.currentTimeMillis())
                            } else {
                                val targetPos = (basePos - 10000L).coerceAtLeast(0L)
                                if (useYouTubeWebEmbed) ytWebSeekRequestMs = targetPos else exoPlayer.seekTo(targetPos)
                                currentPositionMs = targetPos
                                doubleTapFeedback = PlayerDoubleTapFeedback(isRight = false, timestamp = System.currentTimeMillis())
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragAccumulatedDx = 0f
                        dragAccumulatedDy = 0f
                        dragInitialSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        activeGesture = PlayerGestureType.NONE

                        val win = activity?.window
                        val lp = win?.attributes
                        dragInitialBrightness = if (lp != null && lp.screenBrightness >= 0f) {
                            lp.screenBrightness
                        } else {
                            try {
                                val sysB = android.provider.Settings.System.getInt(
                                    context.contentResolver,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                    128
                                )
                                (sysB / 255f).coerceIn(0.05f, 1f)
                            } catch (_: Exception) { 0.5f }
                        }
                        dragInitialVolumeIndex = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedDx += dragAmount.x
                        dragAccumulatedDy += dragAmount.y

                        if (activeGesture == PlayerGestureType.NONE) {
                            val touchSlop = 16f
                            if (abs(dragAccumulatedDx) > touchSlop || abs(dragAccumulatedDy) > touchSlop) {
                                activeGesture = if (abs(dragAccumulatedDx) > abs(dragAccumulatedDy)) {
                                    PlayerGestureType.SWIPE_SEEK
                                } else {
                                    if (dragStartX < size.width / 2f) {
                                        PlayerGestureType.SWIPE_BRIGHTNESS
                                    } else {
                                        PlayerGestureType.SWIPE_VOLUME
                                    }
                                }
                            }
                        }

                        when (activeGesture) {
                            PlayerGestureType.SWIPE_SEEK -> {
                                val videoDuration = if (durationMs > 0) durationMs else exoPlayer.duration.coerceAtLeast(0L)
                                val seekSpanMs = if (videoDuration > 0) {
                                    maxOf(60_000L, minOf(videoDuration, 300_000L))
                                } else 90_000L
                                val deltaFraction = (dragAccumulatedDx / size.width.toFloat()).coerceIn(-1.5f, 1.5f)
                                val deltaMs = (deltaFraction * seekSpanMs).toLong()
                                val targetMs = (dragInitialSeekMs + deltaMs).coerceIn(0L, if (videoDuration > 0) videoDuration else Long.MAX_VALUE)
                                seekGestureState = PlayerSeekHudState(
                                    targetMs = targetMs,
                                    durationMs = videoDuration,
                                    deltaMs = deltaMs,
                                    isForward = deltaMs >= 0
                                )
                            }
                            PlayerGestureType.SWIPE_BRIGHTNESS -> {
                                val deltaFraction = -dragAccumulatedDy / (size.height * 0.7f)
                                val newBrightness = (dragInitialBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                activity?.window?.let { win ->
                                    val attrs = win.attributes
                                    attrs.screenBrightness = newBrightness
                                    win.attributes = attrs
                                }
                                brightnessFraction = newBrightness
                                isBrightnessHudVisible = true
                            }
                            PlayerGestureType.SWIPE_VOLUME -> {
                                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                val deltaFraction = -dragAccumulatedDy / (size.height * 0.7f)
                                val targetVol = (dragInitialVolumeIndex + (deltaFraction * maxVol).toInt()).coerceIn(0, maxVol)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                volumeFraction = targetVol.toFloat() / maxVol.toFloat()
                                isVolumeHudVisible = true
                            }
                            PlayerGestureType.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        if (activeGesture == PlayerGestureType.SWIPE_SEEK) {
                            seekGestureState?.let { state ->
                                if (useYouTubeWebEmbed) ytWebSeekRequestMs = state.targetMs else exoPlayer.seekTo(state.targetMs)
                                currentPositionMs = state.targetMs
                            }
                        }
                        activeGesture = PlayerGestureType.NONE
                    },
                    onDragCancel = {
                        activeGesture = PlayerGestureType.NONE
                    }
                )
            }
            .testTag("detail_exoplayer_container")
            .then(
                if (!controlsVisible && isTv) {
                    Modifier
                        .focusRequester(playerFocusRequester)
                        .focusable()
                } else if (!isTv) {
                    Modifier
                        .focusRequester(playerFocusRequester)
                        .focusable()
                } else {
                    Modifier
                }
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (isTv) {
                        if (!controlsVisible) {
                            // CONTROLS ARE HIDDEN (Normal TV playback):
                            // Left/Right: Fast rewind / Fast forward 10s directly without showing controls
                            // OK / DPAD_CENTER / ENTER: Makes controls visible for navigation
                            // UP / DOWN: Also reveals controls
                            // BACK: Exits player
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    controlsVisible = true
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_MEDIA_REWIND,
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    val basePos = if (useYouTubeWebEmbed) currentPositionMs else exoPlayer.currentPosition
                                    val newPos = (basePos - 10000L).coerceAtLeast(0L)
                                    if (useYouTubeWebEmbed) ytWebSeekRequestMs = newPos else exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    val maxDur = if (durationMs > 0) durationMs else exoPlayer.duration
                                    val basePos = if (useYouTubeWebEmbed) currentPositionMs else exoPlayer.currentPosition
                                    val newPos = (basePos + 10000L).coerceAtMost(if (maxDur > 0) maxDur else Long.MAX_VALUE)
                                    if (useYouTubeWebEmbed) ytWebSeekRequestMs = newPos else exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_PLAY,
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    if (useYouTubeWebEmbed) {
                                        isPlaying = !isPlaying
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onBackClick?.invoke()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            // CONTROLS ARE VISIBLE ON TV:
                            // Return false for D-pad navigation so focus subsystem moves between control buttons and OK activates them
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BACK -> {
                                    controlsVisible = false
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        // MOBILE DEVICE KEY EVENTS (Keyboard, hardware buttons, emulator):
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BACK -> {
                                if (isLocked) {
                                    isLocked = false
                                    true
                                } else if (isFullscreenActive) {
                                    exitFullscreen()
                                    true
                                } else {
                                    onBackClick?.invoke()
                                    true
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
    ) {
        if (useYouTubeWebEmbed) {
            YouTubeWebPlayerSurface(
                videoId = movie.id.ifBlank { movie.detailPath },
                isPlaying = isPlaying,
                isMuted = isMuted,
                seekRequestMs = ytWebSeekRequestMs,
                onReady = { durMs ->
                    isBuffering = false
                    isFetchingStream = false
                    if (durMs > 0L) durationMs = durMs
                },
                onProgress = { curMs, durMs ->
                    currentPositionMs = curMs
                    if (durMs > 0L) durationMs = durMs
                },
                onBufferingChanged = { buf ->
                    isBuffering = buf
                },
                onStatePlayingChanged = { playing ->
                    isPlaying = playing
                },
                onEnded = {
                    isPlaying = false
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Native ExoPlayer View with dynamic resizeMode
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                    if (playerView.resizeMode != resizeMode) {
                        playerView.resizeMode = resizeMode
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Episode auto-play loading indicator
        if (isFetchingStream && isSeries) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEE18181B),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFE50914),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Loading Episode $selectedEpisode...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if ((isBuffering || isFetchingStream) && !controlsVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }



        // Gesture feedback HUD (Double tap to seek, Swipe to seek, Swipe to change settings)
        PlayerGesturesOverlay(
            doubleTapFeedback = doubleTapFeedback,
            seekState = seekGestureState,
            isBrightnessHudVisible = isBrightnessHudVisible,
            brightnessFraction = brightnessFraction,
            isVolumeHudVisible = isVolumeHudVisible,
            volumeFraction = volumeFraction
        )

        // Lock screen floating button (when screen is locked)
        if (isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        controlsVisible = true
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Full Controls Overlay (When unlocked)
        if (!isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    // TOP BAR: Back Button + Title + Volume/Mute + Lock Screen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back button: in fullscreen mode it exits fullscreen back to detail screen; in non-fullscreen it goes back to previous screen
                            IconButton(
                                onClick = {
                                    if (isFullscreenActive) {
                                        exitFullscreen()
                                    } else {
                                        onBackClick?.invoke()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .tvControlFocusable()
                                    .testTag("player_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = movie.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (isSeries && isFullscreenActive) {
                                    Text(
                                        text = "Season $selectedSeason • Episode $selectedEpisode",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Right icons: Download (hidden for Story TV) + Play Next (Series full screen only) + Volume + Lock
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isStoryTv = movie.isStoryTvServer || movie.source.equals("storytv", ignoreCase = true)
                            if (!isStoryTv) {
                                IconButton(
                                    onClick = {
                                        showDownloadDialog = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .tvControlFocusable()
                                        .testTag("player_download_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            if (isSeries && isFullscreenActive) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onPlayNext?.invoke() },
                                    enabled = !isLastEpisodeOfLastSeason,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .tvControlFocusable()
                                        .testTag("player_top_play_next_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Play Next Episode",
                                        tint = if (!isLastEpisodeOfLastSeason) Color.White else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .tvControlFocusable()
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    isLocked = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .tvControlFocusable()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Lock Controls",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // BOTTOM CONTROLS & TIMELINE (Matching Screenshot 1 & 2 exactly)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        // ROW 1: Aspect Ratio, Rewind, White Circle Play/Pause, Fast Forward, Settings, Fullscreen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Aspect Ratio / Resize button (Fit / Fill / Zoom)
                            TvPlayerControlButton(
                                icon = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            )

                            // 2. Picture-in-Picture (PiP) Button
                            TvPlayerControlButton(
                                icon = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                onClick = {
                                    enterPipMode(context)
                                }
                            )

                            // 3. Fast Rewind (<<)
                            TvPlayerControlButton(
                                icon = Icons.Default.FastRewind,
                                contentDescription = "Rewind",
                                iconSize = 28.dp,
                                onClick = {
                                    val basePos = if (useYouTubeWebEmbed) currentPositionMs else exoPlayer.currentPosition
                                    val newPos = (basePos - 10000L).coerceAtLeast(0L)
                                    if (useYouTubeWebEmbed) ytWebSeekRequestMs = newPos else exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            )

                            // 4. White Solid Circle Play / Pause Button with Circular Loading Indicator around it
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(64.dp)
                            ) {
                                if (isBuffering || isFetchingStream) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(62.dp)
                                    )
                                }
                                TvPlayerControlButton(
                                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    isPrimary = true,
                                    size = 54.dp,
                                    iconSize = 32.dp,
                                    focusRequester = playControlFocusRequester,
                                    onClick = {
                                        if (useYouTubeWebEmbed) {
                                            isPlaying = !isPlaying
                                        } else {
                                            if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                exoPlayer.play()
                                            }
                                        }
                                    }
                                )
                            }

                            // 5. Fast Forward (>>)
                            TvPlayerControlButton(
                                icon = Icons.Default.FastForward,
                                contentDescription = "Fast Forward",
                                iconSize = 28.dp,
                                onClick = {
                                    val maxDur = if (durationMs > 0) durationMs else exoPlayer.duration
                                    val basePos = if (useYouTubeWebEmbed) currentPositionMs else exoPlayer.currentPosition
                                    val newPos = (basePos + 10000L).coerceAtMost(if (maxDur > 0) maxDur else Long.MAX_VALUE)
                                    if (useYouTubeWebEmbed) ytWebSeekRequestMs = newPos else exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            )

                            // 6. Settings Gear Button (Opens Settings Modal Dialog)
                            TvPlayerControlButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Settings",
                                onClick = {
                                    tempSelectedVideoQuality = currentActiveVideoQuality
                                    tempSelectedAudioTrackId = currentActiveAudioTrackId
                                    settingsActiveTab = "VIDEO"
                                    showSettingsDialog = true
                                }
                            )

                            // 7. Fullscreen Toggle Button (Not needed on TV since TV is always full screen)
                            if (!isTv) {
                                TvPlayerControlButton(
                                    icon = if (isFullscreenActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    onClick = {
                                        if (isFullscreenActive) {
                                            exitFullscreen()
                                        } else {
                                            enterFullscreen()
                                        }
                                    }
                                )
                            }
                        }

                        // ROW 2: Current Time (00:00) | Normal Scrubber Line | Total Time (00:00)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isUserSeeking by remember { mutableStateOf(false) }
                            var seekTargetMs by remember { mutableLongStateOf(0L) }

                            val displayPosMs = if (isUserSeeking) seekTargetMs else currentPositionMs

                            Text(
                                text = formatTime(displayPosMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )

                            NormalPlayerSeekBar(
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                onSeekTo = { targetMs ->
                                    if (useYouTubeWebEmbed) {
                                        ytWebSeekRequestMs = targetMs
                                        currentPositionMs = targetMs
                                    } else {
                                        exoPlayer.seekTo(targetMs)
                                    }
                                },
                                onSeekingChange = { seeking, targetMs ->
                                    isUserSeeking = seeking
                                    if (seeking) seekTargetMs = targetMs
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )

                            Text(
                                text = formatTime(durationMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // "No dub language available" Notification Banner
            AnimatedVisibility(
                visible = noDubMessage != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFullscreenActive) 56.dp else 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xEE18181B),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = noDubMessage ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // SETTINGS MODAL DIALOG (Scrollable, accessible OK/Cancel in fullscreen!)
        if (showSettingsDialog) {
            VideoAudioSettingsDialog(
                show = showSettingsDialog,
                onDismissRequest = { showSettingsDialog = false },
                videoResolutionOptions = videoResolutionOptions,
                currentVideoQuality = currentActiveVideoQuality,
                onVideoQualitySelected = { q ->
                    applyQualityChange(q)
                },
                availableAudioTracks = availableAudioTracks.map {
                    PlayerAudioTrack(
                        id = it.id,
                        label = it.label,
                        groupIndex = it.groupIndex,
                        trackIndex = it.trackIndex,
                        isDub = it.isDub,
                        dubSubjectId = it.dubSubjectId,
                        dubDetailPath = it.dubDetailPath,
                        lanCode = it.lanCode,
                        isOriginal = it.isOriginal,
                        dub = it.dub
                    )
                },
                currentAudioTrackId = currentActiveAudioTrackId,
                onAudioTrackSelected = { selectedAudio ->
                    if (selectedAudio.isDub) {
                        val trackItem = availableAudioTracks.firstOrNull { it.id == selectedAudio.id }
                            ?: AudioTrackItem(
                                id = selectedAudio.id,
                                label = selectedAudio.label,
                                isDub = true,
                                dubSubjectId = selectedAudio.dubSubjectId,
                                dubDetailPath = selectedAudio.dubDetailPath,
                                lanCode = selectedAudio.lanCode,
                                isOriginal = selectedAudio.isOriginal,
                                dub = selectedAudio.dub
                            )
                        switchDubPlayback(trackItem, preservePos = true)
                    } else if (selectedAudio.groupIndex >= 0) {
                        try {
                            val trackGroup = exoPlayer.currentTracks.groups[selectedAudio.groupIndex]
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(
                                        trackGroup.mediaTrackGroup,
                                        listOf(selectedAudio.trackIndex)
                                    )
                                )
                                .build()
                            lastActiveDubTrackId = selectedAudio.id
                            currentActiveAudioTrackId = selectedAudio.id
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot select track", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No dub language available", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // DOWNLOAD QUALITY CHOOSE MODAL DIALOG
        if (showDownloadDialog) {
            val activeDub = availableAudioTracks.firstOrNull { it.id == currentActiveAudioTrackId }
            val effectiveMovie = if (activeDub != null && activeDub.isDub && activeDub.dubSubjectId.isNotBlank()) {
                movie.copy(
                    id = activeDub.dubSubjectId,
                    detailPath = activeDub.dubDetailPath.ifBlank { movie.detailPath }
                )
            } else {
                movie
            }

            DownloadQualityDialog(
                show = showDownloadDialog,
                movieTitle = movie.title,
                currentDubLabel = activeDub?.label ?: "",
                availableStreams = resolvedStreams,
                isSeriesOrShorts = isSeries,
                availableSeasons = resolvedSeasons,
                seasonEpisodesMap = resolvedSeasonEpisodesMap,
                currentSeason = if (isSeries) selectedSeason else 1,
                currentEpisode = if (isSeries) selectedEpisode else 1,
                onDismissRequest = { showDownloadDialog = false },
                onDownloadConfirmed = { quality, url ->
                    val finalUrl = url.ifBlank { activeStreamUrl }
                    val downloadManager = MovieDownloadManager.getInstance(context)
                    downloadManager.startDownload(
                        movie = effectiveMovie,
                        quality = quality,
                        downloadUrl = finalUrl,
                        dubLabel = activeDub?.label ?: "",
                        seasonNumber = if (isSeries) selectedSeason else 0,
                        episodeNumber = if (isSeries) selectedEpisode else 0,
                        isSeries = isSeries
                    )
                    val epSuffix = if (isSeries && (selectedSeason > 0 || selectedEpisode > 0)) {
                        " (S${selectedSeason.toString().padStart(2, '0')} E${selectedEpisode.toString().padStart(2, '0')})"
                    } else ""
                    Toast.makeText(
                        context,
                        "Download started: ${movie.title}$epSuffix ($quality)",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onBatchDownloadConfirmed = { quality, url, selectedEps ->
                    val downloadManager = MovieDownloadManager.getInstance(context)
                    selectedEps.forEach { (sNum, epNum) ->
                        val finalUrl = if (sNum == selectedSeason && epNum == selectedEpisode) {
                            url.ifBlank { activeStreamUrl }
                        } else ""
                        downloadManager.startDownload(
                            movie = effectiveMovie,
                            quality = quality,
                            downloadUrl = finalUrl,
                            dubLabel = activeDub?.label ?: "",
                            seasonNumber = sNum,
                            episodeNumber = epNum,
                            isSeries = true
                        )
                    }
                    val msg = if (selectedEps.size > 1) {
                        "Downloading ${selectedEps.size} episodes ($quality)"
                    } else {
                        val first = selectedEps.firstOrNull() ?: Pair(selectedSeason, selectedEpisode)
                        "Download started: ${movie.title} S${first.first.toString().padStart(2, '0')}E${first.second.toString().padStart(2, '0')} ($quality)"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

private fun enterPipMode(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = context.findActivity() ?: return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            try {
                activity.enterPictureInPictureMode()
            } catch (_: Exception) {}
        }
    }
}
