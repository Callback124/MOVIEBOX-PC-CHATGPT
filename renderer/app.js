'use strict';
/* ---------- helpers ---------- */
const IMG = 'https://image.tmdb.org/t/p/';
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const html = (h) => { const t = document.createElement('template'); t.innerHTML = h.trim(); return t.content.firstElementChild; };
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ } }
};
const ICON = {
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
  list: '<svg viewBox="0 0 24 24"><path d="M5 4h14v17l-7-4-7 4z"/></svg>',
  history: '<svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 2"/></svg>',
  gear: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>'
};
$('#btn-search').innerHTML = ICON.search;
$('#btn-list').innerHTML = ICON.list;
$('#btn-history').innerHTML = ICON.history;
$('#btn-settings').innerHTML = ICON.gear;

const view = $('#view');
const spinner = '<div class="center"><div class="spinner"></div></div>';

/* ---------- TMDB client ---------- */
const cache = new Map();
async function api(path, params = {}) {
  const key = (store.get('key', '') || '').trim();
  if (!key) throw new Error('NO_KEY');
  const u = new URL('https://api.themoviedb.org/3' + path);
  const p = { language: store.get('lang', 'en-US'), ...params };
  for (const [k, v] of Object.entries(p)) if (v != null && v !== '') u.searchParams.set(k, v);
  const headers = {};
  if (key.length > 40) headers.Authorization = 'Bearer ' + key; else u.searchParams.set('api_key', key);
  const ck = u.toString();
  if (cache.has(ck)) return cache.get(ck);
  const r = await fetch(u, { headers });
  if (r.status === 401) throw new Error('BAD_KEY');
  if (!r.ok) throw new Error('HTTP ' + r.status);
  const data = await r.json();
  cache.set(ck, data);
  return data;
}
const img = (p, size = 'w342') => (p ? (/^https?:\/\//i.test(String(p)) ? String(p) : IMG + size + p) : '');
const region = () => store.get('region', 'IN');

let genreMap = null;
async function genres() {
  if (genreMap) return genreMap;
  genreMap = {};
  try {
    const [m, t] = await Promise.all([api('/genre/movie/list'), api('/genre/tv/list')]);
    [...m.genres, ...t.genres].forEach((g) => { genreMap[g.id] = g.name; });
  } catch { /* non-fatal */ }
  return genreMap;
}

/* ---------- local lists ---------- */
const myList = {
  all: () => store.get('mylist', []),
  has: (type, id) => myList.all().some((x) => x.id === id && x.type === type),
  toggle(item) {
    let a = myList.all();
    if (myList.has(item.type, item.id)) a = a.filter((x) => !(x.id === item.id && x.type === item.type));
    else a.unshift(item);
    store.set('mylist', a);
  }
};
function pushHistory(item) {
  let a = store.get('history', []).filter((x) => !(x.id === item.id && x.type === item.type));
  a.unshift(item);
  store.set('history', a.slice(0, 60));
}
const toLite = (type, d) => ({ id: d.id, type, title: d.title || d.name, poster: d.poster_path, year: (d.release_date || d.first_air_date || '').slice(0, 4), vote: d.vote_average });

/* ---------- components ---------- */
// at least 10 cards per line; more on wider screens (~100px minimum card width)
const colsNow = () => Math.max(10, Math.min(18, Math.floor((window.innerWidth * 0.92 + 10) / 110)));
function applyCols() { document.documentElement.style.setProperty('--cols', colsNow()); }
applyCols();
let rowFills = [];

function card(item, forced, opts = {}) {
  const type = item.type || item.media_type || forced;
  const title = item.title || item.name;
  const year = item.year ?? (item.release_date || item.first_air_date || '').slice(0, 4);
  const poster = item.poster ?? item.poster_path;
  const vote = item.vote ?? item.vote_average;
  const voteStr = Number(vote) > 0 ? `<span class="badge rate">★ ${Number(vote).toFixed(1)}</span>` : '';
  const href = item.moviebox ? `#/title/${esc(type)}/${encodeURIComponent(String(item.id))}` : `#/title/${esc(type)}/${item.id}`;
  const identity = item.movieboxIdentity || (item.moviebox ? identifyMovieBoxCard(item) : null);
  const typeBadge = identity ? `<span class="badge type">${esc(identity.badgeText)}</span>` : '';
  if (opts.rank) {
    return html(`<a class="card top10" href="${href}"><span class="num">${opts.rank}</span>
      <div class="tp">${poster ? `<img loading="lazy" alt="" src="${esc(img(poster))}">` : ''}</div></a>`);
  }
  return html(`<a class="card pos" href="${href}">
    <div class="poster">${poster ? `<img loading="lazy" alt="" src="${esc(img(poster))}">` : `<div class="noimg">${esc(title)}</div>`}
      ${typeBadge}${voteStr}</div>
    <div class="t">${esc(title)}</div><div class="s">${esc(year)}</div></a>`);
}

// A row shows exactly as many cards as fit the screen width (no horizontal scrolling).
function row(title, items, forced, moreHref, opts = {}) {
  const sec = html(`<section class="row"><div class="row-head"><h3>${esc(title)}</h3>${moreHref ? `<a href="${moreHref}">Explore all ›</a>` : ''}</div><div class="scroller"></div></section>`);
  const sc = $('.scroller', sec);
  if (opts.top) sc.classList.add('toprow');
  const list = items.filter((i) => i.poster_path || i.poster);
  const fill = () => {
    sc.innerHTML = '';
    const n = opts.top ? 10 : colsNow();
    list.slice(0, n).forEach((i, k) => sc.appendChild(card(i, forced, opts.top ? { rank: k + 1 } : {})));
  };
  fill(); rowFills.push(fill);
  return sec;
}
let resizeT;
window.addEventListener('resize', () => { clearTimeout(resizeT); resizeT = setTimeout(() => { applyCols(); rowFills.forEach((f) => f()); }, 120); });

function showError(e) {
  if (e.message === 'NO_KEY' || e.message === 'BAD_KEY') {
    view.innerHTML = `<div class="center"><div><h2>${e.message === 'NO_KEY' ? 'Add your TMDB API key' : 'TMDB API key was rejected'}</h2>
      <p>Create a free account at themoviedb.org, open Settings → API, and paste your API key (or Read Access Token) here.</p>
      <button class="btn primary" id="open-set">Open settings</button></div></div>`;
    $('#open-set').onclick = openSettings;
  } else {
    view.innerHTML = `<div class="center"><div><h2>Something went wrong</h2><p>${esc(e.message)}</p><button class="btn" onclick="location.reload()">Retry</button></div></div>`;
  }
}

/* ---------- modal ---------- */
const modal = $('#modal');
let modalPushed = false;
function openModal(inner, cls = '') {
  modal.innerHTML = `<div class="box ${cls}"><button class="close" aria-label="Close">✕</button>${inner}</div>`;
  modal.classList.remove('hidden');
  $('.close', modal).onclick = () => closeModal();
  if (!modalPushed) { try { history.pushState({ m: 1 }, ''); modalPushed = true; } catch { /* ignore */ } }
  const first = $('.box input, .box select, .box .close', modal); if (first) first.focus();
}
function closeModal(fromPop) {
  modal.classList.add('hidden'); modal.innerHTML = '';
  if (modalPushed) { modalPushed = false; if (!fromPop) history.back(); }
}
// TV remote / Android Back button closes the popup instead of leaving the page
window.addEventListener('popstate', () => { if (modalPushed) closeModal(true); });
modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });
document.addEventListener('keydown', (e) => { if ((e.key === 'Escape' || e.key === 'GoBack') && modalPushed) closeModal(); });

