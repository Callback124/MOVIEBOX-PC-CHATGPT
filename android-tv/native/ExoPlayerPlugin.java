package com.example.moviebox.tv;

import android.content.Intent;
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
        if (url == null || url.isEmpty()) { call.reject("url required"); return; }
        Intent i = new Intent(getContext(), PlayerActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", call.getString("title", ""));
        JSObject headers = call.getObject("headers");
        if (headers != null) i.putExtra("headers", headers.toString());
        Double pos = call.getDouble("position", 0.0);
        i.putExtra("pos", (long) ((pos == null ? 0.0 : pos) * 1000));
        getActivity().startActivity(i);
        call.resolve();
    }
}
