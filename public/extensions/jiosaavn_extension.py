"""
Server 6 Extension: JioSaavn
Provides integration for JioSaavn Trending Music, New Albums, Featured Playlists, Search, and Audio Streams.
"""

import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

BASE_URL = "https://www.jiosaavn.com/api.php"


class JioSaavnExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_6",
            server_name="JioSaavn",
            server_badge="Music",
            primary_color="#A855F7"
        )

    def _clean_text(self, text):
        if not text:
            return ""
        s = str(text).strip()
        return (
            s.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        )

    def _get_hq_image(self, img_url):
        if not img_url:
            return ""
        u = img_url.replace("150x150", "500x500").replace("50x50", "500x500")
        if u.startswith("http://"):
            u = u.replace("http://", "https://")
        return u

    def _parse_song(self, s):
        sid = s.get("id") or s.get("songid") or s.get("pid") or ""
        title = self._clean_text(s.get("song") or s.get("title") or s.get("name") or "JioSaavn Song")
        artist = self._clean_text(s.get("primary_artists") or s.get("more_info", {}).get("singers") or s.get("artist") or "Artist")
        image = self._get_hq_image(s.get("image") or s.get("more_info", {}).get("image") or "")
        year = str(s.get("year") or "2025")

        preview_url = s.get("media_preview_url") or s.get("more_info", {}).get("media_preview_url") or ""

        return {
            "id": str(sid),
            "subjectId": str(sid),
            "title": title,
            "coverUrl": image,
            "backdropUrl": image,
            "rating": "9.0",
            "releaseYear": year,
            "genre": f"Music • {artist}" if artist else "Music",
            "corner": "MUSIC",
            "description": f"Song: {title}\nArtist: {artist}\nAlbum: {self._clean_text(s.get('album', 'Single'))}",
            "detailPath": "",
            "subjectType": 6, # 6 = Music Video Song
            "is_series": False,
            "directUrl": preview_url
        }

    def get_home_data(self):
        url = f"{BASE_URL}?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0"
        res = self.http_get(url)

        hero_banners = []
        rows = []

        if res and isinstance(res, dict):
            # 1. Trending Songs
            trending = res.get("new_trending", [])
            parsed_trending = [self._parse_song(x) for x in trending]
            hero_banners = parsed_trending[:8]

            if parsed_trending:
                rows.append({
                    "id": "saavn_trending",
                    "title": "JioSaavn Trending Music",
                    "items": parsed_trending
                })

            # 2. New Albums
            new_albums = res.get("new_albums", [])
            if new_albums:
                parsed_albums = [self._parse_song(x) for x in new_albums]
                rows.append({
                    "id": "saavn_albums",
                    "title": "Fresh New Releases & Albums",
                    "items": parsed_albums
                })

            # 3. Top Playlists
            top_playlists = res.get("top_playlists", [])
            if top_playlists:
                parsed_playlists = [self._parse_song(x) for x in top_playlists]
                rows.append({
                    "id": "saavn_playlists",
                    "title": "Editorial & Featured Playlists",
                    "items": parsed_playlists
                })

            # 4. Top Charts
            charts = res.get("charts", [])
            if charts:
                parsed_charts = [self._parse_song(x) for x in charts]
                rows.append({
                    "id": "saavn_charts",
                    "title": "Top India & International Charts",
                    "items": parsed_charts
                })

        return {
            "server_id": self.server_id,
            "server_name": self.server_name,
            "hero_banners": hero_banners,
            "rows": rows
        }

    def get_search_suggestions(self, query):
        q = (query or "").strip()
        if not q:
            return ["Arijit Singh", "Shreya Ghoshal", "Anirudh", "Diljit Dosanjh", "Pritam", "Sidhu Moose Wala"]
        url = f"{BASE_URL}?__call=autocomplete.get&query={urllib.parse.quote(q)}&_format=json&ctx=web6dot0"
        res = self.http_get(url)
        suggestions = []
        if res and isinstance(res, dict):
            songs_data = res.get("songs", {}).get("data", [])
            for s in songs_data:
                title = self._clean_text(s.get("title") or s.get("song"))
                if title and title not in suggestions:
                    suggestions.append(title)
        if not suggestions:
            suggestions = [f"{q} Song", f"{q} Hits", f"{q} Remix", f"{q} Lyrics"]
        return suggestions[:8]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        url = f"{BASE_URL}?__call=search.getResults&q={urllib.parse.quote(q)}&n={per_page}&p={page}&_format=json&ctx=web6dot0"
        res = self.http_get(url)

        items = []
        has_more = False
        if res and isinstance(res, dict):
            raw_list = res.get("results", []) or []
            items = [self._parse_song(x) for x in raw_list]
            has_more = (len(raw_list) >= per_page)

        return {
            "items": items,
            "has_more": has_more,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}?__call=song.getDetails&pids={clean_id}&_format=json&ctx=web6dot0"
        res = self.http_get(url)

        title = "JioSaavn Track"
        desc = ""
        cover = ""
        artist = ""

        if res and isinstance(res, dict):
            songs = res.get("songs", [])
            if songs:
                s = songs[0]
                parsed = self._parse_song(s)
                return parsed

        return {
            "id": clean_id,
            "subjectId": clean_id,
            "title": title,
            "description": desc or f"Stream high-definition 320kbps music audio on JioSaavn.",
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": "9.2",
            "releaseYear": "2025",
            "genre": "Music Song",
            "subjectType": 6,
            "is_series": False,
            "seasons": [],
            "cast": [{"name": artist or "Singer", "character": "Vocals", "avatarUrl": ""}],
            "detailPath": ""
        }

    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}?__call=song.getDetails&pids={clean_id}&_format=json&ctx=web6dot0"
        res = self.http_get(url)

        stream_url = ""
        if res and isinstance(res, dict):
            songs = res.get("songs", [])
            if songs:
                s = songs[0]
                preview = s.get("media_preview_url") or ""
                if preview:
                    # Upgrade preview url to full high-definition 320kbps track
                    stream_url = preview.replace("_96_p.mp4", "_320.mp4").replace("_96.mp4", "_320.mp4")
                if not stream_url:
                    stream_url = preview

        if stream_url:
            return {
                "has_resource": True,
                "default_url": stream_url,
                "streams": [{
                    "url": stream_url,
                    "resolution": "320kbps Audio",
                    "format": "MP4/AAC",
                    "size": 0
                }],
                "headers": {
                    "User-Agent": DEFAULT_USER_AGENT,
                    "Referer": "https://www.jiosaavn.com/",
                    "Origin": "https://www.jiosaavn.com"
                }
            }

        return {
            "has_resource": False,
            "default_url": "",
            "streams": [],
            "headers": {"User-Agent": DEFAULT_USER_AGENT, "Referer": "https://www.jiosaavn.com/"}
        }
