"""
Base Server Extension Interface
Provides the unified contract that all 6 MovieBox streaming server extensions implement:
- get_home_data(): Home feed with hero banners and rows of categorized items.
- get_search_suggestions(query): Fast autocomplete suggestions.
- search(query, page, per_page): Search results grid with pagination.
- get_detail(subject_id, detail_path): Item details, cast, seasons, episode count.
- get_player_stream(subject_id, detail_path, season, episode): Playable streams & HTTP headers.
"""

import ssl
import json
import urllib.parse
import urllib.request
import http.client
from abc import ABC, abstractmethod

SSL_CTX = ssl._create_unverified_context()

DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
)


class BaseServerExtension(ABC):
    def __init__(self, server_id, server_name, server_badge, primary_color):
        self.server_id = server_id
        self.server_name = server_name
        self.server_badge = server_badge
        self.primary_color = primary_color

    def http_get(self, url, headers=None, timeout=10):
        req_headers = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Accept": "*/*",
            "Connection": "close"
        }
        if headers:
            req_headers.update(headers)
        try:
            req = urllib.request.Request(url, headers=req_headers)
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=timeout) as resp:
                try:
                    raw = resp.read()
                except http.client.IncompleteRead as ie:
                    raw = ie.partial
                return json.loads(raw.decode("utf-8", errors="ignore"))
        except Exception:
            return None

    def http_post(self, url, json_payload, headers=None, timeout=10):
        req_headers = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Content-Type": "application/json",
            "Accept": "*/*",
            "Connection": "close"
        }
        if headers:
            req_headers.update(headers)
        try:
            data_bytes = json.dumps(json_payload).encode("utf-8")
            req = urllib.request.Request(url, data=data_bytes, headers=req_headers, method="POST")
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=timeout) as resp:
                try:
                    raw = resp.read()
                except http.client.IncompleteRead as ie:
                    raw = ie.partial
                return json.loads(raw.decode("utf-8", errors="ignore"))
        except Exception:
            return None

    @abstractmethod
    def get_home_data(self):
        """
        Returns:
        {
            "server_id": self.server_id,
            "server_name": self.server_name,
            "hero_banners": [MovieItem, ...],
            "rows": [
                {
                    "id": "...",
                    "title": "...",
                    "items": [MovieItem, ...]
                }, ...
            ]
        }
        """
        pass

    @abstractmethod
    def get_search_suggestions(self, query):
        """
        Returns list of strings: ["suggestion 1", "suggestion 2", ...]
        """
        pass

    @abstractmethod
    def search(self, query, page=1, per_page=24):
        """
        Returns:
        {
            "items": [MovieItem, ...],
            "has_more": bool,
            "page": int,
            "query": str
        }
        """
        pass

    @abstractmethod
    def get_detail(self, subject_id, detail_path=""):
        """
        Returns full metadata:
        {
            "id": str,
            "subjectId": str,
            "title": str,
            "description": str,
            "coverUrl": str,
            "backdropUrl": str,
            "rating": str,
            "releaseYear": str,
            "genre": str,
            "subjectType": int, # 1=Movie, 2=Series, 5=Kids, 6=Music, 7=Shorts, 9=Sports
            "is_series": bool,
            "seasons": [{"seasonNumber": 1, "maxEp": 24}, ...],
            "cast": [{"name": str, "character": str, "avatarUrl": str}, ...],
            "detailPath": str
        }
        """
        pass

    @abstractmethod
    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        """
        Returns stream info for in-built player:
        {
            "has_resource": bool,
            "default_url": str,
            "streams": [
                {
                    "url": str,
                    "resolution": str,
                    "format": str, # MP4, HLS, DASH
                    "size": int
                }, ...
            ],
            "headers": {
                "User-Agent": str,
                "Referer": str,
                "Origin": str
            }
        }
        """
        pass
