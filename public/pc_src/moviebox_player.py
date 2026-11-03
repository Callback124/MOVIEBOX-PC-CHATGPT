"""
MovieBox PC - Google Media3 / ExoPlayer Engine & Local Streaming Proxy
Provides:
1. Local HTTP 206 streaming proxy that injects exact Mobile App headers:
   - Referer: https://movieboxph.org/
   - Origin: https://movieboxph.org
   - User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
   - Byte-Range forwarding & 206 Partial Content for instant seeking in MP4, HLS (.m3u8), and DASH (.mpd).
   - SSL unverified context so Python on Windows NEVER fails with SSL_CERTIFICATE_VERIFY_FAILED.
   - IncompleteRead handling with Connection: close for rock-solid stability.
2. Inbuilt Google Media Player Web UI (matching Android Media3 DetailVideoPlayer):
   - Supports Progressive MP4, HLS.js, Shaka DASH, and HTML5 native video.
   - Dual-stream engine: tries proxied stream first; auto-falls back to direct stream on error.
   - Resolution switcher (1080p, 720p, 480p, 360p) with live hot-swapping.
   - Complete playback controls: Play/Pause, 10s Rewind/Forward, Volume & Mute, Speed (0.5x - 2.0x),
     Aspect Ratio (Fit/Fill/Stretch), and Fullscreen.
"""

import os
import sys
import time
import socket
import ssl
import json
import urllib.parse
import urllib.request
import threading
import http.server
import http.client
import socketserver
import subprocess
import webbrowser

STREAM_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
)
STREAM_REFERER = "https://movieboxph.org/"
STREAM_ORIGIN = "https://movieboxph.org"

# Create SSL context that does not fail on unverified cert chains
SSL_CTX = ssl._create_unverified_context()

