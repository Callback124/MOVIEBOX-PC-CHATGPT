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

/** Native HTTP bridge for the MovieBox Home endpoint. */
@CapacitorPlugin(name = "MovieBoxHomeApi")
public class MovieBoxHomeApi extends Plugin {
    private static final String[] ENDPOINTS = new String[] {
        "https://h5-api.aoneroom.com/wefeed-h5api-bff/home",
        "https://movieboxph.org/wefeed-h5api-bff/home"
    };
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @PluginMethod
    public void getHome(PluginCall call) {
        new Thread(() -> {
            Exception lastError = null;
            for (String endpoint : ENDPOINTS) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(endpoint).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(18000);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setRequestProperty("Origin", "https://movieboxph.org");
                    connection.setRequestProperty("Referer", "https://movieboxph.org/");
                    connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                    connection.setRequestProperty("Accept", "*/*");
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    connection.setRequestProperty("Connection", "close");

                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    StringBuilder body = new StringBuilder();
                    if (stream != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) body.append(line);
                        }
                    }
                    if (status < 200 || status >= 300) {
                        throw new Exception("Home API HTTP " + status);
                    }
                    JSONObject json = new JSONObject(body.toString());
                    if (!json.has("data") || json.isNull("data")) {
                        throw new Exception("Home API response has no data field");
                    }
                    JSObject result = new JSObject();
                    result.put("response", json);
                    call.resolve(result);
                    return;
                } catch (Exception e) {
                    lastError = e;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            call.reject("MovieBox Home API failed: " +
                    (lastError == null ? "unknown network error" : lastError.getMessage()));
        }).start();
    }
}
