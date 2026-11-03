"""
MovieBox Integrated Player Helper
Provides Google Media Player (ExoPlayer/Shaka engine) integration
with automatic HTTP header injection, byte-range seeking, and multi-format support.
"""
import os
import webbrowser
from moviebox_player import MovieBoxPlayerEngine

def play_in_google_media_player(title, stream_url, referer="https://movieboxph.org/"):
    """Launches the Inbuilt Google Media Player in standalone app mode with all headers."""
    return MovieBoxPlayerEngine.get_instance().launch_player(title, stream_url, referer)

def get_proxy_stream_url(stream_url, referer="https://movieboxph.org/"):
    """Returns local proxy stream URL that transparently injects MovieBox headers."""
    return MovieBoxPlayerEngine.get_instance().get_proxy_stream_url(stream_url, referer)

def play_in_system_player(stream_url, referer="https://movieboxph.org/"):
    """
    Plays the stream in Windows Media Player, VLC, or system player
    through the local proxy server so required Referer and User-Agent headers are preserved.
    """
    proxy_url = get_proxy_stream_url(stream_url, referer)
    try:
        os.startfile(proxy_url)
        return True
    except Exception:
        webbrowser.open(proxy_url)
        return True