function playTrailer(videoKey) {
  openModal(`<div class="video"><iframe allowfullscreen allow="autoplay; encrypted-media; picture-in-picture; fullscreen" referrerpolicy="strict-origin-when-cross-origin" src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoKey)}?autoplay=1&rel=0"></iframe></div>`);
}
async function trailerFor(type, id) {
  const lang = store.get('lang', 'en-US').slice(0, 2);
  const d = await api(`/${type}/${id}/videos`, { include_video_language: `${lang},en,null` });
  const yt = (d.results || []).filter((v) => v.site === 'YouTube');
  return (yt.find((v) => v.type === 'Trailer' && v.official) || yt.find((v) => v.type === 'Trailer') || yt.find((v) => v.type === 'Teaser') || yt[0])?.key;
}
async function watchTrailer(type, id) {
  try {
    const k = await trailerFor(type, id);
    if (k) playTrailer(k); else openModal('<div class="center">No trailer available for this title.</div>', 'small');
  } catch (e) { openModal(`<div class="center">${esc(e.message)}</div>`, 'small'); }
}

function openSettings() {
  const lang = store.get('lang', 'en-US'), reg = region();
  const langs = [['en-US', 'English'], ['hi-IN', 'हिन्दी (Hindi)'], ['ta-IN', 'Tamil'], ['te-IN', 'Telugu'], ['bn-IN', 'Bengali'], ['ml-IN', 'Malayalam'], ['kn-IN', 'Kannada'], ['es-ES', 'Español'], ['fr-FR', 'Français']];
  const regs = [['IN', 'India'], ['US', 'United States'], ['GB', 'United Kingdom'], ['CA', 'Canada'], ['AU', 'Australia'], ['AE', 'UAE'], ['DE', 'Germany'], ['FR', 'France']];
  openModal(`<h2 style="margin-top:0">Settings</h2>
    <label class="field" for="s-key">TMDB API key or Read Access Token</label>
    <input id="s-key" type="password" autocomplete="off" placeholder="Paste your key" value="${esc(store.get('key', ''))}">
    <p class="note">MovieBox search session is fetched automatically from the Home API.</p>
    <label class="field" for="s-lang">Content language</label>
    <select id="s-lang">${langs.map(([v, n]) => `<option value="${v}" ${v === lang ? 'selected' : ''}>${n}</option>`).join('')}</select>
    <label class="field" for="s-reg">Region (for "where to watch")</label>
    <select id="s-reg">${regs.map(([v, n]) => `<option value="${v}" ${v === reg ? 'selected' : ''}>${n}</option>`).join('')}</select>
    <p class="note"><b>About:</b> This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability data provided by JustWatch. Music previews and artwork provided by Apple.</p>
    <p class="note">Your key is stored only on this computer. Get a free key at themoviedb.org → Settings → API.</p>
    <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:14px"><button class="btn" id="s-clear">Clear history</button><button class="btn primary" id="s-save">Save</button></div>`, 'small');
  $('#s-save').onclick = () => {
    store.set('key', $('#s-key').value.trim()); store.set('lang', $('#s-lang').value); store.set('region', $('#s-reg').value);
    cache.clear(); genreMap = null; closeModal(); route();
  };
  $('#s-clear').onclick = () => { store.set('history', []); store.set('recents', []); closeModal(); route(); };
}
$('#btn-settings').onclick = openSettings;

