package com.distrigo.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen openings the UI fluidity audit still finds slow (docs/audit/ui_fluidity_and_fps_audit.md,
 * "screen openings"): a sale's page, the client picker in Dépôt Vente's filters, a client's page and the
 * Ventes report. Each is opened five times and a Perfetto trace is kept for every opening, with
 * Compose's composition named in it — what the slowest frames were spent on, before anything is changed.
 *
 * Runs against the app's `tracing` build: the release build, profileable, with
 * androidx.compose.runtime:runtime-tracing. Composition tracing slows composition while it records, so
 * the frame times here are for comparing one part of a frame with another; the frame rate itself is
 * measured on the benchmark build, without it.
 *
 * Read-only, as the profile's walk: it opens pages and goes back. Run by hand, never through Gradle's
 * connected tasks (they uninstall the app and its data):
 * ```
 * ./gradlew :app:assembleTracing :baselineprofile:assembleTracing
 * adb install -r app/build/outputs/apk/tracing/app-tracing.apk
 * adb install -r -t baselineprofile/build/outputs/apk/tracing/baselineprofile-tracing.apk
 * adb shell am instrument -w -e class com.distrigo.baselineprofile.ScreenOpeningBenchmark \
 *     -e androidx.benchmark.perfettoSdkTracing.enable true \
 *     com.distrigo.baselineprofile/androidx.test.runner.AndroidJUnitRunner
 * ```
 * The traces land in /sdcard/Android/media/com.distrigo.baselineprofile/.
 */
@RunWith(AndroidJUnit4::class)
class ScreenOpeningBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Dépôt Vente → a sale's page. */
    @Test
    fun openSale() = measure(setup = {
        if (!onDepotList()) {
            // On the last opening's sale page: Back to the list. On the launcher (the first opening), harmless.
            back()
            if (!onDepotList()) toDepotList()
        }
        pause(1_200)
    }) {
        open("a sale", {
            device.wait(Until.findObject(By.textStartsWith("Vente ")), TIMEOUT) ?: error("no sale row")
        }) { device.wait(Until.gone(By.text("Filtres")), OPEN_TIMEOUT) }
        settle()
    }

    /** Dépôt Vente → Filtres → Client: the searchable sheet of every client with a dépôt sale. */
    @Test
    fun openClientPicker() = measure(setup = {
        if (device.hasObject(By.text("Sélectionner un client"))) {
            back()
            gone("Sélectionner un client")
        }
        if (!device.hasObject(By.text("Filtres avancés"))) {
            if (!onDepotList()) toDepotList()
            waitText("Filtres").click()
            waitText("Filtres avancés")
        }
        pause(1_200)
    }) {
        waitText("Tous les clients").click()
        waitText("Sélectionner un client")
        settle()
    }

    /** Clients → a client's page. */
    @Test
    fun openClientDetail() = measure(setup = {
        if (device.hasObject(By.text("Informations"))) {
            back()
            waitText("Rechercher un client")
        }
        if (!device.hasObject(By.text("Rechercher un client"))) {
            startHome()
            menu("Clients")
            waitText("Rechercher un client")
        }
        pause(1_500)
    }) {
        val search = waitText("Rechercher un client")
        open("a client", { firstRowBelow(search.visibleBounds.centerY() + 250) }) {
            device.wait(Until.hasObject(By.text("Informations")), OPEN_TIMEOUT)
        }
        settle()
    }

    /** Rapports → Ventes. */
    @Test
    fun openVentesReport() = measure(setup = {
        if (!device.hasObject(By.text("Créances et dettes"))) {
            // On the last opening's report: Back to Rapports. On the launcher (the first opening), harmless.
            back()
            if (!device.hasObject(By.text("Créances et dettes"))) {
                startHome()
                menu("Rapports")
                waitText("Créances et dettes")
            }
        }
        pause(1_200)
    }) {
        waitText("Ventes").click()
        gone("Créances et dettes")
        settle()
    }

    // ── How each opening is measured ──

    private fun measure(setup: MacrobenchmarkScope.() -> Unit, opening: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
            packageName = PACKAGE,
            // Frame times only: what fills a frame is read from the traces, where Compose names it.
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            // The app stays up between openings, as a rep's would: each opening is a navigation, not a launch.
            startupMode = null,
            setupBlock = setup,
            measureBlock = opening,
        )

    /** On Dépôt Vente's list, with its sales on screen. */
    private fun MacrobenchmarkScope.onDepotList(): Boolean =
        device.hasObject(By.text("Filtres")) && device.hasObject(By.textStartsWith("Vente "))
            && !device.hasObject(By.text("Filtres avancés"))

    private fun MacrobenchmarkScope.toDepotList() {
        startHome()
        tab("Ventes")
        waitText("Dépôt Vente").click()
        waitText("Filtres")
        pause(1_500)
    }

    /** The app from a cold start, on the Dashboard: where each test's first opening begins. */
    private fun MacrobenchmarkScope.startHome() {
        pressHome()
        startActivityAndWait()
        waitDesc("Dashboard")
        pause(1_500)
    }

    /** The frames after the opening: the slide in, and whatever the page loads and draws once it is up. */
    private fun settle() = pause(1_500)
}
