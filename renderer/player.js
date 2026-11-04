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

  /* ---------- HTML5 player overlay ---------- */
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
      <video playsinline></video>
      <div class="mbp-msg" id="mbmsg"></div>
      <div class="mbp-ui">
        <div class="mbp-top"><button class="mb" id="mbback" aria-label="Back">←</button><span class="mbt">${esc(o.title || '')}</span>
          <button class="mb txt" id="mbcc">CC</button><button class="mb txt" id="mbsp">1×</button></div>
        <div class="mbp-mid"><button class="mb" id="mbrw">⏪ 10</button><button class="mb big" id="mbpl">⏸</button><button class="mb" id="mbff">10 ⏩</button></div>
        <div class="mbp-bot"><span id="mbcur">0:00</span><input type="range" id="mbseek" min="0" max="1000" value="0" aria-label="Seek"><span id="mbdur">0:00</span>
          <button class="mb" id="mbvol" aria-label="Mute">🔊</button><input type="range" id="mbvr" min="0" max="100" value="100" aria-label="Volume"><button class="mb" id="mbfs" aria-label="Fullscreen">⛶</button></div>
      </div></div>`);
    ov = R;
    document.body.appendChild(R);
    try { history.pushState({ p: 1 }, ''); pushed = true; } catch { pushed = false; }
    const v = $('video', R), msg = $('#mbmsg', R);
    const posKey = o.src.startsWith('blob:') ? null : 'mbp:pos:' + o.src;
    const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2]; let si = 2;
    const say = (t, ms = 1400) => { msg.textContent = t; msg.classList.add('on'); clearTimeout(say.t); say.t = setTimeout(() => msg.classList.remove('on'), ms); };
    const wake = () => { R.classList.remove('idle'); clearTimeout(hideT); hideT = setTimeout(() => { if (!v.paused) R.classList.add('idle'); }, 3500); };
    const seek = (d) => { if (isFinite(v.duration)) v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + d)); say((d > 0 ? '+' : '') + d + 's'); };
    const toggle = () => (v.paused ? v.play() : v.pause());
    const vol = (d) => { v.volume = Math.max(0, Math.min(1, v.volume + d)); $('#mbvr', R).value = v.volume * 100; say('Volume ' + Math.round(v.volume * 100) + '%'); };

    // source (HLS via hls.js when needed)
    if (/\.m3u8(\?|$)/i.test(o.src) && window.Hls && window.Hls.isSupported()) {
      hls = new window.Hls(); hls.loadSource(o.src); hls.attachMedia(v);
      hls.on(window.Hls.Events.ERROR, (_, d) => { if (d.fatal) say('Stream error: ' + d.type, 4000); });
    } else v.src = o.src;
    v.addEventListener('error', () => say("Can't play this video (format not supported or link blocked).", 5000));
    v.addEventListener('loadedmetadata', () => {
      $('#mbdur', R).textContent = fmt(v.duration);
      const saved = posKey ? store.get(posKey, 0) : 0;
      if (saved > 10 && saved < v.duration - 30) { v.currentTime = saved; say('Resumed at ' + fmt(saved)); }
    });
    v.addEventListener('timeupdate', () => { $('#mbcur', R).textContent = fmt(v.currentTime); if (v.duration) $('#mbseek', R).value = (v.currentTime / v.duration) * 1000; });
    v.addEventListener('play', () => { $('#mbpl', R).textContent = '⏸'; wake(); });
    v.addEventListener('pause', () => { $('#mbpl', R).textContent = '▶'; R.classList.remove('idle'); });
    v.addEventListener('ended', () => { say('Finished', 3000); R.classList.remove('idle'); if (posKey) store.set(posKey, 0); });
    v.addEventListener('waiting', () => say('Buffering…', 800));
    saveT = setInterval(() => { if (posKey && v.currentTime > 5) store.set(posKey, v.currentTime); }, 5000);
    v.play().catch(() => { $('#mbpl', R).textContent = '▶'; });

    $('#mbback', R).onclick = () => closePlayer();
    $('#mbpl', R).onclick = toggle; v.onclick = toggle;
    $('#mbrw', R).onclick = () => seek(-10); $('#mbff', R).onclick = () => seek(10);
    $('#mbseek', R).oninput = (e) => { if (v.duration) v.currentTime = (e.target.value / 1000) * v.duration; };
    $('#mbvr', R).oninput = (e) => { v.volume = e.target.value / 100; v.muted = false; };
    $('#mbvol', R).onclick = () => { v.muted = !v.muted; $('#mbvol', R).textContent = v.muted ? '🔇' : '🔊'; };
    $('#mbsp', R).onclick = () => { si = (si + 1) % speeds.length; v.playbackRate = speeds[si]; $('#mbsp', R).textContent = speeds[si] + '×'; };
    const fsBtn = $('#mbfs', R);
    if (!document.fullscreenEnabled) fsBtn.style.display = 'none';
    fsBtn.onclick = () => (document.fullscreenElement ? document.exitFullscreen() : R.requestFullscreen().catch(() => {}));
    $('#mbcc', R).onclick = () => {
      const inp = document.createElement('input'); inp.type = 'file'; inp.accept = '.vtt,.srt,text/vtt';
      inp.onchange = async () => {
        const f = inp.files[0]; if (!f) return; let txt = await f.text();
        if (!/^WEBVTT/.test(txt)) txt = srt2vtt(txt);
        if (subUrl) URL.revokeObjectURL(subUrl);
        subUrl = URL.createObjectURL(new Blob([txt], { type: 'text/vtt' }));
        v.querySelectorAll('track').forEach((t) => t.remove());
        const tr = document.createElement('track'); tr.kind = 'subtitles'; tr.label = f.name; tr.default = true; tr.src = subUrl; v.appendChild(tr);
        setTimeout(() => { if (v.textTracks[0]) v.textTracks[0].mode = 'showing'; }, 100); say('Subtitles: ' + f.name, 2500);
      };
      inp.click();
    };

    // remote / keyboard: arrows seek, up/down move between controls, OK activates, Back closes
    const order = ['#mbback', '#mbpl', '#mbseek', '#mbfs'].map((s) => $(s, R)).filter((e) => e.style.display !== 'none');
    let fi = 1;
    keyFn = (e) => {
      if (!ov) return;
      const k = e.key; wake();
      if (k === 'Escape' || k === 'GoBack' || k === 'BrowserBack') { e.preventDefault(); e.stopPropagation(); closePlayer(); return; }
      if (k === 'ArrowLeft') { e.preventDefault(); e.stopPropagation(); seek(e.shiftKey ? -30 : -10); }
      else if (k === 'ArrowRight') { e.preventDefault(); e.stopPropagation(); seek(e.shiftKey ? 30 : 10); }
      else if (k === 'ArrowUp') { e.preventDefault(); e.stopPropagation(); if (R.classList.contains('idle')) return; fi = Math.max(0, fi - 1); order[fi].focus(); }
      else if (k === 'ArrowDown') { e.preventDefault(); e.stopPropagation(); fi = Math.min(order.length - 1, fi + 1); order[fi].focus(); }
      else if (k === ' ' || k === 'MediaPlayPause') { e.preventDefault(); e.stopPropagation(); toggle(); }
      else if (k === 'MediaPlay') v.play(); else if (k === 'MediaPause') v.pause();
      else if (k === 'MediaFastForward') seek(30); else if (k === 'MediaRewind') seek(-30);
      else if (k === 'f' || k === 'F') fsBtn.click();
      else if (k === 'm' || k === 'M') $('#mbvol', R).click();
      else if (k === '+') vol(0.1); else if (k === '-') vol(-0.1);
      else if (k === 'Enter' && document.activeElement === R) { e.preventDefault(); toggle(); }
    };
    document.addEventListener('keydown', keyFn, true);
    R.addEventListener('mousemove', wake); R.addEventListener('touchstart', wake, { passive: true });
    $('#mbpl', R).focus(); wake();
  }

  function play(o) {
    const n = native();
    if (n && /^https?:\/\//i.test(o.src)) {
      n.play({ url: o.src, title: o.title || '', position: store.get('mbp:pos:' + o.src, 0) }).catch(() => openHtml5(o));
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
