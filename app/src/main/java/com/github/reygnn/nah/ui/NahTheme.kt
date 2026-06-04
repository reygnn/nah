package com.github.reygnn.nah.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material-3-Theme für nah: dynamische Farben (Material You) aus dem Wallpaper.
 * Immer Dark — de-CH-only, kein Light-Theme. minSdk 36, also ist
 * [dynamicDarkColorScheme] ohne Versions-Guard verfügbar.
 */
@Composable
fun NahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicDarkColorScheme(LocalContext.current),
        content = content,
    )
}
