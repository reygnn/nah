package com.github.reygnn.nah.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Material-3-Theme für nah: dynamische Farben (Material You) aus dem Wallpaper.
 * Immer Dark — de-CH-only, kein Light-Theme. minSdk 36, also ist
 * [dynamicDarkColorScheme] ohne Versions-Guard verfügbar.
 */
@Composable
fun NahTheme(content: @Composable () -> Unit) {
    // Das Schema an den Context binden statt bei jeder Recomposition neu zu bauen: die
    // Tastatur rekomponiert pro Tastendruck, und dynamicDarkColorScheme allokiert jedes Mal
    // ein komplettes ColorScheme, das als neue Instanz durch MaterialTheme propagieren würde.
    val context = LocalContext.current
    val colorScheme = remember(context) { dynamicDarkColorScheme(context) }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
