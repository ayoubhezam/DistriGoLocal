package com.distrigo.app.data.model.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one ReportFilter every report screen reads and changes, so going from Ventes to another report
 * keeps the period and the source. Held for the app's life, not saved: a new launch starts on the
 * default, "Ce mois", everywhere.
 */
@Singleton
class ReportFilterStore @Inject constructor() {
    private val state = MutableStateFlow(ReportFilter())
    val filter: StateFlow<ReportFilter> = state.asStateFlow()

    fun update(change: (ReportFilter) -> ReportFilter) = state.update(change)
}