/* ---------- views ---------- */
let heroTimer = null;
const MOVIEBOX_HOME_ENDPOINTS = [
  'https://h5-api.aoneroom.com/wefeed-h5api-bff/home',
  'https://movieboxph.org/wefeed-h5api-bff/home'
];
const MOVIEBOX_HOME_HEADERS = {
  'Accept': '*/*',
  'Accept-Language': 'en-US,en;q=0.9',
  'x-client-info': JSON.stringify({ timezone: 'Asia/Calcutta' })
};
async function fetchMovieBoxHome() {
  const nativeApi = window.Capacitor?.Plugins?.MovieBoxHomeApi;
  if (nativeApi?.getHome) {
    const result = await nativeApi.getHome();
    const payload = result?.response || result;
    // Native Android bridge extracts the fresh JWT from the /home x-user response header.
    if (result?.token) {
      window.__movieBoxSessionToken = result.token;
      store.set('movieboxToken', result.token);
    }
    if (payload?.data) return payload;
    throw new Error(payload?.message || 'MovieBox Home API returned no data');
  }
  let lastError;
  for (const endpoint of MOVIEBOX_HOME_ENDPOINTS) {
    try {
      const response = await fetch(endpoint, { method: 'GET', headers: MOVIEBOX_HOME_HEADERS, cache: 'no-store' });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const payload = await response.json();
      if (!payload?.data) throw new Error('Unexpected Home API response');
      return payload;
    } catch (error) { lastError = error; }
  }
  throw new Error('MovieBox Home API unavailable. ' + (lastError?.message || 'Network error'));
}
const MOVIEBOX_SEARCH_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json, text/plain, */*',
  'x-client-info': JSON.stringify({ timezone: 'Asia/Calcutta' })
};
async function movieBoxSearchRequest(kind, keyword, page = 1) {
  let token = (window.__movieBoxSessionToken || store.get('movieboxToken', '') || '').trim();
  const nativeApi = window.Capacitor?.Plugins?.MovieBoxHomeApi;
  // If no cached session exists, obtain it from the native /home response header.
  if (kind === 'search' && !token && nativeApi?.getHome) {
    const homeResult = await nativeApi.getHome();
    token = (homeResult?.token || '').trim();
    if (token) {
      window.__movieBoxSessionToken = token;
      store.set('movieboxToken', token);
    }
  }
  if (kind === 'search' && !token) {
    throw new Error('Fresh token was not present in the /home response x-user header. Retry once the service is reachable.');
  }
  if (nativeApi) {
    const result = kind === 'suggest'
      ? await nativeApi.searchSuggest({ keyword })
      : await nativeApi.search({ keyword, page, pageSize: 24, token });
    const payload = result?.response || result;
    if (payload?.data) return payload;
    throw new Error(payload?.message || 'MovieBox search API returned no data');
  }
  const endpoints = [
    'https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/',
    'https://movieboxph.org/wefeed-h5api-bff/subject/'
  ];
  let lastError;
  for (const base of endpoints) {
    try {
      const isSuggest = kind === 'suggest';
      const headers = { ...MOVIEBOX_SEARCH_HEADERS };
      if (!isSuggest) {
        headers.Authorization = 'Bearer ' + token;
        headers['x-user'] = token;
      }
      const response = await fetch(base + (isSuggest ? 'search-suggest' : 'search'), {
        method: 'POST',
        headers,
        body: JSON.stringify(isSuggest ? { keyword } : { keyword, page, perPage: 24 }),
        cache: 'no-store'
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const payload = await response.json();
      if (payload?.code !== 0 || !payload?.data) throw new Error(payload?.message || 'Unexpected MovieBox search response');
      return payload;
    } catch (error) { lastError = error; }
  }
  throw new Error('MovieBox search API unavailable. ' + (lastError?.message || 'Network error'));
}
function movieBoxUrl(value) {
  if (!value) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'object') return value.url || value.src || value.imageUrl || value.coverUrl || '';
  return '';
}
function identifyMovieBoxCard(item = {}) {
  const id = String(item.subjectId ?? item.subject_id ?? item.id ?? item.idStr ?? '');
  const raw = item.subjectType ?? item.subject_type ?? item.type;
  const type = Number(raw);
  const corner = String(item.corner || '').toUpperCase();
  const genre = String(item.genre || '').toUpperCase();
  let category = 'MOVIE', badgeText = 'MOVIE', isSeries = false, isPlayable = true;
  if (type === 0 || id === '' || id === '0') { category = 'PROMO'; badgeText = 'PROMO'; isPlayable = false; }
  else if (type === 7 || corner.includes('SHORT') || genre.includes('SHORT')) { category = 'SHORT_DRAMA'; badgeText = 'SHORT DRAMA'; isSeries = true; }
  else if (type === 5 || genre.includes('ANIME') || genre.includes('ANIMATION')) { category = 'ANIME'; badgeText = 'ANIME'; isSeries = true; }
  else if (type === 2 || type === 4 || corner.includes('EP') || corner.includes('SEASON') || genre.includes('SERIES') || genre.includes('TV SHOW')) { category = 'SERIES'; badgeText = type === 4 ? 'TV SHOW' : 'TV SERIES'; isSeries = true; }
  else if (type === 3 || genre.includes('DOCUMENTARY')) { category = 'DOCUMENTARY'; badgeText = 'DOCUMENTARY'; }
  else if (type === 6 || genre.includes('MUSIC') || genre.includes('CONCERT')) { category = 'MUSIC'; badgeText = 'MUSIC'; }
  else if (type === 9 || genre.includes('SPORT')) { category = 'SPORTS'; badgeText = 'SPORTS'; }
  return { category, badgeText, isSeries, isPlayable, playParams: isSeries ? { se: 1, ep: 1 } : { se: 0, ep: 0 } };
}
function movieBoxItem(raw) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const item = Object.assign({}, source, source.subject && typeof source.subject === 'object' ? source.subject : {});
  const pick = (...values) => values.map(movieBoxUrl).find(Boolean) || '';
  const id = String(item.subjectId ?? item.subject_id ?? item.id ?? item.idStr ?? '');
  const identity = identifyMovieBoxCard({ ...item, subjectId: id });
  const type = identity.isSeries ? 'tv' : 'movie';
  const poster = pick(item.coverVertical, item.verticalCover, item.poster, item.posterUrl, item.cover, item.coverUrl, item.image, item.thumbnail, item.pic);
  const backdrop = pick(source.image, source.banner, source.bannerImage, source.coverHorizontal, source.horizontalCover,
    item.coverHorizontal, item.horizontalCover, item.backdrop, item.backdropUrl, item.cover, item.image, poster);
  const date = item.releaseDate || item.release_date || item.year || item.releaseTime || '';
  const year = String(date).match(/(19|20)\\d{2}/)?.[0] || '';
  const rating = Number(item.imdbRatingValue ?? item.imdbRating ?? item.rating ?? item.score ?? item.voteAverage ?? item.vote_average ?? 0);
  return { ...item, id, type, media_type: type, moviebox: true, movieboxIdentity: identity,
    title: item.title || item.name || item.subjectName || item.originalTitle || 'Untitled',
    name: item.title || item.name || item.subjectName || item.originalTitle || 'Untitled',
    poster_path: poster, poster, backdrop_path: backdrop,
    overview: item.description || item.introduction || item.overview || '',
    release_date: year, first_air_date: year,
    vote_average: rating > 10 ? rating / 10 : rating, genre_ids: [] };
}

async function homeView() {
  view.innerHTML = spinner;
  try {
    const payload = await fetchMovieBoxHome();
    const data = payload.data || {};
    const operatingList = Array.isArray(data.operatingList) ? data.operatingList : [];
    const bannerBlock = operatingList[0]?.banner;
    const bannerItems = Array.isArray(bannerBlock?.items) ? bannerBlock.items : [];
    const slides = bannerItems.map(movieBoxItem).filter((x) => x.backdrop_path || x.poster_path).slice(0, 7);
    const sections = operatingList.slice(1).map((op) => ({
      title: op.title || op.name || op.subjectTitle || op.label || 'Recommended',
      items: (Array.isArray(op.subjects) ? op.subjects : []).map(movieBoxItem).filter((x) => x.poster_path)
    })).filter((section) => section.items.length);
    if (!slides.length && !sections.length) throw new Error('The Home API returned no banners or subject rows.');
    view.innerHTML = '';
    const hero = html('<div class="hero"><div class="dots"></div></div>');
    slides.forEach((s, i) => {
      const title = s.title || s.name;
      const el = html(`<div class="slide ${i === 0 ? 'on' : ''}"><div class="info">
        <h2>${esc(title)}</h2>
        <div class="meta"><span class="match">${s.vote_average ? Math.round(s.vote_average * 10) + '% Match' : 'Featured'}</span><span>${esc(s.release_date || '')}</span><span class="age">HD</span></div>
        <p>${esc(s.overview)}</p>
        <div class="hbtns"><button class="hb play" data-id="${esc(s.id)}">▶ Play Trailer</button><a class="hb more" href="#/title/${esc(s.type)}/${encodeURIComponent(s.id)}">ⓘ More Info</a></div></div></div>`);
      el.style.backgroundImage = `url("${String(s.backdrop_path || s.poster_path).replace(/["\\\\]/g, '')}")`;
      $('button.play', el).onclick = () => {
        openModal('<div class="center">Trailer lookup is not provided by the Home API.</div>', 'small');
      };
      hero.insertBefore(el, $('.dots', hero));
      const dot = document.createElement('i'); if (i === 0) dot.className = 'on';
      dot.onclick = () => go(i); $('.dots', hero).appendChild(dot);
    });
    let cur = 0;
    function go(i) {
      cur = i;
      hero.querySelectorAll('.slide').forEach((n, k) => n.classList.toggle('on', k === i));
      hero.querySelectorAll('.dots i').forEach((n, k) => n.classList.toggle('on', k === i));
    }
    clearInterval(heroTimer);
    heroTimer = setInterval(() => { if (!document.hidden && slides.length) go((cur + 1) % slides.length); }, 7000);
    if (slides.length) view.appendChild(hero);
    sections.forEach((section, index) => view.appendChild(row(section.title, section.items, null, null, { top: index === 0 && /top\s*10/i.test(section.title) })));
  } catch (error) {
    showError(error);
  }
}
const BROWSE = {
  trending: { title: 'Trending', cats: [['Today', '/trending/all/day', {}, null], ['This Week', '/trending/all/week', {}, null]] },
  movie: { title: 'Movies', cats: [
    ['Popular', '/movie/popular', {}, 'movie'], ['Top Rated', '/movie/top_rated', {}, 'movie'],
    ['Now Playing', '/movie/now_playing', { region: 'IN' }, 'movie'], ['Upcoming', '/movie/upcoming', { region: 'IN' }, 'movie']] },
  tv: { title: 'TV Shows', cats: [
    ['Popular', '/tv/popular', {}, 'tv'], ['Top Rated', '/tv/top_rated', {}, 'tv'],
    ['On The Air', '/tv/on_the_air', {}, 'tv'], ['Airing Today', '/tv/airing_today', {}, 'tv']] },
  anime: { title: 'Anime', cats: [
    ['Popular Series', '/discover/tv', { with_genres: 16, with_original_language: 'ja', sort_by: 'popularity.desc' }, 'tv'],
    ['Top Rated Series', '/discover/tv', { with_genres: 16, with_original_language: 'ja', sort_by: 'vote_average.desc', 'vote_count.gte': 300 }, 'tv'],
    ['Anime Movies', '/discover/movie', { with_genres: 16, with_original_language: 'ja', sort_by: 'popularity.desc' }, 'movie']] },
  short: { title: 'Short TV', cats: [
    ['Quick Watch (under 15 min)', '/discover/tv', { 'with_runtime.lte': 15, 'with_runtime.gte': 1, sort_by: 'popularity.desc', 'vote_count.gte': 5 }, 'tv'],
    ['Under 30 min', '/discover/tv', { 'with_runtime.lte': 30, 'with_runtime.gte': 1, sort_by: 'popularity.desc', 'vote_count.gte': 20 }, 'tv'],
    ['Mini Series', '/discover/tv', { with_type: 2, sort_by: 'popularity.desc' }, 'tv'],
    ['Top Rated Shorts', '/discover/tv', { 'with_runtime.lte': 30, 'with_runtime.gte': 1, sort_by: 'vote_average.desc', 'vote_count.gte': 100 }, 'tv']] },
  regional: { title: 'Regional', cats: [
    ['Hindi Movies', '/discover/movie', { with_original_language: 'hi', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Hindi Series', '/discover/tv', { with_original_language: 'hi', sort_by: 'popularity.desc' }, 'tv'],
    ['Tamil Movies', '/discover/movie', { with_original_language: 'ta', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Tamil Series', '/discover/tv', { with_original_language: 'ta', sort_by: 'popularity.desc' }, 'tv'],
    ['Telugu Movies', '/discover/movie', { with_original_language: 'te', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Telugu Series', '/discover/tv', { with_original_language: 'te', sort_by: 'popularity.desc' }, 'tv'],
    ['Kannada Movies', '/discover/movie', { with_original_language: 'kn', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Kannada Series', '/discover/tv', { with_original_language: 'kn', sort_by: 'popularity.desc' }, 'tv'],
    ['Malayalam Movies', '/discover/movie', { with_original_language: 'ml', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Malayalam Series', '/discover/tv', { with_original_language: 'ml', sort_by: 'popularity.desc' }, 'tv'],
    ['Bengali Movies', '/discover/movie', { with_original_language: 'bn', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Bengali Series', '/discover/tv', { with_original_language: 'bn', sort_by: 'popularity.desc' }, 'tv'],
    ['Marathi Movies', '/discover/movie', { with_original_language: 'mr', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Marathi Series', '/discover/tv', { with_original_language: 'mr', sort_by: 'popularity.desc' }, 'tv'],
    ['Punjabi Movies', '/discover/movie', { with_original_language: 'pa', sort_by: 'popularity.desc', 'vote_count.gte': 10 }, 'movie'], ['Punjabi Series', '/discover/tv', { with_original_language: 'pa', sort_by: 'popularity.desc' }, 'tv']] },
  originals: { title: 'Originals', cats: [
    ['Netflix', '/discover/tv', { with_networks: 213, sort_by: 'popularity.desc' }, 'tv'],
    ['Prime Video', '/discover/tv', { with_networks: 1024, sort_by: 'popularity.desc' }, 'tv'],
    ['Apple TV+', '/discover/tv', { with_networks: 2552, sort_by: 'popularity.desc' }, 'tv'],
    ['Disney+', '/discover/tv', { with_networks: 2739, sort_by: 'popularity.desc' }, 'tv'],
    ['HBO', '/discover/tv', { with_networks: 49, sort_by: 'popularity.desc' }, 'tv'],
    ['Hulu', '/discover/tv', { with_networks: 453, sort_by: 'popularity.desc' }, 'tv']] }
};


/* ---------- infinite grid: fills the screen, loads more as you scroll ---------- */
let stopInf = null;
function infinite(grid, more, fetchPage, mk) {
  if (stopInf) stopInf();
  let page = 1, total = 1, loading = false, done = false, count = 0, dead = false;
  const buf = [];
  async function step() {
    if (loading || dead) return;
    loading = true;
    try {
      for (;;) {
        const cols = colsNow();
        // only place whole rows so the last row is never half-empty (until the very end)
        const n = done ? buf.length : buf.length - (buf.length % cols);
        if (n > 0) buf.splice(0, n).forEach((x) => { grid.appendChild(mk(x)); count++; });
        const need = more.getBoundingClientRect().top < window.innerHeight + 500;
        if (!need || (done && !buf.length)) break;
        if (!done) {
          more.innerHTML = '<div class="spinner"></div>';
          const d = await fetchPage(page);
          if (dead) return;
          total = Math.min(d.total_pages || 1, 500); page++;
          if (page > total) done = true;
          buf.push(...d.items);
        }
      }
      more.innerHTML = done && !count ? 'No results found.' : '';
    } catch (e) { more.textContent = e.message; done = true; }
    loading = false;
    if (done && !buf.length) stop();
  }
  const onScroll = () => step();
  function stop() { dead = true; window.removeEventListener('scroll', onScroll); window.removeEventListener('resize', onScroll); if (stopInf === stop) stopInf = null; }
  stopInf = stop;
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll);
  step();
}

