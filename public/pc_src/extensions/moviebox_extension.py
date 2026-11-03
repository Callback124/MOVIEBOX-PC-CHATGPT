"""
Server 1 Extension: MovieBox
Provides full integration for MovieBox Movies, TV Series, Search, and Player Streams.
"""

import time
import urllib.parse
from .base_extension import BaseServerExtension, DEFAULT_USER_AGENT

API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
WEB_BASE = "https://movieboxph.org/wefeed-h5api-bff"
REFERER = "https://movieboxph.org/"
ORIGIN = "https://movieboxph.org"


class MovieBoxExtension(BaseServerExtension):
    def __init__(self):
        super().__init__(
            server_id="server_1",
            server_name="MovieBox",
            server_badge="MovieBox",
            primary_color="#6366F1"
        )
        self.cached_token = None
        self.token_expiry = 0

    def _get_headers(self, extra=None):
        h = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Origin": ORIGIN,
            "Referer": REFERER,
            "x-client-info": '{"timezone":"Asia/Calcutta"}',
            "Accept": "*/*",
            "Connection": "close"
        }
        if extra:
            h.update(extra)
        return h

    def _get_token(self):
        now = time.time()
        if self.cached_token and now < self.token_expiry:
            return self.cached_token

        # Use reliable session token
        self.cached_token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio"
        self.token_expiry = now + 1800
        return self.cached_token

    def _parse_item(self, it):
        raw_type = it.get("subjectType")
        corner = (it.get("corner") or "").upper()
        genre = (it.get("genre") or "").upper()

        st = 1
        try:
            st = int(raw_type) if raw_type is not None else 1
        except Exception:
            st = 1

        if st == 6 or "MUSIC" in genre:
            st = 6
        elif st == 7 or "SHORT" in corner or "SHORT" in genre:
            st = 7
        elif st == 5 or "KIDS" in genre or "ANIMATION" in genre:
            st = 5
        elif st == 9 or "SPORT" in genre:
            st = 9
        elif st == 2 or "EP" in corner or "SERIES" in genre:
            st = 2

        cover = it.get("coverUrl") or it.get("posterUrl") or it.get("backdropUrl") or ""
        if isinstance(cover, dict):
            cover = cover.get("url", "")

        return {
            "id": str(it.get("subjectId") or it.get("id") or ""),
            "subjectId": str(it.get("subjectId") or it.get("id") or ""),
            "title": it.get("title") or it.get("name") or "",
            "coverUrl": cover,
            "backdropUrl": it.get("backdropUrl") or cover,
            "rating": str(it.get("rating") or "7.8"),
            "releaseYear": str(it.get("releaseYear") or it.get("year") or "2025"),
            "genre": it.get("genre") or "Entertainment",
            "corner": it.get("corner") or "",
            "description": it.get("description") or "",
            "detailPath": it.get("detailPath") or "",
            "subjectType": st,
            "is_series": (st == 2 or st == 7)
        }

    def get_home_data(self):
        url = f"{API_BASE}/home"
        res = self.http_get(url, headers=self._get_headers())
        if not res or not res.get("data"):
            res = self.http_get(f"{WEB_BASE}/home", headers=self._get_headers())

        hero_banners = []
        rows = []

        if res and res.get("data"):
            op_list = res["data"].get("operatingList", [])
            if op_list:
                # 1. Banners
                b_items = (op_list[0].get("banner") or {}).get("items", [])
                for b in b_items:
                    hero_banners.append(self._parse_item(b))

                # 2. Section Rows
                for op in op_list:
                    # In MovieBox, each section stores items under 'subjects' or 'items'
                    items = op.get("subjects") or op.get("items") or (op.get("banner") or {}).get("items") or []
                    title = op.get("title") or op.get("name")
                    if not items or not title or title.startswith("Banner_"):
                        continue

                    parsed_items = [self._parse_item(it) for it in items]
                    rows.append({
                        "id": str(op.get("id") or op.get("genreTopId") or title),
                        "title": title,
                        "genreTopId": str(op.get("genreTopId") or ""),
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
            return ["Spider-Man", "Batman", "Deadpool", "Avengers", "Superman", "Lupin"]

        url = f"{API_BASE}/subject/search-suggest"
        headers = self._get_headers({"Content-Type": "application/json"})
        payload = {"keyword": q}
        res = self.http_post(url, payload, headers=headers)

        suggestions = []
        if res and res.get("data"):
            items = res["data"].get("items", []) or res["data"].get("list", [])
            for it in items:
                word = it.get("word") or it.get("title") or it.get("keyword")
                if word and word not in suggestions:
                    suggestions.append(word)
        if not suggestions:
            suggestions = [f"{q} 2025", f"{q} Movie", f"{q} Season", f"{q} HD"]
        return suggestions[:10]

    def search(self, query, page=1, per_page=24):
        q = (query or "").strip()
        if not q:
            return {"items": [], "has_more": False, "page": page, "query": q}

        tok = self._get_token()
        headers = self._get_headers({
            "Content-Type": "application/json",
            "Authorization": f"Bearer {tok}",
            "x-user": tok
        })
        payload = {"keyword": q, "page": page, "perPage": per_page}
        url = f"{API_BASE}/subject/search"
        res = self.http_post(url, payload, headers=headers)

        items = []
        has_more = False
        if res and res.get("data"):
            raw_list = res["data"].get("items") or res["data"].get("list") or []
            items = [self._parse_item(it) for it in raw_list]
            has_more = res["data"].get("pager", {}).get("hasMore", len(raw_list) >= per_page)

        return {
            "items": items,
            "has_more": has_more,
            "page": page,
            "query": q
        }

    def get_detail(self, subject_id, detail_path=""):
        sid = (subject_id or "").strip()
        path = (detail_path or "").strip()
        if sid == "0":
            sid = ""

        ref = f"https://movieboxph.org/play/{path}" if path else REFERER
        url = f"{API_BASE}/detail?subjectId={sid}&detailPath={path}"
        res = self.http_get(url, headers=self._get_headers({"Referer": ref}))
        if not res or not res.get("data"):
            res = self.http_get(f"{WEB_BASE}/detail?subjectId={sid}&detailPath={path}", headers=self._get_headers({"Referer": ref}))

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
        if sid == "0":
            sid = ""

        # Auto resolve detail if needed
        if not sid or not path:
            det = self.get_detail(sid, path)
            if det:
                if not sid and det.get("subjectId"):
                    sid = det["subjectId"]
                if not path and det.get("detailPath"):
                    path = det["detailPath"]

        ref = f"https://movieboxph.org/play/{path}" if path else REFERER

        trials = [(season, episode)]
        if season != 0 or episode != 0:
            trials.append((0, 0))
        else:
            trials.append((1, 1))

        base_urls = [f"{WEB_BASE}/subject/play", f"{API_BASE}/subject/play"]

        for try_se, try_ep in trials:
            for base in base_urls:
                params = [f"se={try_se}", f"ep={try_ep}"]
                if sid:
                    params.append(f"subjectId={sid}")
                if path:
                    params.append(f"detailPath={path}")
                u = f"{base}?{'&'.join(params)}"
                res = self.http_get(u, headers=self._get_headers({"Referer": ref}))

                if res and res.get("data"):
                    data = res["data"]
                    out_streams = []

                    # 1. MP4 Streams
                    for s in data.get("streams", []):
                        if s.get("url"):
                            res_val = str(s.get("resolution") or s.get("resolutions") or "720")
                            out_streams.append({
                                "url": s["url"],
                                "resolution": res_val.split(",")[0].strip(),
                                "format": s.get("format", "MP4"),
                                "size": s.get("size", 0)
                            })

                    # 2. HLS Streams (.m3u8)
                    for h in data.get("hls", []):
                        if h.get("url"):
                            res_val = str(h.get("resolution") or h.get("resolutions") or "1080")
                            out_streams.append({
                                "url": h["url"],
                                "resolution": res_val.split(",")[0].strip(),
                                "format": "HLS",
                                "size": h.get("size", 0)
                            })

                    # 3. DASH Streams (.mpd)
                    for d in data.get("dash", []):
                        if d.get("url"):
                            res_val = str(d.get("resolution") or d.get("resolutions") or "1080")
                            out_streams.append({
                                "url": d["url"],
                                "resolution": res_val.split(",")[0].strip(),
                                "format": "DASH",
                                "size": d.get("size", 0)
                            })

                    if out_streams:
                        return {
                            "has_resource": True,
                            "default_url": out_streams[0]["url"],
                            "streams": out_streams,
                            "headers": {
                                "User-Agent": DEFAULT_USER_AGENT,
                                "Referer": ref,
                                "Origin": ORIGIN
                            }
                        }

        return {
            "has_resource": False,
            "default_url": "",
            "streams": [],
            "headers": {
                "User-Agent": DEFAULT_USER_AGENT,
                "Referer": ref,
                "Origin": ORIGIN
            }
        }
