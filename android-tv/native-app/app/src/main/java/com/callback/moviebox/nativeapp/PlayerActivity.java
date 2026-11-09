package com.callback.moviebox.nativeapp;

import android.app.Activity;
import android.media.audiofx.LoudnessEnhancer;
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
    private boolean freeReelsMode = false;
    private int freeReelsEpisode = 1;
    private String freeReelsSeriesId = "";
    private String freeReelsPreferredSubtitleLanguage = "";
    private boolean freeReelsApplyPreferredSubtitle = false;
    private int freeReelsPreferredQualityHeight = 0;
    private LoudnessEnhancer loudnessEnhancer;
    private int volumeBoostPercent = 100;
    private boolean volumeMuted = false;
    private JSONArray freeReelsEpisodes = new JSONArray();
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
    private int sheetMode = 0; // 0 = Video/Audio, 2 = Aspect, 3 = Speed, 4 = Volume
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
        if (loudnessEnhancer != null) { try { loudnessEnhancer.release(); } catch (Exception ignored) { } loudnessEnhancer = null; }
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
        if (getIntent().getBooleanExtra("freeReels", false)) {
            requestHeaders.put("User-Agent", "okhttp/4.12.0");
            requestHeaders.put("Origin", "https://free-reels.com");
            requestHeaders.put("Referer", "https://free-reels.com/");
            requestHeaders.put("Accept", "*/*");
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
                if (freeReelsMode && state == Player.STATE_READY && titleView != null) {
                    titleView.setText(formatEpisodeTitle(basePlayerTitle));
                }
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
                if (freeReelsMode && freeReelsPreferredQualityHeight > 0) applyPreferredFreeReelsQuality();
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
        freeReelsMode = getIntent().getBooleanExtra("freeReels", false);
        freeReelsEpisode = getIntent().getIntExtra("episode", 1);
        freeReelsSeriesId = getIntent().getStringExtra("freeReelsSeriesId");
        try { freeReelsEpisodes = new JSONArray(getIntent().getStringExtra("freeReelsEpisodes") == null ? "[]" : getIntent().getStringExtra("freeReelsEpisodes")); } catch (Exception ignored) { freeReelsEpisodes = new JSONArray(); }
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
        JSONArray freeReelsSubtitles;
        try { freeReelsSubtitles = new JSONArray(getIntent().getStringExtra("freeReelsSubtitles") == null ? "[]" : getIntent().getStringExtra("freeReelsSubtitles")); } catch (Exception ignored) { freeReelsSubtitles = new JSONArray(); }
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
            // Subtitle tracks are intentionally omitted for every server.\n            player.setMediaItem(mediaItemBuilder.build());
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
        volBtn = addButton(row, "ic_volume", "Volume", v -> openSheet(4, 0));
        addButton(row, "ic_audio", "Audio", v -> openSheet(0, 1));
        addButton(row, "ic_quality", "Quality", v -> openSheet(0, 0));
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

    private void toggleMute() { volumeMuted = !volumeMuted; applyVolumeBoost(); }
    private List<Opt> aspectOpts() { List<Opt> out=new ArrayList<>(); for(int i=0;i<fitNames.length;i++){final int n=i;out.add(new Opt(fitNames[i],fitIndex==i,()->{fitIndex=n;playerView.setResizeMode(fitModes[n]);renderSheet(false);}));}return out; }
    private List<Opt> speedOpts() { List<Opt> out=new ArrayList<>();for(int i=0;i<speeds.length;i++){final int n=i;out.add(new Opt(String.format(Locale.US,"%.2g×",speeds[i]),speedIndex==i,()->{speedIndex=n;if(player!=null)player.setPlaybackSpeed(speeds[n]);renderSheet(false);}));}return out; }
    private List<Opt> volumeOpts() { List<Opt> out=new ArrayList<>();if(volumeBoostPercent==100)out.add(new Opt("Mute",volumeMuted,()->{volumeMuted=!volumeMuted;applyVolumeBoost();renderSheet(false);}));out.add(new Opt("Volume boost "+volumeBoostPercent+"%",volumeBoostPercent>100,()->{LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),dp(12),dp(18),dp(12));TextView val=new TextView(this);val.setText("Boost: "+volumeBoostPercent+"%");val.setTextColor(Color.WHITE);val.setTextSize(18);p.addView(val);android.widget.SeekBar bar=new android.widget.SeekBar(this);bar.setMax(100);bar.setProgress(volumeBoostPercent-100);p.addView(bar);bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar b,int x,boolean u){volumeBoostPercent=100+x;volumeMuted=false;val.setText("Boost: "+volumeBoostPercent+"%");applyVolumeBoost();}public void onStartTrackingTouch(android.widget.SeekBar b){}public void onStopTrackingTouch(android.widget.SeekBar b){}});sheet.removeAllViews();sheet.addView(p,new LinearLayout.LayoutParams(MATCH,WRAP));bar.setFocusable(true);bar.requestFocus();}));return out; }
    private void applyVolumeBoost(){if(player==null)return;try{if(loudnessEnhancer==null)loudnessEnhancer=new LoudnessEnhancer(player.getAudioSessionId());loudnessEnhancer.setTargetGain((volumeBoostPercent-100)*100);loudnessEnhancer.setEnabled(volumeBoostPercent>100);}catch(Exception e){diag("Volume boost unavailable: "+e.getMessage());}player.setVolume(volumeMuted?0f:1f);if(volBtn!=null)volBtn.setImageResource(drawableId(volumeMuted?"ic_volume_off":"ic_volume"));}

    private void cycleSpeed() { openSheet(3,0); }
    private void cycleFit() { openSheet(2,0); }
    private void applyPreferredFreeReelsQuality(){if(player==null||!freeReelsMode||freeReelsPreferredQualityHeight<=0)return;for(TrackRef ref:trackRefs(C.TRACK_TYPE_VIDEO)){if(ref.format.height==freeReelsPreferredQualityHeight&&!ref.group.isTrackSelected(ref.index)){TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon();b.setOverrideForType(new TrackSelectionOverride(ref.group.getMediaTrackGroup(),ref.index));player.setTrackSelectionParameters(b.build());return;}}}

    private void updateEpisodeSubtitle() {
        if (episodeSubtitleView == null) return;
        episodeSubtitleView.setVisibility(View.GONE);
        if (titleView != null) titleView.setText(formatEpisodeTitle(basePlayerTitle));
        updateEpisodeControls();
    }

    private void playAdjacentEpisode(int direction) {
        if (freeReelsMode) {
            int targetIndex=freeReelsEpisodeIndex()+direction;
            if(targetIndex>=0&&targetIndex<freeReelsEpisodes.length()) playFreeReelsEpisodeAt(targetIndex);
            return;
        }
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

    private void playFreeReelsEpisodeAt(int index) {
        if (!freeReelsMode || index < 0 || index >= freeReelsEpisodes.length()) return;
        JSONObject cached = freeReelsEpisodes.optJSONObject(index);
        if (cached == null) return;
        freeReelsEpisode = cached.optInt("index", index + 1);
        titleView.setText(formatEpisodeTitle(basePlayerTitle) + "  •  Loading stream…");
        if (player != null) { player.stop(); player.clearMediaItems(); }
        updateEpisodeControls();
        final int targetEpisode = freeReelsEpisode;
        new Thread(() -> {
            JSONObject selected = cached;
            try {
                if (freeReelsSeriesId != null && !freeReelsSeriesId.trim().isEmpty()) {
                    JSONObject response = new FreeReelsRepository().dramaInfo(freeReelsSeriesId);
                    JSONObject data = response.optJSONObject("data");
                    JSONObject info = data == null ? null : data.optJSONObject("info");
                    JSONArray freshEpisodes = info == null ? null : info.optJSONArray("episode_list");
                    if (freshEpisodes != null) {
                        for (int i = 0; i < freshEpisodes.length(); i++) {
                            JSONObject candidate = freshEpisodes.optJSONObject(i);
                            if (candidate != null && candidate.optInt("index", i + 1) == targetEpisode) {
                                selected = candidate;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception error) {
                diag("FreeReels subtitle refresh failed for episode " + targetEpisode + ": " + error.getMessage());
            }
            final JSONObject episodeData = selected;
            runOnUiThread(() -> startFreeReelsMedia(cached, episodeData, targetEpisode));
        }).start();
    }

    private void applyPreferredFreeReelsSubtitle() {
        if (player == null || freeReelsPreferredSubtitleLanguage.isEmpty()) return;
        List<TrackRef> refs = trackRefs(C.TRACK_TYPE_TEXT);
        for (TrackRef ref : refs) {
            String language = ref.format.language;
            if (language != null && language.equalsIgnoreCase(freeReelsPreferredSubtitleLanguage)) {
                freeReelsApplyPreferredSubtitle = false;
                selectTrack(ref);
                diag("FreeReels preferred subtitle restored: " + language);
                return;
            }
        }
        // Keep it pending until ExoPlayer finishes discovering the subtitle tracks.
    }

    private void startFreeReelsMedia(JSONObject cached, JSONObject episodeData, int episodeNumber) {
        if (!freeReelsMode || freeReelsEpisode != episodeNumber || player == null) return;
        String stream = cached.optString("external_audio_h264_m3u8", "");
        if (stream.isEmpty()) stream = cached.optString("video_url", "");
        if (stream.isEmpty()) stream = cached.optString("m3u8_url", "");
        if (stream.isEmpty()) stream = cached.optString("external_audio_h265_m3u8", "");
        if (stream.isEmpty()) { toast("FreeReels Episode " + episodeNumber + " has no stream URL"); return; }
        activeSourceUrl = stream;
        MediaItem.Builder builder = new MediaItem.Builder().setUri(stream);
        player.setMediaItem(builder.build());
        freeReelsApplyPreferredSubtitle = !freeReelsPreferredSubtitleLanguage.isEmpty();
        player.prepare();
        player.setPlayWhenReady(true);
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
