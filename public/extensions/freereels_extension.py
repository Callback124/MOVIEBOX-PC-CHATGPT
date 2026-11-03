"""
Server 5 Extension: Free Reels
Provides integration for FreeReels Trending Short Dramas, Episode Lists, and Player Streams.
"""

import gzip
import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

BASE_URL = "https://apiv2.free-reels.com"
AUTH_HEADER = "oauth_signature=bf505aa6bb04c96350296bed13d0a920,oauth_token=fyqFvwztTYc5x8v8egEYrfa88WfQCL5D,ts=1789800192295"


class FreeReelsExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_5",
            server_name="Free Reels",
            server_badge="Free Reels",
            primary_color="#10B981"
        )

    def _get_headers(self, extra=None):
        h = {
            "User-Agent": "okhttp/4.12.0",
            "authorization": AUTH_HEADER,
            "device-id": "BC7C2318F4322CFFE00CBDE5043E5644",
            "android-id": "bc29b75f90824a5c",
            "app-name": "com.freereels.app",
            "country": "IN",
            "language": "en-US",
            "Accept": "application/json",
            "Connection": "close"
        }
        if extra:
            h.update(extra)
        return h

    def _parse_drama(self, d):
        sid = str(d.get("series_id") or d.get("id") or "")
        title = d.get("series_title") or d.get("title") or d.get("name") or "FreeReels Drama"
        cover = d.get("cover_image_url") or d.get("coverUrl") or d.get("image") or ""

        return {
            "id": sid,
            "subjectId": sid,
            "title": title,
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": str(d.get("score") or "8.8"),
            "releaseYear": "2025",
            "genre": d.get("category_name") or "Short Drama",
            "corner": "SHORTS",
            "description": d.get("intro") or d.get("description") or f"Watch {title} on Free Reels.",
            "detailPath": "",
            "subjectType": 7,
            "is_series": True
        }

    def get_home_data(self):
        url = f"{BASE_URL}/frv2-api/homepage/v2/tab/index?tab_key=503&position_index=10000&rec_trigger=1&user_new_theater=false&is_app_coldstart=false"
        res = self.http_get(url, headers=self._get_headers())

        hero_banners = []
        rows = []

        if res and res.get("data"):
            data = res["data"]
            modules = data.get("items", [])
            all_dramas = []

            for mod in modules:
                m_title = mod.get("title") or mod.get("name") or "Trending Dramas"
                sub_items = mod.get("items", [])
                parsed_sub = [self._parse_drama(x) for x in sub_items]
                all_dramas.extend(parsed_sub)

                if parsed_sub:
                    rows.append({
                        "id": f"freereels_mod_{len(rows)}",
                        "title": m_title,
                        "items": parsed_sub
                    })

            if not rows and all_dramas:
                rows.append({
                    "id": "freereels_all",
                    "title": "Popular Free Reels Series",
                    "items": all_dramas
                })

            hero_banners = all_dramas[:8]

        return {
            "server_id": self.server_id,
            "server_name": self.server_name,
            "hero_banners": hero_banners,
            "rows": rows
        }

    def get_search_suggestions(self, query):
        q = (query or "").strip()
        if not q:
            return ["Mute to Muse", "Revenge", "Billionaire", "Doctor", "Marriage", "Secret"]
        return [f"{q} Drama", f"{q} Series", f"{q} Full"]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        url = f"{BASE_URL}/frv2-api/search/drama"
        payload = {"keyword": q, "page": page, "page_size": per_page}
        res = self.http_post(url, payload, headers=self._get_headers())

        items = []
        has_more = False
        if res and res.get("data"):
            raw_list = res["data"].get("list", []) or res["data"].get("drama_list", [])
            items = [self._parse_drama(x) for x in raw_list]
            has_more = (len(raw_list) >= per_page)

        return {
            "items": items,
            "has_more": has_more,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}/frv2-api/drama/info_v2?series_id={clean_id}&clip_content="
        res = self.http_get(url, headers=self._get_headers())

        episodes = []
        title = "FreeReels Drama"
        desc = ""
        cover = ""

        if res and res.get("data"):
            d = res["data"]
            title = d.get("series_title") or title
            desc = d.get("intro") or d.get("description") or ""
            cover = d.get("cover_image_url") or ""

            ep_list = d.get("episode_list", []) or d.get("episodes", [])
            for ep in ep_list:
                v_url = ep.get("video_url") or ep.get("m3u8_url") or ep.get("play_url") or ""
                idx = ep.get("index") or ep.get("episode") or 1
                episodes.append({
                    "episode": idx,
                    "title": ep.get("name") or f"Episode {idx}",
                    "videoUrl": v_url
                })

        total_ep = len(episodes) if episodes else 40
        return {
            "id": clean_id,
            "subjectId": clean_id,
            "title": title,
            "description": desc or f"Watch all episodes of {title} on Free Reels.",
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": "8.8",
            "releaseYear": "2025",
            "genre": "Short Drama",
            "subjectType": 7,
            "is_series": True,
            "seasons": [{"seasonNumber": 1, "maxEp": total_ep, "resolutions": [720, 1080]}],
            "cast": [{"name": "Lead Actor", "character": "Star", "avatarUrl": ""}],
            "detailPath": "",
            "episodes": episodes
        }

    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}/frv2-api/drama/info_v2?series_id={clean_id}&clip_content="
        res = self.http_get(url, headers=self._get_headers())

        stream_url = ""
        target_ep = max(1, episode)

        if res and res.get("data"):
            d = res["data"]
            ep_list = d.get("episode_list", []) or d.get("episodes", [])
            for ep in ep_list:
                if (ep.get("index") or ep.get("episode") or 1) == target_ep:
                    stream_url = ep.get("video_url") or ep.get("m3u8_url") or ep.get("play_url") or ""
                    break
            if not stream_url and ep_list:
                stream_url = ep_list[0].get("video_url") or ep_list[0].get("m3u8_url") or ""

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
                    "Referer": "https://free-reels.com/",
                    "Origin": "https://free-reels.com"
                }
            }

        return {
            "has_resource": False,
            "default_url": "",
            "streams": [],
            "headers": {"User-Agent": DEFAULT_USER_AGENT}
        }
