package com.distrigo.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom bar's tab switch ([switchToTab]) on the root's routes: Stock Camion, opened over a tab, is left when a tab
 * is chosen rather than saved with the tab under it and shown again over it.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchTest {

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** The root as MainActivity builds it, the screens empty: the five tabs and Stock Camion. */
    private fun root(): NavHostController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setLifecycleOwner(Owner().apply { registry.currentState = Lifecycle.State.RESUMED })
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = Screen.TabDashboard.route) {
                TAB_ROUTES.forEach { route -> composable(route) {} }
                composable(Screen.PlusStockCamion.route) {}
            }
        }
    }

    private fun NavHostController.stack() =
        currentBackStack.value.mapNotNull { it.destination.route }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun stockCamionOpenedOverProduitsIsNotShownAgainOnProduits() = onMain {
        val nav = root()
        nav.switchToTab(Screen.TabProduits.route)
        nav.navigate(Screen.PlusStockCamion.route) // from the Plus drawer
        assertEquals(Screen.PlusStockCamion.route, nav.currentDestination?.route)

        nav.switchToTab(Screen.TabAchats.route)
        assertEquals(Screen.TabAchats.route, nav.currentDestination?.route)

        nav.switchToTab(Screen.TabProduits.route)
        assertEquals(Screen.TabProduits.route, nav.currentDestination?.route)
        assertEquals(listOf(Screen.TabDashboard.route, Screen.TabProduits.route), nav.stack())
    }

    @Test
    fun stockCamionOpenedOverTheDashboardOrATourneeIsNotShownAgainThere() = onMain {
        val nav = root()
        nav.navigate(Screen.PlusStockCamion.route) // over the Dashboard
        nav.switchToTab(Screen.TabAchats.route)
        nav.switchToTab(Screen.TabDashboard.route)
        assertEquals(listOf(Screen.TabDashboard.route), nav.stack())

        nav.switchToTab(Screen.TabTournees.route)
        nav.navigate(Screen.PlusStockCamion.route) // a tournée's « Aller au chargement »
        nav.switchToTab(Screen.TabProduits.route)
        nav.switchToTab(Screen.TabTournees.route)
        assertEquals(Screen.TabTournees.route, nav.currentDestination?.route)
    }

    @Test
    fun backFromStockCamionStillReturnsToTheTabItWasOpenedFrom() = onMain {
        val nav = root()
        nav.switchToTab(Screen.TabProduits.route)
        nav.navigate(Screen.PlusStockCamion.route)
        nav.popBackStack()
        assertEquals(Screen.TabProduits.route, nav.currentDestination?.route)
    }
}
