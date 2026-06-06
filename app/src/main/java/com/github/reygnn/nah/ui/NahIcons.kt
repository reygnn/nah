package com.github.reygnn.nah.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Die Funktionstasten-Icons, inline aus Material-Symbols-Pfaddaten gebaut.
 *
 * Bewusst KEINE `material-icons-extended`-Dependency: die zieht tausende
 * ungenutzte Icons rein. Hier brauchen wir nur die paar Funktionstasten-Symbole,
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

    /** ☰ — Hamburger / Menü (Material `menu`, gefüllt). Öffnet die Einstellungen. */
    val Menu: ImageVector = materialIcon(
        "Menu",
        "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z",
    )

    /** ⎵ — Leertaste (Material `space_bar`, gefüllt): macht die sonst leere Space-Taste sichtbar
     *  und ihren Mittelpunkt erkennbar. **Dreifach breit** in einem eigenen 72×24-Viewport (statt
     *  das 24×24-Glyph zu strecken), damit der Balken breit, die Endstriche aber unverzerrt
     *  bleiben. Wird in [TapKey] passend mit 72×24 dp gerendert. */
    val Space: ImageVector = materialIcon(
        "Space",
        "M62 9v4H10V9H8v6h56V9z",
        viewportWidth = 72f,
        widthDp = 72.dp,
    )

    /** 📋 — Einfügen (Material `content_paste`, gefüllt). */
    val Paste: ImageVector = materialIcon(
        "Paste",
        "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 " +
            "1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 " +
            "1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z",
    )

    /**
     * Pfad des Shift-Pfeils — der klassische „fette" ⇧-Glyph (breite Pfeilspitze
     * über schmalerem Stamm), wie ihn Gboard rendert. Bewusst NICHT Material
     * `arrow_upward` (dünner Linien-Pfeil): der wirkt für eine Funktionstaste zu
     * filigran und matcht das Gboard-Vorbild nicht. Proportionen aus
     * Gboard-Referenzscreenshots abgenommen: Spitze y4, Flügel y11.5 (Spanne
     * x4–20), Stamm x8.5–15.5 bis y19.5. Polygon, mittig auf x=12.
     */
    private const val ARROW_UP =
        "M12 4L20 11.5L15.5 11.5L15.5 19.5L8.5 19.5L8.5 11.5L4 11.5Z"

    /** ⇧ — armiertes Shift (gefüllter Pfeil). */
    val Shift: ImageVector = materialIcon("Shift", ARROW_UP)

    /** ⇧ — Shift aus (nur Kontur): unterscheidet OFF sichtbar vom armierten [Shift]. */
    val ShiftOutline: ImageVector = materialIcon("ShiftOutline", ARROW_UP, filled = false)

    /**
     * ⇪ — Caps Lock: derselbe gefüllte Pfeil wie [Shift], plus ein Unterstrich.
     * Bewusst NICHT Material `keyboard_capslock` (anderes Chevron-Glyph) — so
     * teilen sich alle drei Zustände dasselbe Symbol, nur outline/gefüllt/+Strich.
     * Der Balken sitzt unter dem Stamm (Lücke ~2.5, y22–23.5), etwas breiter
     * als der Stamm — wie im Gboard-Caps-Screenshot. Abstand/Breite hier tunebar.
     */
    val ShiftCaps: ImageVector = materialIcon("ShiftCaps", "$ARROW_UP M8 22h8v1.5H8z")
}

/**
 * Strichstärke der Kontur-Variante (inaktiver Shift). Bewusst dünn: 2f wirkte
 * fetter als der gefüllte aktive Pfeil — der Ruhezustand soll der leichteste sein.
 * Hier tunebar, falls die Kontur zu zart/zu kräftig wirkt.
 */
private const val OUTLINE_STROKE = 1.25f

/**
 * Baut aus einem SVG-Pfad eine [ImageVector]. [filled] = gefüllte Silhouette; sonst nur die
 * zarte Kontur (Stroke) — die `Icon`-Composable tintet beides über `tint`. Höhe/Viewport-Höhe
 * sind fix 24; [viewportWidth]/[widthDp] erlauben ein breiteres Glyph (z. B. die doppelt breite
 * Leertaste) ohne Verzerrung, weil Box-Seitenverhältnis und Viewport gleich bleiben.
 */
private fun materialIcon(
    name: String,
    pathStr: String,
    filled: Boolean = true,
    viewportWidth: Float = 24f,
    widthDp: Dp = 24.dp,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = widthDp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathStr).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = if (filled) 0f else OUTLINE_STROKE,
        )
    }.build()
