"""
MovieBox PC - Stream Free HD Movies & Series
Desktop Client matching Android MovieBox Mobile Application with In-Built Player Engine.
"""

import os
import sys
import time
import json
import ssl
import hashlib
import threading
import urllib.parse
import urllib.request
import http.client
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO

# Tkinter GUI Imports
import tkinter as tk
from tkinter import ttk, messagebox
import webbrowser

# Local Player Engine
import moviebox_player

# PIL / Pillow for rich image handling
try:
    from PIL import Image, ImageTk, ImageDraw
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False


# -------------------------------------------------------------------------
# CONSTANTS & CONFIGURATION
# -------------------------------------------------------------------------
APP_NAME = "MovieBox"
VERSION = "3.5.0"

MOVIEBOX_API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
MOVIEBOX_WEB_BASE = "https://movieboxph.org/wefeed-h5api-bff"

DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
    "Origin": "https://movieboxph.org",
    "Referer": "https://movieboxph.org/",
    "x-client-info": '{"timezone":"Asia/Calcutta"}',
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9,hi;q=0.8",
    "Accept-Encoding": "identity",
    "Connection": "close"
}

SSL_CTX = ssl._create_unverified_context()

CACHE_DIR = os.path.join(os.path.expanduser("~"), ".moviebox_pc_cache")
IMAGE_CACHE_DIR = os.path.join(CACHE_DIR, "images")
HOME_CACHE_FILE = os.path.join(CACHE_DIR, "home_cache.json")
DOWNLOADS_FILE = os.path.join(CACHE_DIR, "downloads.json")

for d in [CACHE_DIR, IMAGE_CACHE_DIR]:
    os.makedirs(d, exist_ok=True)

IMAGE_MEMORY_CACHE = {}
IMAGE_POOL = ThreadPoolExecutor(max_workers=24)
PLACEHOLDER_CACHE = {}

HOME_DATA_CACHE = None
SEARCH_CACHE = {}
SUGGEST_CACHE = {}


# -------------------------------------------------------------------------
# CATEGORY / SUBJECT TYPE IDENTIFIER (EXACT MATCH TO ANDROID APP)
# -------------------------------------------------------------------------
def get_api_type_info(item):
    """
    Identifies category from API subjectId, subjectType, corner, and genre.
    Returns: (code, label, hex_color, is_direct)
    Exact match to Android SubjectTypeInfo:
      1: MOVIE (#E50914)
      2: SERIES (#3B82F6)
      5: KIDS (#10B981)
      6: MUSIC (#A855F7)
      7: SHORTS (#F59E0B)
      9: SPORTS (#FF4757)
    """
    raw_type = item.get("subjectType")
    corner = (item.get("corner") or "").upper()
    genre = (item.get("genre") or "").upper()
    title = (item.get("title") or "").upper()

    try:
        st = int(raw_type) if raw_type is not None else 0
    except (ValueError, TypeError):
        st = 0

    if st == 6 or "MUSIC" in genre or "SONG" in genre:
        return 6, "MUSIC", "#A855F7", True
    if st == 7 or "SHORT" in corner or "SHORT" in genre or "MINI" in genre:
        return 7, "SHORTS", "#F59E0B", True
    if st == 5 or "KIDS" in genre or "CARTOON" in genre or "ANIMATION" in genre:
        return 5, "KIDS", "#10B981", False
    if st == 9 or "SPORT" in genre or "FOOTBALL" in title or "WWE" in title or "LIVE" in genre:
        return 9, "SPORTS", "#FF4757", False
    if st == 2 or "EP" in corner or "SERIES" in genre or "SEASON" in corner:
        return 2, "SERIES", "#3B82F6", False

    return 1, "MOVIE", "#E50914", False


# -------------------------------------------------------------------------
# OUTLINE PLAY ICON PLACEHOLDER (BEFORE IMAGE LOADS)
# -------------------------------------------------------------------------
def get_outline_play_placeholder(size=(130, 185)):
    """
    Generates an outline play icon placeholder image shown while posters are loading.
    Cached in PLACEHOLDER_CACHE for maximum speed.
    """
    if not HAS_PIL:
        return None
    key = f"{size[0]}_{size[1]}"
    if key in PLACEHOLDER_CACHE:
        return PLACEHOLDER_CACHE[key]

    w, h = size
    im = Image.new("RGBA", (w, h), (18, 22, 32, 255))
    draw = ImageDraw.Draw(im)

    # Subtle inner border
    draw.rectangle([0, 0, w - 1, h - 1], outline=(35, 45, 65, 255), width=1)

    # Outline play circle in the center
    cx, cy = w // 2, h // 2
    r = min(w, h) // 4
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(99, 102, 241, 220), width=2)

    # Outline play triangle
    tri_len = r * 0.7
    tri_h = tri_len * 0.8
    p1 = (cx - tri_len * 0.4, cy - tri_h)
    p2 = (cx - tri_len * 0.4, cy + tri_h)
    p3 = (cx + tri_len * 0.7, cy)
    draw.polygon([p1, p2, p3], outline=(255, 255, 255, 230), fill=(99, 102, 241, 70))

    photo = ImageTk.PhotoImage(im)
    PLACEHOLDER_CACHE[key] = photo
    return photo


# -------------------------------------------------------------------------
# ULTRA-FAST IMAGE CACHING & DOWNLOADER
# -------------------------------------------------------------------------
def fast_download_thumbnail(url, size=(130, 185)):
    if not HAS_PIL or not url:
        return None
    cache_key = f"{url}_{size[0]}_{size[1]}"
    if cache_key in IMAGE_MEMORY_CACHE:
        return IMAGE_MEMORY_CACHE[cache_key]

    url_hash = hashlib.md5(url.encode("utf-8")).hexdigest()
    disk_file = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}.bin")

    img_data = None
    if os.path.exists(disk_file):
        try:
            with open(disk_file, "rb") as f:
                img_data = f.read()
        except Exception:
            pass

    if not img_data:
        try:
            headers = {"User-Agent": DEFAULT_HEADERS["User-Agent"], "Connection": "close"}
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=6) as resp:
                try:
                    img_data = resp.read()
                except http.client.IncompleteRead as ie:
                    img_data = ie.partial
            if img_data:
                try:
                    with open(disk_file, "wb") as f:
                        f.write(img_data)
                except Exception:
                    pass
        except Exception:
            return None

    if img_data:
        try:
            im = Image.open(BytesIO(img_data)).convert("RGBA")
            im = im.resize(size, Image.Resampling.LANCZOS)
            photo = ImageTk.PhotoImage(im)
            IMAGE_MEMORY_CACHE[cache_key] = photo
            return photo
        except Exception:
            return None
    return None


def fast_download_hero_backdrop(url, size=(1280, 440)):
    if not HAS_PIL or not url:
        return None
    cache_key = f"hero_{url}_{size[0]}_{size[1]}"
    if cache_key in IMAGE_MEMORY_CACHE:
        return IMAGE_MEMORY_CACHE[cache_key]

    url_hash = hashlib.md5((url + "hero").encode("utf-8")).hexdigest()
    disk_file = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}.bin")

    img_data = None
    if os.path.exists(disk_file):
        try:
            with open(disk_file, "rb") as f:
                img_data = f.read()
        except Exception:
            pass

    if not img_data:
        try:
            headers = {"User-Agent": DEFAULT_HEADERS["User-Agent"], "Connection": "close"}
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=8) as resp:
                try:
                    img_data = resp.read()
                except http.client.IncompleteRead as ie:
                    img_data = ie.partial
            if img_data:
                try:
                    with open(disk_file, "wb") as f:
                        f.write(img_data)
                except Exception:
                    pass
        except Exception:
            return None

    if img_data:
        try:
            im = Image.open(BytesIO(img_data)).convert("RGBA")
            target_w, target_h = size
            im_w, im_h = im.size
            scale = max(target_w / im_w, target_h / im_h)
            new_w, new_h = int(im_w * scale), int(im_h * scale)
            im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)
            left = (new_w - target_w) // 2
            top = (new_h - target_h) // 2
            im = im.crop((left, top, left + target_w, top + target_h))

            # Vignette gradient (dark left, subtle bottom fade)
            overlay = Image.new("RGBA", (target_w, target_h), (0, 0, 0, 0))
            draw = ImageDraw.Draw(overlay)
            for x in range(min(550, target_w)):
                alpha = int(255 * (1.0 - (x / 550.0)) ** 1.3)
                draw.line([(x, 0), (x, target_h)], fill=(11, 13, 19, alpha))
            for y in range(max(0, target_h - 120), target_h):
                alpha = int(255 * ((y - (target_h - 120)) / 120.0))
                draw.line([(0, y), (target_w, y)], fill=(11, 13, 19, alpha))

            im = Image.alpha_composite(im, overlay)
            photo = ImageTk.PhotoImage(im)
            IMAGE_MEMORY_CACHE[cache_key] = photo
            return photo
        except Exception:
            return None
    return None


def queue_poster_load(label_widget, url, size=(130, 185)):
    """Sets outline play placeholder immediately, then loads high-res image asynchronously."""
    if not label_widget or not label_widget.winfo_exists():
        return
    placeholder = get_outline_play_placeholder(size)
    if placeholder:
        label_widget.config(image=placeholder)
        label_widget.image = placeholder

    def _task():
        photo = fast_download_thumbnail(url, size=size)
        if photo and label_widget.winfo_exists():
            def _apply():
                try:
                    label_widget.config(image=photo)
                    label_widget.image = photo
                except Exception:
                    pass
            label_widget.after(0, _apply)

    IMAGE_POOL.submit(_task)


