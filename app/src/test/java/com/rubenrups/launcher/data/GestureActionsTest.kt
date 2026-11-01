package com.rubenrups.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionsTest {
    private val nothing = LauncherSettings(
        holdAction = GestureActions.NONE,
        doubleTapAction = GestureActions.NONE,
        tripleTapAction = GestureActions.NONE,
        swipeUp = GestureActions.NONE,
        swipeDown = GestureActions.NONE,
        swipeLeftApp = GestureActions.NONE,
        swipeRightApp = GestureActions.NONE,
    )

    @Test
    fun defaultsKeepSettingsReachable() {
        assertTrue(GestureActions.reachesSettings(LauncherSettings()))
        assertEquals(GestureActions.MENU, LauncherSettings().holdAction)
    }

    @Test
    fun clearingEveryGestureLocksYouOut() {
        assertFalse(GestureActions.reachesSettings(nothing))
    }

    @Test
    fun anyOneGestureOnMenuOrSettingsIsEnough() {
        assertTrue(GestureActions.reachesSettings(nothing.copy(holdAction = GestureActions.MENU)))
        assertTrue(GestureActions.reachesSettings(nothing.copy(doubleTapAction = GestureActions.SETTINGS)))
        assertTrue(GestureActions.reachesSettings(nothing.copy(swipeLeftApp = GestureActions.MENU)))
        assertFalse(GestureActions.reachesSettings(nothing.copy(holdAction = GestureActions.DRAWER)))
    }

    @Test
    fun keysAreUniqueInEveryList() {
        assertEquals(GestureActions.builtIn.size, GestureActions.builtIn.map { it.first }.toSet().size)
        assertEquals(GestureActions.carBuiltIn.size, GestureActions.carBuiltIn.map { it.first }.toSet().size)
    }

    @Test
    fun accessibilityActionsAreOffered() {
        val offered = GestureActions.builtIn.map { it.first }.toSet()
        assertTrue(offered.containsAll(GestureActions.needsAccessibility))
    }

    @Test
    fun labelsResolveAndUnknownKeysDoNot() {
        assertEquals("Lock phone", GestureActions.builtInLabel(GestureActions.LOCK))
        assertNull(GestureActions.builtInLabel("com.example.app/Main"))
    }

    @Test
    fun legacySwipeDownNamesAreUpgraded() {
        assertEquals(GestureActions.NOTIFICATIONS, GestureActions.fromLegacy("NOTIFICATIONS"))
        assertEquals(GestureActions.NONE, GestureActions.fromLegacy("NONE"))
        assertEquals("some.app/key", GestureActions.fromLegacy("some.app/key"))
    }
}
