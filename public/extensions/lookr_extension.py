"""
Server 3 Extension: Lookr
Provides integration for Lookr Movies, Series, Anime, Spotlight Recommendations, and Player Streams.
"""

import time
import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

RECOMMEND_BASE_URL = "https://api.lookr.cloud/wefeed-gogo-bff/recommend"
H5_PLAY_BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play"
H5_DETAIL_BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/detail"
SITE_DOMAIN = "https://lookr.online"
AUTH_BEARER = (
    "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
    "eyJ1aWQiOjU2NTI1NDEwNzg2NjMyMTg4MDAsImV4cCI6MTc5NzA0OTI1NiwiaWF0IjoxNzg5MjcyOTU2fQ."
    "9lDW-RX6eRIdW0GYGIn-Q9HvHRASck7WC8SlTJC0yE0"
)
CLIENT_INFO = "{\"package_name\":\"com.wecloud.lookr\",\"version_name\":\"3.0.16.0706.03\",\"version_code\":316,\"os\":\"android\",\"os_version\":\"14\",\"install_ch\":\"google-play\",\"device_id\":\"45fc9f58647307f975f5c2e70048a08e\",\"install_store\":\"ps\",\"gaid\":\"ad74fab5dc9bf1f9\",\"brand\":\"Infinix\",\"model\":\"Infinix X6711\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"405872\"}"


class LookrExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_3",
            server_name="Lookr",
            server_badge="Lookr",
            primary_color="#3B82F6"
        )

    def _get_headers(self, extra=None):
        ts = int(time.time() * 1000)
        h = {
            "User-Agent": "okhttp/4.12.0",
            "x-client-info": CLIENT_INFO,
            "x-client-status": "0",
            "x-tr-signature": f"{ts}|2|",
            "authorization": AUTH_BEARER,
            "Accept": "*/*",
            "Connection": "close"
        }
        if extra:
            h.update(extra)
        return h

    def _parse_item(self, it):
        sid = str(it.get("id") or it.get("subjectId") or "")
        title = it.get("name") or it.get("title") or "Lookr Title"
        cover = it.get("cover") or it.get("coverUrl") or it.get("posterUrl") or ""
        if isinstance(cover, dict):
            cover = cover.get("url", "")

        subj_type = it.get("subjectType") or 1
        try:
            st = int(subj_type)
        except Exception:
            st = 1

        tag_str = str(it.get("tag") or "")
        if "Anime" in tag_str or "Anime" in title:
            st = 5
        elif "Series" in tag_str or "Drama" in tag_str:
            st = 2

        return {
            "id": sid,
            "subjectId": sid,
            "title": title,
            "coverUrl": cover,
            "backdropUrl": cover,
            "rating": str(it.get("score") or it.get("rating") or "8.0"),
            "releaseYear": str(it.get("year") or "2025"),
            "genre": it.get("classification") or it.get("genre") or "Lookr Selection",
            "corner": "LOOKR",
            "description": it.get("describe") or it.get("description") or f"Watch {title} in HD on Lookr.",
            "detailPath": it.get("detailPath") or "",
            "subjectType": st,
            "is_series": (st == 2)
        }

    def get_home_data(self):
        categories = [
            ("Popular Movies", "Movies", "Popular Movies", "0"),
            ("Hollywood Movies", "Movies", "Hollywood Movies", "4674513975244114264"),
            ("Trending Series", "Series", "Trending drama", "0"),
            ("Trending Anime", "Anime", "Trending Anime", "9019462595941650792")
        ]

        hero_banners = []
        rows = []

        for label, tag, sub_tag, op_id in categories:
            encoded_tag = urllib.parse.quote(tag)
            encoded_sub = urllib.parse.quote(sub_tag)
            url = f"{RECOMMEND_BASE_URL}?tag={encoded_tag}&subTag={encoded_sub}&opId={op_id}&page=1&pageSize=14"
            res = self.http_get(url, headers=self._get_headers())

            if res and res.get("data"):
                data_obj = res["data"]
                items_list = data_obj.get("items") or data_obj.get("movieItems") or []
                parsed_items = [self._parse_item(x) for x in items_list]
                if not hero_banners and parsed_items:
                    hero_banners = parsed_items[:8]
                if parsed_items:
                    rows.append({
                        "id": f"lookr_{tag}_{op_id}",
                        "title": f"Lookr {label}",
                        "items": parsed_items
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
            return ["Jailer", "Mirzapur", "Immortal", "Action", "Thriller", "Anime"]
        return [f"{q} Movie", f"{q} Season", f"{q} Hindi", f"{q} HD"]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        encoded_q = urllib.parse.quote(q)
        url = f"{RECOMMEND_BASE_URL}?tag=search&subTag={encoded_q}&opId=0&page={page}&pageSize={per_page}"
        res = self.http_get(url, headers=self._get_headers())

        items = []
        has_more = False
        if res and res.get("data"):
            data_obj = res["data"]
            raw_list = data_obj.get("items") or data_obj.get("movieItems") or []
            items = [self._parse_item(x) for x in raw_list]
            has_more = (len(raw_list) >= per_page)

        return {
            "items": items,
            "has_more": has_more,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        sid = (subject_id or "").strip()
        path = (detail_path or "").strip()

        url = f"{H5_DETAIL_BASE_URL}?subjectId={sid}&detailPath={path}"
        res = self.http_get(url, headers=self._get_headers({"User-Agent": DEFAULT_USER_AGENT}))

        if res and res.get("data"):
            d = res["data"]
            parsed = self._parse_item(d)
            seasons = []
            for s in d.get("seasons", []):
                seasons.append({
                    "seasonNumber": s.get("seasonNumber", 1),
                    "maxEp": s.get("maxEp", 12),
                    "resolutions": s.get("resolutions", [720, 1080])
                })
            parsed["seasons"] = seasons

            cast = []
            for c in d.get("cast", []):
                cast.append({
                    "name": c.get("name", ""),
                    "character": c.get("character", ""),
                    "avatarUrl": c.get("avatarUrl", "")
                })
            parsed["cast"] = cast
            return parsed

        return None

    def get_player_stream(self, subject_id, detail_path="", season=0, episode=0):
        sid = (subject_id or "").strip()
        path = (detail_path or "").strip()

        trials = [(season, episode)]
        if season != 0 or episode != 0:
            trials.append((0, 0))
        else:
            trials.append((1, 1))

        for se, ep in trials:
            params = [f"se={se}", f"ep={ep}"]
            if sid:
                params.append(f"subjectId={sid}")
            if path:
                params.append(f"detailPath={path}")
            url = f"{H5_PLAY_BASE_URL}?{'&'.join(params)}"
            res = self.http_get(url, headers=self._get_headers({"User-Agent": DEFAULT_USER_AGENT}))

            if res and res.get("data"):
                streams = res["data"].get("streams", [])
                out = []
                for s in streams:
                    if s.get("url"):
                        out.append({
                            "url": s["url"],
                            "resolution": str(s.get("resolution") or s.get("resolutions") or "720").split(",")[0],
                            "format": s.get("format", "MP4"),
                            "size": s.get("size", 0)
                        })
                if out:
                    return {
                        "has_resource": True,
                        "default_url": out[0]["url"],
                        "streams": out,
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
