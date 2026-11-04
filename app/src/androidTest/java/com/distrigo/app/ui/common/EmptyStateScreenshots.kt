package com.distrigo.app.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.theme.DistriGoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The five empty states (illustration, title, hint) drawn in French and in Arabic, saved for a human to look at:
 * `Android/data/com.distrigo.app/files/empty-shots/<lang>-<screen>.png`. Draws [EmptyState] only.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStateScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(target.getExternalFilesDir(null), "empty-shots").apply { deleteRecursively(); mkdirs() }

    private data class Screen(val name: String, val illustration: Int, val title: Int, val hint: Int)

    private val screens = listOf(
        Screen("achats", R.drawable.empty_achats, R.string.purchases_empty, R.string.empty_hint_achats),
        Screen("produits", R.drawable.empty_produits, R.string.common_no_product_found, R.string.empty_hint_produits),
        Screen("stock-camion", R.drawable.empty_stock_camion, R.string.tournees_truck_no_product, R.string.empty_hint_stock_camion),
        Screen("depot-vente", R.drawable.empty_depot_vente, R.string.ventes_empty, R.string.empty_hint_depot_vente),
        Screen("tournees", R.drawable.empty_tournees, R.string.tournees_empty, R.string.empty_hint_tournees),
        Screen("clients", R.drawable.empty_clients, R.string.empty_clients_title, R.string.empty_hint_clients),
        Screen("fournisseurs", R.drawable.empty_fournisseurs, R.string.empty_suppliers_title, R.string.empty_hint_suppliers),
        Screen("charges", R.drawable.empty_charges, R.string.empty_charges_title, R.string.empty_hint_charges),
        Screen("pertes", R.drawable.empty_pertes, R.string.empty_pertes_title, R.string.empty_hint_pertes),
        Screen("retours-clients", R.drawable.empty_retours_clients, R.string.clients_no_return, 0),
        Screen("retours-fournisseurs", R.drawable.empty_retours_fournisseurs, R.string.clients_no_return, 0),
        Screen("inventaire", R.drawable.empty_inventaire, R.string.inventory_history_empty, 0),
        Screen("mouvements", R.drawable.empty_mouvements_stock, R.string.mouvements_none, 0),
        Screen("brouillons-achat", R.drawable.empty_brouillons_achat, R.string.common_drafts_none, R.string.purchases_drafts_empty_body),
        Screen("brouillons-chargement", R.drawable.empty_brouillons_chargement, R.string.common_drafts_none, R.string.chargements_drafts_empty_body),
        Screen("objectif", R.drawable.empty_objectif, R.string.goal_none, R.string.goal_none_body),
        Screen("corbeille", R.drawable.empty_corbeille, R.string.trash_empty_title, R.string.trash_empty_body),
        Screen("selection", R.drawable.empty_selection, R.string.common_cart_empty, 0),
    )

    private fun localized(tag: String): Context =
        target.createConfigurationContext(Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    @Test
    fun everyEmptyStateInFrenchAndArabic() {
        var current by mutableStateOf(screens.first())
        var language by mutableStateOf("fr")
        compose.setContent {
            val text = localized(language)
            CompositionLocalProvider(
                LocalContext provides text,
                LocalConfiguration provides text.resources.configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                DistriGoTheme {
                    Box(Modifier.fillMaxSize().background(DsColors.SurfaceMuted), contentAlignment = Alignment.Center) {
                        EmptyState(current.illustration, stringResource(current.title), if (current.hint != 0) stringResource(current.hint) else null)
                    }
                }
            }
        }
        for (lang in listOf("fr", "ar")) {
            for (screen in screens) {
                compose.runOnIdle {
                    language = lang
                    current = screen
                }
                compose.waitForIdle()
                val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(out, "$lang-${screen.name}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        assertEquals(2 * screens.size, out.listFiles()!!.size)
    }
}
