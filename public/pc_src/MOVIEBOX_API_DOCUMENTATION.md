# MovieBox Complete API Integration Guide
**Pure API Documentation & Implementation for Websites, Desktop & Mobile Apps**

---

## 1. Core Base URLs & Servers

| Environment | Base URL | Usage |
| :--- | :--- | :--- |
| **Primary API** | `https://h5-api.aoneroom.com/wefeed-h5api-bff` | Home, Detail, Search, Suggest |
| **Mirror / Play API** | `https://movieboxph.org/wefeed-h5api-bff` | High-speed Stream & Playback CDN |

---

## 2. Mandatory HTTP Headers (Crucial for Avoiding 403/Cloudflare Block)

Every request to MovieBox API **must** include these exact headers:

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Origin: https://movieboxph.org
Referer: https://movieboxph.org/
x-client-info: {"timezone":"Asia/Calcutta"}
Accept: */*
Accept-Language: en-US,en;q=0.9
Connection: close
```

> **Note on Video Playback**: When playing video files, the streaming CDN requires `Referer: https://movieboxph.org/` and forward `Range: bytes=...` headers.

---

## 3. Endpoints Specification

### A. Home Feed (Hero Banners + Categorized Rows)
- **Method**: `GET`
- **URL**: `https://h5-api.aoneroom.com/wefeed-h5api-bff/home`
- **Response Structure**:
  - `data.operatingList[0].banner.items`: Hero Carousel Banners (10–14 items).
  - `data.operatingList[1..N]`: Categorized Rows (e.g., "Popular Series", "Popular Movies", "Adult Animation", etc.).
  - **Important**: Each row's movies are located inside `op.subjects` (fallback to `op.items`).

#### Example Item in Response:
```json
{
  "subjectId": "4780148614284956296",
  "subjectType": 2,
  "title": "Dead-End Job",
  "coverUrl": "https://img.hakunaymatata.com/...",
  "backdropUrl": "https://img.hakunaymatata.com/...",
  "rating": "7.7",
  "releaseYear": "2026",
  "genre": "Mystery",
  "detailPath": "dead-end-job-oAmYsY887H5"
}
```

---

### B. Search Suggestions (Autocomplete)
- **Method**: `POST`
- **URL**: `https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search-suggest`
- **Headers**: `Content-Type: application/json` + Mandatory Headers
- **Body**:
```json
{
  "keyword": "Spider"
}
```
- **Response**:
```json
{
  "code": 0,
  "data": {
    "items": [
      { "word": "Spider-Man: Brand New Day" },
      { "word": "Spider-Man: No Way Home" },
      { "word": "Spider-Man: Across the Spider-Verse" }
    ]
  }
}
```

---

### C. Search Movies & TV Series
- **Method**: `POST`
- **URL**: `https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search`
- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <TOKEN>`
  - `x-user: <TOKEN>`
- **Body**:
```json
{
  "keyword": "Spider",
  "page": 1,
  "perPage": 20
}
```
- **Static Resilient Token**:
```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio
```
- **Response**:
  - `data.items`: Array of search results.
  - `data.pager.hasMore`: Boolean indicating if more pages are available.

---

### D. Movie & Series Detail
- **Method**: `GET`
- **URL**: `https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId={subjectId}&detailPath={detailPath}`
- **Response**:
  - `data.title`: Title.
  - `data.description`: Full plot synopsis.
  - `data.rating`: Rating (e.g. "8.2").
  - `data.seasons`: Array of seasons `[{ "seasonNumber": 1, "maxEp": 24, "resolutions": [720, 1080] }]`.
  - `data.cast`: Cast array `[{ "name": "...", "character": "...", "avatarUrl": "..." }]`.

---

### E. Video Playback Streams (Direct MP4, HLS, DASH)
- **Method**: `GET`
- **URL**: `https://movieboxph.org/wefeed-h5api-bff/subject/play?subjectId={subjectId}&se={se}&ep={ep}&detailPath={detailPath}`
- **Parameters**:
  - `subjectId`: ID of the movie or series.
  - `detailPath`: slug/path (e.g. `dead-end-job-oAmYsY887H5`).
  - `se`: Season number (`0` for movie, `1` for series season 1).
  - `ep`: Episode number (`0` for movie, `1` for series episode 1).
- **Dual Fallback Rule**:
  - If requested as movie (`se=0&ep=0`) and returns no streams, retry with `se=1&ep=1`.
  - If requested as series and returns no streams, retry with `se=0&ep=0`.
- **Response**:
```json
{
  "code": 0,
  "data": {
    "hasResource": true,
    "streams": [
      {
        "url": "https://bcdnxw.hakunaymatata.com/resource/....mp4",
        "resolutions": "1080,720,480",
        "format": "MP4"
      }
    ],
    "hls": [
      {
        "url": "https://.../master.m3u8",
        "format": "HLS"
      }
    ]
  }
}
```

---

## 4. How to Add to Any Website (Node.js / Express / Next.js API)

Because browser `<video>` tags cannot inject custom `Referer` headers due to browser security policies, use this simple backend proxy route:

### Node.js (Express / Next.js) Stream Proxy:
```javascript
const express = require('express');
const https = require('https');
const app = express();

const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
  'Origin': 'https://movieboxph.org',
  'Referer': 'https://movieboxph.org/',
  'Connection': 'close'
};

// 1. Fetch Streams API
app.get('/api/play', async (req, res) => {
  const { subjectId, se = 0, ep = 0, detailPath = '' } = req.query;
  const playUrl = `https://movieboxph.org/wefeed-h5api-bff/subject/play?subjectId=${subjectId}&se=${se}&ep=${ep}&detailPath=${detailPath}`;
  
  const response = await fetch(playUrl, { headers: HEADERS });
  const data = await response.json();
  res.json(data);
});

// 2. Stream Proxy for HTML5 <video> tag
app.get('/api/stream', (req, res) => {
  const videoUrl = req.query.url;
  if (!videoUrl) return res.status(400).send('Missing url');

  const streamHeaders = {
    ...HEADERS,
    ...(req.headers.range ? { 'Range': req.headers.range } : {})
  };

  https.get(videoUrl, { headers: streamHeaders }, (upstream) => {
    res.writeHead(upstream.statusCode, upstream.headers);
    upstream.pipe(res);
  });
});

app.listen(3000, () => console.log('MovieBox Backend running on port 3000'));
```

### HTML5 Frontend Player:
```html
<video controls width="100%" height="auto" autoplay>
  <!-- Route through proxy so Referer header is preserved -->
  <source src="/api/stream?url=https://bcdnxw.hakunaymatata.com/...mp4" type="video/mp4">
</video>
```
