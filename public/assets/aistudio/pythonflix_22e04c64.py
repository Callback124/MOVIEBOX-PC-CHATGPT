"""PythonFlix - a movie & TV discovery desktop app for Windows 10/11.

Dark streaming-style interface: featured banner, scrolling poster rows,
tabs (Home / Movies / TV Series / Anime / Kids), search and a watchlist.
All data comes from the free TMDB API (https://www.themoviedb.org/).
"""
import io
import json
import os
import queue
import threading
import tkinter as tk
import traceback
from concurrent.futures import ThreadPoolExecutor
from tkinter import ttk

import requests
from PIL import Image, ImageOps, ImageTk

from player_page import PlayerPage

APP_NAME = "PythonFlix"
API = "https://api.themoviedb.org/3"
IMG = "https://image.tmdb.org/t/p/"
REGION = "IN"  # default country for "Available on"; change it in Settings
REGIONS = ["IN", "US", "GB", "CA", "AU", "AE", "SG", "DE", "FR", "JP", "BR"]

BG, PANEL, CARD = "#0b0c10", "#161922", "#1c2029"
FG, MUTED, ACCENT = "#f1f3f8", "#8a90a2", "#7c5cff"

CONF_DIR = os.path.join(os.getenv("APPDATA", os.path.expanduser("~")), APP_NAME)
CONF_FILE = os.path.join(CONF_DIR, "config.json")

LANG = {"hi": "Hindi", "en": "English", "ta": "Tamil", "te": "Telugu",
        "ml": "Malayalam", "kn": "Kannada", "ja": "Japanese", "ko": "Korean",
        "bn": "Bengali", "mr": "Marathi", "pa": "Punjabi", "es": "Spanish"}

# tab -> (media type, fixed discover filters, show genre filter?)
KINDS = {
    "Movies": ("movie", {}, True),
    "TV Series": ("tv", {}, True),
    "Anime": ("tv", {"with_genres": "16", "with_original_language": "ja"}, False),
    "Kids": ("movie", {"with_genres": "16|10751"}, False),
}


