"""
Server 4 Extension: Story TV
Provides integration for Story TV Asian Mini Dramas, Multi-Episode Series, and Player Streams.
"""

import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

BASE_URL = "https://elitemods-tv.onrender.com"


class StoryTvExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_4",
            server_name="Story TV",
            server_badge="Story TV",
            primary_color="#EC4899"
        )

    def _parse_content_item(self, it):
        data = it.get("data") or {}
        show_info = data.get("showInfo") or {}
        content_info = data.get("contentInfo") or {}

        sid = str(show_info.get("id") or content_info.get("contentId") or it.get("id") or "")
        title = show_info.get("title") or content_info.get("epsTitle") or it.get("title") or "Story TV Drama"
        cover = show_info.get("imageUrl") or content_info.get("thumb") or it.get("coverUrl") or ""
        stream_url = content_info.get("epsUrl") or it.get("videoUrl") or ""
        genre_meta = show_info.get("metadata", {}).get("genre") or "Mini Drama"

        return {
            "id": sid,
            "subjectId": sid,
            "title": title,
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": "8.7",
            "releaseYear": "2025",
            "genre": genre_meta,
            "corner": "SHORTS",
            "description": f"Watch {title} full episodes on Story TV.",
            "detailPath": "",
            "subjectType": 7,
            "is_series": True,
            "directUrl": stream_url
        }

    def get_home_data(self):
        url = f"{BASE_URL}/feedservice/v2/explore/shows?page=0&size=28&intCount=0"
        res = self.http_get(url)

        hero_banners = []
        rows = []

        if res and isinstance(res, dict):
            content_list = res.get("data", {}).get("content", []) or res.get("content", []) or res.get("shows", [])
            parsed = [self._parse_content_item(x) for x in content_list]
            hero_banners = parsed[:8]

            if parsed:
                rows.append({
                    "id": "storytv_explore",
                    "title": "Story TV Trending Dramas",
                    "items": parsed[:10]
                })
                if len(parsed) > 10:
                    rows.append({
                        "id": "storytv_popular",
                        "title": "Popular Asian Mini-Series",
                        "items": parsed[10:20]
                    })
                if len(parsed) > 20:
                    rows.append({
                        "id": "storytv_new",
                        "title": "Newly Released Episodes",
                        "items": parsed[20:]
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
            return ["The King's Return", "Contract Marriage", "CEO Romance", "Revenge", "Queen"]
        return [f"{q} Drama", f"{q} Episodes", f"{q} Love"]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        url = f"{BASE_URL}/searchservice/v1/search/{urllib.parse.quote(q)}"
        res = self.http_get(url)

        items = []
        if res and isinstance(res, dict):
            shows = res.get("data", {}).get("shows", []) or res.get("shows", []) or res.get("data", [])
            if isinstance(shows, list):
                items = [self._parse_content_item(x) for x in shows]

        return {
            "items": items,
            "has_more": False,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}/feedservice/v1/episode/list/{clean_id}"
        res = self.http_get(url)

        episodes = []
        title = "Story TV Drama"
        cover = ""

        if res and isinstance(res, dict):
            ep_list = res.get("data", {}).get("episodes", []) or res.get("episodes", [])
            for idx, ep in enumerate(ep_list):
                v_url = ep.get("videoUrl") or ep.get("url") or ""
                episodes.append({
                    "episode": idx + 1,
                    "title": ep.get("title") or f"Episode {idx + 1}",
                    "videoUrl": v_url
                })

        total_ep = len(episodes) if episodes else 30
        return {
            "id": clean_id,
            "subjectId": clean_id,
            "title": title,
            "description": f"Stream all episodes of {title} with English subtitles.",
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": "8.6",
            "releaseYear": "2025",
            "genre": "Asian Drama",
            "subjectType": 7,
            "is_series": True,
            "seasons": [{"seasonNumber": 1, "maxEp": total_ep, "resolutions": [720, 1080]}],
            "cast": [{"name": "Lead Star", "character": "Hero", "avatarUrl": ""}],
            "detailPath": "",
            "episodes": episodes
        }

    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}/feedservice/v1/episode/list/{clean_id}"
        res = self.http_get(url)

        stream_url = ""
        target_idx = max(0, episode - 1)

        if res and isinstance(res, dict):
            ep_list = res.get("data", {}).get("episodes", []) or res.get("episodes", [])
            if ep_list:
                chosen = ep_list[target_idx] if target_idx < len(ep_list) else ep_list[0]
                stream_url = chosen.get("videoUrl") or chosen.get("url") or ""

        if stream_url:
            return {
                "has_resource": True,
                "default_url": stream_url,
                "streams": [{
                    "url": stream_url,
                    "resolution": "720p",
                    "format": "HLS" if ".m3u8" in stream_url else "MP4",
                    "size": 0
                }],
                "headers": {
                    "User-Agent": DEFAULT_USER_AGENT,
                    "Referer": "https://storytv.asia/",
                    "Origin": "https://storytv.asia"
                }
            }

        return {
            "has_resource": False,
            "default_url": "",
            "streams": [],
            "headers": {"User-Agent": DEFAULT_USER_AGENT}
        }
