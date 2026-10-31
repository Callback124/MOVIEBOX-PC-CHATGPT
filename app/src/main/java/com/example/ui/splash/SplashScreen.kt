package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MovieBoxRed
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("splash_screen")
    ) {
        // Subtle background radial accent glow in the center
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MovieBoxRed.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Content: Logo, App Title, Tagline
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo Icon
            Surface(
                modifier = Modifier
                    .size(92.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .testTag("splash_logo"),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF16161E),
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_moviebox_logo),
                        contentDescription = "MovieBox Logo",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Name
            Text(
                text = "MovieBox",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(alpha.value)
                    .testTag("splash_app_title")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tagline
            Text(
                text = "Movies, Series & Shorts Streaming",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.3.sp,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Modern mini loader
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(alpha.value),
                color = MovieBoxRed,
                strokeWidth = 2.5.dp
            )
        }

        // Bottom Subtitle & Branding: "Made By Nadeem"
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .testTag("splash_subtitle_container"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DEVELOPED BY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Main Subtitle: "Made By Nadeem"
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.testTag("splash_made_by_nadeem")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MovieBoxRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.splash_made_by_nadeem),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App Version Badge (e.g. v1.2)
            Text(
                text = "v${com.example.BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 1.sp,
                modifier = Modifier.testTag("splash_version_text")
            )
        }
    }
}
