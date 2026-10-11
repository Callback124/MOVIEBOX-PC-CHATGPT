package com.example.data.music

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.api.JioSaavnApiClient
import com.example.data.api.JioSaavnDecoder
import com.example.data.model.RepeatMode
import com.example.data.model.SaavnSongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MusicPlayerManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val downloadManager = MusicDownloadManager.getInstance(context)

    private var exoPlayer: ExoPlayer? = null
    private var progressPollJob: Job? = null

    // State flows
    private val _currentSong = MutableStateFlow<SaavnSongItem?>(null)
    val currentSong: StateFlow<SaavnSongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SaavnSongItem>>(emptyList())
    val queue: StateFlow<List<SaavnSongItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _currentBitrate = MutableStateFlow("320")
    val currentBitrate: StateFlow<String> = _currentBitrate.asStateFlow()

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlayerOpen = MutableStateFlow(false)
    val isPlayerOpen: StateFlow<Boolean> = _isPlayerOpen.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongIds: StateFlow<Set<String>> = _favoriteSongIds.asStateFlow()

    fun toggleFavorite(songId: String) {
        val current = _favoriteSongIds.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _favoriteSongIds.value = current
    }

    fun isFavorite(songId: String): Boolean = _favoriteSongIds.value.contains(songId)

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isBuffering.value = true
                            }
                            Player.STATE_READY -> {
                                _isBuffering.value = false
                                if (System.currentTimeMillis() - lastSeekTimestampMs > 1500L) {
                                    isUserSeeking = false
                                }
                                if (duration > 0L) {
                                    _durationMs.value = duration
                                }
                            }
                            Player.STATE_ENDED -> {
                                _isBuffering.value = false
                                handleSongEnded()
                            }
                            Player.STATE_IDLE -> {
                                _isBuffering.value = false
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressPolling()
                        } else {
                            stopProgressPolling()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                        _isBuffering.value = false
                        // If 320k fails, attempt auto-fallback to 160k
                        if (_currentBitrate.value == "320") {
                            _currentBitrate.value = "160"
                            _currentSong.value?.let { playSongInternal(it, seekToMs = _playbackPositionMs.value) }
                        }
                    }
                })
            }
    }

    private fun startProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playbackPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    fun openPlayer() {
        _isPlayerOpen.value = true
    }

    fun closePlayer() {
        _isPlayerOpen.value = false
    }

    fun playSong(song: SaavnSongItem, queue: List<SaavnSongItem> = listOf(song), autoOpen: Boolean = true) {
        _queue.value = queue
        val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _queueIndex.value = idx
        _currentSong.value = song
        _currentLyrics.value = null
        _playbackPositionMs.value = 0L
        _durationMs.value = (song.durationSec * 1000L).coerceAtLeast(0L)
        lastSeekTimestampMs = 0L
        isUserSeeking = false
        if (autoOpen) {
            _isPlayerOpen.value = true
        }
        playSongInternal(song, 0L)
        fetchLyrics(force = true)
    }

    fun addToQueue(song: SaavnSongItem) {
        val current = _queue.value.toMutableList()
        current.add(song)
        _queue.value = current
    }

    private fun playSongInternal(song: SaavnSongItem, seekToMs: Long = 0L) {
        scope.launch {
            _isBuffering.value = true
            val player = exoPlayer ?: return@launch

            // Check if downloaded locally
            val downloadedFile = downloadManager.getDownloadedFile(song.id)
            if (downloadedFile != null && downloadedFile.exists()) {
                Log.d(TAG, "Playing downloaded offline song: ${song.title} from ${downloadedFile.absolutePath}")
                try {
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(downloadedFile))
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    if (seekToMs > 0L) player.seekTo(seekToMs)
                    player.play()
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Failed playing offline file, falling back to stream: ${e.message}")
                }
            }

            // Stream from URL
            val streamUrl = withContext(Dispatchers.IO) {
                if (song.directStreamUrl.isNotBlank()) {
                    song.directStreamUrl
                } else {
                    var encUrl = song.encryptedMediaUrl
                    if (encUrl.isBlank() && song.id.isNotBlank()) {
                        try {
                            val details = JioSaavnApiClient.getSongDetails(song.id)
                            if (details != null && details.encryptedMediaUrl.isNotBlank()) {
                                encUrl = details.encryptedMediaUrl
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed fetching song details fallback: ${e.message}")
                        }
                    }
                    JioSaavnDecoder.decryptMediaUrl(encUrl, _currentBitrate.value)
                }
            }

            if (streamUrl.isNullOrBlank()) {
                Log.e(TAG, "Cannot resolve stream URL for song: ${song.title}")
                _isBuffering.value = false
                return@launch
            }

            Log.d(TAG, "Streaming song: ${song.title} at ${_currentBitrate.value}k from $streamUrl")
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)

            val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))

            player.setMediaSource(mediaSource)
            player.prepare()
            if (seekToMs > 0L) player.seekTo(seekToMs)
            player.play()
        }
    }

    @Volatile
    private var lastSeekTimestampMs: Long = 0L

    @Volatile
    private var isUserSeeking: Boolean = false

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                _currentSong.value?.let { playSongInternal(it, _playbackPositionMs.value) }
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        lastSeekTimestampMs = System.currentTimeMillis()
        isUserSeeking = true
        val playerDur = player.duration.takeIf { it > 0L }
        val stateDur = _durationMs.value.takeIf { it > 0L }
        val songDur = ((_currentSong.value?.durationSec ?: 0) * 1000L).takeIf { it > 0L }
        val effectiveDur = playerDur ?: stateDur ?: songDur ?: 0L
        val maxSafePos = if (effectiveDur > 6000L) effectiveDur - 2500L else effectiveDur
        val safePos = if (effectiveDur > 0L) {
            positionMs.coerceIn(0L, maxSafePos)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        _playbackPositionMs.value = safePos
        player.seekTo(safePos)
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (q.size == 1) {
            seekTo(0L)
            exoPlayer?.play()
            return
        }
        var nextIdx = _queueIndex.value + 1
        if (nextIdx >= q.size) {
            nextIdx = 0
        }
        _queueIndex.value = nextIdx
        val nextSong = q[nextIdx]
        _currentSong.value = nextSong
        _currentLyrics.value = null
        playSongInternal(nextSong, 0L)
        fetchLyrics(force = true)
    }

    fun playPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        // If played more than 3 seconds or queue has only 1 song, restart current song
        if (_playbackPositionMs.value > 3000L || q.size == 1) {
            seekTo(0L)
            exoPlayer?.play()
            return
        }
        var prevIdx = _queueIndex.value - 1
        if (prevIdx < 0 || prevIdx >= q.size) {
            prevIdx = (q.size - 1).coerceAtLeast(0)
        }
        _queueIndex.value = prevIdx
        val prevSong = q[prevIdx]
        _currentSong.value = prevSong
        _currentLyrics.value = null
        playSongInternal(prevSong, 0L)
        fetchLyrics(force = true)
    }

    fun setQuality(bitrate: String) {
        if (_currentBitrate.value == bitrate) return
        _currentBitrate.value = bitrate
        val song = _currentSong.value ?: return
        val currentPos = _playbackPositionMs.value
        playSongInternal(song, currentPos)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private fun handleSongEnded() {
        val player = exoPlayer ?: return
        val now = System.currentTimeMillis()

        // 1. If seek happened recently (within 10 seconds) or user is currently seeking,
        // ignore premature STATE_ENDED caused by network seek/buffering and resume playback of current song!
        if (isUserSeeking || (now - lastSeekTimestampMs < 10000L)) {
            Log.w(TAG, "STATE_ENDED ignored because seek occurred recently (${now - lastSeekTimestampMs}ms ago). Resuming playback.")
            isUserSeeking = false
            val targetPos = _playbackPositionMs.value
            player.seekTo(targetPos)
            player.prepare()
            player.play()
            return
        }

        val playerDur = player.duration.takeIf { it > 0L }
        val stateDur = _durationMs.value.takeIf { it > 0L }
        val songDur = ((_currentSong.value?.durationSec ?: 0) * 1000L).takeIf { it > 0L }
        val effectiveDur = playerDur ?: stateDur ?: songDur ?: 0L
        val cur = player.currentPosition

        // 2. CRITICAL: Only advance to next song if playback genuinely reached the very end of track!
        // If duration is unknown, or if current position has not reached the actual end (within 2s), DO NOT skip!
        if (effectiveDur <= 5000L || cur < (effectiveDur - 2000L)) {
            Log.w(TAG, "STATE_ENDED fired prematurely at $cur ms / $effectiveDur ms. Resuming playback rather than skipping.")
            if (effectiveDur > 0L && cur < effectiveDur) {
                player.seekTo(cur)
                player.prepare()
                player.play()
            }
            return
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (_queueIndex.value < _queue.value.size - 1) {
                    playNext()
                } else {
                    _isPlaying.value = false
                    seekTo(0L)
                }
            }
        }
    }

    fun fetchLyrics(force: Boolean = false) {
        val song = _currentSong.value ?: return
        if (!force && (_currentLyrics.value != null || _isLoadingLyrics.value)) return
        scope.launch {
            _isLoadingLyrics.value = true
            val lyrics = withContext(Dispatchers.IO) {
                JioSaavnApiClient.getLyrics(
                    songId = song.id,
                    lyricsId = song.lyricsId,
                    title = song.title,
                    artist = song.artist.ifBlank { song.subtitle }
                )
            }
            _currentLyrics.value = if (!lyrics.isNullOrBlank()) lyrics else "No lyrics available for this song."
            _isLoadingLyrics.value = false
        }
    }

    fun stop() {
        exoPlayer?.stop()
        stopProgressPolling()
        _isPlaying.value = false
        _currentSong.value = null
        _isPlayerOpen.value = false
    }

    companion object {
        private const val TAG = "MusicPlayerManager"

        @Volatile
        private var INSTANCE: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlayerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