# ---------------------------------------------------------------- storage
def load_conf():
    try:
        with open(CONF_FILE, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        data = {}
    data.setdefault("api_key", "")
    data.setdefault("watchlist", {})  # "media:id" -> brief dict
    data.setdefault("region", REGION)
    return data


def save_conf(conf):
    os.makedirs(CONF_DIR, exist_ok=True)
    with open(CONF_FILE, "w", encoding="utf-8") as f:
        json.dump(conf, f, indent=2)


# -------------------------------------------------------------------- API
class Tmdb:
    def __init__(self, key):
        self.key = key.strip()
        self.session = requests.Session()
        self.cache = {}
        self.lock = threading.Lock()

    def get(self, path, **params):
        headers = {}
        if len(self.key) > 40:  # v4 read-access token
            headers["Authorization"] = "Bearer " + self.key
        else:
            params["api_key"] = self.key
        r = self.session.get(API + path, params=params, headers=headers, timeout=15)
        if r.status_code == 401:
            raise PermissionError("Invalid TMDB API key. Please restart and enter it again.")
        r.raise_for_status()
        return r.json()

    def image(self, path, size="w185"):
        url = IMG + size + path
        with self.lock:
            hit = self.cache.get(url)
        if hit is None:
            r = self.session.get(url, timeout=15)
            r.raise_for_status()
            hit = r.content
            with self.lock:
                self.cache[url] = hit
        return hit

    def genres(self):
        return {m: {g["id"]: g["name"] for g in self.get(f"/genre/{m}/list")["genres"]}
                for m in ("movie", "tv")}

    def discover(self, media, page=1, **extra):
        p = {"sort_by": "popularity.desc", "vote_count.gte": 20, "page": page}
        p.update(extra)
        return self.get(f"/discover/{media}", **p)

    def search(self, q):
        res = self.get("/search/multi", query=q)["results"]
        return [brief(m) for m in res if m.get("media_type") in ("movie", "tv")]

    def detail(self, media, mid):
        return self.get(f"/{media}/{mid}", append_to_response="credits,videos,watch/providers")


def brief(m, media=None):
    media = media or m.get("media_type") or "movie"
    gids = m.get("genre_ids") or [g["id"] for g in m.get("genres", [])]
    return {"id": m["id"], "media": media,
            "title": m.get("title") or m.get("name") or "?",
            "poster_path": m.get("poster_path"),
            "backdrop_path": m.get("backdrop_path"),
            "year": (m.get("release_date") or m.get("first_air_date") or "")[:4],
            "rating": m.get("vote_average") or 0,
            "lang": m.get("original_language", ""),
            "genre_ids": gids,
            "overview": (m.get("overview") or "")[:400]}


def compose_banner(data, w, h):
    """Backdrop cropped to w x h with a dark fade on the left for text."""
    im = ImageOps.fit(Image.open(io.BytesIO(data)).convert("RGB"), (w, h))
    mask = Image.new("L", (w, 1))
    mask.putdata([int(235 * max(0.0, 1 - x / (w * 0.7))) for x in range(w)])
    return Image.composite(Image.new("RGB", (w, h), (11, 12, 16)), im, mask.resize((w, h)))


# ---------------------------------------------------------------- widgets
class VScroll(ttk.Frame):
    """Vertically scrollable page; put content in .body"""
    def __init__(self, parent):
        super().__init__(parent)
        # no visible scrollbar: scroll with the mouse wheel / touchpad
        self.canvas = tk.Canvas(self, bg=BG, highlightthickness=0)
        self.body = tk.Frame(self.canvas, bg=BG)
        win = self.canvas.create_window((0, 0), window=self.body, anchor="nw")
        self.body.bind("<Configure>", lambda e: self.canvas.configure(
            scrollregion=self.canvas.bbox("all")))
        self.canvas.bind("<Configure>", lambda e: self.canvas.itemconfigure(win, width=e.width))
        self.canvas.pack(fill="both", expand=True)


class Grid(tk.Frame):
    """Fixed (non-scrolling) poster grid: shows only as many cards as fit on screen."""
    GAP, TITLE = 8, 50

    def __init__(self, parent, app, on_change=None):
        super().__init__(parent, bg=BG)
        self.grid_propagate(False)
        self.app, self.on_change = app, on_change
        self.items, self.empty, self.state, self.job = [], "", None, None
        self.bind("<Configure>", self._cfg)

    def layout(self):
        if self.winfo_width() <= 1:
            self.update_idletasks()
        w, h = max(self.winfo_width(), 600), max(self.winfo_height(), 300)
        cols = max(3, w // 170)
        cw = (w - cols * 2 * self.GAP) // cols
        ch = int(cw * 1.5)
        cell = ch + self.TITLE + 2 * self.GAP
        rows = max(1, h // cell)
        if cell > h:  # very short window: shrink the cards to fit one row
            ch = max(100, h - self.TITLE - 2 * self.GAP)
            cw = int(ch / 1.5)
        return cols, rows, cw, ch

    def fit(self):
        """Remember the current layout and return how many cards fit."""
        self.state = self.layout()
        return self.state[0] * self.state[1]

    def _cfg(self, e):
        if self.job:
            self.after_cancel(self.job)
        self.job = self.after(250, self._relayout)

    def _relayout(self):
        self.job = None
        if self.state and self.on_change and self.layout() != self.state:
            self.on_change()

    def show(self, items, empty="Nothing here yet."):
        self.items, self.empty = items, empty
        self.render()

    def render(self):
        for w in self.winfo_children():
            w.destroy()
        if not self.items:
            tk.Label(self, text=self.empty, bg=BG, fg=MUTED,
                     font=("Segoe UI", 12)).grid(padx=24, pady=24)
            return
        self.state = self.layout()
        cols, rows, cw, ch = self.state
        for i, m in enumerate(self.items[:cols * rows]):
            self.app.card(self, m, cw, ch).grid(
                row=i // cols, column=i % cols, padx=self.GAP, pady=self.GAP, sticky="n")


class Row(tk.Frame):
    """Titled horizontal poster strip with scroll buttons."""
    def __init__(self, parent, app, title):
        super().__init__(parent, bg=BG)
        self.app = app
        head = tk.Frame(self, bg=BG)
        head.pack(fill="x", padx=24)
        tk.Label(head, text=title, bg=BG, fg=FG, font=("Segoe UI", 15, "bold")).pack(side="left")
        for txt, d in (("›", 1), ("‹", -1)):
            tk.Button(head, text=txt, bg=PANEL, fg=FG, relief="flat", width=3,
                      activebackground=ACCENT, activeforeground=FG,
                      command=lambda d=d: self.canvas.xview_scroll(d * 18, "units")
                      ).pack(side="right", padx=2)
        self.canvas = tk.Canvas(self, bg=BG, height=262, highlightthickness=0,
                                xscrollincrement=40)
        self.canvas.pack(fill="x", padx=18, pady=(6, 14))
        self.inner = tk.Frame(self.canvas, bg=BG)
        self.canvas.create_window((0, 0), window=self.inner, anchor="nw")
        self.inner.bind("<Configure>", lambda e: self.canvas.configure(
            scrollregion=self.canvas.bbox("all")))
        self.note("Loading...")

    def note(self, text):
        for w in self.inner.winfo_children():
            w.destroy()
        tk.Label(self.inner, text=text, bg=BG, fg=MUTED).pack(padx=8, pady=70)

    def set_items(self, items):
        for w in self.inner.winfo_children():
            w.destroy()
        if not items:
            self.note("Nothing to show")
            return
        for m in items:
            self.app.card(self.inner, m, 130, 195).pack(side="left", padx=6)


class Hero(tk.Canvas):
    """Featured banner that rotates through trending titles."""
    H = 442

    def __init__(self, parent, app):
        super().__init__(parent, bg=BG, height=self.H, highlightthickness=0)
        self.app = app
        self.items, self.i, self.W = [], 0, 1000
        self.cache, self.thumbs, self.btns, self.job, self.rjob = {}, {}, [], None, None
        self.bind("<Configure>", self._resized)

    def _resized(self, e):
        if self.rjob:
            self.after_cancel(self.rjob)
        self.rjob = self.after(150, self._redo)

    def _redo(self):
        self.rjob = None
        if self.items and (abs(self.winfo_width() - self.W) > 2
                           or abs(self.winfo_height() - self.H) > 2):
            self.cache.clear()
            self.show(self.i)

    def set_items(self, items):
        self.items = items
        self.thumbs, self.cache = {}, {}
        for idx, m in enumerate(items):
            if m.get("poster_path"):
                self.app.bg(lambda p=m["poster_path"]: self.app.api.image(p, "w92"),
                            lambda data, idx=idx: self._thumb(idx, data), quiet=True)
        self.show(0)

    def _thumb(self, idx, data):
        self.thumbs[idx] = ImageTk.PhotoImage(
            Image.open(io.BytesIO(data)).resize((60, 90)))
        self.draw(self.cache.get((self.i, self.W, self.H)))

    def show(self, i):
        if not self.items:
            return
        self.i = i % len(self.items)
        if self.winfo_width() > 100:
            self.W = self.winfo_width()  # always span the full page width
        if self.winfo_height() > 100:
            self.H = self.winfo_height()  # and the full visible height
        if self.job:
            self.after_cancel(self.job)
        self.job = self.after(7000, lambda: self.show(self.i + 1))
        key = (self.i, self.W, self.H)
        if key in self.cache:
            self.draw(self.cache[key])
            return
        self.draw(None)
        m = self.items[self.i]
        w, h = self.W, self.H
        self.app.bg(lambda: compose_banner(self.app.api.image(m["backdrop_path"], "w1280"), w, h),
                    lambda im, key=key: self._got(key, im), quiet=True)

    def _got(self, key, im):
        self.cache[key] = ImageTk.PhotoImage(im)
        if key == (self.i, self.W, self.H):
            self.draw(self.cache[key])

    def draw(self, photo):
        if not self.items:
            return
        for b in self.btns:
            b.destroy()
        self.btns = []
        self.delete("all")
        m = self.items[self.i]
        if photo:
            self.create_image(0, 0, anchor="nw", image=photo)
        y0 = max(60, int(self.H * 0.22))
        self.create_text(40, y0 + 10, anchor="w", width=520, text=m["title"], fill=FG,
                         font=("Segoe UI", 30, "bold"))
        names = self.app.gnames
        genres = ", ".join(names[g] for g in m["genre_ids"][:3] if g in names)
        meta = f"★ {m['rating']:.1f}   |   {m['year']}   |   {genres}"
        self.create_text(40, y0 + 100, anchor="w", text=meta, fill="#c9cdda", font=("Segoe UI", 12))
        self.create_text(40, y0 + 195, anchor="w", width=500, fill="#d5d8e2",
                         text=(m["overview"][:220] + ("..." if len(m["overview"]) > 220 else "")),
                         font=("Segoe UI", 10))
        for x, text, cmd in ((40, "▶  Details", lambda: self.app.open_detail(m["media"], m["id"])),
                             (160, "Trailer", lambda: self.app.play_trailer(m["media"], m["id"]))):
            b = tk.Button(self, text=text, bg=ACCENT if x == 40 else PANEL, fg=FG, relief="flat",
                          font=("Segoe UI", 10, "bold"), padx=16, pady=6,
                          activebackground="#5f43e0", activeforeground=FG, cursor="hand2",
                          command=cmd)
            self.btns.append(b)
            self.create_window(x, min(self.H - 50, y0 + 300), anchor="w", window=b)
        n = len(self.items)
        x0 = self.W - 20 - n * 70
        for idx in range(n):
            x, y = x0 + idx * 70, self.H - 130
            if idx in self.thumbs:
                self.create_image(x, y, anchor="nw", image=self.thumbs[idx], tags=(f"t{idx}",))
            if idx == self.i:
                self.create_rectangle(x - 2, y - 2, x + 62, y + 92, outline=ACCENT, width=2)
            self.tag_bind(f"t{idx}", "<Button-1>", lambda e, idx=idx: self.show(idx))


class Browse(ttk.Frame):
    """One-screen poster grid for Movies / TV Series / Anime / Kids."""
    PER = 20  # TMDB page size

    def __init__(self, parent, app, kind):
        super().__init__(parent)
        self.app, self.kind = app, kind
        self.media, self.fixed, self.has_genre = KINDS[kind]
        self.start, self.cap, self.total, self.loaded, self.tok = 0, 1, 0, False, 0
        bar = ttk.Frame(self)
        bar.pack(fill="x", padx=18, pady=8)
        self.gvar = tk.StringVar(value="All genres")
        if self.has_genre:
            self.cb = ttk.Combobox(bar, textvariable=self.gvar, state="readonly", width=20,
                                   values=["All genres"])
            self.cb.pack(side="left")
            self.cb.bind("<<ComboboxSelected>>", lambda e: self.go(0))
        ttk.Button(bar, text="\u2039 Prev", command=lambda: self.go(self.start - self.cap)
                   ).pack(side="left", padx=(20, 4))
        self.lbl = ttk.Label(bar, text="Page 1")
        self.lbl.pack(side="left", padx=6)
        ttk.Button(bar, text="Next \u203a", command=lambda: self.go(self.start + self.cap)
                   ).pack(side="left")
        self.grid_ = Grid(self, app, on_change=lambda: self.go(self.start))
        self.grid_.pack(fill="both", expand=True)

    def on_show(self):
        if self.has_genre:
            self.cb.configure(values=["All genres"] + sorted(self.app.gmap.get(self.media, {})))
        if not self.loaded:
            self.loaded = True
            self.go(0)

    def go(self, start):
        cap = self.grid_.fit()
        start = max(0, start - start % cap)
        if self.total and start >= self.total:
            return
        self.cap, self.start = cap, start
        self.tok += 1
        tok = self.tok
        extra = dict(self.fixed)
        g = self.app.gmap.get(self.media, {}).get(self.gvar.get())
        if self.has_genre and g:
            extra["with_genres"] = g
        media, per = self.media, self.PER
        first, last = start // per + 1, (start + cap - 1) // per + 1

        def work():
            items, total = [], 0
            for p in range(first, min(last, 500) + 1):
                res = self.app.api.discover(media, p, **extra)
                total = min(res.get("total_results", 0), 10000)
                items += [brief(m, media) for m in res["results"]]
                if p >= res.get("total_pages", 1):
                    break
            off = start - (first - 1) * per
            return items[off:off + cap], total

        def done(r):
            if tok != self.tok:
                return  # a newer request replaced this one
            items, self.total = r
            self.lbl.configure(text=f"Page {start // cap + 1} of {max(1, -(-self.total // cap))}")
            self.grid_.show(items, "No titles found.")
        self.app.bg(work, done)


class LocalPage(ttk.Frame):
    """One-screen grid for a local list (search results, My List) with page buttons."""
    def __init__(self, parent, app):
        super().__init__(parent)
        self.items, self.empty, self.p, self.cap = [], "", 0, 1
        bar = ttk.Frame(self)
        bar.pack(fill="x", padx=18, pady=8)
        self.title = ttk.Label(bar, text="", font=("Segoe UI", 12, "bold"))
        self.title.pack(side="left")
        ttk.Button(bar, text="Next \u203a", command=lambda: self.turn(1)).pack(side="right")
        self.lbl = ttk.Label(bar, text="")
        self.lbl.pack(side="right", padx=6)
        ttk.Button(bar, text="\u2039 Prev", command=lambda: self.turn(-1)).pack(side="right")
        self.grid_ = Grid(self, app, on_change=self.refit)
        self.grid_.pack(fill="both", expand=True)

    def set(self, items, empty, title=""):
        self.items, self.empty, self.p = items, empty, 0
        self.title.configure(text=title)
        self.refit()

    def turn(self, d):
        self.p += d
        self.refit()

    def refit(self):
        self.cap = self.grid_.fit()
        pages = max(1, -(-len(self.items) // self.cap))
        self.p = min(max(self.p, 0), pages - 1)
        self.lbl.configure(text=f"Page {self.p + 1} of {pages}")
        self.grid_.show(self.items[self.p * self.cap:(self.p + 1) * self.cap], self.empty)


class DetailPage(VScroll):
    """Title details shown inside the app (no extra window)."""
    def __init__(self, parent, app):
        super().__init__(parent)
        self.app = app

    def render(self, media, d):
        app = self.app
        for w in self.body.winfo_children():
            w.destroy()
        self.canvas.yview_moveto(0)
        info = brief(d, media)
        tk.Button(self.body, text="\u2039 Back", command=app.back, bg=PANEL, fg=FG, relief="flat",
                  padx=14, pady=6, cursor="hand2", activebackground=ACCENT, activeforeground=FG,
                  font=("Segoe UI", 10)).pack(anchor="w", padx=24, pady=(16, 0))
        wrap = tk.Frame(self.body, bg=BG)
        wrap.pack(fill="x", padx=24, pady=16)
        left = tk.Label(wrap, bg=CARD, text="...", fg=MUTED, width=34, height=20)
        left.pack(side="left", anchor="n")
        right = tk.Frame(wrap, bg=BG)
        right.pack(side="left", fill="both", expand=True, padx=(28, 0))

        def lab(text, size=10, color=FG, bold=False, pad=2):
            tk.Label(right, text=text, bg=BG, fg=color, justify="left", anchor="w", wraplength=640,
                     font=("Segoe UI", size, "bold" if bold else "normal")).pack(anchor="w", pady=pad)

        lab(f"{info['title']} ({info['year']})", 20, bold=True)
        bits = [f"\u2605 {info['rating']:.1f}", "TV Series" if media == "tv" else "Movie"]
        if media == "tv":
            if d.get("number_of_seasons"):
                bits.append(f"{d['number_of_seasons']} season(s)")
        elif d.get("runtime"):
            bits.append(f"{d['runtime']} min")
        genres = ", ".join(g["name"] for g in d.get("genres", []))
        if genres:
            bits.append(genres)
        lab("   |   ".join(bits), 10, MUTED)
        if d.get("tagline"):
            lab(d["tagline"], 10, ACCENT)
        lab(d.get("overview") or "No description available.", 11, pad=8)
        cast = ", ".join(c["name"] for c in d.get("credits", {}).get("cast", [])[:8])
        if cast:
            lab("Cast: " + cast, 9, MUTED)
        region = app.conf["region"]
        prov = d.get("watch/providers", {}).get("results", {}).get(region, {})
        names = sorted({p["provider_name"] for k in ("flatrate", "rent", "buy") for p in prov.get(k, [])})
        lab("Available on: " + (", ".join(names) if names else f"no listed services in {region}"),
            9, MUTED, pad=6)

        btns = tk.Frame(right, bg=BG)
        btns.pack(anchor="w", pady=14)
        url = App._trailer(d)
        if url:
            tk.Button(btns, text="\u25b6 Play Trailer", bg=ACCENT, fg=FG, relief="flat", padx=14,
                      pady=6, font=("Segoe UI", 10, "bold"), cursor="hand2",
                      command=lambda: app.play_trailer_url(url, info["title"])).pack(side="left", padx=(0, 8))
        key = f"{media}:{info['id']}"
        wbtn = tk.Button(btns, bg=PANEL, fg=FG, relief="flat", padx=14, pady=6,
                         font=("Segoe UI", 10), cursor="hand2")

        def refresh():
            wbtn.configure(text="Remove from My List" if key in app.conf["watchlist"]
                           else "+ Add to My List")
        wbtn.configure(command=lambda: (app.toggle_list(info), refresh()))
        refresh()
        wbtn.pack(side="left")

        if d.get("poster_path"):
            def setpic(data):
                ph = ImageTk.PhotoImage(Image.open(io.BytesIO(data)).resize((250, 375)))
                left.image = ph
                left.configure(image=ph, text="", width=250, height=375)
            app.bg(lambda: app.api.image(d["poster_path"], "w342"), setpic, quiet=True)


class SettingsPage(tk.Frame):
    """API key + country, shown inside the app (no extra window)."""
    def __init__(self, parent, app):
        super().__init__(parent, bg=BG)
        self.app = app
        box = tk.Frame(self, bg=BG)
        box.pack(anchor="n", padx=40, pady=30, fill="x")

        def lab(text, big=False):
            tk.Label(box, text=text, bg=BG, fg=FG, font=("Segoe UI", 16 if big else 10, "bold" if big else "normal")
                     ).pack(anchor="w", pady=(18 if not big else 0, 2))
        lab("Settings", big=True)
        lab("TMDB API key (or Read Access Token)")
        self.kv = tk.StringVar()
        self.ent = tk.Entry(box, textvariable=self.kv, show="*", bg=PANEL, fg=FG, insertbackground=FG,
                            relief="flat", font=("Segoe UI", 10), width=60)
        self.ent.pack(anchor="w", ipady=6)
        self.shown = tk.BooleanVar()
        tk.Checkbutton(box, text="Show key", variable=self.shown, bg=BG, fg=MUTED, selectcolor=PANEL,
                       activebackground=BG, activeforeground=FG,
                       command=lambda: self.ent.configure(show="" if self.shown.get() else "*")
                       ).pack(anchor="w")
        tk.Label(box, bg=BG, fg=MUTED, justify="left", font=("Segoe UI", 9),
                 text="Free key: create an account at themoviedb.org > Settings > API > request a key."
                 ).pack(anchor="w", pady=(6, 0))
        lab("Country for 'Available on' (IN, US, GB ...)")
        self.rv = tk.StringVar()
        ttk.Combobox(box, textvariable=self.rv, values=REGIONS, width=8).pack(anchor="w")
        self.status = tk.Label(box, text="", bg=BG, fg=MUTED, wraplength=640, justify="left")
        self.status.pack(anchor="w", pady=(16, 0))
        row = tk.Frame(box, bg=BG)
        row.pack(anchor="w", pady=14)
        tk.Button(row, text="Save", bg=ACCENT, fg=FG, relief="flat", padx=20, pady=7,
                  font=("Segoe UI", 10, "bold"), cursor="hand2", command=self.save).pack(side="left")
        tk.Button(row, text="Cancel", bg=PANEL, fg=FG, relief="flat", padx=20, pady=7,
                  font=("Segoe UI", 10), cursor="hand2", command=app.back).pack(side="left", padx=8)

    def on_show(self):
        self.kv.set(self.app.conf["api_key"])
        self.rv.set(self.app.conf["region"])

    def set_note(self, text, error=False):
        self.status.configure(text=text, fg="#ff6b6b" if error else MUTED)

    def save(self):
        key = self.kv.get().strip()
        region = self.rv.get().strip().upper() or REGION
        if not key:
            self.set_note("Please enter an API key.", True)
            return
        self.set_note("Checking key...")

        def test():
            try:
                Tmdb(key).get("/configuration")
                return True
            except PermissionError:
                return False

        def done(ok):
            if not ok:
                self.set_note("TMDB rejected this key.", True)
                return
            self.set_note("")
            self.app.apply_settings(key, region)
            self.app.show("Home")
        self.app.bg(test, done, quiet=True,
                    fail=lambda: self.set_note("Network error - check your internet.", True))


# -------------------------------------------------------------------- app
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"{APP_NAME} - Movies & TV")
        self.geometry("1150x780")
        self.minsize(980, 600)
        self.configure(bg=BG)
        self.conf = load_conf()
        self.api = None
        self.gnames, self.gmap = {}, {}  # id->name ; media -> name->id
        self.q = queue.Queue()
        self.pool = ThreadPoolExecutor(8)
        self._dead = False
        self._style()
        self._toast_job = None
        self.last_main = "Home"
        self.api = Tmdb(self.conf["api_key"]) if self.conf["api_key"] else None
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.bind_all("<MouseWheel>", self._wheel)
        self._build()
        self.after(50, self._poll)
        if self.api:
            self.bg(self.api.genres, self._genres_ready)
        else:
            self.show("Settings")
            self.pages["Settings"].set_note(
                "Welcome to PythonFlix! Paste your free TMDB API key above and press Save. "
                "The Player tab works without it.")

    # ---- plumbing
    def _style(self):
        s = ttk.Style(self)
        s.theme_use("clam")
        s.configure("TFrame", background=BG)
        s.configure("TLabel", background=BG, foreground=FG)
        s.configure("TButton", background=PANEL, foreground=FG, padding=6, borderwidth=0)
        s.map("TButton", background=[("active", ACCENT)])
        s.configure("TCombobox", fieldbackground=PANEL, background=PANEL, foreground=FG)
        s.configure("Vertical.TScrollbar", background=PANEL, troughcolor=BG, borderwidth=0)

    def close(self):
        self._dead = True
        player = self.pages.get("Player")
        if player:
            player.shutdown()
        self.pool.shutdown(wait=False, cancel_futures=True)
        self.destroy()

    def bg(self, work, done, quiet=False, fail=None):
        """Run work() on a worker thread, then done(result) on the UI thread."""
        def run():
            try:
                res = work()
            except PermissionError as e:
                self.q.put(lambda m=str(e): self._fatal(m))
                return
            except Exception as e:  # network etc.
                if fail:
                    self.q.put(fail)
                if not quiet:
                    self.q.put(lambda m=str(e): self._err(m))
                return
            self.q.put(lambda: done(res))
        try:
            self.pool.submit(run)
        except RuntimeError:
            pass  # pool already closed

    def _poll(self):
        if self._dead:
            return
        try:
            while True:
                fn = self.q.get_nowait()
                try:
                    fn()
                except tk.TclError:
                    pass  # widget closed meanwhile
                except Exception:
                    traceback.print_exc()
        except queue.Empty:
            pass
        self.after(50, self._poll)

    def toast(self, text, ms=4500):
        """Small message at the bottom of the window (no pop-up windows)."""
        self.toast_lbl.configure(text=text)
        self.toast_lbl.place(relx=0.5, rely=0.97, anchor="s")
        self.toast_lbl.lift()
        if self._toast_job:
            self.after_cancel(self._toast_job)
        self._toast_job = self.after(ms, self.toast_lbl.place_forget)

    def _err(self, msg):
        self.toast("Could not load data: " + msg)

    def _fatal(self, msg):
        self.show("Settings")
        self.pages["Settings"].set_note(msg + " Please enter a valid key.", True)

    def back(self):
        self.show(self.last_main)

    def set_fullscreen(self, on):
        self.attributes("-fullscreen", on)
        if on:
            self.top.pack_forget()
        else:
            self.top.pack(fill="x", padx=20, pady=(12, 6), before=self.stage)

    def _wheel(self, e):
        page = self.pages.get(self.current)
        vs = getattr(page, "vs", page)
        try:
            vs.canvas.yview_scroll(int(-e.delta / 120), "units")
        except (AttributeError, tk.TclError):
            pass

    # ---- cards & images
    def card(self, parent, m, w, h):
        f = tk.Frame(parent, bg=BG, cursor="hand2")
        holder = tk.Frame(f, width=w, height=h, bg=CARD)
        holder.pack_propagate(False)
        holder.pack()
        pic = tk.Label(holder, bg=CARD, fg=MUTED, text="...")
        pic.pack(fill="both", expand=True)
        parts = [f, holder, pic]
        lang = LANG.get(m.get("lang"), (m.get("lang") or "").upper())
        if lang:
            badge = tk.Label(pic, text=lang, bg="#000000", fg=FG, font=("Segoe UI", 8), padx=4)
            badge.place(x=4, y=4)
            parts.append(badge)
        name = tk.Label(f, text=m["title"], bg=BG, fg=FG, wraplength=w, justify="left",
                        anchor="nw", font=("Segoe UI", 9), height=2)
        name.pack(fill="x", pady=(4, 0))
        parts.append(name)
        for p in parts:
            p.bind("<Button-1>", lambda e, m=m: self.open_detail(m["media"], m["id"]))
        if m.get("poster_path"):
            self.bg(lambda p=m["poster_path"]: self.api.image(p),
                    lambda data: self._set_img(pic, data, w, h), quiet=True)
        return f

    @staticmethod
    def _set_img(label, data, w, h):
        ph = ImageTk.PhotoImage(Image.open(io.BytesIO(data)).resize((w, h)))
        label.image = ph
        label.configure(image=ph, text="")

    # ---- layout
    def _build(self):
        self.top = top = tk.Frame(self, bg=BG)
        top.pack(fill="x", padx=20, pady=(12, 6))
        tk.Label(top, text="▶ PYTHONFLIX", bg=BG, fg=ACCENT,
                 font=("Segoe UI", 16, "bold")).pack(side="left", padx=(0, 24))
        self.tabs = {}
        for name in ("Home", "Movies", "TV Series", "Anime", "Kids", "Player"):
            t = tk.Label(top, text=name, bg=BG, fg=MUTED, font=("Segoe UI", 11),
                         padx=14, pady=5, cursor="hand2")
            t.pack(side="left", padx=2)
            t.bind("<Button-1>", lambda e, n=name: self.show(n))
            self.tabs[name] = t
        gear = tk.Label(top, text="\u2699 Settings", bg=PANEL, fg=FG, font=("Segoe UI", 10),
                        padx=12, pady=5, cursor="hand2")
        gear.pack(side="right", padx=(8, 0))
        gear.bind("<Button-1>", lambda e: self.show("Settings"))
        lst = tk.Label(top, text="★ My List", bg=PANEL, fg=FG, font=("Segoe UI", 10),
                       padx=12, pady=5, cursor="hand2")
        lst.pack(side="right")
        lst.bind("<Button-1>", lambda e: self.show("My List"))
        self.sv = tk.StringVar()
        ent = tk.Entry(top, textvariable=self.sv, bg=PANEL, fg=FG, insertbackground=FG,
                       relief="flat", width=24, font=("Segoe UI", 10))
        ent.pack(side="right", padx=10, ipady=5)
        ent.bind("<Return>", lambda e: self.do_search())
        tk.Label(top, text="Search:", bg=BG, fg=MUTED).pack(side="right")

        self.stage = tk.Frame(self, bg=BG)
        self.stage.pack(fill="both", expand=True)
        self.pages, self.current = {}, None

        home = VScroll(self.stage)
        self.hero = Hero(home.body, self)
        self.hero.pack(fill="x")
        self.rows = []
        for title in ("Trending Now", "Bollywood", "Hollywood", "South Indian",
                      "Trending TV Series", "Anime", "Kids & Animation", "Top Rated"):
            r = Row(home.body, self, title)
            r.pack(fill="x", pady=(8, 0))
            self.rows.append((title, r))
        self.pages["Home"] = home
        for kind in KINDS:
            self.pages[kind] = Browse(self.stage, self, kind)
        self.pages["Search"] = LocalPage(self.stage, self)
        self.pages["My List"] = LocalPage(self.stage, self)
        self.pages["Detail"] = DetailPage(self.stage, self)
        self.pages["Settings"] = SettingsPage(self.stage, self)
        self.pages["Player"] = PlayerPage(self.stage, self)
        self.toast_lbl = tk.Label(self, bg="#2a2f3d", fg=FG, padx=18, pady=9, font=("Segoe UI", 10))
        home.canvas.bind("<Configure>", lambda e: self.hero.configure(height=max(320, e.height)),
                         add="+")
        self.show("Home")

    def show(self, name):
        if self.api is None and name not in ("Settings", "Player"):
            name = "Settings"  # nothing to browse until an API key is saved
        if self.current:
            self.pages[self.current].pack_forget()
        self.current = name
        if name not in ("Detail", "Settings"):
            self.last_main = name
        for n, t in self.tabs.items():
            t.configure(fg=FG if n == name else MUTED, bg=PANEL if n == name else BG)
        page = self.pages[name]
        page.pack(fill="both", expand=True)
        if isinstance(page, Browse) or name == "Settings":
            page.on_show()
        if name == "My List":
            page.set(list(self.conf["watchlist"].values()),
                     "Your list is empty. Open any title and click 'Add to My List'.", "My List")

    # ---- home data
    def _genres_ready(self, g):
        self.gmap = {m: {n: i for i, n in d.items()} for m, d in g.items()}
        self.gnames = {**g["movie"], **g["tv"]}
        api = self.api

        def disc(media, **p):
            return lambda: [brief(m, media) for m in api.discover(media, **p)["results"]]

        def trending(path, media):
            return lambda: [brief(m, media) for m in api.get(path)["results"]]

        def south():
            lists = [[brief(m, "movie") for m in api.discover("movie", with_original_language=c)["results"]]
                     for c in ("ta", "te", "ml")]
            return [L[i] for i in range(20) for L in lists if i < len(L)]

        loaders = {
            "Trending Now": trending("/trending/movie/week", "movie"),
            "Bollywood": disc("movie", with_original_language="hi"),
            "Hollywood": disc("movie", with_original_language="en"),
            "South Indian": south,
            "Trending TV Series": trending("/trending/tv/week", "tv"),
            "Anime": disc("tv", with_genres="16", with_original_language="ja"),
            "Kids & Animation": disc("movie", with_genres="16|10751", with_original_language="en"),
            "Top Rated": trending("/movie/top_rated", "movie"),
        }
        for title, row in self.rows:
            self.bg(loaders[title], row.set_items, quiet=True,
                    fail=lambda row=row: row.note("Couldn't load this row"))
        self.bg(lambda: [brief(m) for m in api.get("/trending/all/week")["results"]
                         if m.get("media_type") in ("movie", "tv") and m.get("backdrop_path")][:6],
                self.hero.set_items)

    # ---- settings
    def apply_settings(self, key, region):
        self.conf["api_key"], self.conf["region"] = key, region
        save_conf(self.conf)
        self.api = Tmdb(key)
        for _, row in self.rows:
            row.note("Loading...")
        self.hero.items = []
        self.hero.delete("all")
        for kind in KINDS:
            page = self.pages[kind]
            page.loaded, page.total = False, 0
        self.bg(self.api.genres, self._genres_ready)
        cur = self.pages[self.current]
        if isinstance(cur, Browse):
            cur.on_show()

    # ---- actions
    def do_search(self):
        text = self.sv.get().strip()
        if not text:
            return
        self.show("Search")
        self.pages["Search"].set([], "Searching...", f"Results for '{text}'")
        self.bg(lambda: self.api.search(text),
                lambda r: self.pages["Search"].set(r, f"No results for '{text}'.", f"Results for '{text}'"))

    def toggle_list(self, m):
        k = f"{m['media']}:{m['id']}"
        if k in self.conf["watchlist"]:
            del self.conf["watchlist"][k]
        else:
            self.conf["watchlist"][k] = m
        save_conf(self.conf)
        if self.current == "My List":
            self.show("My List")

    def play_trailer(self, media, mid):
        def done(d):
            url = self._trailer(d)
            if url:
                self.play_trailer_url(url, d.get("title") or d.get("name") or "Trailer")
            else:
                self.toast("No trailer found for this title.")
        self.bg(lambda: self.api.detail(media, mid), done)

    def play_trailer_url(self, url, title):
        """Trailers play in the built-in Player tab."""
        self.show("Player")
        self.pages["Player"].play_youtube(url, title)

    @staticmethod
    def _trailer(d):
        vids = [v for v in d.get("videos", {}).get("results", [])
                if v.get("site") == "YouTube" and v.get("type") in ("Trailer", "Teaser")]
        vids.sort(key=lambda v: v.get("type") != "Trailer")
        return "https://www.youtube.com/watch?v=" + vids[0]["key"] if vids else None

    # ---- details (in-app page)
    def open_detail(self, media, mid):
        def done(d):
            self.pages["Detail"].render(media, d)
            self.show("Detail")
        self.bg(lambda: self.api.detail(media, mid), done)


if __name__ == "__main__":
    app = App()
    if not app._dead:
        app.mainloop()
