package com.example.moviebox.tv;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Native HTTP bridge for MovieBox Home and Search APIs. */
@CapacitorPlugin(name = "MovieBoxHomeApi")
public class MovieBoxHomeApi extends Plugin {
    private static final String[] BASES = new String[] {
        "https://h5-api.aoneroom.com/wefeed-h5api-bff/",
        "https://movieboxph.org/wefeed-h5api-bff/"
    };
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";

    @PluginMethod
    public void getHome(PluginCall call) {
        request(call, "GET", "home", null, null);
    }

    @PluginMethod
    public void searchSuggest(PluginCall call) {
        String keyword = call.getString("keyword", "");
        if (keyword.trim().isEmpty()) { call.reject("Search keyword is required"); return; }
        JSONObject body = new JSONObject();
        try { body.put("keyword", keyword); }
        catch (Exception e) { call.reject("Could not create suggestion request"); return; }
        request(call, "POST", "subject/search-suggest", body, null);
    }

    @PluginMethod
    public void search(PluginCall call) {
        String keyword = call.getString("keyword", "");
        int page = call.getInt("page", 1);
        String token = normalizeToken(call.getString("token", ""));
        if (keyword.trim().isEmpty()) { call.reject("Search keyword is required"); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("keyword", keyword);
            body.put("page", Math.max(1, page));
            body.put("perPage", 24);
            request(call, "POST", "subject/search", body, token);
        } catch (Exception e) { call.reject("Could not create search request"); }
    }


    @PluginMethod
    public void detail(PluginCall call) {
        String subjectId = call.getString("subjectId", "");
        String token = normalizeToken(call.getString("token", ""));
        if (subjectId.trim().isEmpty()) { call.reject("subjectId is required"); return; }
        try {
            String query = "detail?subjectId=" + java.net.URLEncoder.encode(subjectId, "UTF-8");
            request(call, "GET", query, null, token);
        } catch (Exception e) { call.reject("Could not create detail request"); }
    }

    @PluginMethod
    public void play(PluginCall call) {
        String subjectId = call.getString("subjectId", "");
        String detailPath = call.getString("detailPath", "");
        int season = call.getInt("se", 0);
        int episode = call.getInt("ep", 0);
        String token = normalizeToken(call.getString("token", ""));
        if (subjectId.trim().isEmpty()) { call.reject("subjectId is required"); return; }
        try {
            StringBuilder query = new StringBuilder("subject/play?subjectId=")
                    .append(java.net.URLEncoder.encode(subjectId, "UTF-8"));
            if (!detailPath.trim().isEmpty()) {
                query.append("&detailPath=").append(java.net.URLEncoder.encode(detailPath.trim(), "UTF-8"));
            }
            query.append("&se=").append(Math.max(0, season)).append("&ep=").append(Math.max(0, episode));
            request(call, "GET", query.toString(), null, token, detailPath);
        } catch (Exception e) { call.reject("Could not create play request"); }
    }

    private static String normalizeToken(String value) {
        if (value == null) return "";
        String token = value.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        return token;
    }

    private void request(PluginCall call, String method, String path, JSONObject body, String token) {
        request(call, method, path, body, token, "");
    }

    private void request(PluginCall call, String method, String path, JSONObject body, String token, String detailPath) {
        new Thread(() -> {
            Exception lastError = null;
            String[] endpoints = path.startsWith("subject/play?")
                    ? new String[] { BASES[1], BASES[0] } : BASES;
            for (String base : endpoints) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(base + path).openConnection();
                    connection.setRequestMethod(method);
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(18000);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setRequestProperty("Origin", "https://movieboxph.org");
                    connection.setRequestProperty("x-request-lang", "en");
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setRequestProperty("Pragma", "no-cache");
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    connection.setRequestProperty("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
                    connection.setRequestProperty("sec-ch-ua-mobile", "?0");
                    connection.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
                    connection.setRequestProperty("Sec-Fetch-Site", "cross-site");
                    connection.setRequestProperty("Sec-Fetch-Mode", "cors");
                    connection.setRequestProperty("Sec-Fetch-Dest", "empty");
                    String referer = "https://movieboxph.org/";
                    if (path.startsWith("subject/play?") && detailPath != null && !detailPath.trim().isEmpty()) {
                        String cleanPath = detailPath.trim();
                        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
                            referer = cleanPath;
                        } else if (cleanPath.startsWith("/")) {
                            referer = "https://movieboxph.org" + cleanPath;
                        } else {
                            if (cleanPath.regionMatches(true, 0, "play/", 0, 5)) cleanPath = cleanPath.substring(5);
                            referer = "https://movieboxph.org/play/" + cleanPath;
                        }
                    }
                    connection.setRequestProperty("Referer", referer);
                    connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                    if ((path.equals("subject/search") || path.startsWith("detail?") || path.startsWith("subject/play?")) && token != null && !token.trim().isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + token);
                        connection.setRequestProperty("x-user", token);
                    }
                    connection.setRequestProperty("Accept", method.equals("POST")
                            ? "application/json, text/plain, */*" : "application/json, text/plain, */*");
                    connection.setRequestProperty("Connection", "close");
                    if ("POST".equals(method)) {
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                        connection.setFixedLengthStreamingMode(bytes.length);
                        try (java.io.OutputStream output = connection.getOutputStream()) {
                            output.write(bytes);
                            output.flush();
                        }
                    }
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    StringBuilder responseBody = new StringBuilder();
                    if (stream != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) responseBody.append(line);
                        }
                    }
                    if (status < 200 || status >= 300) {
                        String detail = responseBody.toString();
                        if (detail.length() > 700) detail = detail.substring(0, 700);
                        throw new Exception("HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
                    }
                    JSONObject json = new JSONObject(responseBody.toString());
                    if (!json.has("data") || json.isNull("data"))
                        throw new Exception("API response has no data field");
                    JSObject result = new JSObject();
                    result.put("response", json);
                    if ("home".equals(path)) {
                        String xUserHeader = connection.getHeaderField("x-user");
                        if (xUserHeader != null && !xUserHeader.trim().isEmpty()) {
                            try {
                                JSONObject userData = new JSONObject(xUserHeader);
                                String freshToken = userData.optString("token", "");
                                if (!freshToken.isEmpty()) result.put("token", freshToken);
                            } catch (Exception ignored) {
                                // Some deployments may return the token as a plain header value.
                                String freshToken = normalizeToken(xUserHeader);
                                if (!freshToken.isEmpty() && !freshToken.startsWith("{"))
                                    result.put("token", freshToken);
                            }
                        }
                    }
                    call.resolve(result);
                    return;
                } catch (Exception e) { lastError = e; }
                finally { if (connection != null) connection.disconnect(); }
            }
            call.reject("MovieBox " + path.split("\\?")[0] + " API failed: "
                    + (lastError == null ? "unknown network error" : lastError.getMessage()));
        }).start();
    }
}
