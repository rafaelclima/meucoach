package com.meucoach.app.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme
import java.util.Locale

/**
 * Profile picture with a graceful fallback: initial of the name, then "M".
 * Shared by the home header, profile screen and onboarding preview.
 */
@Composable
fun ProfileAvatar(
    name: String?,
    avatarPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = "Foto de perfil",
) {
    val colors = McTheme.colors
    val bitmap = remember(avatarPath) {
        avatarPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name?.trim()?.take(1)?.ifBlank { "M" }?.uppercase(Locale.forLanguageTag("pt-BR"))
                    ?: "M",
                style = MaterialTheme.typography.titleLarge,
                color = colors.accentText,
            )
        }
    }
}
