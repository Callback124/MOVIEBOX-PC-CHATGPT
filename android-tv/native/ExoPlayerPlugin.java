package com.example.moviebox.tv;

import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** JS: Capacitor.registerPlugin('ExoPlayer').play({ url, title, position }) */
@CapacitorPlugin(name = "ExoPlayer")
public class ExoPlayerPlugin extends Plugin {
    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        boolean movieBox = call.getBoolean("movieBox", false);
        if ((url == null || url.trim().isEmpty()) && !movieBox) {
            call.reject("url required");
            return;
        }
        final Intent intent = new Intent(getActivity(), PlayerActivity.class);
        intent.putExtra("url", url.trim());
        intent.putExtra("title", call.getString("title", ""));
        intent.putExtra("format", call.getString("format", ""));
        JSObject headers = call.getObject("headers");
        if (headers != null) intent.putExtra("headers", headers.toString());
        JSArray sources = call.getArray("sources");
        if (sources != null) intent.putExtra("sources", sources.toString());
        intent.putExtra("movieBox", movieBox);
        intent.putExtra("subjectId", call.getString("subjectId", ""));
        intent.putExtra("season", call.getInt("season", 0));
        intent.putExtra("episode", call.getInt("episode", 0));
        intent.putExtra("detailPath", call.getString("detailPath", ""));
        intent.putExtra("token", call.getString("token", ""));
        JSArray dubs = call.getArray("dubs");
        if (dubs != null) intent.putExtra("dubs", dubs.toString());
        JSArray episodeSequence = call.getArray("episodeSequence");
        if (episodeSequence != null) intent.putExtra("episodeSequence", episodeSequence.toString());
        Double pos = call.getDouble("position", 0.0);
        intent.putExtra("pos", (long) ((pos == null ? 0.0 : pos) * 1000));
        getActivity().runOnUiThread(() -> {
            try {
                getActivity().startActivity(intent);
                call.resolve();
            } catch (Exception error) {
                call.reject("Could not launch ExoPlayer Activity: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            }
        });
    }
}
