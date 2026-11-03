"""
Server 2 Extension: VSKit
Provides integration for VSKit Short Dramas, Reels, Multi-Episode Shorts, and Player Streams.
"""

import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
SITE_DOMAIN = "https://vskit.online"
SITE_TYPE = "VskitWeb"
AUTH_BEARER = (
    "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
    "eyJ1aWQiOjc5MTY5NjY1MDE2OTIyMDQ4NDgsImF0cCI6MywiZXh0IjoiMTc4OTAxNDE4OSIsImV4cCI6MTc5Njc5MDE4OSwiaWF0IjoxNzg5MDEzODg5fQ."
    "9-OmrTY-your3sdzcSvH-YjFc-WgmhJL7YmtiaDY42k"
)


class VskitExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_2",
            server_name="VSKit",
            server_badge="VSKit",
            primary_color="#F59E0B"
        )

    def _get_headers(self, extra=None):
        h = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Origin": SITE_DOMAIN,
            "Referer": f"{SITE_DOMAIN}/",
            "authorization": AUTH_BEARER,
            "X-Site-Domain": SITE_DOMAIN,
            "X-Site-Type": SITE_TYPE,
            "X-PM-Level": "3",
            "X-PM-Active": "true",
            "x-client-info": '{"timezone":"Asia/Calcutta"}',
            "Accept": "*/*",
            "Connection": "close"
        }
        if extra:
            h.update(extra)
        return h

    def _parse_vskit_item(self, it):
        sid = it.get("subjectId") or it.get("id") or it.get("miniId") or ""
        title = it.get("title") or it.get("name") or "Short Drama"
        cover = it.get("coverUrl") or it.get("posterUrl") or it.get("thumbnail") or ""
        video_url = it.get("videoUrl") or it.get("url") or ""

        return {
            "id": str(sid),
            "subjectId": str(sid),
            "title": title,
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": str(it.get("rating") or "8.2"),
            "releaseYear": str(it.get("releaseYear") or "2025"),
            "genre": it.get("genre") or "Short Drama",
            "corner": "SHORTS",
            "description": it.get("description") or f"Watch {title} mini drama episodes in HD.",
            "detailPath": it.get("detailPath") or "",
            "subjectType": 7, # 7 = Short TV / Mini Drama
            "is_series": True,
            "directUrl": video_url
        }

    def get_home_data(self):
        url = f"{BASE_URL}/vskit/tab-operation-list"
        res = self.http_get(url, headers=self._get_headers())

        hero_banners = []
        rows = []

        if res and res.get("data"):
            data_list = res["data"].get("list", [])
            for section in data_list:
                sec_title = section.get("title") or section.get("name") or "VSKit Trending"
                sec_items = section.get("items", []) or section.get("list", [])
                if not sec_items:
                    continue

                parsed = [self._parse_vskit_item(x) for x in sec_items]
                if not hero_banners and len(parsed) >= 3:
                    hero_banners = parsed[:10]

                rows.append({
                    "id": str(section.get("id") or sec_title),
                    "title": sec_title,
                    "items": parsed
                })

        # Fallback recommend list if rows empty
        if not rows:
            rec_url = f"{BASE_URL}/vskit/recommend-list?page=1&perPage=24&novelType=3"
            r_res = self.http_get(rec_url, headers=self._get_headers())
            if r_res and r_res.get("data"):
                items = [self._parse_vskit_item(x) for x in r_res["data"].get("list", [])]
                hero_banners = items[:6]
                rows.append({
                    "id": "vskit_recommend",
                    "title": "Popular VSKit Short Dramas",
                    "items": items
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
            return ["CEO", "Billionaire", "Revenge", "Love", "Drama", "Secret"]
        url = f"{BASE_URL}/vskit/everyonesearch"
        res = self.http_get(url, headers=self._get_headers())
        suggestions = []
        if res and res.get("data"):
            for s in res["data"].get("list", []):
                val = s.get("title") or s.get("keyword") or s.get("name")
                if val and q.lower() in val.lower():
                    suggestions.append(val)
        if not suggestions:
            suggestions = [f"{q} Drama", f"{q} Love", f"{q} Boss", f"{q} Romance"]
        return suggestions[:8]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        url = f"{BASE_URL}/vskit/search?keyword={urllib.parse.quote(q)}&page={page}&perPage={per_page}"
        res = self.http_get(url, headers=self._get_headers())

        items = []
        has_more = False
        if res and res.get("data"):
            raw_list = res["data"].get("list", [])
            items = [self._parse_vskit_item(x) for x in raw_list]
            has_more = (len(raw_list) >= per_page)

        return {
            "items": items,
            "has_more": has_more,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        clean_id = (subject_id or "").strip()
        url = f"{BASE_URL}/vskit/shorts/mini-list?subjectId={clean_id}&page=1&perPage=100"
        res = self.http_get(url, headers=self._get_headers())

        episodes = []
        item_title = "VSKit Mini Drama"
        cover_url = ""
        desc = ""

        if res and res.get("data"):
            data = res["data"]
            item_title = data.get("title") or item_title
            cover_url = data.get("coverUrl") or ""
            desc = data.get("description") or f"Watch all episodes of {item_title}."

            for ep in data.get("list", []):
                ep_num = ep.get("ep") or ep.get("episode") or 1
                v_url = ep.get("videoUrl") or ep.get("url") or ""
                episodes.append({
                    "episode": ep_num,
                    "title": ep.get("title") or f"Episode {ep_num}",
                    "videoUrl": v_url
                })

        total_ep = len(episodes) if episodes else 30
        return {
            "id": clean_id,
            "subjectId": clean_id,
            "title": item_title,
            "description": desc,
            "coverUrl": cover_url,
            "backdropUrl": cover_url,
            "rating": "8.4",
            "releaseYear": "2025",
            "genre": "Short Drama",
            "subjectType": 7,
            "is_series": True,
            "seasons": [{"seasonNumber": 1, "maxEp": total_ep, "resolutions": [720, 1080]}],
            "cast": [{"name": "Lead Actor", "character": "Hero", "avatarUrl": ""}],
            "detailPath": detail_path,
            "episodes": episodes
        }

    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        clean_id = (subject_id or "").strip()
        target_ep = max(1, episode)

        # 1. Fetch episodes mini-list
        url = f"{BASE_URL}/vskit/shorts/mini-list?subjectId={clean_id}&page=1&perPage=100"
        res = self.http_get(url, headers=self._get_headers())

        stream_url = ""
        if res and res.get("data"):
            for ep in res["data"].get("list", []):
                if (ep.get("ep") or ep.get("episode") or 1) == target_ep:
                    stream_url = ep.get("videoUrl") or ep.get("url") or ""
                    break
            if not stream_url and res["data"].get("list"):
                stream_url = res["data"]["list"][0].get("videoUrl") or ""

        # 2. Fallback to standard subject play
        if not stream_url:
            p_url = f"{BASE_URL}/subject/play?subjectId={clean_id}&se={season}&ep={episode}"
            p_res = self.http_get(p_url, headers=self._get_headers())
            if p_res and p_res.get("data"):
                streams = p_res["data"].get("streams", [])
                if streams and streams[0].get("url"):
                    stream_url = streams[0]["url"]

        if stream_url:
            return {
                "has_resource": True,
                "default_url": stream_url,
                "streams": [{
                    "url": stream_url,
                    "resolution": "720p",
                    "format": "MP4" if ".mp4" in stream_url else "HLS",
                    "size": 0
                }],
                "headers": {
                    "User-Agent": DEFAULT_USER_AGENT,
                    "Referer": f"{SITE_DOMAIN}/",
                    "Origin": SITE_DOMAIN
                }
            }

        return {
            "has_resource": False,
            "default_url": "",
            "streams": [],
            "headers": {"User-Agent": DEFAULT_USER_AGENT, "Referer": f"{SITE_DOMAIN}/", "Origin": SITE_DOMAIN}
        }
