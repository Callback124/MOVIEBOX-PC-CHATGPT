package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.example.data.updater.AppUpdateInfo
import com.example.data.updater.DownloadState
import com.example.ui.theme.MovieBoxRed

@Composable
fun MandatoryUpdateDialog(
    updateInfo: AppUpdateInfo,
    onOpenUpdateLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Compulsory/Mandatory Update: block back press so old version is strictly restricted
    BackHandler(enabled = true) {
        // Deliberately consumed to keep app blocked until updated
    }

    val blockInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = blockInteractionSource,
                indication = null,
                onClick = { /* Consumed: strictly block all touches outside */ }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("mandatory_update_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(0.92f)
                .testTag("mandatory_update_card"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161622),
            border = BorderStroke(1.2.dp, Color(0xFF2E2E40)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon: Update / Download Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MovieBoxRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "App Update Required",
                        tint = MovieBoxRed,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Update Required!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version comparison badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Current: v${updateInfo.currentVersion}",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0B0),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "➔",
                        fontSize = 12.sp,
                        color = MovieBoxRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MovieBoxRed.copy(alpha = 0.2f),
                        border = BorderStroke(0.8.dp, MovieBoxRed.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "New: v${updateInfo.latestVersion}",
                            fontSize = 12.sp,
                            color = MovieBoxRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory notice text
                Text(
                    text = "A new version of MovieBox is available. To continue watching movies and series without interruptions, please update the app now.",
                    fontSize = 13.sp,
                    color = Color(0xFFC0C0D0),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Update Now Button: Opens GitHub release link directly in browser
                Button(
                    onClick = onOpenUpdateLink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("update_now_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MovieBoxRed)
                ) {
                    Text(
                        text = "Update Now",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