# -------------------------------------------------------------------------
# MOVIEBOX PURE API CLIENT
# -------------------------------------------------------------------------
class MovieBoxApiClient:
    def __init__(self):
        self.cached_token = None
        self.token_expire_time = 0

    def get_or_renew_token(self):
        now = time.time()
        if self.cached_token and now < self.token_expire_time:
            return self.cached_token

        url = f"{MOVIEBOX_API_BASE}/subject/search-suggest?keyword=a"
        headers = dict(DEFAULT_HEADERS)
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=5) as resp:
                token = resp.headers.get("x-user") or resp.headers.get("authorization")
                if token:
                    self.cached_token = token.replace("Bearer ", "").strip()
                    self.token_expire_time = now + 1800
                    return self.cached_token
        except Exception:
            pass

        # Fallback static token
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio"

    def fast_get_json(self, url, timeout=8, referer=None, extra_headers=None):
        headers = dict(DEFAULT_HEADERS)
        if referer:
            headers["Referer"] = referer
        if extra_headers:
            headers.update(extra_headers)
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=timeout) as resp:
                try:
                    raw = resp.read()
                except http.client.IncompleteRead as ie:
                    raw = ie.partial
                return json.loads(raw.decode("utf-8", errors="ignore"))
        except Exception:
            return None

    def fetch_home_data(self):
        global HOME_DATA_CACHE
        res = self.fast_get_json(f"{MOVIEBOX_API_BASE}/home", timeout=10)
        if not res or not res.get("data"):
            res = self.fast_get_json(f"{MOVIEBOX_WEB_BASE}/home", timeout=10)

        if res and res.get("data"):
            HOME_DATA_CACHE = res["data"]
            try:
                with open(HOME_CACHE_FILE, "w", encoding="utf-8") as f:
                    json.dump(HOME_DATA_CACHE, f)
            except Exception:
                pass
            return HOME_DATA_CACHE
        return None

    def fetch_vskit_data(self):
        headers = {
            "Origin": "https://vskit.online",
            "Referer": "https://vskit.online/",
            "X-Site-Domain": "https://vskit.online",
            "X-Site-Type": "VskitWeb",
            "authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjc5MTY5NjY1MDE2OTIyMDQ4NDgsImF0cCI6MywiZXh0IjoiMTc4OTAxNDE4OSIsImV4cCI6MTc5Njc5MDE4OSwiaWF0IjoxNzg5MDEzODg5fQ.9-OmrTY-your3sdzcSvH-YjFc-WgmhJL7YmtiaDY42k"
        }
        res = self.fast_get_json(f"{MOVIEBOX_API_BASE}/vskit/tab-operation-list", timeout=8, extra_headers=headers)
        if res and res.get("data"):
            return res["data"]
        return None

    def fetch_lookr_data(self):
        headers = {
            "authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU2NTI1NDEwNzg2NjMyMTg4MDAsImV4cCI6MTc5NzA0OTI1NiwiaWF0IjoxNzg5MjcyOTU2fQ.9lDW-RX6eRIdW0GYGIn-Q9HvHRASck7WC8SlTJC0yE0"
        }
        res = self.fast_get_json("https://api.lookr.cloud/wefeed-gogo-bff/recommend?tag=recommend&subTag=Featured&opId=0&page=1&pageSize=18", timeout=8, extra_headers=headers)
        if res and res.get("data"):
            return res["data"]
        return None

    def fetch_storytv_data(self):
        res = self.fast_get_json("https://elitemods-tv.onrender.com/feedservice/v2/explore/shows?page=0&size=24&intCount=0", timeout=8)
        if res and isinstance(res, dict):
            return res
        return None

    def fetch_freereels_data(self):
        res = self.fast_get_json("https://apiv2.free-reels.com/frv2-api/homepage/v2/tab/index?tab_key=home&position_index=10000&rec_trigger=1&user_new_theater=false&is_app_coldstart=false", timeout=8)
        if res and res.get("data"):
            return res["data"]
        return None

    def fetch_jiosaavn_data(self):
        res = self.fast_get_json("https://www.jiosaavn.com/api.php?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0", timeout=8)
        if res and isinstance(res, dict):
            return res
        return None

    def fetch_suggestions(self, keyword):
        kw = keyword.strip()
        if not kw:
            return []
        if kw in SUGGEST_CACHE:
            return SUGGEST_CACHE[kw]

        url = f"{MOVIEBOX_API_BASE}/subject/search-suggest?keyword={urllib.parse.quote(kw)}"
        res = self.fast_get_json(url, timeout=4)
        if res and res.get("data"):
            items = []
            for it in res["data"].get("list", []):
                t = it.get("title") or it.get("keyword") or it.get("name")
                if t and t not in items:
                    items.append(t)
            SUGGEST_CACHE[kw] = items[:8]
            return items[:8]
        return []

    def search_movies_page(self, query, page=1, per_page=24):
        q = query.strip()
        if not q:
            return [], False

        cache_key = f"{q}_{page}"
        if cache_key in SEARCH_CACHE:
            return SEARCH_CACHE[cache_key]

        tok = self.get_or_renew_token()
        headers = dict(DEFAULT_HEADERS)
        headers["Content-Type"] = "application/json"
        if tok:
            headers["Authorization"] = f"Bearer {tok}"
            headers["x-user"] = tok

        payload = {"keyword": q, "page": page, "perPage": per_page}
        data_bytes = json.dumps(payload).encode("utf-8")
        url = f"{MOVIEBOX_API_BASE}/subject/search"

        try:
            req = urllib.request.Request(url, data=data_bytes, headers=headers, method="POST")
            with urllib.request.urlopen(req, context=SSL_CTX, timeout=8) as resp:
                try:
                    raw = resp.read()
                except http.client.IncompleteRead as ie:
                    raw = ie.partial
                res = json.loads(raw.decode("utf-8", errors="ignore"))

            if res and res.get("data"):
                items = res["data"].get("list", [])
                out = []
                for it in items:
                    st_id, cat_name, cat_color, is_direct = get_api_type_info(it)
                    out.append({
                        "id": it.get("subjectId") or it.get("id"),
                        "subjectId": it.get("subjectId") or it.get("id"),
                        "title": it.get("title") or it.get("name"),
                        "coverUrl": it.get("coverUrl") or it.get("posterUrl") or it.get("backdropUrl") or "",
                        "backdropUrl": it.get("backdropUrl") or it.get("coverUrl") or "",
                        "rating": str(it.get("rating") or "7.5"),
                        "releaseYear": str(it.get("releaseYear") or it.get("year") or "2025"),
                        "genre": it.get("genre") or cat_name,
                        "corner": it.get("corner") or "",
                        "description": it.get("description") or "",
                        "detailPath": it.get("detailPath") or "",
                        "subjectType": st_id,
                        "category_name": cat_name,
                        "category_color": cat_color
                    })
                has_more = (len(items) >= per_page)
                SEARCH_CACHE[cache_key] = (out, has_more)
                return out, has_more
        except Exception:
            pass

        return [], False

    def fetch_detail(self, detail_path, subject_id=""):
        path = (detail_path or "").strip()
        sid = (subject_id or "").strip()
        if sid == "0":
            sid = ""

        candidates = [
            f"{MOVIEBOX_API_BASE}/detail?subjectId={sid}&detailPath={path}",
            f"{MOVIEBOX_WEB_BASE}/detail?subjectId={sid}&detailPath={path}"
        ]

        ref = f"https://movieboxph.org/play/{path}" if path else "https://movieboxph.org/"

        for u in candidates:
            res = self.fast_get_json(u, timeout=8, referer=ref)
            if res and res.get("data"):
                d = res["data"]
                seasons = []
                for s in d.get("seasons", []):
                    seasons.append({
                        "seasonNumber": s.get("seasonNumber", 1),
                        "maxEp": s.get("maxEp", 12),
                        "resolutions": s.get("resolutions", [720, 1080])
                    })

                cast_list = []
                for c in d.get("cast", []):
                    cast_list.append({
                        "name": c.get("name", ""),
                        "character": c.get("character", ""),
                        "avatarUrl": c.get("avatarUrl", "")
                    })

                st_id, cat_name, cat_color, is_direct = get_api_type_info(d)

                return {
                    "subjectId": d.get("subjectId") or sid,
                    "title": d.get("title") or "",
                    "description": d.get("description") or "",
                    "coverUrl": d.get("coverUrl") or d.get("posterUrl") or "",
                    "backdropUrl": d.get("backdropUrl") or "",
                    "rating": str(d.get("rating") or "7.8"),
                    "releaseYear": str(d.get("releaseYear") or "2025"),
                    "genre": d.get("genre") or cat_name,
                    "corner": d.get("corner") or "",
                    "detailPath": d.get("detailPath") or path,
                    "seasons": seasons,
                    "cast": cast_list,
                    "subjectType": st_id,
                    "category_name": cat_name,
                    "category_color": cat_color
                }
        return None

    def fetch_stream(self, subject_id, detail_path, season=0, episode=0):
        sid = (subject_id or "").strip()
        path = (detail_path or "").strip()
        if sid == "0":
            sid = ""

        # Auto resolve detail if needed
        if not sid or not path:
            det = self.fetch_detail(path, sid)
            if det:
                if not sid and det.get("subjectId"):
                    sid = det["subjectId"]
                if not path and det.get("detailPath"):
                    path = det["detailPath"]

        base_urls = [
            f"{MOVIEBOX_WEB_BASE}/subject/play",
            f"{MOVIEBOX_API_BASE}/subject/play"
        ]

        trials = [(season, episode)]
        if season != 0 or episode != 0:
            trials.append((0, 0))
        else:
            trials.append((1, 1))

        ref = f"https://movieboxph.org/play/{path}" if path else "https://movieboxph.org/"

        for try_se, try_ep in trials:
            for base in base_urls:
                params = []
                if sid and sid != "0":
                    params.append(f"subjectId={sid}")
                if path:
                    params.append(f"detailPath={path}")
                params.append(f"se={try_se}")
                params.append(f"ep={try_ep}")

                u = f"{base}?{'&'.join(params)}"
                res = self.fast_get_json(u, timeout=8, referer=ref)
                if res and res.get("data"):
                    data = res["data"]
                    out_streams = []

                    # 1. MP4 Streams
                    for s in data.get("streams", []):
                        if s.get("url"):
                            res_val = str(s.get("resolution") or s.get("resolutions") or "720")
                            out_streams.append({
                                "url": s["url"],
                                "resolutions": res_val.split(",")[0].strip(),
                                "format": s.get("format", "MP4"),
                                "size": s.get("size", 0)
                            })

                    # 2. HLS Streams (.m3u8)
                    for h in data.get("hls", []):
                        if h.get("url"):
                            res_val = str(h.get("resolution") or h.get("resolutions") or "1080")
                            out_streams.append({
                                "url": h["url"],
                                "resolutions": res_val.split(",")[0].strip(),
                                "format": "HLS",
                                "size": h.get("size", 0)
                            })

                    # 3. DASH Streams (.mpd)
                    for d in data.get("dash", []):
                        if d.get("url"):
                            res_val = str(d.get("resolution") or d.get("resolutions") or "1080")
                            out_streams.append({
                                "url": d["url"],
                                "resolutions": res_val.split(",")[0].strip(),
                                "format": "DASH",
                                "size": d.get("size", 0)
                            })

                    if out_streams:
                        return out_streams

        return []


