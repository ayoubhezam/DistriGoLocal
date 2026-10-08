package com.distrigo.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

// The moves both the profile's walk (BaselineProfileGenerator) and the screen-opening traces
// (ScreenOpeningBenchmark) make through the app: as a user would, read-only, and never too early —
// a Back pressed before a page is up leaves the wrong screen.

internal const val PACKAGE = "com.distrigo.app"
internal const val TIMEOUT = 15_000L
internal const val OPEN_TIMEOUT = 6_000L
private val ANY_TEXT: Pattern = Pattern.compile(".+")

/** A bottom-bar tab: the lowest node so described, so a card with the same word is never hit. */
internal fun MacrobenchmarkScope.tab(label: String) {
    device.wait(Until.hasObject(By.desc(label)), TIMEOUT)
    device.findObjects(By.desc(label)).maxByOrNull { it.visibleBounds.centerY() }?.click()
        ?: error("no tab « $label »")
    pause()
}

/** An entry of the Plus drawer, opened from the current tab root's menu button. */
internal fun MacrobenchmarkScope.menu(entry: String) {
    (device.wait(Until.findObject(By.desc("Menu")), TIMEOUT) ?: error("no menu button")).click()
    pause()
    waitText(entry).click()
}

/** n quick flings down the list on screen, then n back up, and the list left to come to rest. */
internal fun MacrobenchmarkScope.fling(n: Int = 1) {
    val x = device.displayWidth / 2
    val low = (device.displayHeight * 0.80).toInt()
    val high = (device.displayHeight * 0.30).toInt()
    repeat(n) { device.swipe(x, low, x, high, 5); pause(800) }
    repeat(n) { device.swipe(x, high, x, low, 5); pause(800) }
    pause(1_500)
}

/**
 * Taps [row] until [opened] says its page is up. A tap on a list still gliding after a fling only
 * stops it, so the first tap may open nothing; a tap that did open something is never repeated.
 */
internal fun MacrobenchmarkScope.open(what: String, row: () -> UiObject2, opened: () -> Boolean) {
    repeat(3) {
        row().click()
        if (opened()) return
    }
    error("$what did not open")
}

/** To the next tab and back, a deliberate thumb swipe across the pager at [y]. */
internal fun MacrobenchmarkScope.swipeTabs(y: Int) {
    val right = (device.displayWidth * 0.88).toInt()
    val left = (device.displayWidth * 0.18).toInt()
    device.swipe(right, y, left, y, 25)
    pause(1_200)
    device.swipe(left, y, right, y, 25)
    pause(1_200)
}

/** Somewhere on the pager under the tab row [tabLabel], scrolled into reach first if it is low. */
internal fun MacrobenchmarkScope.pagerY(tabLabel: String): Int {
    var tabs = waitText(tabLabel).visibleBounds
    if (tabs.centerY() > device.displayHeight * 0.65) {
        val x = device.displayWidth / 2
        device.swipe(x, (device.displayHeight * 0.75).toInt(), x, (device.displayHeight * 0.40).toInt(), 40)
        pause()
        tabs = waitText(tabLabel).visibleBounds
    }
    return minOf(tabs.bottom + 300, bottomLimit())
}

/** The first text on screen below [y]: the top row of a list. */
internal fun MacrobenchmarkScope.firstRowBelow(y: Int): UiObject2 {
    pause()
    return device.findObjects(By.text(ANY_TEXT))
        .filter { it.visibleBounds.let { b: Rect -> b.centerY() > y && b.centerY() < bottomLimit() } }
        .minByOrNull { it.visibleBounds.centerY() }
        ?: error("no row below y=$y")
}

internal fun MacrobenchmarkScope.back() {
    device.pressBack()
    pause()
}

/** Closes the keyboard if it is up; a Back without it would leave the screen. */
internal fun MacrobenchmarkScope.hideKeyboard() {
    if ("mInputShown=true" in device.executeShellCommand("dumpsys input_method")) back()
}

internal fun MacrobenchmarkScope.waitText(text: String): UiObject2 =
    device.wait(Until.findObject(By.text(text)), TIMEOUT) ?: error("« $text » did not appear")

/** Waits for [text] to leave the screen: the page or sheet it belongs to has closed. */
internal fun MacrobenchmarkScope.gone(text: String) {
    check(device.wait(Until.gone(By.text(text)), TIMEOUT)) { "« $text » is still on screen" }
}

internal fun MacrobenchmarkScope.waitDesc(desc: String): UiObject2 =
    device.wait(Until.findObject(By.desc(desc)), TIMEOUT) ?: error("« $desc » did not appear")

/** Above the system navigation bar, where a tap would open Recents instead. */
internal fun MacrobenchmarkScope.bottomLimit(): Int = (device.displayHeight * 0.85).toInt()

internal fun pause(ms: Long = 700) = Thread.sleep(ms)
