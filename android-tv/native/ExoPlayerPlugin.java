package com.example.moviebox.tv;

import android.content.Intent;
import com.getcapacitor.JSObject;
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
        if (url == null || url.trim().isEmpty()) {
            call.reject("url required");
            return;
        }
        try {
            Intent i = new Intent(getContext(), PlayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("url", url.trim());
            i.putExtra("title", call.getString("title", ""));
            i.putExtra("format", call.getString("format", ""));
            JSObject headers = call.getObject("headers");
            if (headers != null) i.putExtra("headers", headers.toString());
            Double pos = call.getDouble("position", 0.0);
            i.putExtra("pos", (long) ((pos == null ? 0.0 : pos) * 1000));
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception error) {
            call.reject("Could not open ExoPlayer Activity: " + error.getMessage(), error);
        }
    }
}
