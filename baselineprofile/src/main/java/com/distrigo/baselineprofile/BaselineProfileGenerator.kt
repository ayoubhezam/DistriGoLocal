package com.distrigo.baselineprofile

import android.graphics.Rect
import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Records the Baseline Profile: what a rep's day runs, so ART compiles it ahead of time.
 *
 * The walk is the frame-rate walk of the UI fluidity audit (docs/audit/ui_fluidity_and_fps_audit.md):
 * the Dashboard, Produits and a product (its tabs, its form's tabs), Dépôt Vente (a sale, the filters
 * and the client picker), the drawer, Clients (a search, a client's tabs), a report, Achats.
 *
 * **Read-only.** It scrolls, opens, swipes between tabs and goes back. It never taps Enregistrer,
 * Supprimer or a menu, never swipes a list row sideways (that delivers a sale), and leaves every
 * filter as it found it. It runs on the phone's own data, which is the point: empty lists would
 * record empty-list code.
 *
 * Run it by hand, never through Gradle's connected tasks (they uninstall the app and its data):
 * ```
 * ./gradlew :app:assembleNonMinifiedBenchmark :baselineprofile:assembleNonMinifiedBenchmark
 * adb install -r app/build/outputs/apk/nonMinifiedBenchmark/app-nonMinifiedBenchmark.apk
 * adb install -r -t baselineprofile/build/outputs/apk/nonMinifiedBenchmark/baselineprofile-nonMinifiedBenchmark.apk
 * adb shell am instrument -w -e class com.distrigo.baselineprofile.BaselineProfileGenerator \
 *     com.distrigo.baselineprofile/androidx.test.runner.AndroidJUnitRunner
 * ```
 * then pull the `…-baseline-prof.txt` it names into app/src/main/baselineProfiles/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // Without root, a profile can only be read back from Android 13 on.
        assumeTrue("Android 13 or later is needed to record a profile", Build.VERSION.SDK_INT >= 33)
        rule.collect(
            packageName = PACKAGE,
            maxIterations = 3,
            stableIterations = 2,
        ) {
            pressHome()
            startActivityAndWait()
            waitDesc("Dashboard")
            fling()

            produits()
            depotVente()
            clients()
            rapport()

            tab("Achats")
            waitText("Filtres")
            fling()
            tab("Dashboard")
        }
    }

    // ── The walk ──

    private fun MacrobenchmarkScope.produits() {
        tab("Produits")
        val search = waitText("Rechercher un produit")
        pause()
        fling(2)
        // Under the count, sort and filter controls, as in the frame-rate walk.
        open("a product", { firstRowBelow(search.visibleBounds.centerY() + 250) }) {
            device.wait(Until.hasObject(By.text("Informations produit")), OPEN_TIMEOUT)
        }
        pause()
        swipeTabs(pagerY("Informations produit"))
        device.findObject(By.desc("Modifier"))?.click() ?: error("no « Modifier » on the product")
        val formTabs = waitText("Essentiel")
        pause()
        swipeTabs(minOf(formTabs.visibleBounds.bottom + 400, bottomLimit()))
        back()
        waitText("Informations produit")
        back()
        waitText("Rechercher un produit")
    }

    private fun MacrobenchmarkScope.depotVente() {
        tab("Ventes")
        waitText("Dépôt Vente").click()
        waitText("Filtres")
        pause()
        fling(2)
        // A sale, and back.
        repeat(2) {
            // Back only once the sale's page has replaced the list: before the profile is recorded
            // the app runs uncompiled, and a Back pressed too early would leave Dépôt Vente instead.
            open("a sale", {
                device.wait(Until.findObject(By.textStartsWith("Vente ")), TIMEOUT) ?: error("no sale row")
            }) { device.wait(Until.gone(By.text("Filtres")), OPEN_TIMEOUT) }
            pause(1_000)
            back()
            waitText("Filtres")
        }
        // Filtres → Client: the picker, opened and closed without a choice.
        waitText("Filtres").click()
        waitText("Tous les clients").click()
        waitText("Sélectionner un client")
        pause()
        back()
        gone("Sélectionner un client")
        pause()
        back()
        gone("Filtres avancés")
        waitText("Filtres")
        back()
        waitText("Dépôt Vente")
    }

    private fun MacrobenchmarkScope.clients() {
        menu("Clients")
        val search = waitText("Rechercher un client")
        pause()
        fling(1)
        // A search typed and erased, a key at a time.
        search.click()
        pause()
        "12".forEach { device.executeShellCommand("input text $it"); pause(300) }
        pause()
        repeat(2) { device.executeShellCommand("input keyevent KEYCODE_DEL"); pause(300) }
        hideKeyboard()
        pause()
        val list = waitText("Rechercher un client")
        open("a client", { firstRowBelow(list.visibleBounds.centerY() + 250) }) {
            device.wait(Until.hasObject(By.text("Informations")), OPEN_TIMEOUT)
        }
        pause()
        swipeTabs(pagerY("Informations"))
        back()
        waitText("Rechercher un client")
        back()
        waitDesc("Menu")
    }

    private fun MacrobenchmarkScope.rapport() {
        menu("Rapports")
        waitText("Créances et dettes")
        waitText("Ventes").click()
        gone("Créances et dettes")
        pause(2_500)
        fling(1)
        back()
        waitText("Créances et dettes")
        back()
        waitDesc("Menu")
    }

    // ── Moves ──

    /** A bottom-bar tab: the lowest node so described, so a card with the same word is never hit. */
    private fun MacrobenchmarkScope.tab(label: String) {
        device.wait(Until.hasObject(By.desc(label)), TIMEOUT)
        device.findObjects(By.desc(label)).maxByOrNull { it.visibleBounds.centerY() }?.click()
            ?: error("no tab « $label »")
        pause()
    }

    /** An entry of the Plus drawer, opened from the current tab root's menu button. */
    private fun MacrobenchmarkScope.menu(entry: String) {
        (device.wait(Until.findObject(By.desc("Menu")), TIMEOUT) ?: error("no menu button")).click()
        pause()
        waitText(entry).click()
    }

    /** n quick flings down the list on screen, then n back up, and the list left to come to rest. */
    private fun MacrobenchmarkScope.fling(n: Int = 1) {
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
    private fun MacrobenchmarkScope.open(what: String, row: () -> UiObject2, opened: () -> Boolean) {
        repeat(3) {
            row().click()
            if (opened()) return
        }
        error("$what did not open")
    }

    /** To the next tab and back, a deliberate thumb swipe across the pager at [y]. */
    private fun MacrobenchmarkScope.swipeTabs(y: Int) {
        val right = (device.displayWidth * 0.88).toInt()
        val left = (device.displayWidth * 0.18).toInt()
        device.swipe(right, y, left, y, 25)
        pause(1_200)
        device.swipe(left, y, right, y, 25)
        pause(1_200)
    }

    /** Somewhere on the pager under the tab row [tabLabel], scrolled into reach first if it is low. */
    private fun MacrobenchmarkScope.pagerY(tabLabel: String): Int {
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
    private fun MacrobenchmarkScope.firstRowBelow(y: Int): UiObject2 {
        pause()
        return device.findObjects(By.text(ANY_TEXT))
            .filter { it.visibleBounds.let { b: Rect -> b.centerY() > y && b.centerY() < bottomLimit() } }
            .minByOrNull { it.visibleBounds.centerY() }
            ?: error("no row below y=$y")
    }

    private fun MacrobenchmarkScope.back() {
        device.pressBack()
        pause()
    }

    /** Closes the keyboard if it is up; a Back without it would leave the screen. */
    private fun MacrobenchmarkScope.hideKeyboard() {
        if ("mInputShown=true" in device.executeShellCommand("dumpsys input_method")) back()
    }

    private fun MacrobenchmarkScope.waitText(text: String): UiObject2 =
        device.wait(Until.findObject(By.text(text)), TIMEOUT) ?: error("« $text » did not appear")

    /** Waits for [text] to leave the screen: the page or sheet it belongs to has closed. */
    private fun MacrobenchmarkScope.gone(text: String) {
        check(device.wait(Until.gone(By.text(text)), TIMEOUT)) { "« $text » is still on screen" }
    }

    private fun MacrobenchmarkScope.waitDesc(desc: String): UiObject2 =
        device.wait(Until.findObject(By.desc(desc)), TIMEOUT) ?: error("« $desc » did not appear")

    /** Above the system navigation bar, where a tap would open Recents instead. */
    private fun MacrobenchmarkScope.bottomLimit(): Int = (device.displayHeight * 0.85).toInt()

    private fun pause(ms: Long = 700) = Thread.sleep(ms)

    private companion object {
        const val PACKAGE = "com.distrigo.app"
        const val TIMEOUT = 15_000L
        const val OPEN_TIMEOUT = 6_000L
        val ANY_TEXT: Pattern = Pattern.compile(".+")
    }
}