PLAYER_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>__TITLE__ - MovieBox Google Media Player</title>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/shaka-player/4.7.11/shaka-player.compiled.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body, html {
      width: 100%; height: 100%;
      background: #000000;
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      overflow: hidden;
      user-select: none;
    }
    #player-container {
      position: relative;
      width: 100%; height: 100%;
      background: #000000;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    video {
      width: 100%; height: 100%;
      object-fit: contain;
      background: #000000;
      cursor: pointer;
    }
    /* Top Header Overlay */
    .top-overlay {
      position: absolute;
      top: 0; left: 0; right: 0;
      height: 70px;
      padding: 16px 24px;
      background: linear-gradient(180deg, rgba(0,0,0,0.92) 0%, rgba(0,0,0,0) 100%);
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: opacity 0.3s ease;
      z-index: 10;
    }
    .brand-title {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .badge {
      background: #6366F1;
      color: #FFF;
      font-size: 11px;
      font-weight: 800;
      padding: 4px 10px;
      border-radius: 4px;
      letter-spacing: 0.5px;
    }
    .video-title {
      font-size: 16px;
      font-weight: 600;
      color: #FFF;
      max-width: 650px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .status-tag {
      font-size: 12px;
      color: #10B981;
      background: rgba(16, 185, 129, 0.12);
      padding: 4px 12px;
      border-radius: 12px;
      border: 1px solid rgba(16, 185, 129, 0.3);
    }
    /* Center Play/Buffering Overlay */
    .center-overlay {
      position: absolute;
      pointer-events: none;
      z-index: 5;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
    }
    .big-icon {
      width: 80px; height: 80px;
      border-radius: 50%;
      background: rgba(99, 102, 241, 0.92);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 34px;
      box-shadow: 0 8px 32px rgba(99, 102, 241, 0.6);
      opacity: 0;
      transform: scale(0.8);
      transition: opacity 0.25s, transform 0.25s;
      cursor: pointer;
      pointer-events: auto;
    }
    .big-icon.visible {
      opacity: 1;
      transform: scale(1);
    }
    .spinner {
      width: 52px; height: 52px;
      border: 4px solid rgba(255,255,255,0.2);
      border-top-color: #6366F1;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      display: none;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .status-msg {
      background: rgba(0,0,0,0.75);
      padding: 8px 16px;
      border-radius: 8px;
      font-size: 13px;
      color: #E2E8F0;
      border: 1px solid rgba(255,255,255,0.1);
      display: none;
    }
    /* Bottom Controls */
    .bottom-overlay {
      position: absolute;
      bottom: 0; left: 0; right: 0;
      background: linear-gradient(0deg, rgba(0,0,0,0.92) 0%, rgba(0,0,0,0) 100%);
      padding: 24px 24px 18px 24px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      transition: opacity 0.3s ease;
      z-index: 10;
    }
    .top-overlay.hidden, .bottom-overlay.hidden {
      opacity: 0;
      pointer-events: none;
    }
    .progress-bar-container {
      position: relative;
      width: 100%;
      height: 18px;
      display: flex;
      align-items: center;
      cursor: pointer;
    }
    .progress-bg {
      position: relative;
      width: 100%;
      height: 5px;
      background: rgba(255, 255, 255, 0.25);
      border-radius: 3px;
      overflow: hidden;
      transition: height 0.15s ease;
    }
    .progress-bar-container:hover .progress-bg {
      height: 8px;
    }
    .progress-buffer {
      position: absolute;
      top: 0; left: 0; bottom: 0;
      width: 0%;
      background: rgba(255, 255, 255, 0.45);
      border-radius: 3px;
    }
    .progress-played {
      position: absolute;
      top: 0; left: 0; bottom: 0;
      width: 0%;
      background: #6366F1;
      border-radius: 3px;
    }
    .progress-thumb {
      position: absolute;
      top: 50%;
      left: 0%;
      width: 14px; height: 14px;
      border-radius: 50%;
      background: #FFFFFF;
      box-shadow: 0 0 6px rgba(0,0,0,0.6);
      transform: translate(-50%, -50%) scale(0);
      transition: transform 0.15s ease;
      pointer-events: none;
    }
    .progress-bar-container:hover .progress-thumb {
      transform: translate(-50%, -50%) scale(1);
    }
    .controls-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .left-controls, .right-controls {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .ctrl-btn {
      background: transparent;
      border: none;
      color: #FFFFFF;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 6px;
      border-radius: 6px;
      transition: background 0.2s, transform 0.1s;
      font-size: 18px;
    }
    .ctrl-btn:hover {
      background: rgba(255, 255, 255, 0.15);
      transform: scale(1.05);
    }
    .ctrl-btn:active {
      transform: scale(0.95);
    }
    .time-display {
      font-size: 13px;
      font-weight: 500;
      color: #E2E8F0;
      letter-spacing: 0.3px;
    }
    .volume-group {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .volume-slider {
      -webkit-appearance: none;
      width: 80px;
      height: 4px;
      border-radius: 2px;
      background: rgba(255, 255, 255, 0.3);
      outline: none;
      cursor: pointer;
    }
    .volume-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      width: 12px; height: 12px;
      border-radius: 50%;
      background: #FFFFFF;
      cursor: pointer;
    }
    select.quality-select, select.speed-select {
      background: rgba(255, 255, 255, 0.12);
      color: #FFF;
      border: 1px solid rgba(255, 255, 255, 0.25);
      padding: 4px 8px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      outline: none;
      cursor: pointer;
    }
    select.quality-select option, select.speed-select option {
      background: #181E2C;
      color: #FFF;
    }
  </style>
</head>
<body>
  <div id="player-container">
    <video id="video-element" playsinline></video>

    <!-- Top Overlay -->
    <div class="top-overlay" id="top-overlay">
      <div class="brand-title">
        <span class="badge">MEDIA3 EXOPLAYER</span>
        <span class="video-title">__TITLE__</span>
      </div>
      <div class="header-right">
        <span class="status-tag" id="status-tag">● Proxy Stream Active</span>
      </div>
    </div>

    <!-- Center Play / Spinner -->
    <div class="center-overlay">
      <div class="big-icon" id="center-play">▶</div>
      <div class="spinner" id="center-spinner"></div>
      <div class="status-msg" id="status-msg"></div>
    </div>

    <!-- Bottom Controls -->
    <div class="bottom-overlay" id="bottom-overlay">
      <!-- Scrubber -->
      <div class="progress-bar-container" id="progress-container">
        <div class="progress-bg">
          <div class="progress-buffer" id="progress-buffer"></div>
          <div class="progress-played" id="progress-played"></div>
        </div>
        <div class="progress-thumb" id="progress-thumb"></div>
      </div>

      <!-- Controls Row -->
      <div class="controls-row">
        <div class="left-controls">
          <button class="ctrl-btn" id="btn-play-pause" title="Play / Pause (Space)">▶</button>
          <button class="ctrl-btn" id="btn-rewind" title="Rewind 10s (Left Arrow)">⏪ 10s</button>
          <button class="ctrl-btn" id="btn-forward" title="Forward 10s (Right Arrow)">10s ⏩</button>
          <div class="volume-group">
            <button class="ctrl-btn" id="btn-mute" title="Mute (M)">🔊</button>
            <input type="range" class="volume-slider" id="volume-slider" min="0" max="1" step="0.05" value="1">
          </div>
          <span class="time-display" id="time-display">00:00 / 00:00</span>
        </div>

        <div class="right-controls">
          <select class="speed-select" id="speed-select" title="Playback Speed">
            <option value="0.75">0.75x</option>
            <option value="1" selected>1.0x</option>
            <option value="1.25">1.25x</option>
            <option value="1.5">1.5x</option>
            <option value="2">2.0x</option>
          </select>
          <select class="quality-select" id="quality-select" title="Stream Quality">
            <option value="auto">Auto Quality</option>
          </select>
          <button class="ctrl-btn" id="btn-fit" title="Toggle Fit / Fill">🔲</button>
          <button class="ctrl-btn" id="btn-fullscreen" title="Fullscreen (F)">⛶</button>
        </div>
      </div>
    </div>
  </div>

  <script>
    const proxiedStreamSrc = "__STREAM_SRC__";
    const directStreamSrc = "__DIRECT_STREAM__";
    const streamsList = __STREAM_LIST_JSON__;

    const video = document.getElementById('video-element');
    const container = document.getElementById('player-container');
    const topOverlay = document.getElementById('top-overlay');
    const bottomOverlay = document.getElementById('bottom-overlay');
    const btnPlayPause = document.getElementById('btn-play-pause');
    const centerPlay = document.getElementById('center-play');
    const centerSpinner = document.getElementById('center-spinner');
    const statusMsg = document.getElementById('status-msg');
    const statusTag = document.getElementById('status-tag');
    const timeDisplay = document.getElementById('time-display');
    const progressContainer = document.getElementById('progress-container');
    const progressPlayed = document.getElementById('progress-played');
    const progressBuffer = document.getElementById('progress-buffer');
    const progressThumb = document.getElementById('progress-thumb');
    const volumeSlider = document.getElementById('volume-slider');
    const btnMute = document.getElementById('btn-mute');
    const btnRewind = document.getElementById('btn-rewind');
    const btnForward = document.getElementById('btn-forward');
    const btnFullscreen = document.getElementById('btn-fullscreen');
    const btnFit = document.getElementById('btn-fit');
    const speedSelect = document.getElementById('speed-select');
    const qualitySelect = document.getElementById('quality-select');

    let isSeeking = false;
    let overlayTimeout = null;
    let currentStreamUrl = proxiedStreamSrc;
    let fallbackAttempted = false;

    // Populate Quality Options
    if (streamsList && streamsList.length > 0) {
      qualitySelect.innerHTML = '';
      streamsList.forEach((s, idx) => {
        const opt = document.createElement('option');
        const res = s.resolutions || s.resolution || '720p';
        opt.value = s.url;
        opt.textContent = `${res} (${s.format || 'MP4'})`;
        if (idx === 0) opt.selected = true;
        qualitySelect.appendChild(opt);
      });
    }

    qualitySelect.addEventListener('change', () => {
      const selectedUrl = qualitySelect.value;
      if (selectedUrl) {
        showStatus(`Switching Quality...`);
        const currTime = video.currentTime || 0;
        const wasPlaying = !video.paused;
        loadStream(selectedUrl, currTime, wasPlaying);
      }
    });

    // Format Seconds to MM:SS or HH:MM:SS
    function formatTime(sec) {
      if (!sec || isNaN(sec)) return "00:00";
      const s = Math.floor(sec);
      const h = Math.floor(s / 3600);
      const m = Math.floor((s % 3600) / 60);
      const remS = s % 60;
      if (h > 0) {
        return `${h}:${m < 10 ? '0' : ''}${m}:${remS < 10 ? '0' : ''}${remS}`;
      }
      return `${m < 10 ? '0' : ''}${m}:${remS < 10 ? '0' : ''}${remS}`;
    }

    function showStatus(text, duration = 3000) {
      statusMsg.textContent = text;
      statusMsg.style.display = 'block';
      setTimeout(() => { statusMsg.style.display = 'none'; }, duration);
    }

    // Auto-hide controls
    function resetOverlayTimer() {
      topOverlay.classList.remove('hidden');
      bottomOverlay.classList.remove('hidden');
      clearTimeout(overlayTimeout);
      if (!video.paused) {
        overlayTimeout = setTimeout(() => {
          topOverlay.classList.add('hidden');
          bottomOverlay.classList.add('hidden');
        }, 3200);
      }
    }
    container.addEventListener('mousemove', resetOverlayTimer);
    container.addEventListener('click', resetOverlayTimer);

    // Play / Pause Toggle
    function togglePlay() {
      if (video.paused || video.ended) {
        video.play().then(() => {
          btnPlayPause.textContent = '⏸';
          centerPlay.classList.remove('visible');
          resetOverlayTimer();
        }).catch(err => {
          console.warn("Autoplay prevented:", err);
          centerPlay.classList.add('visible');
          btnPlayPause.textContent = '▶';
        });
      } else {
        video.pause();
        btnPlayPause.textContent = '▶';
        centerPlay.classList.add('visible');
        clearTimeout(overlayTimeout);
        topOverlay.classList.remove('hidden');
        bottomOverlay.classList.remove('hidden');
      }
    }

    btnPlayPause.addEventListener('click', togglePlay);
    centerPlay.addEventListener('click', togglePlay);
    video.addEventListener('click', togglePlay);

    // Rewind / Forward
    btnRewind.addEventListener('click', () => {
      video.currentTime = Math.max(0, video.currentTime - 10);
      showStatus("⏪ 10 Seconds");
    });
    btnForward.addEventListener('click', () => {
      video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
      showStatus("⏩ 10 Seconds");
    });

    // Speed Control
    speedSelect.addEventListener('change', () => {
      video.playbackRate = parseFloat(speedSelect.value);
      showStatus(`Speed: ${speedSelect.value}x`);
    });

    // Volume & Mute
    volumeSlider.addEventListener('input', () => {
      video.volume = parseFloat(volumeSlider.value);
      video.muted = (video.volume === 0);
      btnMute.textContent = video.muted ? '🔇' : (video.volume > 0.5 ? '🔊' : '🔉');
    });
    btnMute.addEventListener('click', () => {
      video.muted = !video.muted;
      btnMute.textContent = video.muted ? '🔇' : '🔊';
      if (!video.muted && video.volume === 0) {
        video.volume = 0.5;
        volumeSlider.value = 0.5;
      }
    });

    // Fit / Fill Toggle
    let isFilled = false;
    btnFit.addEventListener('click', () => {
      isFilled = !isFilled;
      video.style.objectFit = isFilled ? 'cover' : 'contain';
      showStatus(isFilled ? "Aspect: Fill / Crop" : "Aspect: Fit / Letterbox");
    });

    // Fullscreen Toggle
    function toggleFullscreen() {
      if (!document.fullscreenElement) {
        container.requestFullscreen().catch(err => console.warn(err));
      } else {
        document.exitFullscreen().catch(err => console.warn(err));
      }
    }
    btnFullscreen.addEventListener('click', toggleFullscreen);
    video.addEventListener('dblclick', toggleFullscreen);

    // Scrubber / Progress Seeking
    video.addEventListener('timeupdate', () => {
      if (isSeeking) return;
      const cur = video.currentTime || 0;
      const dur = video.duration || 0;
      timeDisplay.textContent = `${formatTime(cur)} / ${formatTime(dur)}`;
      if (dur > 0) {
        const pct = (cur / dur) * 100;
        progressPlayed.style.width = `${pct}%`;
        progressThumb.style.left = `${pct}%`;
      }
    });

    video.addEventListener('progress', () => {
      const dur = video.duration || 0;
      if (dur > 0 && video.buffered.length > 0) {
        const bufEnd = video.buffered.end(video.buffered.length - 1);
        progressBuffer.style.width = `${(bufEnd / dur) * 100}%`;
      }
    });

    function seekFromEvent(e) {
      const rect = progressContainer.getBoundingClientRect();
      const pos = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      if (video.duration) {
        video.currentTime = pos * video.duration;
      }
    }

    progressContainer.addEventListener('mousedown', (e) => {
      isSeeking = true;
      seekFromEvent(e);
      const onMove = (ev) => seekFromEvent(ev);
      const onUp = () => {
        isSeeking = false;
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    });

    // Buffering & Play States
    video.addEventListener('waiting', () => {
      centerSpinner.style.display = 'block';
    });
    video.addEventListener('playing', () => {
      centerSpinner.style.display = 'none';
      centerPlay.classList.remove('visible');
      btnPlayPause.textContent = '⏸';
      resetOverlayTimer();
    });
    video.addEventListener('pause', () => {
      centerSpinner.style.display = 'none';
      centerPlay.classList.add('visible');
      btnPlayPause.textContent = '▶';
    });
    video.addEventListener('canplay', () => {
      centerSpinner.style.display = 'none';
    });

    // Error Handling: Auto Fallback to Direct Stream if Proxy stream hits error
    video.addEventListener('error', (e) => {
      console.warn("Playback error on stream source:", video.error);
      if (!fallbackAttempted && directStreamSrc && directStreamSrc !== currentStreamUrl) {
        fallbackAttempted = true;
        showStatus("Switching to Direct CDN Stream Fallback...");
        statusTag.textContent = '● Direct CDN Active';
        statusTag.style.color = '#F59E0B';
        loadStream(directStreamSrc, video.currentTime || 0, true);
      } else {
        showStatus("Stream buffering / retrying...");
      }
    });

    // Keyboard Shortcuts
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
      if (e.code === 'Space' || e.code === 'KeyK') {
        e.preventDefault();
        togglePlay();
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault();
        video.currentTime = Math.max(0, video.currentTime - 10);
      } else if (e.code === 'ArrowRight') {
        e.preventDefault();
        video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
      } else if (e.code === 'ArrowUp') {
        e.preventDefault();
        video.volume = Math.min(1, video.volume + 0.1);
        volumeSlider.value = video.volume;
      } else if (e.code === 'ArrowDown') {
        e.preventDefault();
        video.volume = Math.max(0, video.volume - 0.1);
        volumeSlider.value = video.volume;
      } else if (e.code === 'KeyF') {
        e.preventDefault();
        btnFullscreen.click();
      } else if (e.code === 'KeyM') {
        e.preventDefault();
        btnMute.click();
      }
    });

    // Core Stream Loader supporting DASH (.mpd), HLS (.m3u8), and Progressive MP4
    function loadStream(url, startTime = 0, autoPlay = true) {
      centerSpinner.style.display = 'block';
      const lower = url.toLowerCase();
      const isM3U8 = lower.includes('.m3u8');
      const isMPD = lower.includes('.mpd');

      if (isMPD && window.shaka && shaka.Player.isBrowserSupported()) {
        const player = new shaka.Player(video);
        player.load(url).then(() => {
          if (startTime > 0) video.currentTime = startTime;
          if (autoPlay) togglePlay();
        }).catch((err) => {
          console.warn("Shaka load error, falling back:", err);
          video.src = url;
          if (startTime > 0) video.currentTime = startTime;
          if (autoPlay) togglePlay();
        });
      } else if (isM3U8 && window.Hls && Hls.isSupported()) {
        const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          if (startTime > 0) video.currentTime = startTime;
          if (autoPlay) togglePlay();
        });
      } else {
        video.src = url;
        if (startTime > 0) video.currentTime = startTime;
        if (autoPlay) {
          video.play().then(() => {
            btnPlayPause.textContent = '⏸';
            centerPlay.classList.remove('visible');
          }).catch(() => {
            centerPlay.classList.add('visible');
            btnPlayPause.textContent = '▶';
          });
        }
      }
    }

    window.addEventListener('DOMContentLoaded', () => {
      loadStream(proxiedStreamSrc, 0, true);
    });
  </script>
