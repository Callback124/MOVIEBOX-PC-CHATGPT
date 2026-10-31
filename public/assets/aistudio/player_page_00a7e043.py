"""Built-in media player page for PythonFlix.

Uses the VLC engine (libVLC), so it plays almost every video and audio format:
MP4, MKV, AVI, MOV, WEBM, FLV, TS, MP3, FLAC, AAC, WAV, OGG, M4A, HLS (.m3u8) links ...
It lives inside the app (no extra window): open files, a whole folder, or paste a link.
"""
import os
import re
import sys
import tkinter as tk
from tkinter import filedialog, ttk

try:
    import vlc  # pip install python-vlc  (needs the free VLC app installed too)
    VLC_ERROR = ""
except Exception as e:  # python-vlc missing, or VLC itself not installed
    vlc = None
    VLC_ERROR = str(e)

BG, PANEL, CARD, FG, MUTED, ACCENT = "#0b0c10", "#161922", "#1c2029", "#f1f3f8", "#8a90a2", "#7c5cff"

VIDEO_EXT = ("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "ts", "m2ts",
             "3gp", "vob", "ogv", "divx", "rmvb", "asf")
AUDIO_EXT = ("mp3", "flac", "wav", "aac", "m4a", "ogg", "oga", "opus", "wma", "aiff", "ape", "mka")
MEDIA_EXT = VIDEO_EXT + AUDIO_EXT + ("m3u8", "m3u")
PATTERN = " ".join("*." + e for e in MEDIA_EXT)
SPEEDS = ["0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"]


def fmt(ms):
    s = max(0, int(ms)) // 1000
    h, s = divmod(s, 3600)
    m, s = divmod(s, 60)
    return f"{h}:{m:02}:{s:02}" if h else f"{m}:{s:02}"