async function browseView(kind, catIdx = 0) {
  const cfg = BROWSE[kind]; if (!cfg) return (location.hash = '#/home');
  await genres();
  view.innerHTML = `<h1 class="page">${cfg.title}</h1><div class="chips" id="cats"></div><div class="grid" id="grid"></div><div class="center" id="more" style="min-height:90px"></div>`;
  const cats = $('#cats');
  cfg.cats.forEach((c, i) => {
    const b = html(`<button class="chip ${i === catIdx ? 'on' : ''}">${esc(c[0])}</button>`);
    b.onclick = () => browseView(kind, i); cats.appendChild(b);
  });
  const [, path, params, type] = cfg.cats[catIdx];
  const grid = $('#grid'), more = $('#more');
  infinite(grid, more, async (page) => {
    const d = await api(path, { ...params, page });
    return { total_pages: d.total_pages, items: d.results.filter((x) => x.poster_path && x.media_type !== 'person') };
  }, (x) => card(x, type));
}

async function searchView(q) {
  clearInterval(heroTimer);
  view.innerHTML = `<div class="searchbar"><span class="si">${ICON.search}</span><input id="q" placeholder="Search movies, series, anime..." autocomplete="off" value="${esc(q || '')}"><div class="sugg hidden" id="sugg"></div></div><div id="out"></div>`;
  const input = $('#q'), sugg = $('#sugg'), out = $('#out');
  if (!q) input.focus();
  const recents = () => store.get('recents', []);
  let suggestionTimer = null, suggestionRequest = 0;
  function renderRecents() {
    const r = recents();
    out.innerHTML = r.length ? `<div class="section-head"><h3>Recent</h3><button class="linkbtn" id="clr">🗑 Clear</button></div><div class="chips" id="rc"></div>` : '<div class="center">Type a title to start searching.</div>';
    if (!r.length) return;
    r.forEach((t) => {
      const c = html(`<button class="chip sm">${esc(t)}</button>`);
      c.onclick = () => { location.hash = '#/search/' + encodeURIComponent(t); };
      $('#rc').appendChild(c);
    });
    $('#clr').onclick = () => { store.set('recents', []); renderRecents(); };
  }
  async function results(text) {
    const keyword = String(text || '').trim();
    if (!keyword) return;
    sugg.classList.add('hidden');
    const recent = [keyword, ...recents().filter((x) => x.toLowerCase() !== keyword.toLowerCase())].slice(0, 12);
    store.set('recents', recent);
    out.innerHTML = `<div class="section-head"><h3>Results for "${esc(keyword)}"</h3></div><div class="grid" id="grid"></div><div class="center" id="more" style="min-height:90px"></div>`;
    const grid = $('#grid'), more = $('#more');
    infinite(grid, more, async (page) => {
      const payload = await movieBoxSearchRequest('search', keyword, page);
      const data = payload.data || {};
      const rawItems = Array.isArray(data.items) ? data.items : (Array.isArray(data.list) ? data.list : []);
      const items = rawItems.map(movieBoxItem).filter((item) => item.id && item.poster_path);
      const pager = data.pager || {};
      const totalPages = Number(pager.totalPages ?? pager.total_pages ?? 0)
        || (pager.hasMore === true ? page + 1 : page);
      return { total_pages: totalPages, items };
    }, (item) => card(item));
  }
  async function suggestions(keyword) {
    const requestId = ++suggestionRequest;
    try {
      const payload = await movieBoxSearchRequest('suggest', keyword);
      if (requestId !== suggestionRequest || input.value.trim() !== keyword) return;
      const data = payload.data || {};
      const rawItems = Array.isArray(data.items) ? data.items : (Array.isArray(data.list) ? data.list : []);
      const words = rawItems.map((item) => typeof item === 'string' ? item : item?.word || item?.title || '')
        .map((word) => String(word).trim()).filter(Boolean).slice(0, 8);
      sugg.innerHTML = '';
      words.forEach((word) => {
        const option = html(`<a href="#/search/${encodeURIComponent(word)}"><span>${esc(word)}<small>Search suggestion</small></span></a>`);
        sugg.appendChild(option);
      });
      sugg.classList.toggle('hidden', !words.length);
    } catch (error) {
      if (requestId === suggestionRequest) {
        sugg.textContent = error && error.message ? error.message : 'Suggestion request failed';
        sugg.classList.remove('hidden');
      }
    }
  }
  input.addEventListener('input', () => {
    clearTimeout(suggestionTimer);
    const keyword = input.value.trim();
    suggestionRequest++;
    if (keyword.length < 2) { sugg.classList.add('hidden'); return; }
    suggestionTimer = setTimeout(() => suggestions(keyword), 250);
  });
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && input.value.trim()) {
      clearTimeout(suggestionTimer);
      suggestionRequest++;
      location.hash = '#/search/' + encodeURIComponent(input.value.trim());
    } else if (event.key === 'Escape') sugg.classList.add('hidden');
  });
  if (q) results(q); else renderRecents();
}

