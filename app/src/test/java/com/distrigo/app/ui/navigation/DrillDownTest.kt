package com.distrigo.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which root entry a drill-down goes back to, if any ([reopenIndex]). */
class DrillDownTest {

    private val clients = OpenEntry("tab_clients", drillId = null, shown = null)
    private fun clientsOn(id: Int) = OpenEntry("tab_clients", drillId = null, shown = DrillTarget.Client(id).key)
    private fun drill(screen: Screen.Drill, id: Int) = OpenEntry(screen.route, drillId = id, shown = null)

    @Test
    fun aRecordNowhereOnTheStackIsPushed() {
        val stack = listOf(clientsOn(10), drill(Screen.DrillQuartier, 5))
        assertNull(reopenIndex(stack, DrillTarget.Client(11), Screen.DrillClient.route))
        assertNull(reopenIndex(stack, DrillTarget.Secteur(1), Screen.DrillSecteur.route))
    }

    /** Clients list → client 10 → its quartier → client 10: back to the first page, not a copy. */
    @Test
    fun aClientOpenedFromItsSectionIsGoneBackTo() {
        val stack = listOf(clientsOn(10), drill(Screen.DrillQuartier, 5))
        assertEquals(0, reopenIndex(stack, DrillTarget.Client(10), Screen.DrillClient.route))
    }

    @Test
    fun aRecordOpenedByItsDrillRouteIsGoneBackTo() {
        val stack = listOf(clients, drill(Screen.DrillQuartier, 5), drill(Screen.DrillClient, 10))
        assertEquals(1, reopenIndex(stack, DrillTarget.Quartier(5), Screen.DrillQuartier.route))
        // Its page noted it too: still it
        val noted = listOf(clients, OpenEntry(Screen.DrillQuartier.route, 5, DrillTarget.Quartier(5).key), drill(Screen.DrillClient, 10))
        assertEquals(1, reopenIndex(noted, DrillTarget.Quartier(5), Screen.DrillQuartier.route))
    }

    /** A quartier reached from its secteur's page, inside Paramètres, has no drill route: its note finds it. */
    @Test
    fun aQuartierShownInsideItsSecteursSectionIsGoneBackTo() {
        val secteurs = OpenEntry(Screen.DrillSecteur.route, drillId = 1, shown = DrillTarget.Quartier(5).key)
        val stack = listOf(clientsOn(10), secteurs, drill(Screen.DrillClient, 10))
        assertEquals(1, reopenIndex(stack, DrillTarget.Quartier(5), Screen.DrillQuartier.route))
        // Its section shows the quartier now, not the secteur it was opened on: the secteur is opened again
        assertNull(reopenIndex(stack, DrillTarget.Secteur(1), Screen.DrillSecteur.route))
    }

    @Test
    fun theNearestOfTwoIsGoneBackTo() {
        val stack = listOf(clientsOn(10), drill(Screen.DrillQuartier, 5), drill(Screen.DrillClient, 10), drill(Screen.DrillSecteur, 1))
        assertEquals(2, reopenIndex(stack, DrillTarget.Client(10), Screen.DrillClient.route))
    }

    /** Ids are per kind: quartier 5 is not client 5, by route or by note. */
    @Test
    fun anotherKindWithTheSameIdIsNotIt() {
        val stack = listOf(clientsOn(5), drill(Screen.DrillQuartier, 5))
        assertNull(reopenIndex(stack, DrillTarget.Secteur(5), Screen.DrillSecteur.route))
        assertEquals(1, reopenIndex(stack, DrillTarget.Quartier(5), Screen.DrillQuartier.route))
        assertEquals(0, reopenIndex(stack, DrillTarget.Client(5), Screen.DrillClient.route))
    }
}
