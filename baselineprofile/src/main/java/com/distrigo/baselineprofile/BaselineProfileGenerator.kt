package com.distrigo.baselineprofile

import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