async function detailView(type, id) {
  clearInterval(heroTimer);
  if (!['movie', 'tv'].includes(type)) return (location.hash = '#/home');
  view.innerHTML = spinner;
  const lang = store.get('lang', 'en-US').slice(0, 2);
  const d = await api(`/${type}/${id}`, { append_to_response: 'videos,credits,recommendations', include_video_language: `${lang},en,null` });
  const title = d.title || d.name, year = (d.release_date || d.first_air_date || '').slice(0, 4);
  const lite = toLite(type, d); pushHistory(lite);
  const runtime = d.runtime || (d.episode_run_time && d.episode_run_time[0]);

  const trailerKey = (d.videos?.results || []).filter((v) => v.site === 'YouTube').sort((a, b) => (b.type === 'Trailer') - (a.type === 'Trailer'))[0]?.key;

  view.innerHTML = '';
  const page = html(`<div>
    <div class="backdrop"></div>
    <div class="d-wrap">
      <div class="d-poster">${d.poster_path ? `<img alt="" src="${esc(img(d.poster_path, 'w500'))}">` : ''}</div>
      <div class="d-info">
        <h1>${esc(title)} <span style="color:var(--muted);font-weight:400">${year ? '(' + year + ')' : ''}</span></h1>
        ${d.tagline ? `<p class="tagline">${esc(d.tagline)}</p>` : ''}
        <div class="meta"><span class="pill type">${type === 'tv' ? 'SERIES' : 'MOVIE'}</span><span class="pill rate">★ ${Number(d.vote_average || 0).toFixed(1)}</span>
          ${runtime ? `<span>${runtime} min</span>` : ''}${type === 'tv' ? `<span>${d.number_of_seasons} season(s) · ${d.number_of_episodes} eps</span>` : ''}
          <span>${esc((d.genres || []).map((g) => g.name).join(' • '))}</span></div>
        <div style="display:flex;gap:10px;margin:14px 0;flex-wrap:wrap">
          ${trailerKey ? '<button class="btn primary" id="tr">▶ Watch Trailer</button>' : ''}
          <button class="btn" id="ml"></button>
          ${d.homepage ? `<a class="btn" href="${esc(d.homepage)}" target="_blank" rel="noopener">Official site ↗</a>` : ''}
        </div>
        <h3 class="h-sm">Synopsis</h3><p>${esc(d.overview || 'No synopsis available.')}</p>
        <h3 class="h-sm">Audio / Languages</h3>
        <div class="chips" style="padding:0">${(d.spoken_languages || []).map((l) => `<span class="chip sm">${esc(l.english_name)}</span>`).join('') || '<span class="note">Unknown</span>'}</div>
      </div>
    </div>
    <h3 class="h-sm pad" style="margin-top:30px">Cast &amp; Crew</h3><div class="cast" id="cast"></div>
    <div id="seasons"></div><div id="recs"></div></div>`);
  $('.backdrop', page).style.backgroundImage = d.backdrop_path ? `url(${img(d.backdrop_path, 'w1280')})` : 'none';
  const mlBtn = $('#ml', page);
  const syncML = () => { mlBtn.textContent = myList.has(type, d.id) ? '✓ In My List' : '+ My List'; };
  syncML(); mlBtn.onclick = () => { myList.toggle(lite); syncML(); };
  if (trailerKey) $('#tr', page).onclick = () => playTrailer(trailerKey);
  const castBox = $('#cast', page);
  const people = [...(d.credits?.cast || []).slice(0, 14), ...(d.credits?.crew || []).filter((c) => ['Director', 'Creator', 'Writer'].includes(c.job)).slice(0, 3)];
  people.forEach((p) => castBox.appendChild(html(`<div class="person"><div class="av">${p.profile_path ? `<img loading="lazy" alt="" src="${esc(img(p.profile_path, 'w185'))}">` : ''}</div><b>${esc(p.name)}</b><small>${esc(p.character || p.job || '')}</small></div>`)));
  if (!people.length) castBox.innerHTML = '<span class="note">No cast information.</span>';
  const recs = (d.recommendations?.results || []).filter((x) => x.poster_path);
  if (recs.length) $('#recs', page).appendChild(row('You may also like', recs, type));
  view.appendChild(page);

  if (type === 'tv' && d.seasons?.length) {
    const seasons = d.seasons.filter((s) => s.season_number > 0 && s.episode_count > 0);
    const list = seasons.length ? seasons : d.seasons;
    const box = $('#seasons', page);
    box.innerHTML = `<div class="section-head"><h3>Episodes</h3><select class="sel" id="ssel">${list.map((s) => `<option value="${s.season_number}">${esc(s.name)} (${s.episode_count} eps)</option>`).join('')}</select></div><div class="eps" id="eps"></div>`;
    const loadSeason = async (n) => {
      const eps = $('#eps', box); eps.innerHTML = '<div class="spinner" style="margin:20px"></div>';
      try {
        const s = await api(`/tv/${id}/season/${n}`);
        eps.innerHTML = '';
        s.episodes.forEach((e) => eps.appendChild(html(`<div class="ep"><div class="still">${e.still_path ? `<img loading="lazy" alt="" src="${esc(img(e.still_path, 'w300'))}">` : 'E' + e.episode_number}</div>
          <div class="b"><b>${e.episode_number}. ${esc(e.name)}</b><small>${esc(e.air_date || '')}${e.runtime ? ' · ' + e.runtime + ' min' : ''}${e.vote_average ? ' · ★ ' + e.vote_average.toFixed(1) : ''}</small><p>${esc(e.overview || '')}</p></div></div>`)));
      } catch (e) { eps.textContent = e.message; }
    };
    $('#ssel', box).onchange = (e) => loadSeason(e.target.value);
    loadSeason(list[0].season_number);
  }
}

