package com.github.reygnn.nah.ui

import androidx.compose.ui.graphics.Color

/**
 * Festes Dark-Theme. de-CH-only, kein Light-Theme (Linie aus den Geschwister-
 * Projekten). Theming-Auswahl ist bewusst Fast-Follow, nicht v1.
 */
object NahColors {
    val Background = Color(0xFF121212)
    val CharKey = Color(0xFF2C2C2E)
    val FunctionKey = Color(0xFF1C1C1E)
    val OnKey = Color(0xFFFFFFFF)
    val OnKeyDim = Color(0xFFB0B0B0)

    // Shift-Status-Farben (Material-Palette): einmaliges Shift vs. Caps Lock.
    val ShiftActive = Color(0xFF4F83CC) // Stahlblau (einmaliges Shift)
    val CapsActive = Color(0xFFEC407A)  // Rosa (Caps Lock, Material Pink 400)
}
