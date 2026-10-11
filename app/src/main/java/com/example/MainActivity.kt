package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen always on while the app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        com.example.data.repository.WatchHistoryManager.init(applicationContext)
        com.example.data.repository.RecentSearchesManager.init(applicationContext)

        // Configure ultra-high-performance Coil ImageLoader for instantaneous image rendering
        val imageOkHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cache(Cache(applicationContext.cacheDir.resolve("http_image_cache"), 150L * 1024 * 1024))
            .build()

        val isTv = com.example.util.DeviceUtils.isAndroidTv(applicationContext)
        if (isTv) {
            try {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } catch (_: Exception) {}
        } else {
            try {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (_: Exception) {}
        }
        val imageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient(imageOkHttpClient)
            .components {
                add(coil.decode.SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(if (isTv) 0.20 else 0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val uiState by homeViewModel.uiState.collectAsState()
                    var isSplashVisible by remember { mutableStateOf(true) }

                    LaunchedEffect(uiState.isLoading) {
                        if (!uiState.isLoading) {
                            delay(180)
                            isSplashVisible = false
                        } else {
                            delay(500)
                            isSplashVisible = false
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main home screen loads in background
                        HomeScreen(viewModel = homeViewModel)

                        // Splash screen overlay with elegant fade out
                        AnimatedVisibility(
                            visible = isSplashVisible,
                            enter = fadeIn(),
                            exit = fadeOut(animationSpec = tween(300))
                        ) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }
}