function listView(kind) {
  clearInterval(heroTimer);
  const items = kind === 'mylist' ? myList.all() : store.get('history', []);
  const name = kind === 'mylist' ? 'My List' : 'History';
  view.innerHTML = `<div class="section-head"><h1 class="page" style="margin:0">${name}</h1>${items.length && kind === 'history' ? '<button class="linkbtn" id="clr">🗑 Clear</button>' : ''}</div><div class="grid" id="grid"></div>`;
  const grid = $('#grid');
  if (!items.length) grid.outerHTML = `<div class="center">${kind === 'mylist' ? 'Nothing saved yet. Open a title and tap "+ My List".' : 'Titles you open will appear here.'}</div>`;
  items.forEach((i) => grid.appendChild(card(i)));
  if ($('#clr')) $('#clr').onclick = () => { store.set('history', []); listView(kind); };
}


/* ---------- music (Apple iTunes Search API: 30-second official previews) ---------- */
const player = $('#player');
const audio = new Audio();
let queue = [], qi = -1;
const fmt = (t) => (isFinite(t) ? Math.floor(t / 60) + ':' + String(Math.floor(t % 60)).padStart(2, '0') : '0:00');
const artBig = (u) => (u || '').replace(/\d+x\d+bb/, '300x300bb');
function renderPlayer() {
  const t = queue[qi]; if (!t) return;
  player.classList.remove('hidden');
  player.innerHTML = `<img alt="" src="${esc(t.art)}"><div class="pi"><b>${esc(t.name)}</b><small>${esc(t.artist)} · 30s preview</small></div>
    <button id="pp" class="pb" aria-label="Previous">⏮</button><button id="pl" class="pb big">${audio.paused ? '▶' : '⏸'}</button><button id="pn" class="pb" aria-label="Next">⏭</button>
    <span id="pt">0:00</span><input id="pr" type="range" min="0" max="100" value="0"><span>0:30</span>`;
  $('#pl').onclick = () => (audio.paused ? audio.play() : audio.pause());
  $('#pp').onclick = () => playAt(qi - 1); $('#pn').onclick = () => playAt(qi + 1);
  $('#pr').oninput = (e) => { if (audio.duration) audio.currentTime = (e.target.value / 100) * audio.duration; };
}
function playAt(i) {
  if (i < 0 || i >= queue.length) return;
  qi = i; audio.src = queue[i].preview; audio.play().catch(() => {}); renderPlayer();
  document.querySelectorAll('.trk').forEach((n, k) => n.classList.toggle('now', k === i));
}
audio.addEventListener('play', () => { const b = $('#pl'); if (b) b.textContent = '⏸'; });
audio.addEventListener('pause', () => { const b = $('#pl'); if (b) b.textContent = '▶'; });
audio.addEventListener('ended', () => playAt(qi + 1));
audio.addEventListener('timeupdate', () => { const r = $('#pr'); if (r && audio.duration) { r.value = (audio.currentTime / audio.duration) * 100; $('#pt').textContent = fmt(audio.currentTime); } });
const normTrack = (x) => ({ id: x.trackId, name: x.trackName, artist: x.artistName, art: artBig(x.artworkUrl100), preview: x.previewUrl });