</body>
</html>
"""


class StreamingProxyHandler(http.server.BaseHTTPRequestHandler):
    """
    Local proxy that fetches video streams from MovieBox upstream CDN with exact
    Referer, User-Agent, Origin, and byte-range headers, and rewrites M3U8 playlists.
    """

    def log_message(self, format, *args):
        # Silence verbose request logs
        return

    def do_HEAD(self):
        self.handle_proxy_request(send_body=False)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/player":
            self.serve_player_ui(parsed.query)
            return
        elif path == "/stream":
            self.handle_proxy_request(send_body=True)
            return
        elif path == "/ping":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(b"OK")
            return

        self.send_response(404)
        self.end_headers()

    def serve_player_ui(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        target_url = params.get("url", [""])[0]
        referer = params.get("ref", [STREAM_REFERER])[0]
        title = params.get("title", ["MovieBox Live"])[0]
        streams_json = params.get("streams", ["[]"])[0]

        host = self.headers.get("Host", f"127.0.0.1:{self.server.server_port}")
        proxied_stream_url = (
            f"http://{host}/stream?url={urllib.parse.quote(target_url, safe='')}"
            f"&ref={urllib.parse.quote(referer, safe='')}"
        )

        html = (
            PLAYER_HTML.replace("__TITLE__", title)
            .replace("__STREAM_SRC__", proxied_stream_url)
            .replace("__DIRECT_STREAM__", target_url)
            .replace("__STREAM_LIST_JSON__", streams_json if streams_json.startswith("[") else "[]")
        )

        data = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def handle_proxy_request(self, send_body=True):
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        target_url = params.get("url", [""])[0]
        referer = params.get("ref", [STREAM_REFERER])[0]

        if not target_url:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing url parameter")
            return

        # Upstream Request with Mobile App Headers
        req = urllib.request.Request(target_url)
        req.add_header("User-Agent", STREAM_USER_AGENT)
        req.add_header("Referer", referer if referer else STREAM_REFERER)
        req.add_header("Origin", STREAM_ORIGIN)
        req.add_header("Accept", "*/*")
        req.add_header("Accept-Encoding", "identity")
        req.add_header("Connection", "close")

        # Forward incoming Range header (CRITICAL for Video Seeking!)
        incoming_range = self.headers.get("Range")
        if incoming_range:
            req.add_header("Range", incoming_range)

        try:
            # Using SSL_CTX guarantees SSL certificate handshake never fails on Windows
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=15) as resp:
                status_code = resp.status
                headers = resp.headers

                content_type = headers.get("Content-Type", "video/mp4")
                content_length = headers.get("Content-Length")
                content_range = headers.get("Content-Range")
                accept_ranges = headers.get("Accept-Ranges", "bytes")

                is_m3u8 = (
                    "mpegurl" in content_type.lower()
                    or target_url.lower().endswith(".m3u8")
                )

                if is_m3u8 and send_body:
                    try:
                        body_bytes = resp.read()
                    except http.client.IncompleteRead as ie:
                        body_bytes = ie.partial
                    text = body_bytes.decode("utf-8", errors="replace")

                    host = self.headers.get("Host", f"127.0.0.1:{self.server.server_port}")
                    proxy_base = f"http://{host}"

                    rewritten_lines = []
                    for line in text.splitlines():
                        s = line.strip()
                        if not s or s.startswith("#"):
                            rewritten_lines.append(line)
                        else:
                            resolved = urllib.parse.urljoin(target_url, s)
                            proxied = (
                                f"{proxy_base}/stream?url="
                                f"{urllib.parse.quote(resolved, safe='')}"
                                f"&ref={urllib.parse.quote(referer, safe='')}"
                            )
                            rewritten_lines.append(proxied)

                    out_data = "\n".join(rewritten_lines).encode("utf-8")
                    self.send_response(200)
                    self.send_header("Content-Type", "application/vnd.apple.mpegurl")
                    self.send_header("Content-Length", str(len(out_data)))
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(out_data)
                    return

                # Normal Video Stream (MP4, TS, MPD chunks)
                self.send_response(status_code)
                self.send_header("Content-Type", content_type)
                if content_length:
                    self.send_header("Content-Length", content_length)
                if content_range:
                    self.send_header("Content-Range", content_range)
                self.send_header("Accept-Ranges", accept_ranges)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Headers", "*")
                self.end_headers()

                if send_body:
                    while True:
                        try:
                            chunk = resp.read(64 * 1024)
                        except http.client.IncompleteRead as ie:
                            chunk = ie.partial
                        if not chunk:
                            break
                        self.wfile.write(chunk)

        except urllib.error.HTTPError as he:
            self.send_response(he.code)
            self.end_headers()
        except Exception:
            # Client disconnected or network reset
            pass


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


class MovieBoxPlayerEngine:
    """Singleton Engine that manages the local stream proxy and player window."""

    _instance = None

    def __init__(self):
        self.server = None
        self.port = None
        self.server_thread = None
        self.current_process = None
        self.ensure_server_started()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = MovieBoxPlayerEngine()
        return cls._instance

    def ensure_server_started(self):
        if self.server is not None:
            return

        # Pick open port
        for p in range(54321, 54400):
            try:
                self.server = ThreadedHTTPServer(("127.0.0.1", p), StreamingProxyHandler)
                self.port = p
                break
            except Exception:
                continue

        if self.server is None:
            # Fallback to random free port
            self.server = ThreadedHTTPServer(("127.0.0.1", 0), StreamingProxyHandler)
            self.port = self.server.server_port

        self.server_thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.server_thread.start()

    def get_proxy_stream_url(self, raw_url, referer=STREAM_REFERER):
        self.ensure_server_started()
        return (
            f"http://127.0.0.1:{self.port}/stream?url="
            f"{urllib.parse.quote(raw_url, safe='')}"
            f"&ref={urllib.parse.quote(referer, safe='')}"
        )

    def get_player_url(self, title, raw_url, referer=STREAM_REFERER, streams=None):
        self.ensure_server_started()
        streams_json = json.dumps(streams or [])
        return (
            f"http://127.0.0.1:{self.port}/player?url="
            f"{urllib.parse.quote(raw_url, safe='')}"
            f"&ref={urllib.parse.quote(referer, safe='')}"
            f"&title={urllib.parse.quote(title, safe='')}"
            f"&streams={urllib.parse.quote(streams_json, safe='')}"
        )

    def launch_player(self, title, raw_url, referer=STREAM_REFERER, streams=None):
        """
        Launches the Inbuilt Google Media Player in standalone app mode if requested externally.
        """
        player_url = self.get_player_url(title, raw_url, referer, streams)

        candidate_paths = [
            os.path.expandvars(r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"),
            os.path.expandvars(r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"),
            os.path.expandvars(r"%LocalAppData%\Microsoft\Edge\Application\msedge.exe"),
            os.path.expandvars(r"%ProgramFiles%\Google\Chrome\Application\chrome.exe"),
            os.path.expandvars(r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"),
            os.path.expandvars(r"%LocalAppData%\Google\Chrome\Application\chrome.exe"),
            os.path.expandvars(r"%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe"),
            os.path.expandvars(r"%ProgramFiles(x86)%\BraveSoftware\Brave-Browser\Application\brave.exe"),
            os.path.expandvars(r"%LocalAppData%\BraveSoftware\Brave-Browser\Application\brave.exe"),
            "msedge.exe",
            "chrome.exe",
            "brave.exe"
        ]

        for exe in candidate_paths:
            if not os.path.isabs(exe) or os.path.exists(exe):
                try:
                    cmd = [
                        exe,
                        f"--app={player_url}",
                        "--window-size=1200,720",
                        "--window-position=80,80",
                        "--autoplay-policy=no-user-gesture-required",
                        "--enable-features=HardwareAcceleratedVideoDecode"
                    ]
                    proc = subprocess.Popen(cmd)
                    self.current_process = proc
                    return True
                except Exception:
                    continue

        webbrowser.open(player_url)
        return True
