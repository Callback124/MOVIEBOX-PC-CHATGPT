'use strict';
/* MovieBox Player: Internet Archive free movies, your own files, or any direct/HLS link.
   Android TV app  -> native Media3 ExoPlayer screen (http/https sources)
   Windows / fallback -> built-in HTML5 player (+ hls.js for .m3u8) */
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const html = (h) => { const t = document.createElement('template'); t.innerHTML = h.trim(); return t.content.firstElementChild; };
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ } }
  };
  const pad = (n) => String(n).padStart(2, '0');
  const fmt = (t) => {
    if (!isFinite(t) || t < 0) t = 0; t = Math.floor(t);
    const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = t % 60;
    return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
  };
  const srt2vtt = (txt) => 'WEBVTT\n\n' + txt.replace(/\r/g, '').replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');

  /* ---------- native ExoPlayer (Android TV app) ---------- */
  let nativePlugin;
  function native() {
    if (nativePlugin !== undefined) return nativePlugin;
    nativePlugin = null;
    try {
      const C = window.Capacitor;
      if (C && C.getPlatform && C.getPlatform() === 'android' && C.registerPlugin) nativePlugin = C.registerPlugin('ExoPlayer');
    } catch { /* not in the TV app */ }
    return nativePlugin;
  }

  /* ---------- HTML5 player overlay (same layout as the TV ExoPlayer screen) ---------- */
  const IC = {
    down: '<path d="M4 8l8 8 8-8" stroke-width="2.6"/>',
    lockOpen: '<path d="M5 11h14v10H5z"/><path d="M8 11V7a4 4 0 0 1 7.6-1.7"/><path d="M11 15h2v2h-2z" fill="currentColor"/>',
    lock: '<path d="M5 11h14v10H5z"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/><path d="M11 15h2v2h-2z" fill="currentColor"/>',
    replay: '<path d="M6.34 7.34A8 8 0 1 0 12 5"/><path d="M12 1.5L8 5l4 3.5"/>',
    forward: '<path d="M17.66 7.34A8 8 0 1 1 12 5"/><path d="M12 1.5L16 5l-4 3.5"/>',
    play: '<path d="M7 4.5v15l13-7.5z" fill="currentColor" stroke="none"/>',
    pause: '<path d="M6.5 4.5h4v15h-4zM13.5 4.5h4v15h-4z" fill="currentColor" stroke="none"/>',
    vol: '<path d="M3 9.5h4l5-4.5v14l-5-4.5H3z" fill="currentColor" stroke="none"/><path d="M15.5 9a4.2 4.2 0 0 1 0 6M18.2 6.2a8 8 0 0 1 0 11.6"/>',
    volOff: '<path d="M3 9.5h4l5-4.5v14l-5-4.5H3z" fill="currentColor" stroke="none"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
    audio: '<path d="M9.5 17.5V5.5l10-2.2v12.2"/><path d="M4.5 17.5a2.5 2.5 0 1 0 5 0 2.5 2.5 0 1 0-5 0z" fill="currentColor"/><path d="M14.5 15.5a2.5 2.5 0 1 0 5 0 2.5 2.5 0 1 0-5 0z" fill="currentColor"/>',
    quality: '<path d="M3 5h14v11H3z"/><path d="M8 8l4.5 2.5L8 13z" fill="currentColor"/><path d="M16 18.5a3 3 0 1 0 6 0 3 3 0 1 0-6 0z"/>',
    cc: '<path d="M3 5h18v14H3z" fill="currentColor" stroke="none"/><path d="M10.2 10.3A2.6 2.6 0 1 0 10.2 13.7M17.2 10.3A2.6 2.6 0 1 0 17.2 13.7" stroke="#000" stroke-width="1.7"/>',
    speed: '<path d="M3.5 17.5a9 9 0 1 1 17 0"/><path d="M12 16.5l4.5-5.5"/>',
    fit: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" stroke-width="2.2"/>',
    pip: '<path d="M3 5h18v14H3z"/><path d="M12 11h7v6h-7z" fill="currentColor"/>'
  };
  const svg = (k, extra = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC[k]}${extra}</svg>`;
  const TXT10 = '<text x="12" y="15.9" text-anchor="middle" font-size="8" font-weight="700" fill="currentColor" stroke="none" font-family="Arial,sans-serif">10</text>';
  const clock = (t) => {
    if (!isFinite(t) || t < 0) t = 0; t = Math.floor(t);
    const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = t % 60;
    return h ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
  };
  const codecName = (c) => { c = String(c || '').toLowerCase(); return /avc|h264/.test(c) ? 'H.264' : /hvc|hev|h265/.test(c) ? 'H.265' : /av01|av1/.test(c) ? 'AV1' : /vp0?9/.test(c) ? 'VP9' : /vp0?8/.test(c) ? 'VP8' : ''; };
  const qTag = (h) => (h >= 2160 ? '4K' : h >= 1080 ? 'FHD' : h >= 720 ? 'HD' : '');
  const rateText = (b) => (b >= 1e6 ? (b / 1e6).toFixed(1) + ' Mbps' : b > 0 ? Math.round(b / 1000) + ' kbps' : '');
  const langName = (c) => { try { return c && c !== 'und' ? (new Intl.DisplayNames(['en'], { type: 'language' }).of(c) || c) : ''; } catch { return c || ''; } };
  const chanText = (n) => ({ 1: 'Mono', 2: 'Stereo', 6: '5.1', 8: '7.1' }[n] || (n ? n + ' ch' : 'Stereo'));

  let ov = null, hls = null, hideT = null, saveT = null, pushed = false, keyFn = null, subUrl = null;
  function closePlayer(fromPop) {
    if (!ov) return;
    try { if (saveT) clearInterval(saveT); } catch { /* ignore */ }
    clearTimeout(hideT);
    if (hls) { try { hls.destroy(); } catch { /* ignore */ } hls = null; }
    const v = $('video', ov); if (v) { v.pause(); v.removeAttribute('src'); v.load(); }
    if (subUrl) { URL.revokeObjectURL(subUrl); subUrl = null; }
    document.removeEventListener('keydown', keyFn, true);
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    ov.remove(); ov = null;
    if (pushed) { pushed = false; if (!fromPop) history.back(); }
  }
  window.addEventListener('popstate', () => { if (pushed && ov) closePlayer(true); });

  function openHtml5(o) {
    closePlayer(true);
    const R = html(`<div class="mbp" tabindex="-1">
      <video playsinline></video><div class="mbp-scrim"></div><div class="mbp-msg" id="mbmsg"></div>
      <div class="mbp-top"><button class="ib" id="mbback" aria-label="Close">${svg('down')}</button><span class="mbt"></span><button class="ib" id="mblock" aria-label="Lock controls">${svg('lockOpen')}</button></div>
      <div class="mbp-mid"><button class="ib cx" id="mbrw" aria-label="Back 10 seconds">${svg('replay', TXT10)}</button><button class="ib cp" id="mbpl" aria-label="Play">${svg('play')}</button><button class="ib cx" id="mbff" aria-label="Forward 10 seconds">${svg('forward', TXT10)}</button></div>
      <div class="mbp-bot"><div class="mbseek" id="mbseek" tabindex="0" role="slider" aria-label="Seek"><i class="buf"></i><i class="fill"></i><i class="knob"></i></div>
        <div class="mbrow"><span id="mbtime">00:00 · 00:00</span><span class="sp"></span>
          <button class="ib" id="mbvol" aria-label="Mute">${svg('vol')}</button><button class="ib" id="mbau" aria-label="Audio">${svg('audio')}</button><button class="ib" id="mbq" aria-label="Video quality">${svg('quality')}</button><button class="ib" id="mbcc" aria-label="Subtitles">${svg('cc')}</button><button class="ib" id="mbsp" aria-label="Speed">${svg('speed')}</button><button class="ib" id="mbfit" aria-label="Aspect ratio">${svg('fit')}</button><button class="ib" id="mbpip" aria-label="Picture in picture">${svg('pip')}</button></div></div>
      <div class="mbp-layer hidden"><div class="mbp-sheet"></div></div></div>`);
    $('.mbt', R).textContent = o.title || '';
    ov = R; document.body.appendChild(R);
    try { history.pushState({ p: 1 }, ''); pushed = true; } catch { pushed = false; }
    const v = $('video', R), msg = $('#mbmsg', R), layer = $('.mbp-layer', R), sheet = $('.mbp-sheet', R);
    const q = (id) => $(id, R);
    const posKey = o.src.startsWith('blob:') ? null : 'mbp:pos:' + o.src;
    const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2]; let si = 2;
    const fits = [['contain', 'Fit'], ['cover', 'Zoom'], ['fill', 'Stretch']]; let fi = 0;
    let locked = false, sheetOpen = false, tab = 0, mode = 'av', audioNone = false, dragging = false;
    const say = (t, ms = 1400) => { msg.textContent = t; msg.classList.add('on'); clearTimeout(say.t); say.t = setTimeout(() => msg.classList.remove('on'), ms); };
    const wake = () => { R.classList.remove('idle'); clearTimeout(hideT); hideT = setTimeout(() => { if (!v.paused && !sheetOpen) R.classList.add('idle'); }, 3500); };
    const seek = (d) => { if (isFinite(v.duration)) { v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + d)); say((d > 0 ? '+' : '') + d + 's'); } };
    const toggle = () => (v.paused ? v.play() : v.pause());
    const syncVol = () => { q('#mbvol').innerHTML = svg(v.muted || v.volume === 0 || audioNone ? 'volOff' : 'vol'); };
    const setFill = (f) => {
      q('.fill').style.width = f * 100 + '%'; q('.knob').style.left = f * 100 + '%';
      q('#mbtime').textContent = clock(f * (v.duration || 0)) + ' · ' + (isFinite(v.duration) ? clock(v.duration) : 'LIVE');
    };
    const frac = (e) => { const r = q('#mbseek').getBoundingClientRect(); return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)); };
    const setLock = (on) => { locked = on; R.classList.toggle('locked', on); q('#mblock').innerHTML = svg(on ? 'lock' : 'lockOpen'); say(on ? 'Controls locked' : 'Controls unlocked'); wake(); };

    /* ----- track sheet (Video / Audio / Subtitles) ----- */
    const esc2 = esc;
    function videoRows() {
      const rows = [];
      if (hls && hls.levels && hls.levels.length > 1) {
        const auto = hls.autoLevelEnabled;
        rows.push({ label: 'Auto • Adaptive', on: auto, run: () => { hls.currentLevel = -1; } });
        hls.levels.map((l, i) => ({ l, i })).sort((a, b) => (b.l.height || 0) - (a.l.height || 0) || (b.l.bitrate || 0) - (a.l.bitrate || 0)).forEach(({ l, i }) => {
          const parts = [l.height ? l.height + 'p' : (l.name || 'Video'), qTag(l.height), codecName(l.videoCodec || l.codecSet), rateText(l.bitrate)].filter(Boolean);
          rows.push({ label: parts.join(' • '), on: !auto && hls.currentLevel === i, run: () => { hls.currentLevel = i; } });
        });
      } else {
        const h = v.videoHeight;
        rows.push({ label: [h ? h + 'p' : 'Video', qTag(h), 'Original'].filter(Boolean).join(' • '), on: true, run: () => {} });
      }
      return rows;
    }
    function audioRows() {
      const rows = [{ label: 'None • Mute audio', on: audioNone, run: () => { audioNone = true; v.muted = true; syncVol(); } }];
      const pick = (fn) => () => { audioNone = false; v.muted = false; fn(); syncVol(); };
      if (hls && hls.audioTracks && hls.audioTracks.length) {
        hls.audioTracks.forEach((t) => rows.push({
          label: `${t.name || langName(t.lang) || 'Audio'} • ${chanText(t.attrs && +t.attrs.CHANNELS)}`,
          on: !audioNone && hls.audioTrack === t.id, run: pick(() => { hls.audioTrack = t.id; })
        }));
      } else if (v.audioTracks && v.audioTracks.length) {
        [...v.audioTracks].forEach((t, i) => rows.push({
          label: `${langName(t.language) || t.label || 'Track ' + (i + 1)} • Stereo`, on: !audioNone && t.enabled,
          run: pick(() => { [...v.audioTracks].forEach((x, k) => { x.enabled = k === i; }); })
        }));
      } else rows.push({ label: 'Default • Stereo', on: !audioNone, run: pick(() => {}) });
      return rows;
    }
    function subRows() {
      const tts = [...v.textTracks].filter((t) => t.kind === 'subtitles' || t.kind === 'captions');
      const showing = tts.find((t) => t.mode === 'showing');
      const rows = [{ label: 'Off', on: !showing, run: () => { tts.forEach((t) => { t.mode = 'disabled'; }); } }];
      tts.forEach((t, i) => rows.push({ label: t.label || langName(t.language) || 'Subtitles ' + (i + 1), on: showing === t, run: () => { tts.forEach((x) => { x.mode = x === t ? 'showing' : 'disabled'; }); } }));
      rows.push({ label: 'Load subtitle file…', on: false, run: () => { closeSheet(); pickSub(); }, plain: true });
      return rows;
    }
    function renderSheet() {
      const tabs = mode === 'subs' ? ['Subtitles'] : ['Video', 'Audio'];
      const rows = mode === 'subs' ? subRows() : tab === 0 ? videoRows() : audioRows();
      sheet.innerHTML = `<div class="grab"></div><div class="tabs2">${tabs.map((t, i) => `<button class="tb ${i === (mode === 'subs' ? 0 : tab) ? 'on' : ''}" data-i="${i}">${t}</button>`).join('')}</div>
        <div class="opts">${rows.map((r, i) => `<button class="opt ${r.on ? 'on' : ''}" data-i="${i}"><span>${esc2(r.label)}</span>${r.on ? '<b>✓</b>' : ''}</button>`).join('')}</div>`;
      sheet.querySelectorAll('.tb').forEach((b) => { b.onclick = () => { tab = +b.dataset.i; renderSheet(); }; });
      sheet.querySelectorAll('.opt').forEach((b) => { b.onclick = () => { rows[+b.dataset.i].run(); setTimeout(() => { if (sheetOpen) renderSheet(); }, 200); }; });
      const on = sheet.querySelector('.opt.on') || sheet.querySelector('.opt'); if (on && document.activeElement && !sheet.contains(document.activeElement)) on.focus({ preventScroll: true });
    }
    function openSheet(m, t) { mode = m; if (t != null) tab = t; sheetOpen = true; R.classList.remove('idle'); clearTimeout(hideT); layer.classList.remove('hidden'); renderSheet(); }
    function closeSheet() { sheetOpen = false; layer.classList.add('hidden'); q('#mbpl').focus({ preventScroll: true }); wake(); }
    layer.addEventListener('click', (e) => { if (e.target === layer) closeSheet(); });
    function pickSub() {
      const inp = document.createElement('input'); inp.type = 'file'; inp.accept = '.vtt,.srt,text/vtt';
      inp.onchange = async () => {
        const f = inp.files[0]; if (!f) return; let txt = await f.text();
        if (!/^WEBVTT/.test(txt)) txt = srt2vtt(txt);
        if (subUrl) URL.revokeObjectURL(subUrl);
        subUrl = URL.createObjectURL(new Blob([txt], { type: 'text/vtt' }));
        v.querySelectorAll('track').forEach((t) => t.remove());
        const tr = document.createElement('track'); tr.kind = 'subtitles'; tr.label = f.name; tr.default = true; tr.src = subUrl; v.appendChild(tr);
        setTimeout(() => { const t = [...v.textTracks].find((x) => x.label === f.name); if (t) t.mode = 'showing'; }, 150); say('Subtitles: ' + f.name, 2500);
      };
      inp.click();
    }

    /* ----- source ----- */
    if (/\.m3u8(\?|$)/i.test(o.src) && window.Hls && window.Hls.isSupported()) {
      hls = new window.Hls(); hls.loadSource(o.src); hls.attachMedia(v);
      hls.on(window.Hls.Events.ERROR, (_, d) => { if (d.fatal) say('Stream error: ' + d.type, 4000); });
      const refresh = () => { if (sheetOpen) renderSheet(); };
      ['MANIFEST_PARSED', 'LEVEL_SWITCHED', 'AUDIO_TRACK_SWITCHED', 'AUDIO_TRACKS_UPDATED'].forEach((n) => { if (window.Hls.Events[n]) hls.on(window.Hls.Events[n], refresh); });
    } else v.src = o.src;
    v.addEventListener('error', () => say("Can't play this video (format not supported or link blocked).", 5000));
    v.addEventListener('loadedmetadata', () => {
      const saved = posKey ? store.get(posKey, 0) : 0;
      if (saved > 10 && saved < v.duration - 30) { v.currentTime = saved; say('Resumed at ' + clock(saved)); }
      setFill(v.duration ? v.currentTime / v.duration : 0);
    });
    v.addEventListener('timeupdate', () => {
      if (dragging) return;
      const f = isFinite(v.duration) && v.duration ? v.currentTime / v.duration : 0;
      setFill(f);
      if (v.buffered.length) q('.buf').style.width = Math.min(100, (v.buffered.end(v.buffered.length - 1) / (v.duration || 1)) * 100) + '%';
    });
    v.addEventListener('play', () => { q('#mbpl').innerHTML = svg('pause'); q('#mbpl').setAttribute('aria-label', 'Pause'); wake(); });
    v.addEventListener('pause', () => { q('#mbpl').innerHTML = svg('play'); q('#mbpl').setAttribute('aria-label', 'Play'); R.classList.remove('idle'); });
    v.addEventListener('ended', () => { say('Finished', 3000); R.classList.remove('idle'); if (posKey) store.set(posKey, 0); });
    v.addEventListener('waiting', () => say('Buffering…', 800));
    v.addEventListener('volumechange', syncVol);
    saveT = setInterval(() => { if (posKey && v.currentTime > 5) store.set(posKey, v.currentTime); }, 5000);
    v.play().catch(() => {});

    /* ----- buttons ----- */
    q('#mbback').onclick = () => closePlayer();
    q('#mblock').onclick = () => setLock(!locked);
    q('#mbpl').onclick = toggle;
    q('#mbrw').onclick = () => seek(-10); q('#mbff').onclick = () => seek(10);
    q('#mbvol').onclick = () => { audioNone = false; v.muted = !v.muted; syncVol(); };
    q('#mbau').onclick = () => openSheet('av', 1);
    q('#mbq').onclick = () => openSheet('av', 0);
    q('#mbcc').onclick = () => openSheet('subs');
    q('#mbsp').onclick = () => { si = (si + 1) % speeds.length; v.playbackRate = speeds[si]; say('Speed ' + speeds[si] + '×'); };
    q('#mbfit').onclick = () => { fi = (fi + 1) % fits.length; v.style.objectFit = fits[fi][0]; say(fits[fi][1]); };
    if (!document.pictureInPictureEnabled) q('#mbpip').style.display = 'none';
    q('#mbpip').onclick = () => { if (document.pictureInPictureElement) document.exitPictureInPicture(); else v.requestPictureInPicture().catch(() => say('Picture-in-picture not available')); };
    const sk = q('#mbseek');
    sk.addEventListener('pointerdown', (e) => { if (!isFinite(v.duration)) return; dragging = true; sk.setPointerCapture(e.pointerId); setFill(frac(e)); wake(); });
    sk.addEventListener('pointermove', (e) => { if (dragging) setFill(frac(e)); });
    sk.addEventListener('pointerup', (e) => { if (!dragging) return; dragging = false; v.currentTime = frac(e) * v.duration; });
    R.addEventListener('click', (e) => {
      if (e.target === R || e.target === v || e.target.classList.contains('mbp-scrim')) {
        if (R.classList.contains('idle')) wake(); else { R.classList.add('idle'); clearTimeout(hideT); }
      }
    });
    R.addEventListener('dblclick', (e) => {
      if (locked || (e.target !== v && e.target !== R && !e.target.classList.contains('mbp-scrim'))) return;
      const x = e.clientX / window.innerWidth; if (x < 0.33) seek(-10); else if (x > 0.66) seek(10);
    });
    R.addEventListener('mousemove', () => { if (R.classList.contains('idle')) wake(); else if (!sheetOpen) { clearTimeout(hideT); hideT = setTimeout(() => { if (!v.paused && !sheetOpen) R.classList.add('idle'); }, 3500); } });

    /* ----- keyboard (PC) ----- */
    keyFn = (e) => {
      if (!ov) return;
      const k = e.key;
      if (k === 'Escape' || k === 'GoBack' || k === 'BrowserBack') {
        e.preventDefault(); e.stopPropagation();
        if (sheetOpen) closeSheet(); else if (locked) setLock(false); else closePlayer();
        return;
      }
      if (sheetOpen) {
        if (k === 'ArrowDown' || k === 'ArrowUp') {
          e.preventDefault(); e.stopPropagation();
          const items = [...sheet.querySelectorAll('.opt')]; let i = items.indexOf(document.activeElement);
          i = k === 'ArrowDown' ? Math.min(items.length - 1, i + 1) : Math.max(0, i - 1); if (items[i]) items[i].focus();
        }
        return;
      }
      if (locked) return;
      wake();
      if (k === 'ArrowLeft') { e.preventDefault(); e.stopPropagation(); seek(e.shiftKey ? -30 : -10); }
      else if (k === 'ArrowRight') { e.preventDefault(); e.stopPropagation(); seek(e.shiftKey ? 30 : 10); }
      else if (k === 'ArrowUp' || k === 'ArrowDown') { e.preventDefault(); e.stopPropagation(); audioNone = false; v.muted = false; v.volume = Math.max(0, Math.min(1, v.volume + (k === 'ArrowUp' ? 0.1 : -0.1))); say('Volume ' + Math.round(v.volume * 100) + '%'); }
      else if (k === ' ' || k === 'k' || k === 'MediaPlayPause') { e.preventDefault(); e.stopPropagation(); toggle(); }
      else if (k === 'MediaPlay') v.play(); else if (k === 'MediaPause') v.pause();
      else if (k === 'MediaFastForward') seek(30); else if (k === 'MediaRewind') seek(-30);
      else if (k === 'f' || k === 'F') { if (document.fullscreenElement) document.exitFullscreen(); else R.requestFullscreen().catch(() => {}); }
      else if (k === 'm' || k === 'M') q('#mbvol').click();
      else if (k === 'c' || k === 'C') q('#mbcc').click();
    };
    document.addEventListener('keydown', keyFn, true);
    q('#mbpl').focus({ preventScroll: true }); wake();
  }

  function play(o) {
    const n = native();
    if (n && /^https?:\/\//i.test(o.src)) {
      n.play({ url: o.src, title: o.title || '', position: store.get('mbp:pos:' + o.src, 0), headers: o.headers || {} }).catch((error) => alert('Native ExoPlayer could not start: ' + (error?.message || 'Unknown error')));
    } else openHtml5(o);
  }

  /* ---------- Internet Archive (free movies) ---------- */
  const ROWS = 40;
  async function archiveSearch(q, page) {
    const query = (q ? `(${q}) AND ` : '') + 'collection:(feature_films) AND mediatype:(movies)';
    const u = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl[]=identifier&fl[]=title&fl[]=year&sort[]=downloads+desc&rows=${ROWS}&page=${page}&output=json`;
    const r = await fetch(u); if (!r.ok) throw new Error('Internet Archive error ' + r.status);
    const d = (await r.json()).response;
    return { total_pages: Math.max(1, Math.ceil(d.numFound / ROWS)), items: d.docs };
  }
  async function archiveFile(id) {
    const r = await fetch('https://archive.org/metadata/' + encodeURIComponent(id)); if (!r.ok) throw new Error('Could not load this movie');
    const files = (await r.json()).files || [];
    const rank = (f) => (/h\.264|mpeg4/i.test(f.format || '') && /\.mp4$/i.test(f.name) ? 0 : /\.mp4$/i.test(f.name) ? 1 : /\.(webm|ogv)$/i.test(f.name) ? 2 : 9);
    const best = files.filter((f) => rank(f) < 9).sort((a, b) => rank(a) - rank(b) || (+b.size || 0) - (+a.size || 0))[0];
    if (!best) throw new Error('No playable video file found');
    return `https://archive.org/download/${encodeURIComponent(id)}/${best.name.split('/').map(encodeURIComponent).join('/')}`;
  }

  /* ---------- Player screen ---------- */
  window.MB_playerView = function (view) {
    const tab = store.get('ptab', 'free');
    view.innerHTML = `<h1 class="page">Player</h1><div class="chips" id="ptabs"></div><div id="pbody"></div>
      <p class="note pad">${native() ? 'Playing with native Media3 ExoPlayer.' : 'Playing with the built-in HTML5 player.'} Free movies come from the Internet Archive.</p>`;
    [['free', 'Free Movies'], ['file', 'My Files'], ['url', 'Video Link']].forEach(([k, n]) => {
      const b = html(`<button class="chip ${k === tab ? 'on' : ''}">${n}</button>`);
      b.onclick = () => { store.set('ptab', k); window.MB_playerView(view); }; $('#ptabs').appendChild(b);
    });
    const body = $('#pbody');
    if (tab === 'free') {
      body.innerHTML = `<div class="searchbar"><input id="pq" placeholder="Search free movies (e.g. Nosferatu, Charlie Chaplin)..." autocomplete="off"></div>
        <div class="grid" id="pgrid"></div><div class="center" id="pmore" style="min-height:90px"></div>`;
      const q = store.get('pq', '');
      $('#pq').value = q;
      $('#pq').addEventListener('keydown', (e) => { if (e.key === 'Enter') { store.set('pq', e.target.value.trim()); window.MB_playerView(view); } });
      infinite($('#pgrid'), $('#pmore'), (page) => archiveSearch(q, page), (m) => {
        const a = html(`<a class="card pos" href="#/player" tabindex="0"><div class="poster"><img loading="lazy" alt="" src="https://archive.org/services/img/${esc(m.identifier)}"></div>
          <div class="t">${esc(m.title || m.identifier)}</div><div class="s">${esc(m.year || '')}</div></a>`);
        a.onclick = async (e) => {
          e.preventDefault();
          try { const src = await archiveFile(m.identifier); play({ src, title: m.title || m.identifier }); }
          catch (err) { alert(err.message); }
        };
        return a;
      });
    } else if (tab === 'file') {
      body.innerHTML = `<div class="pad"><p>Pick a video or audio file from this device.</p><label class="btn primary" tabindex="0" id="pfl">Choose file<input type="file" id="pfile" accept="video/*,audio/*,.mkv,.m3u8" style="display:none"></label></div>`;
      $('#pfl').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#pfile').click(); });
      $('#pfile').onchange = (e) => { const f = e.target.files[0]; if (f) openHtml5({ src: URL.createObjectURL(f), title: f.name }); };
    } else {
      const recent = store.get('purls', []);
      body.innerHTML = `<div class="searchbar"><input id="purl" placeholder="Paste a direct video link (.mp4, .webm, .m3u8 ...)" autocomplete="off"></div>
        <div class="pad" style="display:flex;gap:10px"><button class="btn primary" id="pgo">Play</button></div><div class="chips" id="prec"></div>`;
      const go = (src) => {
        src = (src || '').trim();
        if (!/^https?:\/\//i.test(src)) { alert('Enter a full link starting with http:// or https://'); return; }
        store.set('purls', [src, ...store.get('purls', []).filter((x) => x !== src)].slice(0, 8));
        play({ src, title: src.split('/').pop().split('?')[0] || 'Video' });
      };
      $('#pgo').onclick = () => go($('#purl').value);
      $('#purl').addEventListener('keydown', (e) => { if (e.key === 'Enter') go(e.target.value); });
      recent.forEach((u) => { const c = html(`<button class="chip sm">${esc(u.length > 60 ? u.slice(0, 57) + '…' : u)}</button>`); c.onclick = () => go(u); $('#prec').appendChild(c); });
    }
  };
  window.MB_play = play;
})();
