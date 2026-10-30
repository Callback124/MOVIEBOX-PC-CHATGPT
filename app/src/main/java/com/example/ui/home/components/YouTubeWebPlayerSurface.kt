package com.example.ui.home.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reliable YouTube IFrame Player API bridge inside a dark, chromeless WebView.
 * Synchronizes play/pause, mute/unmute, seek, current position, total duration,
 * and ended callbacks with the custom Compose player controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebPlayerSurface(
    videoId: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    seekRequestMs: Long = -1L,
    onReady: (durationMs: Long) -> Unit = {},
    onProgress: (currentPositionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBufferingChanged: (Boolean) -> Unit = {},
    onStatePlayingChanged: (Boolean) -> Unit = {},
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnBufferingChanged by rememberUpdatedState(onBufferingChanged)
    val currentOnStatePlayingChanged by rememberUpdatedState(onStatePlayingChanged)
    val currentOnEnded by rememberUpdatedState(onEnded)

    val cleanVideoId = remember(videoId) {
        videoId.trim()
            .substringAfter("v=", videoId.trim())
            .substringBefore("&")
            .substringAfter("/shorts/", videoId.trim())
            .substringBefore("?")
            .trim()
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPlayerReady by remember(cleanVideoId) { mutableStateOf(false) }

    val htmlContent = remember(cleanVideoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; background: #000000; }
                html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; }
                #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; border: 0; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player = null;
                var progressTimer = null;
                var userMuted = false;

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$cleanVideoId',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'rel': 0,
                            'showinfo': 0,
                            'modestbranding': 1,
                            'iv_load_policy': 3,
                            'fs': 0,
                            'playsinline': 1,
                            'disablekb': 1,
                            'origin': 'https://www.youtube.com'
                        },
                        events: {
                            'onReady': function(event) {
                                try {
                                    event.target.playVideo();
                                } catch(e) {}
                                var dur = Math.round((event.target.getDuration() || 0) * 1000);
                                if (window.AndroidYT) {
                                    window.AndroidYT.onPlayerReady(dur);
                                }
                                startProgressLoop();

                                // Unmute/play fallback for WebViews with strict autoplay policies
                                setTimeout(function() {
                                    if (player && player.getPlayerState && player.getPlayerState() !== 1) {
                                        try {
                                            player.mute();
                                            player.playVideo();
                                            setTimeout(function() {
                                                if (!userMuted && player && player.unMute) player.unMute();
                                            }, 400);
                                        } catch(e) {}
                                    }
                                }, 500);
                            },
                            'onStateChange': function(event) {
                                if (!window.AndroidYT) return;
                                var st = event.data;
                                if (st === YT.PlayerState.PLAYING) {
                                    var dur = Math.round((player.getDuration() || 0) * 1000);
                                    window.AndroidYT.onBuffering(false);
                                    window.AndroidYT.onPlayingChanged(true, dur);
                                } else if (st === YT.PlayerState.PAUSED) {
                                    window.AndroidYT.onBuffering(false);
                                    window.AndroidYT.onPlayingChanged(false, Math.round((player.getDuration() || 0) * 1000));
                                } else if (st === YT.PlayerState.BUFFERING) {
                                    window.AndroidYT.onBuffering(true);
                                } else if (st === YT.PlayerState.ENDED) {
                                    window.AndroidYT.onBuffering(false);
                                    window.AndroidYT.onEnded();
                                }
                            },
                            'onError': function(event) {
                                if (window.AndroidYT) {
                                    window.AndroidYT.onBuffering(false);
                                }
                                // Fallback directly to embedded iframe if API threw error
                                var p = document.getElementById('player');
                                if (p && p.tagName !== 'IFRAME') {
                                    p.innerHTML = '<iframe width="100%" height="100%" src="https://www.youtube-nocookie.com/embed/$cleanVideoId?autoplay=1&playsinline=1&controls=0&rel=0" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>';
                                }
                            }
                        }
                    });
                }

                function startProgressLoop() {
                    if (progressTimer) clearInterval(progressTimer);
                    progressTimer = setInterval(function() {
                        if (player && player.getCurrentTime && window.AndroidYT) {
                            var cur = Math.round((player.getCurrentTime() || 0) * 1000);
                            var dur = Math.round((player.getDuration() || 0) * 1000);
                            window.AndroidYT.onProgress(cur, dur);
                        }
                    }, 400);
                }

                function ytPlay() {
                    if (player && player.playVideo) player.playVideo();
                }
                function ytPause() {
                    if (player && player.pauseVideo) player.pauseVideo();
                }
                function ytSeekTo(ms) {
                    if (player && player.seekTo) player.seekTo(ms / 1000.0, true);
                }
                function ytSetMuted(muted) {
                    userMuted = muted;
                    if (player && player.mute && player.unMute) {
                        if (muted) player.mute(); else player.unMute();
                    }
                }
                function ytLoadVideo(vid) {
                    if (player && player.loadVideoById) {
                        player.loadVideoById(vid);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(isPlaying, isPlayerReady) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isPlayerReady) {
            if (isPlaying) {
                wv.evaluateJavascript("ytPlay();", null)
            } else {
                wv.evaluateJavascript("ytPause();", null)
            }
        }
    }

    LaunchedEffect(isMuted, isPlayerReady) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isPlayerReady) {
            wv.evaluateJavascript("ytSetMuted($isMuted);", null)
        }
    }

    LaunchedEffect(seekRequestMs) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isPlayerReady && seekRequestMs >= 0L) {
            wv.evaluateJavascript("ytSeekTo($seekRequestMs);", null)
        }
    }

    DisposableEffect(cleanVideoId) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.evaluateJavascript("ytPause();", null)
                } catch (_: Exception) {}
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(AndroidColor.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onPlayerReady(durationMs: Long) {
                            post {
                                isPlayerReady = true
                                currentOnBufferingChanged(false)
                                currentOnReady(durationMs)
                            }
                        }

                        @JavascriptInterface
                        fun onProgress(currentMs: Long, durationMs: Long) {
                            post {
                                currentOnProgress(currentMs, durationMs)
                            }
                        }

                        @JavascriptInterface
                        fun onBuffering(buffering: Boolean) {
                            post {
                                currentOnBufferingChanged(buffering)
                            }
                        }

                        @JavascriptInterface
                        fun onPlayingChanged(playing: Boolean, durationMs: Long) {
                            post {
                                currentOnStatePlayingChanged(playing)
                                if (durationMs > 0L) {
                                    currentOnReady(durationMs)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onEnded() {
                            post {
                                currentOnEnded()
                            }
                        }
                    },
                    "AndroidYT"
                )

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: ""
                        // Prevent external intent redirection so player stays embedded inside our app
                        if (url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("vnd.youtube:")) {
                            return true
                        }
                        // Allow WebView to load YouTube scripts, embeds, and streams
                        return false
                    }
                }

                webViewRef = this
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { wv ->
            webViewRef = wv
            val currentTag = wv.tag as? String
            if (currentTag != cleanVideoId) {
                wv.tag = cleanVideoId
                isPlayerReady = false
                wv.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = modifier
    )
}