async function itunes(url) {
  const r = await fetch(url); if (!r.ok) throw new Error('Music service error ' + r.status);
  return r.json();
}
async function musicView(q) {
  clearInterval(heroTimer);
  const cc = region().toLowerCase();
  view.innerHTML = `<div class="searchbar"><span class="si">${ICON.search}</span><input id="mq" placeholder="Search songs, artists..." autocomplete="off" value="${esc(q || '')}"></div>
    <h1 class="page" id="mt">${q ? 'Results for "' + esc(q) + '"' : 'Top Songs'}</h1><div class="chips" id="mchips"></div><div class="tracks" id="tracks"><div class="center"><div class="spinner"></div></div></div>
    <p class="note pad">Previews and artwork provided by Apple. Only 30-second previews are available.</p>`;
  const input = $('#mq'); input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && input.value.trim()) location.hash = '#/music/' + encodeURIComponent(input.value.trim()); });
  const tracks = $('#tracks');
  const chips = [['Bollywood', 'bollywood'], ['Punjabi', 'punjabi'], ['Tamil', 'tamil hits'], ['Telugu', 'telugu hits'], ['Pop', 'pop hits'], ['Hip-Hop', 'hip hop'], ['Lo-fi', 'lofi']];
  chips.forEach(([n, t]) => { const c = html(`<button class="chip sm">${n}</button>`); c.onclick = () => { location.hash = '#/music/' + encodeURIComponent(t); }; $('#mchips').appendChild(c); });
  try {
    let list;
    if (q) list = (await itunes(`https://itunes.apple.com/search?term=${encodeURIComponent(q)}&media=music&entity=song&limit=50&country=${cc}`)).results;
    else {
      const chart = await itunes(`https://rss.applemarketingtools.com/api/v2/${cc}/music/most-played/50/songs.json`);
      const ids = chart.feed.results.map((x) => x.id).join(',');
      list = (await itunes(`https://itunes.apple.com/lookup?id=${ids}&country=${cc}`)).results;
    }
    queue = list.filter((x) => x.previewUrl && x.trackName).map(normTrack); qi = -1;
    tracks.innerHTML = queue.length ? '' : '<div class="center">No songs found.</div>';
    queue.forEach((t, i) => {
      const row = html(`<div class="trk"><span class="n">${i + 1}</span><img loading="lazy" alt="" src="${esc(t.art)}"><div class="ti"><b>${esc(t.name)}</b><small>${esc(t.artist)}</small></div><span class="pbtn">▶</span></div>`);
      row.tabIndex = 0; row.onclick = () => playAt(i); row.onkeydown = (e) => { if (e.key === 'Enter') playAt(i); }; tracks.appendChild(row);
    });
  } catch (e) { tracks.innerHTML = `<div class="center">${esc(e.message)}</div>`; }
}

/* ---------- router ---------- */
function setTab(name) {
  document.querySelectorAll('#tabs a').forEach((a) => a.classList.toggle('active', a.dataset.tab === name));
}
async function route() {
  clearInterval(heroTimer); rowFills = []; if (stopInf) stopInf();
  const [, a, b, c] = (location.hash || '#/home').split('/');
  window.scrollTo(0, 0);
  try {
    if (a === 'browse') { setTab(b); await browseView(b, Math.max(0, parseInt(c, 10) || 0)); }
    else if (a === 'player') { setTab('player'); window.MB_playerView(view); }
    else if (a === 'music') { setTab('music'); await musicView(b ? decodeURIComponent(b) : ''); }
    else if (a === 'search') { setTab(''); await searchView(b ? decodeURIComponent(b) : ''); }
    else if (a === 'title') { setTab(''); await detailView(b, c); }
    else if (a === 'mylist' || a === 'history') { setTab(''); listView(a); }
    else { setTab('home'); await homeView(); }
  } catch (e) { showError(e); }
}
window.addEventListener('scroll', () => document.querySelector('.topbar').classList.toggle('solid', window.scrollY > 10), { passive: true });
window.addEventListener('hashchange', route);
route();
