package com.github.reygnn.nah.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinnt die Entscheidungslogik des asynchronen Einfüge-Schutzes deterministisch (der reale
 * Framework-Timing-Race ist instrumentiert nicht reproduzierbar — diese reine Einheit ersetzt das
 * durch einen deterministischen Beweis der Regel). Kernverträge:
 *  - gleiches Feld → der Paste committet,
 *  - echter Feldwechsel ODER Feld-Ende → der Paste wird verworfen (kein Commit ins fremde Feld),
 *  - reiner Restart desselben Feldes → der Paste überlebt (sonst ginge ein legitimer Paste bei
 *    Rotation/restartInput verloren).
 */
class PasteGuardTest {

    @Test
    fun `ohne Feldwechsel darf der Paste committen`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        assertTrue(guard.mayCommit(token))
    }

    @Test
    fun `ein echter Feldwechsel verwirft einen laufenden Paste`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        guard.onFieldStarted(restarting = false) // Fokus auf ein FREMDES Feld
        assertFalse(guard.mayCommit(token))
    }

    @Test
    fun `ein reiner Restart desselben Feldes laesst den Paste ueberleben`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        guard.onFieldStarted(restarting = true) // Config-Change/Rotation/restartInput — gleiches Feld
        assertTrue(guard.mayCommit(token))
    }

    @Test
    fun `auch mehrere Restarts hintereinander entwerten den Paste nicht`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        repeat(3) { guard.onFieldStarted(restarting = true) }
        assertTrue(guard.mayCommit(token))
    }

    @Test
    fun `ein Feld-Ende verwirft einen laufenden Paste`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        guard.onFieldFinished()
        assertFalse(guard.mayCommit(token))
    }

    @Test
    fun `Feld-Ende und neues Feld verwirft einen Paste aus dem alten Feld`() {
        val guard = PasteGuard()
        val token = guard.beginPaste()
        guard.onFieldFinished()
        guard.onFieldStarted(restarting = false)
        assertFalse(guard.mayCommit(token))
    }

    @Test
    fun `der Doppel-Edge-Kern - Paste aus F1 darf nach Wechsel zu F2 nicht committen, ein neuer Paste in F2 schon`() {
        val guard = PasteGuard()
        val tokenF1 = guard.beginPaste()      // Einfüge-Geste in Feld F1
        guard.onFieldStarted(restarting = false) // Fokus wechselt auf F2 (echtes neues Feld)
        assertFalse("Paste aus F1 darf NIE ins fremde F2 landen", guard.mayCommit(tokenF1))
        val tokenF2 = guard.beginPaste()      // frische Geste, jetzt in F2
        assertTrue("ein in F2 begonnener Paste committet dort korrekt", guard.mayCommit(tokenF2))
    }
}
