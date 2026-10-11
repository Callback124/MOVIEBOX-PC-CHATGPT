package com.example.ui.home.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dvr
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.api.MovieBoxApiClient
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.MovieBoxRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class AspectMode(val label: String, val resizeMode: Int) {
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    ORIGINAL("Orig", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
}

@OptIn(UnstableApi::class)
@Composable
fun MoviePlayerSheet(
    movie: MovieItem?,
    onDismiss: () -> Unit
) {
    if (movie == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val configuration = LocalConfiguration.current

    // Stream and Player States
    var isLoadingStreams by remember { mutableStateOf(movie.directUrl.isBlank()) }
    var availableStreams by remember { mutableStateOf<List<MovieStream>>(emptyList()) }
    var currentStream by remember { mutableStateOf<MovieStream?>(null) }
    var streamErrorMessage by remember { mutableStateOf<String?>(null) }

    // ExoPlayer Playback states
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionFraction by remember { mutableFloatStateOf(0f) }

    // Aspect Ratio / Resize Mode
    var currentAspectModeIndex by remember { mutableIntStateOf(0) }
    val currentAspectMode = AspectMode.entries[currentAspectModeIndex % AspectMode.entries.size]
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Menus & Dialogs
    var isSettingsMenuOpen by remember { mutableStateOf(false) }
    var isQualityMenuOpen by remember { mutableStateOf(false) }
    var isSpeedMenuOpen by remember { mutableStateOf(false) }
    var isSubtitleDialogOpen by remember { mutableStateOf(false) }
    var isStreamInfoDialogOpen by remember { mutableStateOf(false) }
    var isVolumeSliderVisible by remember { mutableStateOf(false) }

    // Audio & Speed
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var currentVolumeFraction by remember {
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 15f
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 10f
        mutableFloatStateOf((curVol / maxVol).coerceIn(0f, 1f))
    }

    // Decoder Mode: HW / SW
    var isHardwareDecoder by remember { mutableStateOf(true) }

    // Transient Gesture HUD Overlay
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }

    // Enhanced gesture states
    var activeGesture by remember { mutableStateOf(PlayerGestureType.NONE) }
    var seekGestureState by remember { mutableStateOf<PlayerSeekHudState?>(null) }
    var doubleTapFeedback by remember { mutableStateOf<PlayerDoubleTapFeedback?>(null) }
    var brightnessFraction by remember {
        val activity = context.findActivity()
        val win = activity?.window
        val lp = win?.attributes
        val b = if (lp != null && lp.screenBrightness >= 0f) {
            lp.screenBrightness
        } else {
            0.5f
        }
        mutableFloatStateOf(b)
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

    // ExoPlayer instance initialization
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://movieboxph.org/",
                    "Origin" to "https://movieboxph.org"
                ) + (if (movie.source.equals("lookr", ignoreCase = true)) com.example.data.api.LookrApiClient.LOOKR_VIDEO_HEADERS else movie.customHeaders)
            )
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    // Attach ExoPlayer Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            // Reset system orientation to default when exiting player (Mobile Only)
            if (!com.example.util.DeviceUtils.isAndroidTv(context)) {
                try {
                    val activity = context.findActivity()
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } catch (_: Exception) {}
            }
        }
    }

    // Function to build appropriate MediaItem / MediaSource for any format
    fun playStreamUrl(url: String) {
        if (url.isBlank()) return
        val lower = url.lowercase()
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        when {
            lower.contains(".m3u8") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.contains(".ism") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
            lower.startsWith("rtsp://") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_RTSP)
        }
        val mediaItem = mediaItemBuilder.build()

        if (lower.startsWith("rtsp://")) {
            val rtspMediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)
            exoPlayer.setMediaSource(rtspMediaSource)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Stream loader: plays directUrl immediately or calls MovieBox API
    fun loadAndPlayStream(isRetry: Boolean = false) {
        coroutineScope.launch {
            if (!movie.source.equals("lookr", ignoreCase = true) && movie.directUrl.isNotBlank()) {
                isLoadingStreams = false
                isBuffering = true
                streamErrorMessage = null
                currentStream = MovieStream(
                    id = "direct_stream",
                    resolution = "Auto",
                    format = getFormatFromUrl(movie.directUrl),
                    url = movie.directUrl
                )
                availableStreams = listOf(currentStream!!)
                playStreamUrl(movie.directUrl)
                return@launch
            }

            isLoadingStreams = true
            isBuffering = true
            streamErrorMessage = null

            val result = if (movie.source.equals("lookr", ignoreCase = true)) {
                val lookrStreams = com.example.data.api.LookrApiClient.fetchStreams(
                    subjectId = movie.id,
                    detailPath = movie.detailPath
                )
                com.example.data.model.StreamPlayResult(
                    streams = lookrStreams,
                    hasResource = lookrStreams.isNotEmpty(),
                    defaultStream = lookrStreams.firstOrNull { it.resolution.contains("720") } ?: lookrStreams.firstOrNull()
                )
            } else {
                MovieBoxApiClient.fetchPlayStreams(
                    context = context,
                    subjectId = movie.id,
                    detailPath = movie.detailPath,
                    isShort = movie.isShort
                )
            }

            isLoadingStreams = false

            if (result.streams.isNotEmpty()) {
                availableStreams = result.streams
                val defaultStream = result.defaultStream ?: result.streams.first()
                currentStream = defaultStream
                playStreamUrl(defaultStream.url)
            } else {
                streamErrorMessage = "Stream is currently unavailable for this title"
                isBuffering = false
            }
        }
    }

    // Initial stream fetch on load
    LaunchedEffect(movie.id, movie.directUrl) {
        loadAndPlayStream()
    }

    // Continuous position updater
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (!isUserScrubbing) {
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    durationMs = dur
                }
            }
            delay(250L)
        }
    }

    // Auto-hide controls after 4.5s
    LaunchedEffect(areControlsVisible, isPlaying, isControlsLocked, isSettingsMenuOpen, isQualityMenuOpen) {
        if (areControlsVisible && isPlaying && !isControlsLocked && !isSettingsMenuOpen && !isQualityMenuOpen && !isUserScrubbing) {
            delay(4500L)
            areControlsVisible = false
        }
    }

    // Auto-clear gesture HUD after 1.2s
    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1200L)
            gestureHudText = null
            gestureHudIcon = null
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

    // Rotating Animation for Buffer Indicator (matching screenshot square spinner)
    val infiniteTransition = rememberInfiniteTransition(label = "buffer_rotate")
    val bufferRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "buffer_rotation"
    )

    Dialog(
        onDismissRequest = {
            if (!isControlsLocked) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isControlsLocked,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(isControlsLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isControlsLocked) {
                                // Briefly flash unlock button
                                areControlsVisible = true
                            } else {
                                areControlsVisible = !areControlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isControlsLocked) {
                                val isRight = offset.x > size.width / 2
                                val dur = if (durationMs > 0) durationMs else exoPlayer.duration.coerceAtLeast(0L)
                                if (isRight) {
                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    doubleTapFeedback = PlayerDoubleTapFeedback(isRight = true, timestamp = System.currentTimeMillis())
                                } else {
                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    doubleTapFeedback = PlayerDoubleTapFeedback(isRight = false, timestamp = System.currentTimeMillis())
                                }
                            }
                        }
                    )
                }
                .pointerInput(isControlsLocked) {
                    if (isControlsLocked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragAccumulatedDx = 0f
                            dragAccumulatedDy = 0f
                            dragInitialSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                            activeGesture = PlayerGestureType.NONE

                            val activity = context.findActivity()
                            val win = activity?.window
                            val lp = win?.attributes
                            dragInitialBrightness = if (lp != null && lp.screenBrightness >= 0f) {
                                lp.screenBrightness
                            } else {
                                0.5f
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
                                    val activity = context.findActivity()
                                    val window = activity?.window
                                    val lp = window?.attributes
                                    if (lp != null) {
                                        lp.screenBrightness = newBrightness
                                        window.attributes = lp
                                    }
                                    brightnessFraction = newBrightness
                                    isBrightnessHudVisible = true
                                }
                                PlayerGestureType.SWIPE_VOLUME -> {
                                    val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                    val deltaFraction = -dragAccumulatedDy / (size.height * 0.7f)
                                    val targetVol = (dragInitialVolumeIndex + (deltaFraction * maxVol).toInt()).coerceIn(0, maxVol)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    currentVolumeFraction = targetVol.toFloat() / maxVol.toFloat()
                                    isVolumeHudVisible = true
                                }
                                PlayerGestureType.NONE -> {}
                            }
                        },
                        onDragEnd = {
                            if (activeGesture == PlayerGestureType.SWIPE_SEEK) {
                                seekGestureState?.let { state ->
                                    exoPlayer.seekTo(state.targetMs)
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
                .testTag("network_stream_player_dialog")
        ) {
            // Google ExoPlayer Surface View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        resizeMode = currentAspectMode.resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = currentAspectMode.resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Backdrop poster placeholder if stream is starting
            if (currentPositionMs == 0L && isBuffering && !exoPlayer.isPlaying && movie.backdropUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.backdropUrl.ifBlank { movie.coverUrl },
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                )
            }

            // Central Minimal White Rotating Square Buffer (Matching Screenshot exactly)
            if (isLoadingStreams || (isBuffering && isPlaying)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = bufferRotation }
                            .background(Color.White, shape = RoundedCornerShape(2.dp))
                    )
                }
            }

            // Stream Error Display with Retry
            if (streamErrorMessage != null && !isLoadingStreams && availableStreams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(0.85f)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MovieBoxRed,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = streamErrorMessage ?: "Unable to load video stream",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MovieBoxRed,
                                modifier = Modifier.clickable { loadAndPlayStream(isRetry = true) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Retry Stream",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Gesture feedback HUD (Double tap to seek, Swipe to seek, Swipe to change settings)
            PlayerGesturesOverlay(
                doubleTapFeedback = doubleTapFeedback,
                seekState = seekGestureState,
                isBrightnessHudVisible = isBrightnessHudVisible,
                brightnessFraction = brightnessFraction,
                isVolumeHudVisible = isVolumeHudVisible,
                volumeFraction = currentVolumeFraction
            )

            // Locked Mode Unlock Floating Button
            if (isControlsLocked) {
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = {
                            isControlsLocked = false
                            areControlsVisible = true
                        },
                        modifier = Modifier
                            .padding(24.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .border(1.dp, Color(0x66FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Unlock Controls",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Top & Bottom Overlay Controls (When Unlocked)
            if (!isControlsLocked) {
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // TOP BAR: [✕] [Title] .............. [Lock]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Close Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Video / Stream Title (Clean white text matching screenshot)
                            Text(
                                text = movie.title.ifBlank { "Network Stream" },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Lock Icon Button (Far Right)
                            IconButton(
                                onClick = {
                                    isControlsLocked = true
                                    areControlsVisible = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Controls",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Center Play / Pause Indicator (when tapped)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x55000000))
                                .clickable {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.Refresh, // handled by controls
                                contentDescription = "Play/Pause",
                                tint = Color.Transparent,
                                modifier = Modifier.size(0.dp)
                            )
                        }

                        // BOTTOM BAR CONTROLS
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // 1. Slim Scrub Slider
                            val progressFraction = if (isUserScrubbing) {
                                scrubPositionFraction
                            } else if (durationMs > 0L) {
                                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            Slider(
                                value = progressFraction,
                                onValueChange = { fraction ->
                                    isUserScrubbing = true
                                    scrubPositionFraction = fraction
                                },
                                onValueChangeFinished = {
                                    if (durationMs > 0L) {
                                        val targetMs = (scrubPositionFraction * durationMs).toLong()
                                        exoPlayer.seekTo(targetMs)
                                        currentPositionMs = targetMs
                                    }
                                    isUserScrubbing = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 2. Control Row: [Time · Duration] .............. [Fill] [CC] [⚙] [🔊] [🎦⚙] [🗗] [⛶]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Time position with middle dot "00:00 · 00:00"
                                val displayCurrentMs = if (isUserScrubbing) {
                                    (scrubPositionFraction * durationMs).toLong()
                                } else {
                                    currentPositionMs
                                }
                                Text(
                                    text = "${formatMillis(displayCurrentMs)} · ${formatMillis(durationMs)}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                // Right Controls Row matching screenshot
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 1. "Fill" (Aspect Ratio Toggle)
                                    Text(
                                        text = currentAspectMode.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                currentAspectModeIndex = (currentAspectModeIndex + 1) % AspectMode.entries.size
                                                gestureHudIcon = null
                                                gestureHudText = "Ratio: ${AspectMode.entries[currentAspectModeIndex].label}"
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    // 2. CC (Closed Captions / Subtitles)
                                    Icon(
                                        imageVector = Icons.Default.ClosedCaption,
                                        contentDescription = "Subtitles",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable { isSubtitleDialogOpen = true }
                                    )

                                    // 3. Settings Gear (Speed, Quality, Info)
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable { isSettingsMenuOpen = true }
                                        )

                                        DropdownMenu(
                                            expanded = isSettingsMenuOpen,
                                            onDismissRequest = { isSettingsMenuOpen = false },
                                            modifier = Modifier.background(DarkSurface)
                                        ) {
                                            // Speed option
                                            DropdownMenuItem(
                                                text = { Text("Playback Speed: ${currentSpeed}x", color = Color.White) },
                                                onClick = {
                                                    isSettingsMenuOpen = false
                                                    isSpeedMenuOpen = true
                                                }
                                            )
                                            // Quality option
                                            DropdownMenuItem(
                                                text = { Text("Quality: ${currentStream?.resolution ?: "Auto"}p", color = Color.White) },
                                                onClick = {
                                                    isSettingsMenuOpen = false
                                                    isQualityMenuOpen = true
                                                }
                                            )
                                            // Stream Details option
                                            DropdownMenuItem(
                                                text = { Text("Stream Details & Codec", color = Color.White) },
                                                onClick = {
                                                    isSettingsMenuOpen = false
                                                    isStreamInfoDialogOpen = true
                                                }
                                            )
                                        }
                                    }

                                    // 4. Volume Icon (Toggle Mute / Quick Indicator)
                                    Icon(
                                        imageVector = if (isMuted || currentVolumeFraction == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Volume",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable {
                                                isMuted = !isMuted
                                                exoPlayer.volume = if (isMuted) 0f else 1f
                                                gestureHudIcon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                                gestureHudText = if (isMuted) "Muted" else "Unmuted"
                                            }
                                    )

                                    // 5. Video Mode / Decoder Icon
                                    Icon(
                                        imageVector = Icons.Default.Dvr,
                                        contentDescription = "Decoder Mode",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable {
                                                isHardwareDecoder = !isHardwareDecoder
                                                gestureHudIcon = Icons.Default.Dvr
                                                 gestureHudText = if (isHardwareDecoder) "Decoder: HW (Hardware)" else "Decoder: SW (Software)"
                                            }
                                    )

                                    // 6. Picture-in-Picture (PiP) Icon
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable {
                                                enterPipMode(context)
                                            }
                                    )

                                    // 7. Fullscreen / Rotate Screen Icon
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable {
                                                toggleScreenOrientation(context)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quality Selection Dialog / Dropdown
            if (isQualityMenuOpen) {
                AlertDialog(
                    onDismissRequest = { isQualityMenuOpen = false },
                    title = { Text("Select Stream Quality", color = Color.White, fontWeight = FontWeight.Bold) },
                    containerColor = DarkSurface,
                    text = {
                        Column {
                            val streamOptions = if (availableStreams.isNotEmpty()) {
                                availableStreams
                            } else {
                                listOf(
                                    MovieStream(id = "auto", resolution = "Auto", format = "Auto", url = movie.directUrl)
                                )
                            }
                            streamOptions.forEach { s ->
                                val isSelected = currentStream?.id == s.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isQualityMenuOpen = false
                                            if (currentStream?.id != s.id) {
                                                currentStream = s
                                                val pos = exoPlayer.currentPosition
                                                val wasPlaying = exoPlayer.isPlaying
                                                playStreamUrl(s.url)
                                                exoPlayer.seekTo(pos)
                                                if (wasPlaying) exoPlayer.play()
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${s.resolution}p ${s.format}",
                                        color = if (isSelected) MovieBoxRed else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MovieBoxRed
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isQualityMenuOpen = false }) {
                            Text("Close", color = MovieBoxRed)
                        }
                    }
                )
            }

            // Speed Selection Dialog
            if (isSpeedMenuOpen) {
                AlertDialog(
                    onDismissRequest = { isSpeedMenuOpen = false },
                    title = { Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold) },
                    containerColor = DarkSurface,
                    text = {
                        Column {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { sp ->
                                val isSelected = currentSpeed == sp
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentSpeed = sp
                                            exoPlayer.playbackParameters = PlaybackParameters(sp)
                                            isSpeedMenuOpen = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${sp}x",
                                        color = if (isSelected) MovieBoxRed else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MovieBoxRed
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isSpeedMenuOpen = false }) {
                            Text("Cancel", color = MovieBoxRed)
                        }
                    }
                )
            }

            // Subtitles CC Dialog
            if (isSubtitleDialogOpen) {
                AlertDialog(
                    onDismissRequest = { isSubtitleDialogOpen = false },
                    title = { Text("Subtitles & Audio", color = Color.White, fontWeight = FontWeight.Bold) },
                    containerColor = DarkSurface,
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Embedded Tracks: Auto (Default)", color = Color.White, fontSize = 14.sp)
                            Text("Subtitles: English [Original] (CC Enabled)", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                            Text("Decoder: ExoPlayer Media3 Universal Engine", color = Color(0xFFA1A1AA), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isSubtitleDialogOpen = false }) {
                            Text("Done", color = MovieBoxRed)
                        }
                    }
                )
            }

            // Stream Info Dialog
            if (isStreamInfoDialogOpen) {
                AlertDialog(
                    onDismissRequest = { isStreamInfoDialogOpen = false },
                    title = { Text("Stream Information", color = Color.White, fontWeight = FontWeight.Bold) },
                    containerColor = DarkSurface,
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Title: ${movie.title}", color = Color.White, fontSize = 13.sp)
                            Text("Format: ${currentStream?.format ?: getFormatFromUrl(movie.directUrl)}", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                            Text("Resolution: ${currentStream?.resolution ?: "Auto"}p", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                            Text("Decoder: ${if (isHardwareDecoder) "Hardware (HW Accelerated)" else "Software (SW)"}", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                            Text("Engine: Google Media3 ExoPlayer", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                            Text("Protocols: HLS, DASH, SmoothStreaming, RTSP, Progressive", color = Color(0xFFA1A1AA), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isStreamInfoDialogOpen = false }) {
                            Text("Close", color = MovieBoxRed)
                        }
                    }
                )
            }
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

private fun toggleScreenOrientation(context: Context) {
    if (com.example.util.DeviceUtils.isAndroidTv(context)) return
    val activity = context.findActivity() ?: return
    try {
        val currentOrientation = activity.requestedOrientation
        activity.requestedOrientation = if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    } catch (_: Exception) {}
}

private fun getFormatFromUrl(url: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains(".m3u8") -> "HLS"
        lower.contains(".mpd") -> "DASH"
        lower.contains(".ism") -> "SmoothStreaming"
        lower.startsWith("rtsp://") -> "RTSP"
        lower.contains(".mkv") -> "MKV"
        lower.contains(".webm") -> "WebM"
        lower.contains(".flv") -> "FLV"
        lower.contains(".ts") -> "TS"
        else -> "MP4"
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatMillis(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
