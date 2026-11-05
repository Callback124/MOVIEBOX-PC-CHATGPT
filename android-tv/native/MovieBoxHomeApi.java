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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Native HTTP bridge for MovieBox Home and Search APIs. */
@CapacitorPlugin(name = "MovieBoxHomeApi")
public class MovieBoxHomeApi extends Plugin {
    private static final String[] BASES = new String[] {
        "https://h5-api.aoneroom.com/wefeed-h5api-bff/",
        "https://movieboxph.org/wefeed-h5api-bff/"
    };
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

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
        String token = call.getString("token", "");
        if (keyword.trim().isEmpty()) { call.reject("Search keyword is required"); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("keyword", keyword);
            body.put("page", Math.max(1, page));
            body.put("perPage", 24);
            request(call, "POST", "subject/search", body, token);
        } catch (Exception e) { call.reject("Could not create search request"); }
    }

    private void request(PluginCall call, String method, String path, JSONObject body) {\n        request(call, method, path, body, null);\n    }\n\n    private void request(PluginCall call, String method, String path, JSONObject body, String token) {
        new Thread(() -> {
            Exception lastError = null;
            for (String base : BASES) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(base + path).openConnection();
                    connection.setRequestMethod(method);
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(18000);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setRequestProperty("Origin", "https://movieboxph.org");
                    connection.setRequestProperty("Referer", "https://movieboxph.org/");
                    connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                    if (path.equals("subject/search")) {
                        connection.setRequestProperty("Authorization", "Bearer " + SEARCH_TOKEN);
                        connection.setRequestProperty("x-user", SEARCH_TOKEN);
                    }
                    connection.setRequestProperty("Accept", method.equals("POST")
                            ? "application/json, text/plain, */*" : "application/json, text/plain, */*");
                    connection.setRequestProperty("Connection", "close");
                    if ("POST".equals(method)) {
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                        connection.setFixedLengthStreamingMode(bytes.length);
                        connection.getOutputStream().write(bytes);
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
                    if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
                    JSONObject json = new JSONObject(responseBody.toString());
                    if (!json.has("data") || json.isNull("data"))
                        throw new Exception("API response has no data field");
                    JSObject result = new JSObject();
                    result.put("response", json);
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
