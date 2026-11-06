package com.example.moviebox.tv;

import android.os.Bundle;
import android.webkit.ValueCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (getBridge() == null || getBridge().getWebView() == null) {
            super.onBackPressed();
            return;
        }
        getBridge().getWebView().evaluateJavascript(
                "(function(){return window.MB_handleBack ? window.MB_handleBack() : false;})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String handled) {
                        if (!"true".equals(handled)) MainActivity.super.onBackPressed();
                    }
                }
        );
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ExoPlayerPlugin.class);
        registerPlugin(MovieBoxHomeApi.class);
        super.onCreate(savedInstanceState);
    }
}
