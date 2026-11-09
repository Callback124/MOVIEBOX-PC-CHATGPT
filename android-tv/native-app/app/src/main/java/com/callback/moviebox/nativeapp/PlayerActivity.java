package com.callback.moviebox.nativeapp;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.util.Log;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen native Media3 ExoPlayer screen with a custom control layer:
 * back + title + lock (top), replay-10 / play / forward-10 (centre), seek bar + time and
 * volume / audio / quality / subtitles / speed / aspect / PiP (bottom), and a Video/Audio track sheet.
 * Everything is D-pad (TV remote) friendly. All shapes are square (no rounded corners).
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    private static final int MINT = 0xFFB7F0DF;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout root;
    private FrameLayout ui;
    private View scrim;
    private LinearLayout topBar;
    private LinearLayout centerRow;
    private LinearLayout bottomBar;
    private ImageView backBtn;
    private ImageView lockBtn;
    private ImageView playBtn;
    private ImageView volBtn;
    private TextView titleView;
    private TextView timeView;
    private SeekView seek;
    private FrameLayout sheetLayer;
    private LinearLayout sheet;
    private JSONArray sourceVariants = new JSONArray();
    private String activeSourceUrl = "";
    private JSONArray dubVariants = new JSONArray();
    private JSONArray episodeSequence = new JSONArray();
    private TextView episodeSubtitleView;
    private ImageView previousEpisodeBtn;
    private ImageView nextEpisodeBtn;
    private String basePlayerTitle = "";
    private String originalPlayerTitle = "";
    private String movieBoxSubjectId = "";
    private String movieBoxDetailPath = "";
    private String movieBoxToken = "";
    private int movieBoxSeason = 0;
    private int movieBoxEpisode = 0;
    private boolean movieBoxMode = false;
    private boolean storyTvMode = false;
    private String storyTvShowId = "";
    private int storyTvEpisode = 1;
    private int storyTvEpisodeCount = 1;
    private boolean resourcesLoading = false;
    private TextView diagnosticText;
    private final StringBuilder diagnosticBuffer = new StringBuilder();
    private static final String DIAG_PREFS = "story_tv_diagnostics";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean locked = false;
    private boolean controlsVisible = true;
    private boolean sheetOpen = false;
    private boolean muted = false;
    private boolean audioNone = false;
    private boolean videoForced = false;
    private int sheetMode = 0; // 0 = Video/Audio tabs, 1 = Subtitles
    private int sheetTab = 0;
    private int lastRowIdx = 0;
    private int fitIndex = 0;
    private int speedIndex = 2;
    private final int[] fitModes = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL};
    private final String[] fitNames = {"Fit", "Zoom", "Stretch"};
    private final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private final Runnable hider = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.getPlayWhenReady() && !sheetOpen) {
                controlsVisible = false;
                applyVisibility();
            }
        }
    };
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    /* ===================================================== lifecycle ===================================================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecorder();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        diag("PlayerActivity onCreate; storyTv=" + getIntent().getBooleanExtra("storyTv", false)
                + "; showIdPresent=" + (getIntent().getStringExtra("storyShowId") != null)
                + "; episode=" + getIntent().getIntExtra("storyEpisode", 1));
        buildUi();
        hideSystemUi();
        initPlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        diag("Lifecycle: onStart");
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        diag("Lifecycle: onStop; finishing=" + isFinishing());
        handler.removeCallbacks(ticker);
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        diag("Lifecycle: onDestroy; finishing=" + isFinishing());
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onBackPressed() {
        if (sheetOpen) closeSheet();
        else if (locked) setLocked(false);
        else super.onBackPressed();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPip, newConfig);
        if (isInPip && sheetOpen) closeSheet();
        ui.setVisibility(isInPip ? View.GONE : View.VISIBLE);
    }

    /* ===================================================== player ===================================================== */

    private void initPlayer() {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36");
        requestHeaders.put("Accept", "*/*");
        // The web player sends playback requests in the MovieBox page context.
        // Native ExoPlayer must send the same Origin/Referer or CDN hosts may reject
        // the manifest/segments with HTTP 403/404 even when the URL is valid.
        if (getIntent().getBooleanExtra("storyTv", false)) {
            requestHeaders.put("Origin", "https://storytv.asia");
            requestHeaders.put("Referer", "https://storytv.asia/");
        }
        if (getIntent().getBooleanExtra("movieBox", false)) {
            requestHeaders.put("Origin", "https://movieboxph.org");
            requestHeaders.put("Referer", movieBoxReferer(getIntent().getStringExtra("detailPath")));
            requestHeaders.put("Accept-Language", "en-US,en;q=0.9");
        }
        String headerJson = getIntent().getStringExtra("headers");
        if (headerJson != null && !headerJson.isEmpty()) {
            try {
                JSONObject suppliedHeaders = new JSONObject(headerJson);
                Iterator<String> names = suppliedHeaders.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    String value = suppliedHeaders.optString(name, "");
                    if (!value.isEmpty()) requestHeaders.put(name, value);
                }
            } catch (Exception ignored) { }
        }
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(requestHeaders.remove("User-Agent"))
                .setDefaultRequestProperties(requestHeaders)
                .setAllowCrossProtocolRedirects(true);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                diag("EXOPLAYER ERROR: code=" + error.errorCode + " (" + error.getErrorCodeName() + ")\n" + stackTrace(error));
                Throwable cause = error;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                if (cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    int status = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                    toast("Stream server HTTP " + status + " (" + error.getErrorCodeName() + ")");
                } else {
                    String detail = cause.getMessage();
                    toast("Cannot play this video: " + error.getErrorCodeName()
                            + (detail == null || detail.trim().isEmpty() ? "" : " — " + detail));
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                updatePlayIcon();
                if (playWhenReady) scheduleHide();
                else showControls();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                diag("Playback state changed: " + playbackStateName(state));
                updatePlayIcon();
                if (state == Player.STATE_ENDED) {
                    showControls();
                    if (hasNextEpisode()) {
                        if (storyTvMode) playStoryTvEpisode(storyTvEpisode + 1);
                        else playAdjacentEpisode(1);
                    }
                }
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                if (sheetOpen) renderSheet(false);
            }
        });

        String url = getIntent().getStringExtra("url");
        activeSourceUrl = url == null ? "" : url;
        try { sourceVariants = new JSONArray(getIntent().getStringExtra("sources") == null ? "[]" : getIntent().getStringExtra("sources")); } catch (Exception ignored) { sourceVariants = new JSONArray(); }
        try { dubVariants = new JSONArray(getIntent().getStringExtra("dubs") == null ? "[]" : getIntent().getStringExtra("dubs")); } catch (Exception ignored) { dubVariants = new JSONArray(); }
        try { episodeSequence = new JSONArray(getIntent().getStringExtra("episodeSequence") == null ? "[]" : getIntent().getStringExtra("episodeSequence")); } catch (Exception ignored) { episodeSequence = new JSONArray(); }
        movieBoxMode = getIntent().getBooleanExtra("movieBox", false);
        storyTvMode = getIntent().getBooleanExtra("storyTv", false);
        storyTvShowId = getIntent().getStringExtra("storyShowId");
        storyTvEpisode = getIntent().getIntExtra("storyEpisode", 1);
        storyTvEpisodeCount = getIntent().getIntExtra("storyEpisodeCount", storyTvEpisode);
        movieBoxSubjectId = getIntent().getStringExtra("subjectId");
        movieBoxDetailPath = getIntent().getStringExtra("detailPath");
        movieBoxToken = getIntent().getStringExtra("token");
        movieBoxSeason = getIntent().getIntExtra("season", 0);
        movieBoxEpisode = getIntent().getIntExtra("episode", 0);
        String title = getIntent().getStringExtra("title");
        String dubTitle = getIntent().getStringExtra("dubTitle");
        basePlayerTitle = dubTitle != null && !dubTitle.trim().isEmpty()
                ? dubTitle.trim() : (title == null ? "" : title);
        originalPlayerTitle = title == null ? basePlayerTitle : title;
        updateEpisodeSubtitle();
        // A detail-page dub chip may not include the localized subject title.
        // Resolve it from the selected dub subject just as the in-player dub menu does.
        if (movieBoxMode && (dubTitle == null || dubTitle.trim().isEmpty()
                || dubTitle.trim().equals(title))) {
            new Thread(() -> {
                String resolved = fetchMovieBoxSubjectTitle(movieBoxSubjectId);
                if (resolved != null && !resolved.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        basePlayerTitle = resolved.trim();
                        titleView.setText(formatEpisodeTitle(basePlayerTitle));
                    });
                }
            }).start();
        }
        String format = getIntent().getStringExtra("format");
        long pos = getIntent().getLongExtra("pos", 0L);
        titleView.setText(formatEpisodeTitle(basePlayerTitle));
        if (url != null && !url.trim().isEmpty()) {
            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
            if (format != null) {
                String f = format.toUpperCase(Locale.ROOT);
                if (f.contains("HLS") || f.contains("M3U8")) mediaItemBuilder.setMimeType("application/x-mpegURL");
                else if (f.contains("DASH") || f.contains("MPD")) mediaItemBuilder.setMimeType("application/dash+xml");
                else if (f.contains("MP4")) mediaItemBuilder.setMimeType("video/mp4");
            }
            player.setMediaItem(mediaItemBuilder.build());
            player.prepare();
            if (pos > 0) player.seekTo(pos);
            player.setPlayWhenReady(true);
        } else if (storyTvMode) {
            titleView.setText(formatEpisodeTitle(basePlayerTitle) + "  •  Loading stream…");
            fetchStoryTvStream(storyTvEpisode);
        } else if (movieBoxMode) {
            titleView.setText(formatEpisodeTitle(basePlayerTitle) + "  •  Loading resources…");
            fetchMovieBoxResources(movieBoxSubjectId, movieBoxDetailPath, movieBoxSeason, movieBoxEpisode, false);
        } else {
            toast("No video URL was supplied");
        }
        updatePlayIcon();
        showControls();
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0);
            player.play();
        } else if (player.getPlayWhenReady()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long dur = player.getDuration();
        long t = player.getCurrentPosition() + deltaMs;
        if (dur != C.TIME_UNSET && dur > 0) t = Math.min(t, dur);
        player.seekTo(Math.max(0, t));
    }

    private void updatePlayIcon() {
        if (player == null || playBtn == null) return;
        boolean playing = player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED;
        playBtn.setImageResource(drawableId(playing ? "ic_pause" : "ic_play"));
    }

    private void updateProgress() {
        if (player == null || seek == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        long buf = player.getBufferedPosition();
        boolean unknown = dur == C.TIME_UNSET || dur <= 0;
        seek.set(pos, unknown ? 0 : dur, buf);
        long shown = seek.dragging ? seek.pos : pos;
        timeView.setText(clock(shown) + " · " + (unknown ? "LIVE" : clock(dur)));
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /* ===================================================== UI building ===================================================== */

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(MATCH, MATCH));

        ui = new FrameLayout(this);
        ui.setClickable(true);
        ui.setOnClickListener(v -> toggleControls());
        root.addView(ui, new FrameLayout.LayoutParams(MATCH, MATCH));

        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0x00000000, 0x00000000, 0xDD000000}));
        ui.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        // ---- top: back chevron + title + lock
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(24), dp(14), dp(24), 0);
        backBtn = iconButton("ic_chevron_down", 10);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.leftMargin = dp(10);
        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        titleColumn.addView(titleView, new LinearLayout.LayoutParams(MATCH, WRAP));
        episodeSubtitleView = new TextView(this);
        episodeSubtitleView.setTextColor(0xCCFFFFFF);
        episodeSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleColumn.addView(episodeSubtitleView, new LinearLayout.LayoutParams(MATCH, WRAP));
        topBar.addView(titleColumn, tp);
        lockBtn = iconButton("ic_lock_open", 10);
        lockBtn.setOnClickListener(v -> setLocked(!locked));
        topBar.addView(lockBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ui.addView(topBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

        // ---- centre: replay 10 / play / forward 10
        centerRow = new LinearLayout(this);
        centerRow.setOrientation(LinearLayout.HORIZONTAL);
        centerRow.setGravity(Gravity.CENTER);
        previousEpisodeBtn = iconButton("ic_skip_previous", 5);
        previousEpisodeBtn.setOnClickListener(v -> playAdjacentEpisode(-1));
        LinearLayout.LayoutParams epPrevLp = new LinearLayout.LayoutParams(dp(76), dp(84));
        epPrevLp.rightMargin = dp(22);
        centerRow.addView(previousEpisodeBtn, epPrevLp);
        centerRow.addView(skipCell("ic_replay", -10000), new LinearLayout.LayoutParams(dp(76), dp(84)));
        playBtn = iconButton("ic_play", 14);
        playBtn.setOnClickListener(v -> togglePlay());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(104), dp(104));
        pp.leftMargin = dp(38);
        pp.rightMargin = dp(38);
        centerRow.addView(playBtn, pp);
        centerRow.addView(skipCell("ic_forward", 10000), new LinearLayout.LayoutParams(dp(76), dp(84)));
        nextEpisodeBtn = iconButton("ic_skip_next", 5);
        nextEpisodeBtn.setOnClickListener(v -> playAdjacentEpisode(1));
        LinearLayout.LayoutParams epNextLp = new LinearLayout.LayoutParams(dp(76), dp(84));
        epNextLp.leftMargin = dp(22);
        centerRow.addView(nextEpisodeBtn, epNextLp);
        updateEpisodeControls();
        ui.addView(centerRow, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));

        // ---- bottom: seek bar, time, icon row
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(24), 0, dp(24), dp(12));
        seek = new SeekView(this);
        bottomBar.addView(seek, new LinearLayout.LayoutParams(MATCH, dp(36)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        timeView.setText("00:00 · 00:00");
        timeView.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        row.addView(timeView, new LinearLayout.LayoutParams(0, WRAP, 1f));
        volBtn = addButton(row, "ic_volume", "Volume", v -> toggleMute());
        addButton(row, "ic_audio", "Audio", v -> openSheet(0, 1));
        addButton(row, "ic_quality", "Quality", v -> openSheet(0, 0));
        addButton(row, "ic_cc", "Subtitles", v -> openSheet(1, 0));
        addButton(row, "ic_fit", "Aspect", v -> cycleFit());
        addButton(row, "ic_speed", "Speed", v -> cycleSpeed());
        bottomBar.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        ui.addView(bottomBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));

        // ---- track sheet (right side panel)
        sheetLayer = new FrameLayout(this);
        sheetLayer.setBackgroundColor(0x55000000);
        sheetLayer.setClickable(true);
        sheetLayer.setOnClickListener(v -> closeSheet());
        sheetLayer.setVisibility(View.GONE);
        root.addView(sheetLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xF20B1F1B);
        sbg.setStroke(dp(1), 0x22FFFFFF);
        sheet.setBackground(sbg);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                Math.min(dp(560), (int) (dm.widthPixels * 0.5f)), (int) (dm.heightPixels * 0.78f), Gravity.END | Gravity.BOTTOM);
        sp.setMargins(0, 0, dp(16), dp(16));
        sheetLayer.addView(sheet, sp);

        if (getIntent().getBooleanExtra("storyTv", false)) addDiagnosticsPanel();
        setContentView(root);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int drawableId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private Drawable stateBg(int fill) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(fill == Color.TRANSPARENT ? 0x33FFFFFF : fill);
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(fill == Color.TRANSPARENT ? 0x33FFFFFF : fill);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    private void makeFocusable(View v) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(stateBg(Color.TRANSPARENT));
    }

    private ImageView iconButton(String icon, int padDp) {
        ImageView v = new ImageView(this);
        v.setImageResource(drawableId(icon));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(padDp);
        v.setPadding(p, p, p, p);
        makeFocusable(v);
        return v;
    }

    private ImageView addButton(LinearLayout row, String icon, String label, View.OnClickListener l) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(2), dp(3), dp(2));
        makeFocusable(item);

        ImageView v = new ImageView(this);
        v.setImageResource(drawableId(icon));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        item.addView(v, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        item.addView(caption, new LinearLayout.LayoutParams(MATCH, WRAP));

        item.setOnClickListener(l);
        row.addView(item, new LinearLayout.LayoutParams(dp(76), dp(62)));
        return v;
    }

    private FrameLayout skipCell(String icon, final long deltaMs) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(drawableId(icon));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        f.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        TextView t = new TextView(this);
        t.setText("10");
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, 0);
        f.addView(t, new FrameLayout.LayoutParams(MATCH, MATCH));
        makeFocusable(f);
        f.setOnClickListener(v -> seekBy(deltaMs));
        return f;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /* ===================================================== controls ===================================================== */

    private void applyVisibility() {
        int top = controlsVisible ? View.VISIBLE : View.INVISIBLE;
        int rest = (controlsVisible && !locked) ? View.VISIBLE : View.INVISIBLE;
        scrim.setVisibility(rest);
        topBar.setVisibility(top);
        backBtn.setVisibility(rest);
        titleView.setVisibility(rest);
        lockBtn.setVisibility(top);
        centerRow.setVisibility(rest);
        bottomBar.setVisibility(rest);
    }

    private void showControls() {
        controlsVisible = true;
        applyVisibility();
        scheduleHide();
        if (!sheetOpen) (locked ? lockBtn : playBtn).requestFocus();
    }

    private void toggleControls() {
        if (controlsVisible) {
            controlsVisible = false;
            applyVisibility();
        } else {
            showControls();
        }
    }

    private void scheduleHide() {
        handler.removeCallbacks(hider);
        handler.postDelayed(hider, 10000);
    }

    private void setLocked(boolean on) {
        locked = on;
        lockBtn.setImageResource(drawableId(on ? "ic_lock" : "ic_lock_open"));
        toast(on ? "Controls locked" : "Controls unlocked");
        controlsVisible = true;
        applyVisibility();
        scheduleHide();
        (on ? lockBtn : playBtn).requestFocus();
    }

    private void toggleMute() {
        muted = !muted;
        if (player != null) player.setVolume(muted ? 0f : 1f);
        volBtn.setImageResource(drawableId(muted ? "ic_volume_off" : "ic_volume"));
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % speeds.length;
        if (player != null) player.setPlaybackSpeed(speeds[speedIndex]);
        toast("Speed " + speeds[speedIndex] + "×");
    }

    private void cycleFit() {
        fitIndex = (fitIndex + 1) % fitModes.length;
        playerView.setResizeMode(fitModes[fitIndex]);
        toast(fitNames[fitIndex]);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            } catch (Exception e) {
                toast("Picture-in-picture not available");
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && player != null) {
            switch (k) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    togglePlay();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.pause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(30000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-30000);
                    return true;
                default:
                    break;
            }
            boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                    || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
            if (!sheetOpen && nav && !controlsVisible) {
                if (!locked) {
                    if (k == KeyEvent.KEYCODE_DPAD_LEFT) seekBy(-10000);
                    else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) seekBy(10000);
                }
                showControls();
                return true;
            }
            if (controlsVisible && !sheetOpen) scheduleHide();
        }
        return super.dispatchKeyEvent(e);
    }

    /* ===================================================== seek bar ===================================================== */

    private class SeekView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        long pos;
        long dur;
        long buf;
        boolean dragging;

        SeekView(android.content.Context c) {
            super(c);
            setFocusable(true);
            setClickable(true);
        }

        void set(long p, long d, long b) {
            if (!dragging) pos = p;
            dur = d;
            buf = b;
            invalidate();
        }

        private float fraction(MotionEvent e) {
            float pad = dp(8);
            float w = getWidth() - 2 * pad;
            if (w <= 0) return 0f;
            return Math.max(0f, Math.min(1f, (e.getX() - pad) / w));
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = dp(8);
            float x0 = pad;
            float x1 = getWidth() - pad;
            float cy = getHeight() / 2f;
            float th = dp(3);
            float knob = isFocused() ? dp(8) : dp(6);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55FFFFFF);
            c.drawRect(x0, cy - th / 2, x1, cy + th / 2, paint);
            if (dur > 0) {
                float bx = x0 + (x1 - x0) * Math.min(1f, (float) buf / dur);
                paint.setColor(0x88FFFFFF);
                c.drawRect(x0, cy - th / 2, bx, cy + th / 2, paint);
            }
            float fx = dur > 0 ? x0 + (x1 - x0) * Math.min(1f, (float) pos / dur) : x0;
            paint.setColor(Color.WHITE);
            c.drawRect(x0, cy - th / 2, fx, cy + th / 2, paint);
            c.drawRect(fx - knob, cy - knob, fx + knob, cy + knob, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (dur <= 0) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    pos = (long) (fraction(e) * dur);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        pos = (long) (fraction(e) * dur);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        if (player != null) player.seekTo((long) (fraction(e) * dur));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                seekBy(-10000);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                seekBy(10000);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                togglePlay();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }

    /* ===================================================== track sheet ===================================================== */

    private static class Opt {
        final String label;
        final boolean on;
        final Runnable run;

        Opt(String label, boolean on, Runnable run) {
            this.label = label;
            this.on = on;
            this.run = run;
        }
    }

    private static class TrackRef {
        final Tracks.Group group;
        final int index;
        final Format format;

        TrackRef(Tracks.Group group, int index, Format format) {
            this.group = group;
            this.index = index;
            this.format = format;
        }
    }

    private void openSheet(int mode, int tab) {
        sheetMode = mode;
        if (mode == 0) sheetTab = tab;
        sheetOpen = true;
        handler.removeCallbacks(hider);
        sheetLayer.setVisibility(View.VISIBLE);
        renderSheet(true);
    }

    private void closeSheet() {
        sheetOpen = false;
        sheetLayer.setVisibility(View.GONE);
        scheduleHide();
        playBtn.requestFocus();
    }

    private void renderSheet(boolean focusSelected) {
        sheet.removeAllViews();

        View grab = new View(this);
        grab.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(72), dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = dp(12);
        gp.bottomMargin = dp(14);
        sheet.addView(grab, gp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(0x1AFFFFFF);
        tabs.setPadding(dp(6), dp(6), dp(6), dp(6));
        String[] names = sheetMode == 1 ? new String[]{"Subtitles"} : new String[]{"Resources", "Audio"};
        int active = sheetMode == 1 ? 0 : sheetTab;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(8), dp(13), dp(8), dp(13));
            t.setFocusable(true);
            t.setClickable(true);
            if (i == active) {
                t.setTextColor(Color.BLACK);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setBackground(stateBg(MINT));
            } else {
                t.setTextColor(0xB3FFFFFF);
                t.setBackground(stateBg(Color.TRANSPARENT));
            }
            t.setOnClickListener(v -> {
                sheetTab = idx;
                lastRowIdx = 0;
                renderSheet(true);
            });
            tabs.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(MATCH, WRAP);
        tl.leftMargin = dp(14);
        tl.rightMargin = dp(14);
        sheet.addView(tabs, tl);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        List<Opt> opts = sheetMode == 1 ? subtitleOpts() : (sheetTab == 0 ? videoOpts() : dubOpts());
        View selected = null;
        for (int i = 0; i < opts.size(); i++) {
            View r = optRow(opts.get(i), i);
            list.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
            if (opts.get(i).on && selected == null) selected = r;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        sl.topMargin = dp(12);
        sl.leftMargin = dp(14);
        sl.rightMargin = dp(14);
        sl.bottomMargin = dp(14);
        sheet.addView(sv, sl);

        View target = null;
        if (focusSelected) {
            target = selected != null ? selected : (list.getChildCount() > 0 ? list.getChildAt(0) : null);
        } else if (list.getChildCount() > 0) {
            target = list.getChildAt(Math.min(lastRowIdx, list.getChildCount() - 1));
        }
        if (target != null) target.requestFocus();
    }

    private View optRow(final Opt o, final int index) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(15), dp(18), dp(15));
        TextView t = new TextView(this);
        t.setText(o.label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(o.on ? MINT : Color.WHITE);
        if (o.on) t.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (o.on) {
            TextView c = new TextView(this);
            c.setText("✓");
            c.setTextColor(MINT);
            c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            r.addView(c, new LinearLayout.LayoutParams(WRAP, WRAP));
        }
        r.setBackground(stateBg(o.on ? 0xFF1D4A42 : Color.TRANSPARENT));
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(v -> {
            lastRowIdx = index;
            o.run.run();
            handler.postDelayed(() -> {
                if (sheetOpen) renderSheet(false);
            }, 250);
        });
        return r;
    }

    private List<TrackRef> trackRefs(int type) {
        List<TrackRef> out = new ArrayList<>();
        if (player == null) return out;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSupported(i)) out.add(new TrackRef(g, i, g.getTrackFormat(i)));
            }
        }
        return out;
    }

    private void selectTrack(TrackRef r) {
        int type = r.group.getType();
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
        if (type == C.TRACK_TYPE_AUDIO) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false);
            audioNone = false;
        } else if (type == C.TRACK_TYPE_TEXT) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
        } else if (type == C.TRACK_TYPE_VIDEO) {
            videoForced = true;
        }
        b.setOverrideForType(new TrackSelectionOverride(r.group.getMediaTrackGroup(), r.index));
        player.setTrackSelectionParameters(b.build());
    }

    private void disableType(int type) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type, true).build());
    }

    private List<Opt> videoOpts() {
        List<Opt> out = new ArrayList<>();
        // MovieBox resources are the authoritative quality choices. Keep every API resource
        // in this single menu; do not add the currently-playing decoder track as a second row.
        if (movieBoxMode) {
            if (sourceVariants.length() == 0) {
                out.add(new Opt(resourcesLoading ? "Loading resources…" : "No resources available", false, () -> { }));
                return out;
            }
            for (int i = 0; i < sourceVariants.length(); i++) {
                final JSONObject source = sourceVariants.optJSONObject(i);
                if (source == null) continue;
                String quality = source.optString("quality", "Auto");
                boolean selected = source.optString("url", "").equals(activeSourceUrl);
                out.add(new Opt(quality, selected, () -> switchSource(source)));
            }
            return out;
        }
        List<TrackRef> refs = trackRefs(C.TRACK_TYPE_VIDEO);
        Collections.sort(refs, new Comparator<TrackRef>() {
            @Override
            public int compare(TrackRef a, TrackRef b) {
                int c = Integer.compare(b.format.height, a.format.height);
                return c != 0 ? c : Integer.compare(bitrate(b.format), bitrate(a.format));
            }
        });
        if (refs.size() > 1) {
            out.add(new Opt("Auto • Adaptive", !videoForced, () -> {
                videoForced = false;
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build());
            }));
        }
        for (final TrackRef r : refs) {
            boolean on = refs.size() == 1 || (videoForced && r.group.isTrackSelected(r.index));
            out.add(new Opt(r.format.height > 0 ? r.format.height + "p" : "Auto", on, () -> selectTrack(r)));
        }
        if (refs.isEmpty()) out.add(new Opt(resourcesLoading ? "Loading…" : "Auto", false, () -> { }));
        return out;
    }

    private String fetchFreshMovieBoxToken() throws Exception {
        String[] bases = new String[]{"https://movieboxph.org/wefeed-h5api-bff/", "https://h5-api.aoneroom.com/wefeed-h5api-bff/"};
        Exception last = null;
        for (String base : bases) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(base + "home").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36");
                connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                connection.setRequestProperty("Origin", "https://movieboxph.org");
                connection.setRequestProperty("Referer", "https://movieboxph.org/");
                connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                connection.setRequestProperty("x-request-lang", "en");
                int status = connection.getResponseCode();
                InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (input != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                }
                if (status < 200 || status >= 300) throw new Exception("MovieBox /home HTTP " + status + ": " + body);
                JSONObject payload = new JSONObject(body.toString());
                String header = connection.getHeaderField("x-user");
                String token = "";
                if (header != null && !header.trim().isEmpty()) {
                    String value = header.trim();
                    if (value.startsWith("{")) {
                        try { token = new JSONObject(value).optString("token", ""); } catch (Exception ignored) { }
                    } else {
                        token = value;
                    }
                }
                if (token.trim().isEmpty()) token = payload.optString("token", "");
                token = token == null ? "" : token.trim().replaceFirst("(?i)^Bearer\\s+", "");
                if (token.isEmpty()) throw new Exception("MovieBox /home did not return a fresh x-user token");
                return token;
            } catch (Exception e) {
                last = e;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last == null ? new Exception("MovieBox /home session request failed") : last;
    }

    private String movieBoxReferer(String detailPath) {
        String path = detailPath == null ? "" : detailPath.trim();
        if (path.isEmpty()) return "https://movieboxph.org/";
        if (path.startsWith("https://") || path.startsWith("http://")) return path;
        if (path.startsWith("/")) return "https://movieboxph.org" + path;
        if (path.regionMatches(true, 0, "play/", 0, 5)) path = path.substring(5);
        return "https://movieboxph.org/play/" + path;
    }

    private void fetchMovieBoxResources(String subjectId, String detailPath, int season, int episode, boolean preservePosition) {
        if (resourcesLoading) return;
        resourcesLoading = true;
        final long resumeAt = preservePosition && player != null ? Math.max(0, player.getCurrentPosition()) : 0L;
        new Thread(() -> {
            Exception last = null;
            try {
                // Match the web app's session flow: /home first, then use its fresh x-user token.
                movieBoxToken = fetchFreshMovieBoxToken();
            } catch (Exception e) {
                last = e;
            }
            String[] bases = new String[]{"https://movieboxph.org/wefeed-h5api-bff/", "https://h5-api.aoneroom.com/wefeed-h5api-bff/"};
            if (last == null) {
                for (String base : bases) {
                    HttpURLConnection connection = null;
                    try {
                        String query = "subject/play?subjectId=" + URLEncoder.encode(subjectId, "UTF-8")
                                + "&se=" + Math.max(0, season) + "&ep=" + Math.max(0, episode);
                        if (detailPath != null && !detailPath.isEmpty()) query += "&detailPath=" + URLEncoder.encode(detailPath, "UTF-8");
                        connection = (HttpURLConnection) new URL(base + query).openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(25000);
                        connection.setUseCaches(false);
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36");
                        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                        connection.setRequestProperty("Origin", "https://movieboxph.org");
                        connection.setRequestProperty("Referer", movieBoxReferer(detailPath));
                        connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                        connection.setRequestProperty("x-request-lang", "en");
                        String cleanToken = movieBoxToken.replaceFirst("(?i)^Bearer\\s+", "");
                        connection.setRequestProperty("Authorization", "Bearer " + cleanToken);
                        connection.setRequestProperty("x-user", cleanToken);
                        int status = connection.getResponseCode();
                        InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                        StringBuilder body = new StringBuilder();
                        if (input != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                            String line;
                            while ((line = reader.readLine()) != null) body.append(line);
                            reader.close();
                        }
                        if (status < 200 || status >= 300) throw new Exception("MovieBox subject/play HTTP " + status + ": " + body);
                        JSONObject payload = new JSONObject(body.toString());
                        if (payload.has("code") && payload.optInt("code") != 0) {
                            throw new Exception("MovieBox API " + payload.optString("reason") + ": " + payload.optString("message", "play request rejected"));
                        }
                        JSONObject data = payload.optJSONObject("data");
                        if (data == null) throw new Exception("MovieBox subject/play response has no data field: " + body);
                        JSONArray found = new JSONArray();
                        collectMovieBoxSources(data, "data", false, new JSONObject(), found);
                        if (found.length() == 0) throw new Exception("MovieBox returned data but no playable stream URL was found. Response keys: " + data.names());
                        List<JSONObject> sortedSources = new ArrayList<>();
                        for (int i = 0; i < found.length(); i++) {
                            JSONObject item = found.optJSONObject(i);
                            if (item != null) sortedSources.add(item);
                        }
                        Collections.sort(sortedSources, (a, b) -> Integer.compare(
                                qualityNumber(b.optString("quality", "")), qualityNumber(a.optString("quality", ""))));
                        JSONArray ordered = new JSONArray();
                        for (JSONObject item : sortedSources) ordered.put(item);
                        final JSONArray orderedSources = ordered;
                        JSONObject first = orderedSources.getJSONObject(0);
                        runOnUiThread(() -> {
                            sourceVariants = orderedSources;
                            activeSourceUrl = first.optString("url", "");
                            titleView.setText(formatEpisodeTitle(basePlayerTitle));
                            playSource(first, resumeAt);
                            if (sheetOpen) renderSheet(false);
                        });
                        last = null;
                        break;
                    } catch (Exception e) {
                        last = e;
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                }
            }
            final Exception error = last;
            runOnUiThread(() -> {
                resourcesLoading = false;
                if (error != null) toast("Resource loading failed: " + error.getMessage());
                else if (sheetOpen) renderSheet(false);
            });
        }).start();
    }

    private void collectMovieBoxSources(Object value, String key, boolean inStream, JSONObject inherited, JSONArray out) {
        if (value == null || value == JSONObject.NULL) return;
        boolean context = inStream || key.matches("(?i)streams?|hls|dash|play|playback|video|videos|source|sources|quality|qualities|file|files|media|download");
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) collectMovieBoxSources(arr.opt(i), key, context, inherited, out);
        } else if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            JSONObject meta = new JSONObject();
            java.util.Iterator<String> inheritedKeys = inherited.keys();
            while (inheritedKeys.hasNext()) {
                String inheritedKey = inheritedKeys.next();
                try { meta.put(inheritedKey, inherited.opt(inheritedKey)); } catch (Exception ignored) { }
            }
            for (String k : new String[]{"resolutions","resolution","quality","label","name","definition","height","format","mimeType","mime","protocol","type"}) {
                if (obj.has(k)) try { meta.put(k, obj.opt(k)); } catch (Exception ignored) { }
            }
            String url = "";
            for (String k : new String[]{"url","src","playUrl","streamUrl","fileUrl","uri","videoUrl","mediaUrl","downloadUrl","masterUrl"}) {
                String candidate = obj.optString(k, "");
                if (candidate.startsWith("http")) { url = candidate; break; }
            }
            if (!url.isEmpty() && context) addMovieBoxSource(url, key, meta, out);
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) { String child = it.next(); collectMovieBoxSources(obj.opt(child), child, context, meta, out); }
        } else if (value instanceof String) {
            String url = (String) value;
            if (url.startsWith("http") && (context || url.matches("(?i).*\\.(m3u8|mpd|mp4|m4v|webm)(\\?.*)?$"))) addMovieBoxSource(url, key, inherited, out);
        }
    }

    private static int qualityNumber(String quality) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3,4})").matcher(quality == null ? "" : quality);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void addMovieBoxSource(String url, String key, JSONObject meta, JSONArray out) {
        String format = meta.optString("format", meta.optString("mimeType", meta.optString("protocol", key)));
        String lower = (key + " " + format + " " + url).toLowerCase(Locale.ROOT);
        if (lower.contains("m3u8") || lower.contains("hls")) format = "HLS";
        else if (lower.contains("mpd") || lower.contains("dash")) format = "DASH";
        else if (lower.contains("mp4")) format = "MP4";

        Object raw = meta.opt("resolutions");
        if (raw == null || raw == JSONObject.NULL || raw.toString().trim().isEmpty()) raw = meta.opt("resolution");
        if (raw == null || raw == JSONObject.NULL || raw.toString().trim().isEmpty()) raw = meta.opt("quality");
        if (raw == null || raw == JSONObject.NULL || raw.toString().trim().isEmpty()) raw = meta.opt("label");
        if (raw == null || raw == JSONObject.NULL || raw.toString().trim().isEmpty()) raw = meta.opt("name");

        List<String> qualities = new ArrayList<>();
        if (raw instanceof JSONArray) {
            JSONArray values = (JSONArray) raw;
            for (int i = 0; i < values.length(); i++) addQualityToken(qualities, values.opt(i));
        } else if (raw != null && raw != JSONObject.NULL) {
            String value = String.valueOf(raw);
            for (String part : value.split("[,;|]")) addQualityToken(qualities, part);
        }
        if (qualities.isEmpty()) {
            int height = meta.optInt("height", 0);
            if (height > 0) qualities.add(height + "p");
            else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(2160|1440|1080|720|576|540|480|360|240|144)\\s*p?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(meta.toString() + " " + url);
                if (m.find()) qualities.add(m.group(1) + "p");
            }
        }
        if (qualities.isEmpty()) qualities.add("Auto");

        for (String quality : qualities) {
            boolean duplicate = false;
            for (int i = 0; i < out.length(); i++) {
                JSONObject existing = out.optJSONObject(i);
                if (existing != null && url.equals(existing.optString("url")) && quality.equals(existing.optString("quality"))) { duplicate = true; break; }
            }
            if (duplicate) continue;
            try {
                JSONObject item = new JSONObject();
                item.put("url", url); item.put("format", format); item.put("quality", quality);
                out.put(item);
            } catch (Exception ignored) { }
        }
    }

    private static void addQualityToken(List<String> out, Object value) {
        if (value == null || value == JSONObject.NULL) return;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(2160|1440|1080|720|576|540|480|360|240|144)\\s*p?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            String quality = m.group(1) + "p";
            if (!out.contains(quality)) out.add(quality);
        }
    }

    private void playSource(JSONObject source, long position) {
        String url = source.optString("url", "");
        if (url.isEmpty() || player == null) return;
        String format = source.optString("format", "").toUpperCase(Locale.ROOT);
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        if (format.contains("HLS")) b.setMimeType("application/x-mpegURL");
        else if (format.contains("DASH")) b.setMimeType("application/dash+xml");
        else if (format.contains("MP4")) b.setMimeType("video/mp4");
        activeSourceUrl = url;
        player.setMediaItem(b.build());
        player.prepare();
        if (position > 0) player.seekTo(position);
        player.setPlayWhenReady(true);
    }

    private List<Opt> dubOpts() {
        List<Opt> out = new ArrayList<>();
        for (int i = 0; i < dubVariants.length(); i++) {
            JSONObject dub = dubVariants.optJSONObject(i);
            if (dub == null) continue;
            String id = dub.optString("subjectId", "");
            if (id.isEmpty()) continue;
            String name = dub.optString("lanName", dub.optString("lanCode", "Audio"));
            boolean selected = id.equals(movieBoxSubjectId);
            out.add(new Opt(name, selected, () -> {
                movieBoxSubjectId = id;
                movieBoxDetailPath = dub.optString("detailPath", "");
                final String fallbackTitle = originalPlayerTitle;
                final String selectedDubName = name;
                titleView.setText("Loading title…");
                new Thread(() -> {
                    String resolvedTitle = dub.optString("title", dub.optString("name", "")).trim();
                    if (resolvedTitle.isEmpty()) resolvedTitle = fetchMovieBoxSubjectTitle(id);
                    final String finalTitle = resolvedTitle == null || resolvedTitle.trim().isEmpty() ? fallbackTitle : resolvedTitle.trim();
                    runOnUiThread(() -> {
                        basePlayerTitle = finalTitle;
                        titleView.setText(formatEpisodeTitle(basePlayerTitle));
                        fetchMovieBoxResources(movieBoxSubjectId, movieBoxDetailPath, movieBoxSeason, movieBoxEpisode, true);
                        if (sheetOpen) renderSheet(false);
                    });
                }).start();
                toast("Loading " + selectedDubName + "…");
            }));
        }
        if (out.isEmpty()) out.add(new Opt("No dubbed languages available", false, () -> { }));
        return out;
    }

    private String fetchMovieBoxSubjectTitle(String subjectId) {
        String[] bases = new String[]{"https://movieboxph.org/wefeed-h5api-bff/", "https://h5-api.aoneroom.com/wefeed-h5api-bff/"};
        for (String base : bases) {
            HttpURLConnection connection = null;
            try {
                String query = "detail?subjectId=" + URLEncoder.encode(subjectId, "UTF-8");
                connection = (HttpURLConnection) new URL(base + query).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(18000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Origin", "https://movieboxph.org");
                connection.setRequestProperty("Referer", "https://movieboxph.org/");
                connection.setRequestProperty("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}");
                connection.setRequestProperty("x-request-lang", "en");
                if (movieBoxToken != null && !movieBoxToken.isEmpty()) {
                    String clean = movieBoxToken.replaceFirst("(?i)^Bearer\\s+", "");
                    connection.setRequestProperty("Authorization", "Bearer " + clean);
                    connection.setRequestProperty("x-user", clean);
                }
                InputStream input = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                StringBuilder body = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                JSONObject payload = new JSONObject(body.toString());
                JSONObject data = payload.optJSONObject("data");
                JSONObject subject = data == null ? null : data.optJSONObject("subject");
                if (subject != null) {
                    String title = subject.optString("title", subject.optString("name", "")).trim();
                    if (!title.isEmpty()) return title;
                }
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
        }
        return "";
    }

    private int currentEpisodeIndex() {
        for (int i = 0; i < episodeSequence.length(); i++) {
            JSONObject item = episodeSequence.optJSONObject(i);
            if (item != null && item.optInt("se", -1) == movieBoxSeason && item.optInt("ep", -1) == movieBoxEpisode) return i;
        }
        return -1;
    }

    private boolean hasNextEpisode() {
        if (storyTvMode) return storyTvEpisode < storyTvEpisodeCount;
        int i = currentEpisodeIndex();
        return episodeSequence.length() > 0 && (i < 0 || i + 1 < episodeSequence.length());
    }
    private boolean hasPreviousEpisode() {
        if (storyTvMode) return storyTvEpisode > 1;
        int i = currentEpisodeIndex();
        return i > 0;
    }

    private void updateEpisodeControls() {
        boolean episodic = (movieBoxMode && episodeSequence.length() > 0) || (storyTvMode && storyTvEpisodeCount > 1);
        if (previousEpisodeBtn != null) {
            previousEpisodeBtn.setVisibility(episodic ? View.VISIBLE : View.GONE);
            previousEpisodeBtn.setEnabled(episodic && hasPreviousEpisode());
            previousEpisodeBtn.setAlpha(hasPreviousEpisode() ? 1f : 0.35f);
        }
        if (nextEpisodeBtn != null) {
            nextEpisodeBtn.setVisibility(episodic ? View.VISIBLE : View.GONE);
            nextEpisodeBtn.setEnabled(episodic && hasNextEpisode());
            nextEpisodeBtn.setAlpha(hasNextEpisode() ? 1f : 0.35f);
        }
    }

    private String formatEpisodeTitle(String rawTitle) {
        String clean = rawTitle == null ? "" : rawTitle.trim();
        if (storyTvMode) return clean + " · Episode " + storyTvEpisode;
        if (!movieBoxMode || episodeSequence.length() == 0 || movieBoxEpisode <= 0) return clean;
        clean = clean.replaceAll("(?i)\\s*[·|—-]?\\s*S\\d{1,2}\\s*E\\d{1,3}\\s*$", "").trim();
        return clean + " · S" + String.format(Locale.US, "%02d", movieBoxSeason)
                + " E" + String.format(Locale.US, "%02d", movieBoxEpisode);
    }

    private void updateEpisodeSubtitle() {
        if (episodeSubtitleView == null) return;
        episodeSubtitleView.setVisibility(View.GONE);
        if (titleView != null) titleView.setText(formatEpisodeTitle(basePlayerTitle));
        updateEpisodeControls();
    }

    private void playAdjacentEpisode(int direction) {
        if (storyTvMode) {
            int targetEpisode = storyTvEpisode + direction;
            if (targetEpisode >= 1 && targetEpisode <= storyTvEpisodeCount) playStoryTvEpisode(targetEpisode);
            return;
        }
        int index = currentEpisodeIndex();
        if (index < 0 && direction > 0) index = -1;
        int target = index + direction;
        if (target < 0 || target >= episodeSequence.length()) return;
        JSONObject next = episodeSequence.optJSONObject(target);
        if (next == null) return;
        movieBoxSeason = next.optInt("se", movieBoxSeason);
        movieBoxEpisode = next.optInt("ep", movieBoxEpisode);
        updateEpisodeSubtitle();
        fetchMovieBoxResources(movieBoxSubjectId, movieBoxDetailPath, movieBoxSeason, movieBoxEpisode, false);
    }

    private void playStoryTvEpisode(int episodeNumber) {
        if (!storyTvMode || episodeNumber < 1 || episodeNumber > storyTvEpisodeCount) return;
        storyTvEpisode = episodeNumber;
        updateEpisodeSubtitle();
        titleView.setText(formatEpisodeTitle(basePlayerTitle) + "  •  Loading stream…");
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        updateEpisodeControls();
        fetchStoryTvStream(episodeNumber);
    }


    private void installCrashRecorder() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            String report = "FATAL CRASH on " + thread.getName() + "\n" + stackTrace(error);
            getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit().putString("last_crash", report).apply();
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void diag(String message) {
        String line = new java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new java.util.Date())
                + "  " + message;
        Log.e("StoryTVDebug", line);
        synchronized (diagnosticBuffer) {
            diagnosticBuffer.append(line).append("\n\n");
            if (diagnosticBuffer.length() > 18000) diagnosticBuffer.delete(0, diagnosticBuffer.length() - 18000);
        }
        if (diagnosticText != null) runOnUiThread(() -> diagnosticText.setText(diagnosticBuffer.toString()));
    }

    private void addDiagnosticsPanel() {
        String previousCrash = getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).getString("last_crash", "");
        if (previousCrash != null && !previousCrash.isEmpty()) {
            diagnosticBuffer.append("PREVIOUS APP CRASH (saved):\n").append(previousCrash).append("\n\n");
            getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit().remove("last_crash").apply();
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackgroundColor(0xE6000000);
        TextView heading = new TextView(this);
        heading.setText("STORY TV DEBUG LOG  •  tap COPY to share");
        heading.setTextColor(0xFFB7F0DF);
        heading.setTextSize(12);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView scroll = new ScrollView(this);
        diagnosticText = new TextView(this);
        diagnosticText.setTextColor(Color.WHITE);
        diagnosticText.setTextSize(10);
        diagnosticText.setTextIsSelectable(true);
        diagnosticText.setPadding(0, dp(6), 0, dp(6));
        scroll.addView(diagnosticText);
        panel.addView(scroll, new LinearLayout.LayoutParams(MATCH, dp(125)));
        Button copy = new Button(this);
        copy.setText("COPY ERROR LOG");
        copy.setOnClickListener(v -> {
            String report;
            synchronized (diagnosticBuffer) { report = diagnosticBuffer.toString(); }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Story TV diagnostics", report));
            Toast.makeText(this, "Diagnostic log copied", Toast.LENGTH_LONG).show();
        });
        panel.addView(copy, new LinearLayout.LayoutParams(MATCH, WRAP));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM);
        lp.setMargins(dp(8), 0, dp(8), dp(8));
        root.addView(panel, lp);
        diagnosticText.setText(diagnosticBuffer.toString());
    }

    private static String stackTrace(Throwable error) {
        if (error == null) return "(no exception details)";
        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static String playbackStateName(int state) {
        if (state == Player.STATE_IDLE) return "IDLE";
        if (state == Player.STATE_BUFFERING) return "BUFFERING";
        if (state == Player.STATE_READY) return "READY";
        if (state == Player.STATE_ENDED) return "ENDED";
        return "UNKNOWN(" + state + ")";
    }

    private static String safe(String value) {
        return value == null ? "(null)" : value;
    }

    private static String safeHost(String value) {
        try { return new URL(value).getHost(); } catch (Exception ignored) { return "(invalid URL)"; }
    }

    private void fetchStoryTvStream(int episodeNumber) {
        diag("API request start: metadata episode=" + episodeNumber + "; showId=" + safe(storyTvShowId));
        titleView.setText(basePlayerTitle + " · Episode " + episodeNumber + "  •  Loading stream…");
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                int cursor=((episodeNumber-1)/5)*5;
                String endpoint="https://elitemods-tv.onrender.com/feedservice/v1/episode/metadata/"+URLEncoder.encode(storyTvShowId,"UTF-8")+"?cursor="+cursor+"&dir=NEXT";
                connection=(HttpURLConnection)new URL(endpoint).openConnection();
                connection.setRequestMethod("GET");connection.setConnectTimeout(15000);connection.setReadTimeout(25000);
                connection.setRequestProperty("Authorization","Bearer eyJhbGciOiJIUzI1NiJ9.eyJjcmVhdGVkRGF0ZSI6IkZyaSBKdW4gMTIgMDc6MDQ6MTAgVVRDIDIwMjYiLCJzZXNzaW9uSWQiOiIxNTI0MDExNDMiLCJkZXZpY2VJZCI6IjI4MDhkNDliN2UwOGI3MTYiLCJleHAiOjE3ODE1MDcwNTB9._GKqF_5WtYkgAIJVhkt3L27t9fvgLkFtfgaOLSPsrOA");
                connection.setRequestProperty("appVersion","62");connection.setRequestProperty("os","Android 13 (API 33)");
                connection.setRequestProperty("deviceId","a6229bad5c179d51");connection.setRequestProperty("platform","0");
                connection.setRequestProperty("iamsinu","iamsinu");connection.setRequestProperty("network_type","WIFI");
                connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","ktor-client");
                int status=connection.getResponseCode();
                diag("Story API HTTP status: " + status + "; endpoint cursor=" + cursor);
                InputStream input=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
                ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
                JSONObject response=new JSONObject(new String(output.toByteArray(),"UTF-8"));
                if(status<200||status>=300)throw new Exception("Story TV HTTP "+status);
                JSONArray content=response.optJSONObject("data")==null?new JSONArray():response.optJSONObject("data").optJSONArray("content");
                JSONObject target=null;if(content!=null)for(int i=0;i<content.length();i++){JSONObject item=content.optJSONObject(i);if(item!=null&&item.optInt("index")==episodeNumber){target=item;break;}}
                if(target==null)throw new Exception("Episode "+episodeNumber+" stream not found");
                String stream=target.optString("epsUrl","");
                diag("Episode metadata found; epsUrlPresent=" + !stream.isEmpty() + "; contentCount=" + (content == null ? 0 : content.length()));
                if(stream.isEmpty())throw new Exception("Empty stream URL");
                final String streamUrl=stream;final String episodeName=target.optString("epsTitle","Episode "+episodeNumber);
                runOnUiThread(() -> {
                    basePlayerTitle=getIntent().getStringExtra("title");if(basePlayerTitle==null)basePlayerTitle="Story TV";
                    titleView.setText(basePlayerTitle+" · Episode "+episodeNumber);
                    MediaItem item=new MediaItem.Builder().setUri(Uri.parse(streamUrl)).setMimeType("application/x-mpegURL")
                            .setMediaMetadata(new MediaMetadata.Builder().setTitle(episodeName).build()).build();
                    if (player == null || isFinishing() || isDestroyed()) return;
                    diag("Setting HLS media item for episode " + episodeNumber + "; URL host=" + safeHost(streamUrl));
                    player.setMediaItem(item);
                    player.prepare();
                    player.setPlayWhenReady(true);
                    updateEpisodeControls();
                    showControls();
                });
            } catch(Exception error) { final String message=error.getMessage();
                diag("STORY API/FETCH ERROR: " + stackTrace(error));
                runOnUiThread(() -> {titleView.setText(basePlayerTitle+" · Episode "+episodeNumber);toast("Story TV stream failed: "+message);showControls();});}
            finally {if(connection!=null)connection.disconnect();}
        }).start();
    }

    private void switchSource(JSONObject source) {
        if (player == null || source == null) return;
        String url = source.optString("url", "");
        String format = source.optString("format", "");
        if (url.isEmpty()) return;
        long position = Math.max(0, player.getCurrentPosition());
        boolean wasPlaying = player.getPlayWhenReady();
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        String f = format.toUpperCase(Locale.ROOT);
        if (f.contains("HLS") || f.contains("M3U8")) builder.setMimeType("application/x-mpegURL");
        else if (f.contains("DASH") || f.contains("MPD")) builder.setMimeType("application/dash+xml");
        else if (f.contains("MP4")) builder.setMimeType("video/mp4");
        activeSourceUrl = url;
        player.setMediaItem(builder.build());
        player.prepare();
        if (position > 0) player.seekTo(position);
        player.setPlayWhenReady(wasPlaying);
        toast("Source changed: " + source.optString("quality", format));
    }

    private List<Opt> audioOpts() {
        List<Opt> out = new ArrayList<>();
        out.add(new Opt("None • Mute audio", audioNone, () -> {
            audioNone = true;
            disableType(C.TRACK_TYPE_AUDIO);
        }));
        List<TrackRef> refs = trackRefs(C.TRACK_TYPE_AUDIO);
        for (final TrackRef r : refs) {
            boolean on = !audioNone && (refs.size() == 1 || r.group.isTrackSelected(r.index));
            out.add(new Opt(audioLabel(r.format), on, () -> selectTrack(r)));
        }
        return out;
    }

    private List<Opt> subtitleOpts() {
        List<Opt> out = new ArrayList<>();
        List<TrackRef> refs = trackRefs(C.TRACK_TYPE_TEXT);
        boolean any = false;
        for (TrackRef r : refs) {
            if (r.group.isTrackSelected(r.index)) any = true;
        }
        out.add(new Opt("Off", !any, () -> disableType(C.TRACK_TYPE_TEXT)));
        int n = 0;
        for (final TrackRef r : refs) {
            n++;
            String name = r.format.label != null ? r.format.label : langName(r.format.language);
            if (name.isEmpty()) name = "Subtitles " + n;
            out.add(new Opt(name, r.group.isTrackSelected(r.index), () -> selectTrack(r)));
        }
        if (refs.isEmpty()) out.add(new Opt("No dubbed languages available", false, () -> { }));
        return out;
    }

    /* ===================================================== labels ===================================================== */

    private static int bitrate(Format f) {
        if (f.averageBitrate != Format.NO_VALUE) return f.averageBitrate;
        if (f.bitrate != Format.NO_VALUE) return f.bitrate;
        return 0;
    }

    private static String codec(Format f) {
        String c = f.codecs == null ? "" : f.codecs.toLowerCase(Locale.ROOT);
        String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
        if (c.contains("avc") || MimeTypes.VIDEO_H264.equals(m)) return "H.264";
        if (c.contains("hev") || c.contains("hvc") || MimeTypes.VIDEO_H265.equals(m)) return "H.265";
        if (c.contains("av01") || MimeTypes.VIDEO_AV1.equals(m)) return "AV1";
        if (c.contains("vp09") || c.contains("vp9") || MimeTypes.VIDEO_VP9.equals(m)) return "VP9";
        if (c.contains("vp8") || MimeTypes.VIDEO_VP8.equals(m)) return "VP8";
        return "";
    }

    private static String rateText(int bps) {
        if (bps >= 1000000) return String.format(Locale.US, "%.1f Mbps", bps / 1000000f);
        if (bps > 0) return (bps / 1000) + " kbps";
        return "";
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(p);
        }
        return sb.toString();
    }

    private static String videoLabel(Format f) {
        List<String> p = new ArrayList<>();
        int h = f.height;
        p.add(h > 0 ? h + "p" : "Video");
        if (h >= 2160) p.add("4K");
        else if (h >= 1080) p.add("FHD");
        else if (h >= 720) p.add("HD");
        p.add(codec(f));
        p.add(rateText(bitrate(f)));
        return join(p);
    }

    private static String langName(String code) {
        if (code == null || code.isEmpty() || "und".equals(code)) return "";
        String n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
        return (n == null || n.isEmpty()) ? code : n;
    }

    private static String audioLabel(Format f) {
        String lang = langName(f.language);
        if (lang.isEmpty()) lang = f.label != null ? f.label : "Audio";
        String ch;
        switch (f.channelCount) {
            case 1:
                ch = "Mono";
                break;
            case 2:
                ch = "Stereo";
                break;
            case 6:
                ch = "5.1";
                break;
            case 8:
                ch = "7.1";
                break;
            default:
                ch = f.channelCount > 0 ? f.channelCount + " ch" : "Stereo";
                break;
        }
        return lang + " • " + ch;
    }
}
