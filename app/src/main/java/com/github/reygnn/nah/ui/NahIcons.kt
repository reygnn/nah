package com.github.reygnn.nah.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Die drei Funktionstasten-Icons, inline aus Material-Symbols-Pfaddaten gebaut.
 *
 * Bewusst KEINE `material-icons-extended`-Dependency: die zieht tausende
 * ungenutzte Icons rein. Hier brauchen wir genau Backspace, Shift und Return,
 * also parsen wir die SVG-Pfade direkt in [ImageVector]s — `compose-ui-graphics`
 * ([PathParser]) ist ohnehin schon auf dem Classpath.
 *
 * Die Füllfarbe ist irrelevant ([Color.Black]): die `Icon`-Composable tintet den
 * Vektor über einen Color-Filter mit der übergebenen `tint`-Farbe (siehe `fg` in
 * [TapKey]).
 */
object NahIcons {
    /** ⌫ — Backspace (Material `backspace`, gefüllt). */
    val Backspace: ImageVector = materialIcon(
        "Backspace",
        "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 " +
            "2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 " +
            "12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z",
    )

    /** ⏎ — Return / Enter (Material `keyboard_return`, gefüllt). */
    val Return: ImageVector = materialIcon(
        "Return",
        "M19 7v4H5.83l3.58-3.59L8 6l-6 6 6 6 1.41-1.41L5.83 13H21V7h-2z",
    )

    /** Pfad des Aufwärts-Pfeils (Material `arrow_upward`) — gefüllt wie outline. */
    private const val ARROW_UP = "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z"

    /** ⇧ — armiertes Shift (gefüllter Pfeil). */
    val Shift: ImageVector = materialIcon("Shift", ARROW_UP)

    /** ⇧ — Shift aus (nur Kontur): unterscheidet OFF sichtbar vom armierten [Shift]. */
    val ShiftOutline: ImageVector = materialIcon("ShiftOutline", ARROW_UP, filled = false)

    /**
     * ⇪ — Caps Lock: derselbe gefüllte Pfeil wie [Shift], plus ein Unterstrich.
     * Bewusst NICHT Material `keyboard_capslock` (anderes Chevron-Glyph) — so
     * teilen sich alle drei Zustände dasselbe Symbol, nur outline/gefüllt/+Strich.
     * Der Balken sitzt unter dem Pfeil (y22–24); Abstand/Breite hier tunebar.
     */
    val ShiftCaps: ImageVector = materialIcon("ShiftCaps", "$ARROW_UP M6 22h12v2H6z")
}

/**
 * Strichstärke der Kontur-Variante (inaktiver Shift). Bewusst dünn: 2f wirkte
 * fetter als der gefüllte aktive Pfeil — der Ruhezustand soll der leichteste sein.
 * Hier tunebar, falls die Kontur zu zart/zu kräftig wirkt.
 */
private const val OUTLINE_STROKE = 1.25f

/**
 * Baut aus einem 24×24-SVG-Pfad eine [ImageVector]. [filled] = gefüllte Silhouette;
 * sonst nur die zarte Kontur (Stroke) — die `Icon`-Composable tintet beides über `tint`.
 */
private fun materialIcon(name: String, pathStr: String, filled: Boolean = true): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathStr).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = if (filled) 0f else OUTLINE_STROKE,
        )
    }.build()
