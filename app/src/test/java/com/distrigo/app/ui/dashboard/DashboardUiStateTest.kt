package com.distrigo.app.ui.dashboard

import com.distrigo.app.data.repository.ActivityFilter
import com.distrigo.app.data.repository.DashboardAlerts
import com.distrigo.app.data.repository.DashboardSales
import com.distrigo.app.data.repository.SalesWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** The Dashboard shown all at once (owner, 2026-10-22): the skeleton until every block is read, then never again. */
class DashboardUiStateTest {

    private val window = SalesWindow(count = 3, total = 15_000.0, paid = 10_000.0, cost = 9_000.0, clients = 2)
    private val sales = DashboardSales(ZonedDateTime.parse("2026-10-22T09:00:00+01:00[Africa/Algiers]"), window, window)
    private val alerts = DashboardAlerts(outOfStock = 1, lowStock = 2, oldClientDebt = 0.0)

    private fun state(
        name: Block<String?> = Block.Ready("Ayoub"),
        sales: Block<DashboardSales> = Block.Ready(this.sales),
        alerts: Block<DashboardAlerts> = Block.Ready(this.alerts),
        activity: Block<List<com.distrigo.app.data.repository.RecentSale>> = Block.Ready(emptyList()),
    ) = DashboardUiState.of(name, sales, Block.Ready(null), alerts, Block.Ready(null), activity, ActivityFilter.ALL)

    @Test
    fun anyBlockStillLoadingKeepsTheSkeleton() {
        assertEquals(DashboardUiState.Loading, state(name = Block.Loading))
        assertEquals(DashboardUiState.Loading, state(sales = Block.Loading))
        assertEquals(DashboardUiState.Loading, state(alerts = Block.Loading))
        assertEquals(DashboardUiState.Loading, state(activity = Block.Loading))
    }

    @Test
    fun everyBlockReadShowsTheDashboardWithAllOfIt() {
        val ready = state() as DashboardUiState.Ready
        assertEquals("Ayoub", ready.content.firstName)
        assertEquals(15_000.0, ready.content.sales!!.today.total, 0.0)
        assertEquals(alerts, ready.content.alerts)
        assertNull(ready.content.goal)
        assertNull(ready.content.tournee)
    }

    /** A block whose first read failed counts as read: the skeleton never waits on it forever. */
    @Test
    fun aFailedBlockDoesNotHoldTheSkeleton() {
        val ready = state(alerts = Block.Failed, activity = Block.Failed) as DashboardUiState.Ready
        assertNull(ready.content.alerts)
        assertTrue(ready.content.activity.isEmpty())
    }

    /** An account without a first name is read too: « Bonjour » alone, not a skeleton. */
    @Test
    fun noFirstNameIsANameRead() {
        assertNull((state(name = Block.Ready(null)) as DashboardUiState.Ready).content.firstName)
    }
}