def natural(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


def is_youtube(u):
    return "youtube.com/" in u or "youtu.be/" in u


class PlayerPage(tk.Frame):
    def __init__(self, parent, app):
        super().__init__(parent, bg=BG)
        self.app = app
        self.items = []  # [(title, location)]
        self.index = -1
        self.dragging = False
        self.fs = False
        self.fresh = False
        self.a_tracks, self.s_tracks = [], []
        self.ok = False
        if vlc is None:
            self._no_vlc(VLC_ERROR)
            return
        try:
            self.inst = vlc.Instance("--no-video-title-show", "--quiet", "--audio-visual=visual")
            self.mp = self.inst.media_player_new()
            self.mp.video_set_mouse_input(False)  # let Tk receive clicks on the video area
            self.mp.video_set_key_input(False)
        except Exception as e:
            self._no_vlc(str(e))
            return
        self.ok = True
        self._build()
        self.after(300, self._tick)
        app.bind("<KeyPress>", self._key, add="+")

    # ------------------------------------------------------------ no VLC
    def _no_vlc(self, err):
        box = tk.Frame(self, bg=BG)
        box.place(relx=0.5, rely=0.4, anchor="center")
        tk.Label(box, text="Built-in player needs VLC", bg=BG, fg=FG,
                 font=("Segoe UI", 18, "bold")).pack()
        tk.Label(box, bg=BG, fg=MUTED, font=("Segoe UI", 11), justify="center", text=(
            "VLC is a free engine that plays almost every video and audio format.\n\n"
            "1. Open Command Prompt and run:\n"
            "       winget install -e --id VideoLAN.VLC\n"
            "2. Run:  pip install python-vlc\n"
            "3. Restart PythonFlix.\n\n"
            + (f"(details: {err[:120]})" if err else ""))).pack(pady=12)

    # ------------------------------------------------------------ layout
    def _build(self):
        btn = dict(bg=PANEL, fg=FG, relief="flat", bd=0, padx=10, pady=5, cursor="hand2",
                   activebackground=ACCENT, activeforeground=FG, takefocus=0, font=("Segoe UI", 10))

        self.side = tk.Frame(self, bg=PANEL, width=290)
        self.side.pack(side="right", fill="y")
        self.side.pack_propagate(False)
        self.left = tk.Frame(self, bg=BG)
        self.left.pack(side="left", fill="both", expand=True)

        # --- video area
        self.video = tk.Frame(self.left, bg="black")
        self.video.pack(fill="both", expand=True)
        self.hint = tk.Label(self.video, bg="black", fg=MUTED, font=("Segoe UI", 13), justify="center",
                             text="Open a file or folder, or paste a link\n\nSpace: play/pause   ← →: seek 10 s   "
                                  "↑ ↓: volume   F: fullscreen   M: mute")
        self.hint.place(relx=0.5, rely=0.5, anchor="center")
        self.video.bind("<Button-1>", lambda e: self.toggle())
        self.video.bind("<Double-Button-1>", lambda e: self.fullscreen())

        # --- seek bar
        bar = tk.Frame(self.left, bg=BG)
        bar.pack(fill="x", padx=12, pady=(8, 0))
        self.t_lbl = tk.Label(bar, text="0:00", bg=BG, fg=MUTED, width=6)
        self.t_lbl.pack(side="left")
        self.seek = ttk.Scale(bar, from_=0, to=1000, orient="horizontal")
        self.seek.pack(side="left", fill="x", expand=True, padx=6)
        self.seek.bind("<ButtonPress-1>", lambda e: setattr(self, "dragging", True))
        self.seek.bind("<ButtonRelease-1>", self._seek_release)
        self.d_lbl = tk.Label(bar, text="0:00", bg=BG, fg=MUTED, width=6)
        self.d_lbl.pack(side="left")

        # --- buttons
        row = tk.Frame(self.left, bg=BG)
        row.pack(fill="x", padx=12, pady=8)
        tk.Button(row, text="⏮", command=self.prev, **btn).pack(side="left")
        self.play_btn = tk.Button(row, text="▶", command=self.toggle, **btn)
        self.play_btn.pack(side="left", padx=3)
        tk.Button(row, text="⏹", command=self.stop, **btn).pack(side="left")
        tk.Button(row, text="⏭", command=self.next, **btn).pack(side="left", padx=3)
        tk.Label(row, text="\U0001f50a", bg=BG, fg=MUTED).pack(side="left", padx=(12, 2))
        self.vol = ttk.Scale(row, from_=0, to=100, orient="horizontal", length=90,
                             command=lambda v: self.ok and self.mp.audio_set_volume(int(float(v))))
        self.vol.set(80)
        self.vol.pack(side="left")
        self.speed_var = tk.StringVar(value="1x")
        sp = ttk.Combobox(row, textvariable=self.speed_var, values=SPEEDS, width=6, state="readonly")
        sp.pack(side="left", padx=(12, 0))
        sp.bind("<<ComboboxSelected>>", lambda e: self.mp.set_rate(float(self.speed_var.get().rstrip("x"))))
        self.audio_var, self.sub_var = tk.StringVar(), tk.StringVar()
        self.audio_cb = ttk.Combobox(row, textvariable=self.audio_var, width=16, state="readonly")
        self.audio_cb.pack(side="left", padx=(8, 0))
        self.audio_cb.bind("<<ComboboxSelected>>", self._pick_audio)
        self.sub_cb = ttk.Combobox(row, textvariable=self.sub_var, width=16, state="readonly")
        self.sub_cb.pack(side="left", padx=(8, 0))
        self.sub_cb.bind("<<ComboboxSelected>>", self._pick_sub)
        tk.Button(row, text="⛶ Fullscreen", command=self.fullscreen, **btn).pack(side="right")

        # --- playlist panel
        tk.Label(self.side, text="Playlist", bg=PANEL, fg=FG, font=("Segoe UI", 13, "bold")
                 ).pack(anchor="w", padx=12, pady=(12, 6))
        top = tk.Frame(self.side, bg=PANEL)
        top.pack(fill="x", padx=12)
        tk.Button(top, text="Open files", command=self.open_files, **btn).pack(side="left")
        tk.Button(top, text="Open folder", command=self.open_folder, **btn).pack(side="left", padx=6)
        top2 = tk.Frame(self.side, bg=PANEL)
        top2.pack(fill="x", padx=12, pady=6)
        tk.Button(top2, text="Remove", command=self.remove, **btn).pack(side="left")
        tk.Button(top2, text="Clear", command=self.clear, **btn).pack(side="left", padx=6)

        self.url = tk.StringVar()
        ent = tk.Entry(self.side, textvariable=self.url, bg=CARD, fg=FG, insertbackground=FG,
                       relief="flat", font=("Segoe UI", 10))
        ent.pack(fill="x", padx=12, ipady=5)
        ent.bind("<Return>", lambda e: self.play_link())
        tk.Button(self.side, text="Play link (mp4 / m3u8 / YouTube)", command=self.play_link, **btn
                  ).pack(fill="x", padx=12, pady=6)

        self.status = tk.Label(self.side, text="", bg=PANEL, fg=MUTED, wraplength=260, justify="left",
                               font=("Segoe UI", 9))
        self.status.pack(side="bottom", anchor="w", padx=12, pady=8)
        lf = tk.Frame(self.side, bg=PANEL)
        lf.pack(fill="both", expand=True, padx=12, pady=(4, 0))
        self.lb = tk.Listbox(lf, bg=CARD, fg=FG, selectbackground=ACCENT, selectforeground=FG,
                             relief="flat", highlightthickness=0, activestyle="none",
                             font=("Segoe UI", 10))
        self.lb.pack(fill="both", expand=True)
        self.lb.bind("<Double-Button-1>", lambda e: self._play_selected())
        self.lb.bind("<Return>", lambda e: self._play_selected())

    # ------------------------------------------------------------ playlist
    def open_files(self):
        if not self.ok:
            return
        paths = filedialog.askopenfilenames(
            title="Open video / audio files",
            filetypes=[("Media files", PATTERN), ("All files", "*.*")])
        self.add([(os.path.basename(p), p) for p in paths], play=True)

    def open_folder(self):
        if not self.ok:
            return
        d = filedialog.askdirectory(title="Open a folder of videos / music")
        if not d:
            return
        files = []
        for root, _dirs, names in os.walk(d):
            for n in names:
                if n.lower().rsplit(".", 1)[-1] in MEDIA_EXT:
                    files.append(os.path.join(root, n))
        files.sort(key=natural)
        if not files:
            self.status.configure(text="No video or audio files found in that folder.")
            return
        self.add([(os.path.basename(p), p) for p in files], play=True)

    def play_link(self):
        u = self.url.get().strip()
        if not u or not self.ok:
            return
        self.url.set("")
        if is_youtube(u):
            self.play_youtube(u, "YouTube video")
        else:
            self.add([(u.rsplit("/", 1)[-1][:60] or u, u)], play=True)

    def add(self, entries, play=False):
        if not entries:
            return
        start = len(self.items)
        self.items.extend(entries)
        for title, _ in entries:
            self.lb.insert("end", title)
        if play:
            self.play_index(start)

    def remove(self):
        sel = self.lb.curselection()
        if not sel:
            return
        i = sel[0]
        self.lb.delete(i)
        del self.items[i]
        if i < self.index:
            self.index -= 1
        elif i == self.index:
            self.stop()
            self.index = -1

    def clear(self):
        self.stop()
        self.items.clear()
        self.lb.delete(0, "end")
        self.index = -1

    def _play_selected(self):
        sel = self.lb.curselection()
        if sel:
            self.play_index(sel[0])

    # ------------------------------------------------------------ playback
    def _attach(self):
        self.update_idletasks()
        h = self.video.winfo_id()
        if sys.platform.startswith("win"):
            self.mp.set_hwnd(h)
        elif sys.platform == "darwin":
            self.mp.set_nsobject(h)
        else:
            self.mp.set_xwindow(h)

    def play_index(self, i):
        if not self.ok or not (0 <= i < len(self.items)):
            return
        self.index = i
        title, loc = self.items[i]
        self.lb.selection_clear(0, "end")
        self.lb.selection_set(i)
        self.lb.see(i)
        self._attach()
        media = self.inst.media_new(loc) if "://" in loc else self.inst.media_new_path(loc)
        self.mp.set_media(media)
        self.mp.play()
        self.mp.audio_set_volume(int(float(self.vol.get())))
        self.hint.place_forget()
        self.status.configure(text=f"Playing: {title}")
        self.fresh = True
        self.audio_cb.configure(values=[])
        self.sub_cb.configure(values=[])
        self.audio_var.set("")
        self.sub_var.set("")

    def play_youtube(self, url, title="Video"):
        """Resolve a YouTube link (e.g. a movie trailer) and play it in the built-in player."""
        if not self.ok:
            self.app.toast("The built-in player needs VLC. Open the Player tab for steps.")
            return
        self.status.configure(text="Loading trailer ...")

        def work():
            import yt_dlp  # pip install yt-dlp
            opts = {"quiet": True, "noplaylist": True, "skip_download": True,
                    "format": "best[ext=mp4][height<=720]/best[height<=720]/best"}
            with yt_dlp.YoutubeDL(opts) as y:
                return y.extract_info(url, download=False)["url"]

        self.app.bg(work, lambda direct: self.add([(f"{title} (trailer)", direct)], play=True),
                    quiet=True,
                    fail=lambda: self.status.configure(
                        text="Could not load this video. Run: pip install -U yt-dlp"))

    def toggle(self):
        if not self.ok:
            return
        if self.index < 0:
            if self.items:
                self.play_index(0)
            else:
                self.open_files()
            return
        state = self.mp.get_state()
        if state in (vlc.State.Stopped, vlc.State.Ended, vlc.State.NothingSpecial):
            self.play_index(self.index)
        else:
            self.mp.pause()

    def stop(self):
        if not self.ok:
            return
        self.mp.stop()
        self.seek.set(0)
        self.t_lbl.configure(text="0:00")
        self.d_lbl.configure(text="0:00")
        self.play_btn.configure(text="▶")

    def next(self):
        if self.index + 1 < len(self.items):
            self.play_index(self.index + 1)

    def prev(self):
        if self.ok and self.mp.get_time() > 3000:
            self.mp.set_time(0)
        elif self.index > 0:
            self.play_index(self.index - 1)

    def _seek_release(self, e):
        self.dragging = False
        if self.ok and self.index >= 0:
            self.mp.set_position(float(self.seek.get()) / 1000)

    def _tick(self):
        try:
            if self.index >= 0:
                state = self.mp.get_state()
                length, t = self.mp.get_length(), self.mp.get_time()
                if not self.dragging and length > 0:
                    self.seek.set(1000 * t / length)
                self.t_lbl.configure(text=fmt(t))
                self.d_lbl.configure(text=fmt(length))
                self.play_btn.configure(text="⏸" if state == vlc.State.Playing else "▶")
                if state == vlc.State.Playing and self.fresh:
                    self.fresh = False
                    self.mp.set_rate(float(self.speed_var.get().rstrip("x")))
                    self.after(800, self._load_tracks)
                if state == vlc.State.Ended:
                    if self.index + 1 < len(self.items):
                        self.play_index(self.index + 1)
                    else:
                        self.mp.stop()
                        self.status.configure(text="Finished.")
        except tk.TclError:
            return
        except Exception:
            pass
        self.after(300, self._tick)

    # ------------------------------------------------------------ tracks
    @staticmethod
    def _names(desc):
        out = []
        for tid, name in desc or []:
            out.append((tid, name.decode("utf-8", "ignore") if isinstance(name, bytes) else str(name)))
        return out

    def _load_tracks(self):
        try:
            self.a_tracks = self._names(self.mp.audio_get_track_description())
            self.s_tracks = self._names(self.mp.video_get_spu_description())
            self.audio_cb.configure(values=[n for _, n in self.a_tracks])
            self.sub_cb.configure(values=[n for _, n in self.s_tracks])
            for tid, n in self.a_tracks:
                if tid == self.mp.audio_get_track():
                    self.audio_var.set(n)
            for tid, n in self.s_tracks:
                if tid == self.mp.video_get_spu():
                    self.sub_var.set(n)
        except tk.TclError:
            pass

    def _pick_audio(self, e):
        for tid, n in self.a_tracks:
            if n == self.audio_var.get():
                self.mp.audio_set_track(tid)

    def _pick_sub(self, e):
        for tid, n in self.s_tracks:
            if n == self.sub_var.get():
                self.mp.video_set_spu(tid)

    # ------------------------------------------------------------ misc
    def fullscreen(self):
        self.fs = not self.fs
        self.app.set_fullscreen(self.fs)
        if self.fs:
            self.side.pack_forget()
        else:
            self.side.pack(side="right", fill="y", before=self.left)

    def _key(self, e):
        if not self.ok or self.app.current != "Player":
            return
        if isinstance(self.app.focus_get(), (tk.Entry, ttk.Combobox)):
            return
        k = e.keysym.lower()
        if k == "space":
            self.toggle()
        elif k == "left":
            self.mp.set_time(max(0, self.mp.get_time() - 10000))
        elif k == "right":
            self.mp.set_time(self.mp.get_time() + 10000)
        elif k == "up":
            self.vol.set(min(100, float(self.vol.get()) + 5))
        elif k == "down":
            self.vol.set(max(0, float(self.vol.get()) - 5))
        elif k == "f":
            self.fullscreen()
        elif k == "escape" and self.fs:
            self.fullscreen()
        elif k == "m":
            self.mp.audio_toggle_mute()
        elif k == "n":
            self.next()
        elif k == "p":
            self.prev()

    def shutdown(self):
        try:
            if self.ok:
                self.mp.stop()
        except Exception:
            pass