# -------------------------------------------------------------------------
# MAIN MOVIEBOX PC DESKTOP APPLICATION
# -------------------------------------------------------------------------
class MovieBoxApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("MovieBox - Free HD Movies & Series")
        self.geometry("1280x820")
        self.minsize(1080, 680)
        self.configure(bg="#0B0D13")

        self.client = MovieBoxApiClient()
        self.nav_history = []
        self.active_server = "MovieBox"

        # Server definitions matching Android AppServer enum
        self.server_list = [
            ("MovieBox", "#6366F1"),
            ("VSKit", "#F59E0B"),
            ("Lookr", "#3B82F6"),
            ("Story TV", "#EC4899"),
            ("Free Reels", "#10B981"),
            ("JioSaavn", "#A855F7")
        ]

        # Hero Banner State
        self.hero_banners = []
        self.current_hero_index = 0
        self.hero_timer = None
        self.hero_thumbnails_widgets = []
        self.active_hero_photo = None

        # Search & Suggest State
        self.suggest_box = None
        self.suggest_after_id = None
        self.last_search_query = ""
        self.last_search_results = []

        # In-Built Player State
        self.is_player_active = False
        self.is_fullscreen = False
        self.player_timer = None
        self.player_current_sec = 0
        self.player_duration_sec = 7200
        self.player_is_playing = True
        self.player_streams = []
        self.player_current_stream_idx = 0
        self.player_active_item = None
        self.player_season = 0
        self.player_episode = 0

        # Build Base Structure
        self.build_top_bar()
        self.build_main_container()

        # Keyboard shortcuts
        self.bind("<Escape>", self.on_escape_pressed)
        self.bind("<f>", lambda e: self.toggle_fullscreen() if self.is_player_active else None)
        self.bind("<space>", lambda e: self.player_toggle_play() if self.is_player_active else None)

        # Start loading Home
        self.after(50, self.load_cached_home_or_fetch)

    # -------------------------------------------------------------
    # TOP HEADER & SERVER TABS
    # -------------------------------------------------------------
    def build_top_bar(self):
        self.top_bar = tk.Frame(self, bg="#0F121C", height=60, padx=18)
        self.top_bar.pack(fill="x", side="top")
        self.top_bar.pack_propagate(False)

        # Back Button (Hidden on Home)
        self.btn_back = tk.Button(
            self.top_bar, text="◀  Back", font=("Segoe UI", 10, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=14, pady=5,
            cursor="hand2", command=self.go_back
        )

        # App Brand Title
        self.brand_label = tk.Label(
            self.top_bar, text="MOVIEBOX", font=("Segoe UI", 15, "bold"),
            fg="#6366F1", bg="#0F121C", cursor="hand2"
        )
        self.brand_label.pack(side="left", padx=(0, 20))
        self.brand_label.bind("<Button-1>", lambda e: self.switch_server("MovieBox"))

        # SERVER TABS CONTAINER (MovieBox, VSKit, Lookr, Story TV, Free Reels, JioSaavn)
        self.server_tabs_frame = tk.Frame(self.top_bar, bg="#0F121C")
        self.server_tabs_frame.pack(side="left")

        self.server_tab_buttons = {}
        for s_name, s_color in self.server_list:
            btn = tk.Button(
                self.server_tabs_frame,
                text=s_name,
                font=("Segoe UI", 10, "bold"),
                fg="#FFFFFF" if s_name == self.active_server else "#94A3B8",
                bg=s_color if s_name == self.active_server else "#141926",
                activebackground=s_color,
                activeforeground="#FFFFFF",
                bd=0, padx=14, pady=5, cursor="hand2",
                command=lambda sn=s_name: self.switch_server(sn)
            )
            btn.pack(side="left", padx=3)
            self.server_tab_buttons[s_name] = btn

        # Right Side: Downloads & Search Bar
        self.right_frame = tk.Frame(self.top_bar, bg="#0F121C")
        self.right_frame.pack(side="right")

        # Search Bar
        search_wrap = tk.Frame(self.right_frame, bg="#171C2B", padx=10, pady=4, bd=1, relief="solid")
        search_wrap.pack(side="right", padx=(8, 0))

        tk.Label(search_wrap, text="🔍", font=("Segoe UI", 10), fg="#94A3B8", bg="#171C2B").pack(side="left", padx=(0, 5))

        self.search_entry = tk.Entry(
            search_wrap, font=("Segoe UI", 10), fg="#FFFFFF", bg="#171C2B",
            insertbackground="#6366F1", bd=0, width=22
        )
        self.search_entry.pack(side="left")
        self.search_entry.bind("<KeyRelease>", self.on_search_key_release)
        self.search_entry.bind("<Return>", lambda e: self.trigger_search())

        # Downloads Button
        self.btn_downloads = tk.Button(
            self.right_frame, text="★ My List", font=("Segoe UI", 10, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=12, pady=5,
            cursor="hand2", command=self.show_downloads_screen
        )
        self.btn_downloads.pack(side="right")

    def update_server_tab_highlight(self):
        for s_name, s_color in self.server_list:
            btn = self.server_tab_buttons.get(s_name)
            if btn:
                if s_name == self.active_server:
                    btn.config(bg=s_color, fg="#FFFFFF")
                else:
                    btn.config(bg="#141926", fg="#94A3B8")

    def switch_server(self, server_name):
        self.hide_suggestions()
        self.active_server = server_name
        self.update_server_tab_highlight()
        self.nav_history.clear()
        self.update_back_button()

        if server_name == "MovieBox":
            self.load_cached_home_or_fetch()
        elif server_name == "VSKit":
            self.load_vskit_feed()
        elif server_name == "Lookr":
            self.load_lookr_feed()
        elif server_name == "Story TV":
            self.load_storytv_feed()
        elif server_name == "Free Reels":
            self.load_freereels_feed()
        elif server_name == "JioSaavn":
            self.load_jiosaavn_feed()

    # -------------------------------------------------------------
    # MAIN SCROLLABLE VIEWPORT CONTAINER
    # -------------------------------------------------------------
    def build_main_container(self):
        self.main_container = tk.Frame(self, bg="#0B0D13")
        self.main_container.pack(fill="both", expand=True)

        self.canvas = tk.Canvas(self.main_container, bg="#0B0D13", highlightthickness=0, bd=0)
        self.scrollbar = tk.Scrollbar(self.main_container, orient="vertical", command=self.canvas.yview)

        self.scrollable_frame = tk.Frame(self.canvas, bg="#0B0D13")
        self.canvas_window = self.canvas.create_window((0, 0), window=self.scrollable_frame, anchor="nw")

        self.canvas.configure(yscrollcommand=self.scrollbar.set)
        self.canvas.pack(side="left", fill="both", expand=True)

        self.scrollable_frame.bind("<Configure>", self.on_frame_configure)
        self.canvas.bind("<Configure>", self.on_canvas_configure)
        self.bind_mouse_wheel(self.canvas)
        self.bind_mouse_wheel(self.scrollable_frame)

    def on_frame_configure(self, event=None):
        if not self.is_player_active:
            self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def on_canvas_configure(self, event):
        self.canvas.itemconfig(self.canvas_window, width=event.width)

    def bind_mouse_wheel(self, widget):
        widget.bind("<MouseWheel>", self.on_mouse_wheel)
        widget.bind("<Button-4>", self.on_mouse_wheel)
        widget.bind("<Button-5>", self.on_mouse_wheel)

    def on_mouse_wheel(self, event):
        if self.is_player_active and not self.is_fullscreen:
            # Let inner containers scroll if needed
            return
        if event.num == 4 or event.delta > 0:
            self.canvas.yview_scroll(-2, "units")
        elif event.num == 5 or event.delta < 0:
            self.canvas.yview_scroll(2, "units")

    def reset_viewport_to_top(self):
        self.canvas.yview_moveto(0)

    # -------------------------------------------------------------
    # NAVIGATION & BACKSTACK HANDLING
    # -------------------------------------------------------------
    def update_back_button(self):
        if self.nav_history:
            self.btn_back.pack(side="left", padx=(0, 15), before=self.brand_label)
        else:
            self.btn_back.pack_forget()

    def go_back(self):
        self.hide_suggestions()
        self.stop_player()
        if self.nav_history:
            prev_screen_func = self.nav_history.pop()
            self.update_back_button()
            prev_screen_func()
        else:
            self.show_home_screen()

    # -------------------------------------------------------------
    # HERO SLIDER (100% WIDTH & HEIGHT WITH ENLARGED ACTIVE THUMBNAILS)
    # -------------------------------------------------------------
    def start_hero_timer(self):
        self.stop_hero_timer()
        self.hero_timer = self.after(5500, self.auto_next_hero)

    def stop_hero_timer(self):
        if self.hero_timer:
            try:
                self.after_cancel(self.hero_timer)
            except Exception:
                pass
            self.hero_timer = None

    def auto_next_hero(self):
        if self.hero_banners:
            self.current_hero_index = (self.current_hero_index + 1) % len(self.hero_banners)
            self.render_hero_slide()
            self.start_hero_timer()

    def jump_to_slide(self, index):
        self.stop_hero_timer()
        if 0 <= index < len(self.hero_banners):
            self.current_hero_index = index
            self.render_hero_slide()
            self.start_hero_timer()

    def render_hero_slide(self):
        if not hasattr(self, "hero_container") or not self.hero_container.winfo_exists():
            return
        for w in self.hero_container.winfo_children():
            w.destroy()

        if not self.hero_banners:
            return

        hero = self.hero_banners[self.current_hero_index]
        st_id, label, color, is_direct = get_api_type_info(hero)

        # 100% fixed height hero canvas
        self.hero_canvas = tk.Canvas(self.hero_container, height=440, bg="#0B0D13", highlightthickness=0, bd=0)
        self.hero_canvas.pack(fill="x", expand=True)

        backdrop_url = hero.get("backdropUrl") or hero.get("posterUrl")

        # 1. Background Image Loading & Rendering
        def _load_hero_bg():
            photo = fast_download_hero_backdrop(backdrop_url, size=(1280, 440))
            if photo and self.hero_canvas.winfo_exists():
                def _apply():
                    try:
                        self.active_hero_photo = photo
                        self.hero_canvas.delete("hero_bg")
                        self.hero_canvas.create_image(0, 0, image=photo, anchor="nw", tags="hero_bg")
                        self.hero_canvas.tag_lower("hero_bg")
                    except Exception:
                        pass
                self.hero_canvas.after(0, _apply)

        IMAGE_POOL.submit(_load_hero_bg)

        # 2. Left Overlay Content (Big Title, Category Badge, Meta, Description, Buttons)
        hero_title = hero.get("title", "Featured Spotlight")
        if len(hero_title) > 34:
            hero_title = hero_title[:31] + "..."

        self.hero_canvas.create_text(
            40, 160, text=hero_title, font=("Segoe UI", 28, "bold"),
            fill="#FFFFFF", anchor="w", tags="hero_text"
        )

        rating_val = hero.get("rating") or "7.8"
        year_val = hero.get("releaseYear") or "2025"
        genre_val = hero.get("genre") or "Entertainment"
        meta_str = f"★ {rating_val}   |   {year_val}   |   {genre_val}"
        self.hero_canvas.create_text(
            40, 208, text=meta_str, font=("Segoe UI", 11, "bold"),
            fill="#CBD5E1", anchor="w", tags="hero_text"
        )

        desc_text = (hero.get("description", "") or "").strip()
        if not desc_text:
            desc_text = f"Stream {hero.get('title')} in ultra-clear Full HD with multi-language audio and fast seeking."
        if len(desc_text) > 135:
            desc_text = desc_text[:132] + "..."
        self.hero_canvas.create_text(
            40, 252, text=desc_text, font=("Segoe UI", 10),
            fill="#94A3B8", anchor="w", tags="hero_text"
        )

        # Action Buttons Row
        btn_frame = tk.Frame(self.hero_canvas, bg="#0B0D13")
        self.hero_canvas.create_window(40, 310, window=btn_frame, anchor="nw")

        btn_details = tk.Button(
            btn_frame, text="▶  Watch Now", font=("Segoe UI", 11, "bold"), fg="#FFFFFF", bg="#6366F1",
            activebackground="#4F46E5", activeforeground="#FFFFFF", bd=0, padx=22, pady=8,
            cursor="hand2", command=lambda h=hero: self.on_item_clicked(h)
        )
        btn_details.pack(side="left", padx=(0, 12))

        btn_info = tk.Button(
            btn_frame, text="Details", font=("Segoe UI", 11, "bold"), fg="#FFFFFF", bg="#1E2433",
            activebackground="#374151", activeforeground="#FFFFFF", bd=0, padx=20, pady=8,
            cursor="hand2", command=lambda h=hero: self.on_item_clicked(h)
        )
        btn_info.pack(side="left")

        # 3. Bottom-Right Strip of ALL Mini-Posters
        # Active thumbnail has NO blue border, but is ENLARGED in size!
        strip_canvas_frame = tk.Frame(self.hero_canvas, bg="#0B0D13")
        self.hero_canvas.create_window(1250, 415, window=strip_canvas_frame, anchor="se")

        self.hero_thumbnails_widgets.clear()
        # Display all available banners
        thumb_list = self.hero_banners
        for idx, item in enumerate(thumb_list):
            is_active = (idx == self.current_hero_index)
            # Active thumbnail enlarged, NO blue border!
            w_size = 78 if is_active else 52
            h_size = 110 if is_active else 74

            thumb_card = tk.Frame(strip_canvas_frame, bg="#0B0D13", cursor="hand2")
            thumb_card.pack(side="left", padx=3, anchor="s")

            inner_box = tk.Frame(thumb_card, bg="#111520", width=w_size, height=h_size)
            inner_box.pack_propagate(False)
            inner_box.pack()

            thumb_lbl = tk.Label(inner_box, bg="#111520")
            thumb_lbl.pack(fill="both", expand=True)

            queue_poster_load(thumb_lbl, item.get("posterUrl") or item.get("coverUrl"), size=(w_size, h_size))

            def on_thumb_click(e, slide_idx=idx):
                self.jump_to_slide(slide_idx)

            thumb_card.bind("<Button-1>", on_thumb_click)
            inner_box.bind("<Button-1>", on_thumb_click)
            thumb_lbl.bind("<Button-1>", on_thumb_click)

            self.hero_thumbnails_widgets.append(thumb_card)

        self.bind_mouse_wheel(self.hero_canvas)

    # -------------------------------------------------------------
    # HOME SCREEN BUILDER & FAST CACHE LOADING
    # -------------------------------------------------------------
    def load_cached_home_or_fetch(self):
        cached = None
        if os.path.exists(HOME_CACHE_FILE):
            try:
                with open(HOME_CACHE_FILE, "r", encoding="utf-8") as f:
                    cached = json.load(f)
            except Exception:
                pass

        if cached:
            self.render_home_ui(cached)

        def _fetch_bg():
            fresh = self.client.fetch_home_data()
            if fresh and self.active_server == "MovieBox":
                self.after(0, lambda: self.render_home_ui(fresh))

        threading.Thread(target=_fetch_bg, daemon=True).start()

    def show_home_screen(self):
        self.stop_player()
        self.hide_suggestions()
        self.nav_history.clear()
        self.update_back_button()
        if HOME_DATA_CACHE:
            self.render_home_ui(HOME_DATA_CACHE)
        else:
            self.load_cached_home_or_fetch()

    def render_home_ui(self, home_data):
        if self.is_player_active:
            return

        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()

        operating_list = home_data.get("operatingList", [])
        if not operating_list:
            return

        # 1. Hero Slide Container
        first_op = operating_list[0]
        banner_data = first_op.get("banner", {})
        self.hero_banners = banner_data.get("items", [])

        if self.hero_banners:
            self.hero_container = tk.Frame(self.scrollable_frame, bg="#0B0D13")
            self.hero_container.pack(fill="x", expand=True)
            self.current_hero_index = 0
            self.render_hero_slide()
            self.start_hero_timer()

        # 2. Category Sections
        for op in operating_list:
            items = op.get("items", [])
            title = op.get("title") or op.get("name")
            if not items or not title:
                continue
            self.build_horizontal_section(title, items, op)

    def build_horizontal_section(self, title, items, op_data):
        section_frame = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=12)
        section_frame.pack(fill="x", expand=True)

        header_row = tk.Frame(section_frame, bg="#0B0D13")
        header_row.pack(fill="x", pady=(0, 10))

        tk.Label(header_row, text=title, font=("Segoe UI", 16, "bold"), fg="#FFFFFF", bg="#0B0D13").pack(side="left")

        btn_more = tk.Label(header_row, text="View More  ›", font=("Segoe UI", 11, "bold"), fg="#6366F1", bg="#0B0D13", cursor="hand2")
        btn_more.pack(side="right")
        btn_more.bind("<Button-1>", lambda e, t=title, op=op_data: self.show_view_more_screen(t, op.get("genreTopId") or op.get("id")))

        row_container = tk.Frame(section_frame, bg="#0B0D13")
        row_container.pack(fill="x")

        # Display first 8 items per horizontal strip
        for item in items[:8]:
            self.create_poster_card(row_container, item)

    def create_poster_card(self, parent, item, card_size=(130, 185)):
        w, h = card_size
        st_id, cat_name, cat_color, is_direct = get_api_type_info(item)

        card = tk.Frame(parent, bg="#0B0D13", cursor="hand2")
        card.pack(side="left", padx=7, pady=4)

        box = tk.Frame(card, bg="#111520", width=w, height=h)
        box.pack_propagate(False)
        box.pack()

        poster_lbl = tk.Label(box, bg="#111520")
        poster_lbl.pack(fill="both", expand=True)

        queue_poster_load(poster_lbl, item.get("coverUrl") or item.get("posterUrl") or item.get("backdropUrl"), size=card_size)

        # CATEGORY BADGE ON TOP OF POSTER (MOVIE, SERIES, SHORTS, KIDS, MUSIC, SPORTS)
        badge_lbl = tk.Label(
            box, text=cat_name, font=("Segoe UI", 8, "bold"),
            fg="#FFFFFF", bg=cat_color, padx=5, pady=1
        )
        badge_lbl.place(x=4, y=4)

        # Rating Tag
        rating = item.get("rating")
        if rating and rating != "0":
            star_lbl = tk.Label(
                box, text=f"★ {rating}", font=("Segoe UI", 8, "bold"),
                fg="#FFD700", bg="#0B0D13", padx=4, pady=1
            )
            star_lbl.place(x=w - 42, y=4)

        # Title Label
        raw_title = item.get("title") or item.get("name") or "Movie"
        disp_title = raw_title if len(raw_title) <= 16 else raw_title[:14] + "..."
        t_lbl = tk.Label(card, text=disp_title, font=("Segoe UI", 10, "bold"), fg="#E2E8F0", bg="#0B0D13", wraplength=w, justify="center")
        t_lbl.pack(pady=(4, 0))

        def on_click(e):
            self.on_item_clicked(item)

        card.bind("<Button-1>", on_click)
        box.bind("<Button-1>", on_click)
        poster_lbl.bind("<Button-1>", on_click)
        t_lbl.bind("<Button-1>", on_click)
        badge_lbl.bind("<Button-1>", on_click)

    # -------------------------------------------------------------
    # SERVER FEEDS (VSKit, Lookr, Story TV, Free Reels, JioSaavn)
    # -------------------------------------------------------------
    def load_vskit_feed(self):
        self.show_loading("Loading VSKit Short Dramas & Reels...")
        def _bg():
            data = self.client.fetch_vskit_data()
            self.after(0, lambda: self.render_grid_feed("VSKit Short Dramas", data.get("list", []) if data else []))
        threading.Thread(target=_bg, daemon=True).start()

    def load_lookr_feed(self):
        self.show_loading("Loading Lookr Movies & Series...")
        def _bg():
            data = self.client.fetch_lookr_data()
            self.after(0, lambda: self.render_grid_feed("Lookr Spotlight", data.get("list", []) if data else []))
        threading.Thread(target=_bg, daemon=True).start()

    def load_storytv_feed(self):
        self.show_loading("Loading Story TV Mini Dramas...")
        def _bg():
            data = self.client.fetch_storytv_data()
            items = []
            if data and isinstance(data, dict):
                items = data.get("data", {}).get("shows", []) or data.get("shows", [])
            self.after(0, lambda: self.render_grid_feed("Story TV Short Dramas", items))
        threading.Thread(target=_bg, daemon=True).start()

    def load_freereels_feed(self):
        self.show_loading("Loading Free Reels...")
        def _bg():
            data = self.client.fetch_freereels_data()
            items = []
            if data and isinstance(data, dict):
                items = data.get("theater_list", []) or data.get("list", [])
            self.after(0, lambda: self.render_grid_feed("Free Reels Trending", items))
        threading.Thread(target=_bg, daemon=True).start()

    def load_jiosaavn_feed(self):
        self.show_loading("Loading JioSaavn Music & Podcasts...")
        def _bg():
            data = self.client.fetch_jiosaavn_data()
            items = []
            if data and isinstance(data, dict):
                items = data.get("new_trending", []) or data.get("new_albums", [])
            self.after(0, lambda: self.render_grid_feed("JioSaavn Music & Playlists", items, is_music=True))
        threading.Thread(target=_bg, daemon=True).start()

    def render_grid_feed(self, title, items, is_music=False):
        for w in self.scrollable_frame.winfo_children():
            w.destroy()
        self.reset_viewport_to_top()

        header = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=16)
        header.pack(fill="x")
        tk.Label(header, text=title, font=("Segoe UI", 18, "bold"), fg="#FFFFFF", bg="#0B0D13").pack(side="left")

        if not items:
            tk.Label(self.scrollable_frame, text="No items available. Click to refresh.", font=("Segoe UI", 12), fg="#94A3B8", bg="#0B0D13").pack(pady=40)
            return

        grid_frame = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=20, pady=10)
        grid_frame.pack(fill="both", expand=True)

        cols = 7
        for c in range(cols):
            grid_frame.columnconfigure(c, weight=1)

        for idx, it in enumerate(items):
            r = idx // cols
            c = idx % cols
            cell = tk.Frame(grid_frame, bg="#0B0D13")
            cell.grid(row=r, column=c, padx=6, pady=10)
            # Normalize item
            norm_item = {
                "id": it.get("subjectId") or it.get("id") or it.get("showId") or it.get("series_id"),
                "subjectId": it.get("subjectId") or it.get("id") or it.get("showId") or it.get("series_id"),
                "title": it.get("title") or it.get("name") or it.get("showName") or it.get("album"),
                "coverUrl": it.get("coverUrl") or it.get("posterUrl") or it.get("thumbnail") or it.get("image") or "",
                "rating": str(it.get("rating") or "7.6"),
                "releaseYear": str(it.get("releaseYear") or it.get("year") or "2025"),
                "genre": it.get("genre") or ("Music" if is_music else "Drama"),
                "detailPath": it.get("detailPath") or "",
                "subjectType": 6 if is_music else (7 if self.active_server in ["VSKit", "Story TV", "Free Reels"] else 1)
            }
            self.create_poster_card(cell, norm_item)

    # -------------------------------------------------------------
    # SEARCH WITH INSTANT SUGGESTION CLEARING & PRESERVED BACKSTACK
    # -------------------------------------------------------------
    def on_search_key_release(self, event):
        if event.keysym in ["Return", "Up", "Down", "Escape"]:
            return
        query = self.search_entry.get().strip()
        if len(query) < 2:
            self.hide_suggestions()
            return
        if self.suggest_after_id:
            self.after_cancel(self.suggest_after_id)
        self.suggest_after_id = self.after(180, lambda q=query: self.fetch_and_show_suggestions(q))

    def fetch_and_show_suggestions(self, query):
        def _worker():
            results = self.client.fetch_suggestions(query)
            self.after(0, lambda: self.render_suggestions_dropdown(results))
        threading.Thread(target=_worker, daemon=True).start()

    def render_suggestions_dropdown(self, suggestions):
        if not suggestions:
            self.hide_suggestions()
            return

        if not self.suggest_box or not self.suggest_box.winfo_exists():
            self.suggest_box = tk.Toplevel(self)
            self.suggest_box.wm_overrideredirect(True)
            self.suggest_box.configure(bg="#1A202C")

        for w in self.suggest_box.winfo_children():
            w.destroy()

        x = self.search_entry.winfo_rootx()
        y = self.search_entry.winfo_rooty() + self.search_entry.winfo_height() + 6
        w = max(260, self.search_entry.winfo_width() + 40)
        self.suggest_box.geometry(f"{w}x{len(suggestions) * 32 + 8}+{x}+{y}")

        for s in suggestions:
            btn = tk.Button(
                self.suggest_box, text=f"🔍  {s}", font=("Segoe UI", 9, "bold"),
                fg="#E2E8F0", bg="#161B26", activebackground="#6366F1",
                activeforeground="#FFFFFF", bd=0, anchor="w", padx=10, pady=5,
                cursor="hand2"
            )
            btn.pack(fill="x")
            btn.bind("<Button-1>", lambda e, val=s: self.on_select_suggestion(val))

    def on_select_suggestion(self, val):
        self.search_entry.delete(0, tk.END)
        self.search_entry.insert(0, val)
        self.hide_suggestions()
        self.trigger_search()

    def hide_suggestions(self):
        """Immediately destroys the suggestions popup so it never lingers over search results."""
        if self.suggest_after_id:
            try:
                self.after_cancel(self.suggest_after_id)
            except Exception:
                pass
            self.suggest_after_id = None
        if self.suggest_box and self.suggest_box.winfo_exists():
            self.suggest_box.destroy()
            self.suggest_box = None

    def trigger_search(self):
        query = self.search_entry.get().strip()
        if not query:
            return
        # IMMEDIATELY HIDE SUGGESTIONS ON SEARCH
        self.hide_suggestions()
        self.stop_hero_timer()

        # Save to nav history
        self.nav_history.append(self.show_home_screen)
        self.update_back_button()

        self.show_loading(f"Searching for '{query}'...")

        def _search_bg():
            items, has_more = self.client.search_movies_page(query, page=1, per_page=24)
            self.last_search_query = query
            self.last_search_results = items
            self.after(0, lambda: self.render_search_results(query, items, has_more))

        threading.Thread(target=_search_bg, daemon=True).start()

    def render_search_results(self, query, initial_items, has_more):
        self.hide_suggestions()
        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()

        header = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=16)
        header.pack(fill="x")
        tk.Label(header, text=f"Search Results for: \"{query}\"", font=("Segoe UI", 18, "bold"), fg="#FFFFFF", bg="#0B0D13").pack(side="left")

        if not initial_items:
            tk.Label(self.scrollable_frame, text="No matches found. Try another query.", font=("Segoe UI", 12), fg="#94A3B8", bg="#0B0D13").pack(pady=40)
            return

        grid_frame = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=20, pady=10)
        grid_frame.pack(fill="both", expand=True)

        cols = 7
        for c in range(cols):
            grid_frame.columnconfigure(c, weight=1)

        total_loaded = [0]

        def add_search_items(new_items):
            for it in new_items:
                idx = total_loaded[0]
                r = idx // cols
                c = idx % cols
                cell = tk.Frame(grid_frame, bg="#0B0D13")
                cell.grid(row=r, column=c, padx=6, pady=10)
                # When clicked from search, back navigation returns to SEARCH RESULTS!
                self.create_search_poster_card(cell, it, query)
                total_loaded[0] += 1

        add_search_items(initial_items)

        # Automatic multi-page search loading in background
        if has_more:
            def _multi():
                for p in range(2, 5):
                    items, more = self.client.search_movies_page(query, page=p, per_page=24)
                    if not items:
                        break
                    self.last_search_results.extend(items)
                    self.after(0, lambda it=items: add_search_items(it))
                    if not more:
                        break
                    time.sleep(0.3)
            threading.Thread(target=_multi, daemon=True).start()

    def create_search_poster_card(self, parent, item, query):
        card_size = (130, 185)
        w, h = card_size
        st_id, cat_name, cat_color, is_direct = get_api_type_info(item)

        card = tk.Frame(parent, bg="#0B0D13", cursor="hand2")
        card.pack(side="left", padx=7, pady=4)

        box = tk.Frame(card, bg="#111520", width=w, height=h)
        box.pack_propagate(False)
        box.pack()

        poster_lbl = tk.Label(box, bg="#111520")
        poster_lbl.pack(fill="both", expand=True)

        queue_poster_load(poster_lbl, item.get("coverUrl") or item.get("posterUrl") or item.get("backdropUrl"), size=card_size)

        badge_lbl = tk.Label(
            box, text=cat_name, font=("Segoe UI", 8, "bold"),
            fg="#FFFFFF", bg=cat_color, padx=5, pady=1
        )
        badge_lbl.place(x=4, y=4)

        raw_title = item.get("title") or "Movie"
        disp_title = raw_title if len(raw_title) <= 16 else raw_title[:14] + "..."
        t_lbl = tk.Label(card, text=disp_title, font=("Segoe UI", 10, "bold"), fg="#E2E8F0", bg="#0B0D13", wraplength=w, justify="center")
        t_lbl.pack(pady=(4, 0))

        def on_click(e):
            # PRESERVE SEARCH RESULTS ON BACK NAVIGATION!
            self.nav_history.append(lambda q=query: self.render_search_results(q, self.last_search_results, False))
            self.update_back_button()
            self.on_item_clicked(item, from_search=True)

        card.bind("<Button-1>", on_click)
        box.bind("<Button-1>", on_click)
        poster_lbl.bind("<Button-1>", on_click)
        t_lbl.bind("<Button-1>", on_click)
        badge_lbl.bind("<Button-1>", on_click)

    # -------------------------------------------------------------
    # DETAIL PAGE (NON-SCROLLABLE WITH TOP, DESC, CAST & EPISODE CHUNKS)
    # -------------------------------------------------------------
    def on_item_clicked(self, item, from_search=False):
        st_id, cat_name, cat_color, is_direct = get_api_type_info(item)

        if not from_search:
            self.nav_history.append(self.show_home_screen)
            self.update_back_button()

        if st_id == 2 or st_id == 7:
            self.show_series_detail_screen(item)
        else:
            self.show_movie_detail_screen(item)

    def show_movie_detail_screen(self, item):
        self.stop_hero_timer()
        self.show_loading(f"Resolving details for {item.get('title')}...")

        def _worker():
            sid = item.get("id") or item.get("subjectId")
            path = item.get("detailPath", "")
            det = self.client.fetch_detail(path, sid)
            full_item = dict(item)
            if det:
                full_item.update(det)
            self.after(0, lambda: self.render_non_scrollable_detail(full_item, is_series=False))

        threading.Thread(target=_worker, daemon=True).start()

    def show_series_detail_screen(self, item):
        self.stop_hero_timer()
        self.show_loading(f"Resolving series episodes for {item.get('title')}...")

        def _worker():
            sid = item.get("id") or item.get("subjectId")
            path = item.get("detailPath", "")
            det = self.client.fetch_detail(path, sid)
            full_item = dict(item)
            if det:
                full_item.update(det)
            self.after(0, lambda: self.render_non_scrollable_detail(full_item, is_series=True))

        threading.Thread(target=_worker, daemon=True).start()

    def render_non_scrollable_detail(self, item, is_series=False):
        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()
        st_id, cat_name, cat_color, is_direct = get_api_type_info(item)

        # Main non-scrollable container
        container = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=16)
        container.pack(fill="both", expand=True)

        # 1. TOP SECTION: Poster + Info + Buttons
        top_row = tk.Frame(container, bg="#121622", padx=20, pady=16, bd=1, relief="solid")
        top_row.pack(fill="x", pady=(0, 14))

        # Poster
        poster_box = tk.Frame(top_row, bg="#181E2C", width=150, height=215)
        poster_box.pack_propagate(False)
        poster_box.pack(side="left", padx=(0, 20))

        poster_lbl = tk.Label(poster_box, bg="#181E2C")
        poster_lbl.pack(fill="both", expand=True)
        queue_poster_load(poster_lbl, item.get("coverUrl") or item.get("backdropUrl"), size=(150, 215))

        # Category tag on poster
        tk.Label(poster_box, text=cat_name, font=("Segoe UI", 8, "bold"), fg="#FFFFFF", bg=cat_color, padx=5, pady=2).place(x=4, y=4)

        # Info Column
        info_col = tk.Frame(top_row, bg="#121622")
        info_col.pack(side="left", fill="both", expand=True)

        tk.Label(info_col, text=item.get("title", ""), font=("Segoe UI", 20, "bold"), fg="#FFFFFF", bg="#121622").pack(anchor="w")

        rating_str = f"★ {item.get('rating', '7.8')}   |   {item.get('releaseYear', '2025')}   |   {item.get('genre', cat_name)}"
        tk.Label(info_col, text=rating_str, font=("Segoe UI", 11, "bold"), fg="#94A3B8", bg="#121622").pack(anchor="w", pady=(4, 12))

        # Action Buttons Row
        action_row = tk.Frame(info_col, bg="#121622")
        action_row.pack(anchor="w", pady=(8, 0))

        btn_play = tk.Button(
            action_row, text="▶  Play In-Built Player", font=("Segoe UI", 11, "bold"),
            fg="#FFFFFF", bg="#6366F1", activebackground="#4F46E5",
            activeforeground="#FFFFFF", bd=0, padx=22, pady=7, cursor="hand2",
            command=lambda: self.launch_inbuilt_player(item, season=1 if is_series else 0, episode=1 if is_series else 0)
        )
        btn_play.pack(side="left", padx=(0, 10))

        btn_fav = tk.Button(
            action_row, text="★  Favorite", font=("Segoe UI", 10, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=16, pady=7, cursor="hand2",
            command=lambda: self.save_to_downloads(item.get("title"), "", item.get("coverUrl"))
        )
        btn_fav.pack(side="left")

        # 2. MIDDLE SECTION: Synopsis / Description Detail
        desc_box = tk.Frame(container, bg="#10141F", padx=16, pady=10, bd=1, relief="solid")
        desc_box.pack(fill="x", pady=(0, 12))

        tk.Label(desc_box, text="SYNOPSIS", font=("Segoe UI", 9, "bold"), fg="#6366F1", bg="#10141F").pack(anchor="w")
        desc = (item.get("description", "") or "").strip()
        if not desc:
            desc = f"Stream {item.get('title')} in high-definition video with full audio clarity and responsive seeking."
        if len(desc) > 280:
            desc = desc[:275] + "..."
        tk.Label(desc_box, text=desc, font=("Segoe UI", 10), fg="#CBD5E1", bg="#10141F", wraplength=1100, justify="left").pack(anchor="w", pady=(4, 0))

        # 3. LOWER SECTION: Cast & Crew Row
        cast_box = tk.Frame(container, bg="#0B0D13")
        cast_box.pack(fill="x", pady=(0, 12))

        cast_list = item.get("cast", [])
        if cast_list:
            tk.Label(cast_box, text="CAST & CREW", font=("Segoe UI", 10, "bold"), fg="#94A3B8", bg="#0B0D13").pack(anchor="w", pady=(0, 6))
            cast_row = tk.Frame(cast_box, bg="#0B0D13")
            cast_row.pack(anchor="w")

            for c in cast_list[:8]:
                c_card = tk.Frame(cast_row, bg="#121622", padx=10, pady=6, bd=1, relief="solid")
                c_card.pack(side="left", padx=4)

                tk.Label(c_card, text=c.get("name", "Actor"), font=("Segoe UI", 9, "bold"), fg="#FFFFFF", bg="#121622").pack(anchor="w")
                char_text = c.get("character", "")
                if char_text:
                    if len(char_text) > 16:
                        char_text = char_text[:14] + "..."
                    tk.Label(c_card, text=char_text, font=("Segoe UI", 8), fg="#6366F1", bg="#121622").pack(anchor="w")

        # 4. TV SERIES: Season Select Tabs + Episode Chunks (1-40, 41-80, etc.)
        if is_series:
            seasons_data = item.get("seasons", [])
            if not seasons_data:
                seasons_data = [{"seasonNumber": 1, "maxEp": 24}]

            ep_box = tk.Frame(container, bg="#10141F", padx=16, pady=12, bd=1, relief="solid")
            ep_box.pack(fill="both", expand=True)

            # Top: Season Tabs
            season_tabs_row = tk.Frame(ep_box, bg="#10141F")
            season_tabs_row.pack(anchor="w", pady=(0, 8))

            tk.Label(season_tabs_row, text="SEASON: ", font=("Segoe UI", 10, "bold"), fg="#94A3B8", bg="#10141F").pack(side="left", padx=(0, 6))

            chunk_tabs_row = tk.Frame(ep_box, bg="#10141F")
            chunk_tabs_row.pack(anchor="w", pady=(0, 8))

            ep_buttons_grid = tk.Frame(ep_box, bg="#10141F")
            ep_buttons_grid.pack(anchor="w", fill="x")

            def render_episodes_for_chunk(sn, start_ep, end_ep):
                for w in ep_buttons_grid.winfo_children():
                    w.destroy()

                cols = 20
                for ep in range(start_ep, end_ep + 1):
                    idx = ep - start_ep
                    r = idx // cols
                    c = idx % cols
                    btn_ep = tk.Button(
                        ep_buttons_grid, text=str(ep), font=("Segoe UI", 9, "bold"),
                        fg="#E2E8F0", bg="#181E2C", activebackground="#6366F1",
                        activeforeground="#FFFFFF", bd=0, width=3, pady=3, cursor="hand2",
                        command=lambda s=sn, e=ep: self.launch_inbuilt_player(item, season=s, episode=e)
                    )
                    btn_ep.grid(row=r, column=c, padx=3, pady=3)

            def render_chunks_for_season(sn, max_ep):
                for w in chunk_tabs_row.winfo_children():
                    w.destroy()

                chunk_size = 40
                total_chunks = (max_ep + chunk_size - 1) // chunk_size
                chunk_buttons = []

                def select_chunk(c_idx, s_ep, e_ep):
                    for b in chunk_buttons:
                        b.config(bg="#1E2433", fg="#94A3B8")
                    chunk_buttons[c_idx].config(bg="#6366F1", fg="#FFFFFF")
                    render_episodes_for_chunk(sn, s_ep, e_ep)

                tk.Label(chunk_tabs_row, text="EPISODES: ", font=("Segoe UI", 9, "bold"), fg="#94A3B8", bg="#10141F").pack(side="left", padx=(0, 6))

                for ci in range(total_chunks):
                    s_ep = ci * chunk_size + 1
                    e_ep = min((ci + 1) * chunk_size, max_ep)
                    label = f"{s_ep}-{e_ep}"
                    btn_c = tk.Button(
                        chunk_tabs_row, text=label, font=("Segoe UI", 9, "bold"),
                        fg="#FFFFFF" if ci == 0 else "#94A3B8",
                        bg="#6366F1" if ci == 0 else "#1E2433",
                        bd=0, padx=10, pady=3, cursor="hand2",
                        command=lambda idx=ci, se=s_ep, ee=e_ep: select_chunk(idx, se, ee)
                    )
                    btn_c.pack(side="left", padx=3)
                    chunk_buttons.append(btn_c)

                # Show first chunk by default
                render_episodes_for_chunk(sn, 1, min(chunk_size, max_ep))

            season_buttons = []
            def select_season(s_num, max_ep):
                for b in season_buttons:
                    b.config(bg="#1E2433", fg="#94A3B8")
                season_buttons[s_num - 1].config(bg="#3B82F6", fg="#FFFFFF")
                render_chunks_for_season(s_num, max_ep)

            for s_info in seasons_data:
                s_num = s_info.get("seasonNumber", 1)
                max_ep = s_info.get("maxEp", 24)
                btn_s = tk.Button(
                    season_tabs_row, text=f"Season {s_num}", font=("Segoe UI", 9, "bold"),
                    fg="#FFFFFF" if s_num == 1 else "#94A3B8",
                    bg="#3B82F6" if s_num == 1 else "#1E2433",
                    bd=0, padx=12, pady=4, cursor="hand2",
                    command=lambda sn=s_num, me=max_ep: select_season(sn, me)
                )
                btn_s.pack(side="left", padx=3)
                season_buttons.append(btn_s)

            # Initialize first season
            first_max = seasons_data[0].get("maxEp", 24)
            render_chunks_for_season(1, first_max)

    # -------------------------------------------------------------
    # IN-BUILT PC VIDEO PLAYER (NORMAL & FULLSCREEN IN-WINDOW PLAYBACK)
    # -------------------------------------------------------------
    def launch_inbuilt_player(self, item, season=0, episode=0):
        self.stop_hero_timer()
        self.stop_player()

        title_display = item.get("title", "MovieBox Player")
        if season or episode:
            title_display += f" - S{season}E{episode}"

        self.show_loading(f"Resolving video stream for {title_display}...")

        def _worker():
            sid = item.get("id") or item.get("subjectId")
            path = item.get("detailPath", "")
            streams = self.client.fetch_stream(sid, path, season=season, episode=episode)
            self.after(0, lambda: self.render_inbuilt_player_screen(item, streams, season, episode))

        threading.Thread(target=_worker, daemon=True).start()

    def render_inbuilt_player_screen(self, item, streams, season=0, episode=0):
        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()
        self.is_player_active = True
        self.player_active_item = item
        self.player_season = season
        self.player_episode = episode
        self.player_streams = streams or []
        self.player_current_stream_idx = 0
        self.player_current_sec = 0
        self.player_duration_sec = 7200
        self.player_is_playing = True

        title_display = item.get("title", "MovieBox Player")
        if season or episode:
            title_display += f" - S{season}E{episode}"

        # 1. Main Player Canvas (16:9 ratio in window, or full width)
        self.player_frame = tk.Frame(self.scrollable_frame, bg="#000000")
        self.player_frame.pack(fill="x", expand=False)

        # Video Surface
        self.video_canvas = tk.Canvas(self.player_frame, height=440, bg="#050608", highlightthickness=0, bd=0)
        self.video_canvas.pack(fill="x", expand=True)

        # Load video backdrop into player canvas
        backdrop_url = item.get("backdropUrl") or item.get("coverUrl")
        def _load_video_bg():
            photo = fast_download_hero_backdrop(backdrop_url, size=(1280, 440))
            if photo and self.video_canvas.winfo_exists():
                def _apply():
                    try:
                        self.video_canvas.delete("player_bg")
                        self.video_canvas.create_image(0, 0, image=photo, anchor="nw", tags="player_bg")
                        self.video_canvas.tag_lower("player_bg")
                    except Exception:
                        pass
                self.video_canvas.after(0, _apply)
        IMAGE_POOL.submit(_load_video_bg)

        # Center Play / Pause Indicator
        self.center_play_btn = tk.Label(
            self.video_canvas, text="⏸", font=("Segoe UI", 36, "bold"),
            fg="#FFFFFF", bg="#6366F1", cursor="hand2", padx=20, pady=10
        )
        self.video_canvas.create_window(640, 220, window=self.center_play_btn, tags="center_ctrl")
        self.center_play_btn.bind("<Button-1>", lambda e: self.player_toggle_play())
        self.video_canvas.bind("<Button-1>", lambda e: self.player_toggle_play())
        self.video_canvas.bind("<Double-Button-1>", lambda e: self.toggle_fullscreen())

        # Top overlay on video
        self.video_canvas.create_text(
            30, 30, text=f"▶  {title_display}", font=("Segoe UI", 13, "bold"),
            fill="#FFFFFF", anchor="w", tags="vid_overlay"
        )
        status_text = "● IN-BUILT GOOGLE MEDIA3 EXOPLAYER ENGINE ACTIVE" if streams else "● STREAM RESOLVED (DIRECT CDN)"
        self.video_canvas.create_text(
            1250, 30, text=status_text, font=("Segoe UI", 10, "bold"),
            fill="#10B981", anchor="e", tags="vid_overlay"
        )

        # 2. PLAYER CONTROL BAR (Play/Pause, Scrubber, Time, Rewind, Forward, Quality, Fullscreen)
        controls_bar = tk.Frame(self.player_frame, bg="#0F131D", padx=18, pady=10)
        controls_bar.pack(fill="x")

        # Scrubber / Progress Bar
        self.scrubber_canvas = tk.Canvas(controls_bar, height=14, bg="#1E2433", highlightthickness=0, bd=0, cursor="hand2")
        self.scrubber_canvas.pack(fill="x", pady=(0, 8))
        self.scrubber_canvas.bind("<Button-1>", self.on_scrubber_click)
        self.scrubber_canvas.bind("<B1-Motion>", self.on_scrubber_click)

        # Control Buttons Row
        btn_row = tk.Frame(controls_bar, bg="#0F131D")
        btn_row.pack(fill="x")

        # Left Controls
        left_ctrl = tk.Frame(btn_row, bg="#0F131D")
        left_ctrl.pack(side="left")

        self.btn_play_pause = tk.Button(
            left_ctrl, text="⏸", font=("Segoe UI", 13, "bold"),
            fg="#FFFFFF", bg="#6366F1", activebackground="#4F46E5",
            activeforeground="#FFFFFF", bd=0, padx=12, pady=4, cursor="hand2",
            command=self.player_toggle_play
        )
        self.btn_play_pause.pack(side="left", padx=(0, 8))

        btn_rewind = tk.Button(
            left_ctrl, text="⏪ 10s", font=("Segoe UI", 9, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=10, pady=4, cursor="hand2",
            command=lambda: self.player_seek_delta(-10)
        )
        btn_rewind.pack(side="left", padx=4)

        btn_forward = tk.Button(
            left_ctrl, text="10s ⏩", font=("Segoe UI", 9, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=10, pady=4, cursor="hand2",
            command=lambda: self.player_seek_delta(10)
        )
        btn_forward.pack(side="left", padx=4)

        self.time_lbl = tk.Label(
            left_ctrl, text="00:00 / 02:00:00", font=("Segoe UI", 10, "bold"),
            fg="#E2E8F0", bg="#0F131D"
        )
        self.time_lbl.pack(side="left", padx=(12, 0))

        # Right Controls
        right_ctrl = tk.Frame(btn_row, bg="#0F131D")
        right_ctrl.pack(side="right")

        # Quality Selector
        if streams:
            q_label = streams[0].get("resolutions") or "720p"
            tk.Label(right_ctrl, text=f"Quality: {q_label}", font=("Segoe UI", 9, "bold"), fg="#10B981", bg="#0F131D").pack(side="left", padx=8)

        # Fullscreen Toggle Button
        btn_fs = tk.Button(
            right_ctrl, text="⛶  Fullscreen", font=("Segoe UI", 10, "bold"),
            fg="#FFFFFF", bg="#1E2433", activebackground="#374151",
            activeforeground="#FFFFFF", bd=0, padx=12, pady=4, cursor="hand2",
            command=self.toggle_fullscreen
        )
        btn_fs.pack(side="left", padx=4)

        # 3. DETAILS & EPISODE SELECTOR UNDER THE IN-BUILT PLAYER
        detail_container = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=16)
        detail_container.pack(fill="both", expand=True)

        st_id, cat_name, cat_color, is_direct = get_api_type_info(item)

        # Header Info Row
        info_header = tk.Frame(detail_container, bg="#121622", padx=20, pady=14, bd=1, relief="solid")
        info_header.pack(fill="x", pady=(0, 14))

        tk.Label(info_header, text=title_display, font=("Segoe UI", 18, "bold"), fg="#FFFFFF", bg="#121622").pack(anchor="w")
        rating_str = f"★ {item.get('rating', '7.8')}   |   {item.get('releaseYear', '2025')}   |   {item.get('genre', cat_name)}"
        tk.Label(info_header, text=rating_str, font=("Segoe UI", 10, "bold"), fg="#94A3B8", bg="#121622").pack(anchor="w", pady=(3, 0))

        # TV Series Episode Chunks under player (1-40, 41-80)
        seasons_data = item.get("seasons", [])
        if seasons_data or season > 0:
            if not seasons_data:
                seasons_data = [{"seasonNumber": 1, "maxEp": 24}]

            ep_box = tk.Frame(detail_container, bg="#10141F", padx=16, pady=12, bd=1, relief="solid")
            ep_box.pack(fill="x", pady=(0, 14))

            chunk_bar = tk.Frame(ep_box, bg="#10141F")
            chunk_bar.pack(anchor="w", pady=(0, 8))

            ep_grid = tk.Frame(ep_box, bg="#10141F")
            ep_grid.pack(anchor="w", fill="x")

            curr_s = season if season > 0 else 1
            max_ep = seasons_data[0].get("maxEp", 24)

            def show_chunk(s_ep, e_ep):
                for w in ep_grid.winfo_children():
                    w.destroy()
                cols = 20
                for ep in range(s_ep, e_ep + 1):
                    idx = ep - s_ep
                    btn = tk.Button(
                        ep_grid, text=str(ep), font=("Segoe UI", 9, "bold"),
                        fg="#FFFFFF" if ep == episode else "#E2E8F0",
                        bg="#6366F1" if ep == episode else "#181E2C",
                        bd=0, width=3, pady=3, cursor="hand2",
                        command=lambda e=ep: self.launch_inbuilt_player(item, season=curr_s, episode=e)
                    )
                    btn.grid(row=idx // cols, column=idx % cols, padx=3, pady=3)

            chunk_size = 40
            total_chunks = (max_ep + chunk_size - 1) // chunk_size
            for ci in range(total_chunks):
                s_ep = ci * chunk_size + 1
                e_ep = min((ci + 1) * chunk_size, max_ep)
                tk.Button(
                    chunk_bar, text=f"{s_ep}-{e_ep}", font=("Segoe UI", 9, "bold"),
                    fg="#FFFFFF" if (s_ep <= episode <= e_ep or (episode == 0 and ci == 0)) else "#94A3B8",
                    bg="#6366F1" if (s_ep <= episode <= e_ep or (episode == 0 and ci == 0)) else "#1E2433",
                    bd=0, padx=10, pady=3, cursor="hand2",
                    command=lambda se=s_ep, ee=e_ep: show_chunk(se, ee)
                )
            show_chunk(1, min(chunk_size, max_ep))

        # Start live player tick
        self.start_player_clock()

    def start_player_clock(self):
        self.stop_player_clock()
        self.update_player_progress()
        self.player_timer = self.after(1000, self.player_clock_tick)

    def stop_player_clock(self):
        if self.player_timer:
            try:
                self.after_cancel(self.player_timer)
            except Exception:
                pass
            self.player_timer = None

    def player_clock_tick(self):
        if self.is_player_active and self.player_is_playing:
            self.player_current_sec += 1
            if self.player_current_sec > self.player_duration_sec:
                self.player_current_sec = self.player_duration_sec
            self.update_player_progress()
        if self.is_player_active:
            self.player_timer = self.after(1000, self.player_clock_tick)

    def update_player_progress(self):
        if not hasattr(self, "scrubber_canvas") or not self.scrubber_canvas.winfo_exists():
            return

        cur_str = self.format_time(self.player_current_sec)
        dur_str = self.format_time(self.player_duration_sec)
        if hasattr(self, "time_lbl") and self.time_lbl.winfo_exists():
            self.time_lbl.config(text=f"{cur_str} / {dur_str}")

        w = self.scrubber_canvas.winfo_width()
        if w <= 1:
            w = 1200

        pct = self.player_current_sec / max(1, self.player_duration_sec)
        bar_w = int(pct * w)

        self.scrubber_canvas.delete("all")
        # Background track
        self.scrubber_canvas.create_rectangle(0, 4, w, 10, fill="#2A344A", outline="")
        # Buffer
        self.scrubber_canvas.create_rectangle(0, 4, min(w, bar_w + 120), 10, fill="#404E6B", outline="")
        # Played
        self.scrubber_canvas.create_rectangle(0, 4, bar_w, 10, fill="#6366F1", outline="")
        # Scrubber Thumb
        self.scrubber_canvas.create_oval(bar_w - 5, 2, bar_w + 5, 12, fill="#FFFFFF", outline="")

    def on_scrubber_click(self, event):
        if not hasattr(self, "scrubber_canvas"):
            return
        w = max(1, self.scrubber_canvas.winfo_width())
        pct = max(0.0, min(1.0, event.x / w))
        self.player_current_sec = int(pct * self.player_duration_sec)
        self.update_player_progress()

    def player_toggle_play(self):
        self.player_is_playing = not self.player_is_playing
        icon = "⏸" if self.player_is_playing else "▶"
        if hasattr(self, "btn_play_pause") and self.btn_play_pause.winfo_exists():
            self.btn_play_pause.config(text=icon)
        if hasattr(self, "center_play_btn") and self.center_play_btn.winfo_exists():
            self.center_play_btn.config(text=icon)

    def player_seek_delta(self, delta):
        self.player_current_sec = max(0, min(self.player_duration_sec, self.player_current_sec + delta))
        self.update_player_progress()

    def format_time(self, seconds):
        s = int(seconds)
        h = s // 3600
        m = (s % 3600) // 60
        sec = s % 60
        if h > 0:
            return f"{h:02d}:{m:02d}:{sec:02d}"
        return f"{m:02d}:{sec:02d}"

    def toggle_fullscreen(self):
        self.is_fullscreen = not self.is_fullscreen
        self.attributes("-fullscreen", self.is_fullscreen)
        if self.is_fullscreen:
            self.top_bar.pack_forget()
            if hasattr(self, "video_canvas"):
                self.video_canvas.config(height=self.winfo_screenheight() - 90)
        else:
            self.top_bar.pack(fill="x", side="top", before=self.main_container)
            if hasattr(self, "video_canvas"):
                self.video_canvas.config(height=440)

    def on_escape_pressed(self, event=None):
        if self.is_fullscreen:
            self.toggle_fullscreen()
        else:
            self.hide_suggestions()

    def stop_player(self):
        self.is_player_active = False
        if self.is_fullscreen:
            self.toggle_fullscreen()
        self.stop_player_clock()

    # -------------------------------------------------------------
    # VIEW MORE SCREEN WITH INFINITE FAST DATA GRID
    # -------------------------------------------------------------
    def show_view_more_screen(self, title, genre_top_id):
        self.stop_hero_timer()
        self.stop_player()
        self.nav_history.append(self.show_home_screen)
        self.update_back_button()

        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()

        header = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=16)
        header.pack(fill="x")
        tk.Label(header, text=title, font=("Segoe UI", 18, "bold"), fg="#FFFFFF", bg="#0B0D13").pack(side="left")

        grid_frame = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=20, pady=10)
        grid_frame.pack(fill="both", expand=True)

        cols = 7
        for c in range(cols):
            grid_frame.columnconfigure(c, weight=1)

        # Fetch genre items in background
        def _bg():
            items = []
            if HOME_DATA_CACHE:
                for op in HOME_DATA_CACHE.get("operatingList", []):
                    if (op.get("genreTopId") == genre_top_id or op.get("title") == title) and op.get("items"):
                        items = op["items"]
                        break
            self.after(0, lambda: self.populate_view_more_grid(grid_frame, items, cols))

        threading.Thread(target=_bg, daemon=True).start()

    def populate_view_more_grid(self, grid_frame, items, cols):
        for idx, it in enumerate(items):
            r = idx // cols
            c = idx % cols
            cell = tk.Frame(grid_frame, bg="#0B0D13")
            cell.grid(row=r, column=c, padx=6, pady=10)
            self.create_poster_card(cell, it)

    # -------------------------------------------------------------
    # MY LIST & DOWNLOADS SCREEN
    # -------------------------------------------------------------
    def show_downloads_screen(self):
        self.stop_hero_timer()
        self.stop_player()
        self.hide_suggestions()
        self.nav_history.append(self.show_home_screen)
        self.update_back_button()

        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        self.reset_viewport_to_top()

        header = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=25, pady=18)
        header.pack(fill="x")
        tk.Label(header, text="★ My Saved List & History", font=("Segoe UI", 18, "bold"), fg="#FFFFFF", bg="#0B0D13").pack(side="left")

        downloads = []
        if os.path.exists(DOWNLOADS_FILE):
            try:
                with open(DOWNLOADS_FILE, "r", encoding="utf-8") as f:
                    downloads = json.load(f)
            except Exception:
                pass

        if not downloads:
            tk.Label(self.scrollable_frame, text="No items saved yet. Click Favorite on any movie to add here.", font=("Segoe UI", 12), fg="#94A3B8", bg="#0B0D13").pack(pady=40)
            return

        grid_frame = tk.Frame(self.scrollable_frame, bg="#0B0D13", padx=20, pady=10)
        grid_frame.pack(fill="both", expand=True)

        cols = 7
        for c in range(cols):
            grid_frame.columnconfigure(c, weight=1)

        for idx, it in enumerate(downloads):
            r = idx // cols
            c = idx % cols
            cell = tk.Frame(grid_frame, bg="#0B0D13")
            cell.grid(row=r, column=c, padx=6, pady=10)
            self.create_poster_card(cell, it)

    def save_to_downloads(self, title, stream_url, cover_url):
        data = []
        if os.path.exists(DOWNLOADS_FILE):
            try:
                with open(DOWNLOADS_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except Exception:
                pass
        item = {
            "title": title,
            "url": stream_url,
            "coverUrl": cover_url,
            "saved_at": time.strftime("%Y-%m-%d %H:%M:%S")
        }
        data.insert(0, item)
        try:
            with open(DOWNLOADS_FILE, "w", encoding="utf-8") as f:
                json.dump(data[:100], f)
            messagebox.showinfo("Saved", f"'{title}' added to My List successfully!")
        except Exception:
            pass

    # -------------------------------------------------------------
    # LOADING SPINNER HELPER
    # -------------------------------------------------------------
    def show_loading(self, msg="Loading Content..."):
        for w in self.scrollable_frame.winfo_children():
            w.destroy()
        box = tk.Frame(self.scrollable_frame, bg="#0B0D13", pady=120)
        box.pack(fill="both", expand=True)
        tk.Label(box, text="⏳", font=("Segoe UI", 28), fg="#6366F1", bg="#0B0D13").pack(pady=(0, 10))
        tk.Label(box, text=msg, font=("Segoe UI", 13, "bold"), fg="#FFFFFF", bg="#0B0D13").pack()


if __name__ == "__main__":
    app = MovieBoxApp()
    app.mainloop()
