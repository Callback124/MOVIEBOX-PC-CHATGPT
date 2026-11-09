package com.callback.moviebox.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class LookrRepository {
    private static final String FEED_BASE = "https://api.lookr.cloud";
    private static final String H5_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff/";
    private static final String AUTH = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU2NTI1NDEwNzg2NjMyMTg4MDAsImV4cCI6MTc5NzA0OTI1NiwiaWF0IjoxNzg5MjcyOTU2fQ.9lDW-RX6eRIdW0GYGIn-Q9HvHRASck7WC8SlTJC0yE0";
    private static final String CLIENT_INFO = "{\"package_name\":\"com.wecloud.lookr\",\"version_name\":\"3.0.16.0706.03\",\"version_code\":316,\"os\":\"android\",\"os_version\":\"14\",\"install_ch\":\"google-play\",\"device_id\":\"45fc9f58647307f975f5c2e70048a08e\",\"install_store\":\"ps\",\"gaid\":\"ad74fab5dc9bf1f9\",\"brand\":\"Infinix\",\"model\":\"Infinix X6711\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"405872\"}";

    private JSONObject request(String endpoint, boolean feed, boolean playback) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "okhttp/4.12.0");
            c.setRequestProperty("Authorization", AUTH);
            if (feed) {
                c.setRequestProperty("x-client-info", CLIENT_INFO);
                c.setRequestProperty("x-client-status", "0");
                c.setRequestProperty("x-tr-signature", System.currentTimeMillis() + "|2|");
            } else {
                c.setRequestProperty("x-source", "lookr");
                c.setRequestProperty("Origin", playback ? "https://fzmovienow.top" : "https://netnaijafilm.run");
                c.setRequestProperty("Referer", playback ? "https://fzmovienow.top/" : "https://netnaijafilm.run/");
            }
            int status = c.getResponseCode();
            InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder body = new StringBuilder();
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
            }
            JSONObject json = new JSONObject(body.toString());
            if (status < 200 || status >= 300) {
                throw new Exception("Lookr HTTP " + status + ": " + json.optString("message", body.toString()));
            }
            if (json.has("code") && json.optInt("code", 0) != 0) {
                throw new Exception("Lookr API " + json.optInt("code") + ": " + json.optString("message", "Request failed"));
            }
            return json;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public JSONObject feed(String tag, String subTag, String opId, int page, int pageSize) throws Exception {
        String url = FEED_BASE + "/wefeed-gogo-bff/recommend?tag=" + URLEncoder.encode(tag, "UTF-8")
                + "&subTag=" + URLEncoder.encode(subTag == null ? "" : subTag, "UTF-8")
                + "&opId=" + URLEncoder.encode(opId == null ? "0" : opId, "UTF-8")
                + "&page=" + Math.max(1, page) + "&pageSize=" + Math.max(1, pageSize);
        return request(url, true, false);
    }

    public JSONObject detail(String subjectId) throws Exception {
        String url = H5_BASE + "detail?subjectId=" + URLEncoder.encode(subjectId, "UTF-8");
        return request(url, false, false);
    }

    public JSONObject play(String subjectId, int season, int episode) throws Exception {
        String url = H5_BASE + "subject/play?subjectId=" + URLEncoder.encode(subjectId, "UTF-8")
                + "&se=" + Math.max(0, season) + "&ep=" + Math.max(0, episode);
        return request(url, false, true);
    }

    public static JSONArray items(JSONObject response) {
        JSONObject data = response == null ? null : response.optJSONObject("data");
        if (data == null) return new JSONArray();
        JSONArray a = data.optJSONArray("movieItems");
        if (a == null) a = data.optJSONArray("items");
        return a == null ? new JSONArray() : a;
    }

    public static JSONObject pager(JSONObject response) {
        JSONObject data = response == null ? null : response.optJSONObject("data");
        JSONObject p = data == null ? null : data.optJSONObject("pager");
        return p == null ? new JSONObject() : p;
    }
}
