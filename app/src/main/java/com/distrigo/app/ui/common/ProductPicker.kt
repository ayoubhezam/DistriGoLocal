package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.purchases.ProductListFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine

/**
 * A sale form's step 02 list — its search, its filters and its products, paged — held by the section
 * the form opens from (Tournées, Clients, Ventes) rather than by one sale.
 *
 * A sale's own session is created on every visit, and a list created with it starts empty: its first
 * frames showed a blank step 02 before the first page arrived. Held here, the list outlives the sale
 * and its pages stay cached (`cachedIn`), which the next sale's step 02 shows from its very first
 * frame. Each sale still opens on an unfiltered list: [startSale] clears the search and the filters
 * the first time it sees a sale, and leaving one through [reset] clears them for the next.
 */
class ProductPicker(
    scope      : CoroutineScope,
    repository : ProductRepository,
    toQuery    : (ProductListFilters, String) -> ProductListQuery,
) {
    var search by mutableStateOf("")
    var filters by mutableStateOf(ProductListFilters())

    val list = PagedProductList(
        scope      = scope,
        repository = repository,
        query      = combine(snapshotFlow { filters }, debouncedSearch { search }) { f, s -> toQuery(f, s) },
    )

    private var sale: String? = null

    /** Clears the search and the filters. A no-op when already clear, so the cached page stands. */
    fun reset() {
        if (search.isNotEmpty()) search = ""
        if (filters != ProductListFilters()) filters = ProductListFilters()
    }

    /** Called by each step of a sale with that sale's key: the first call of a new sale clears the list. */
    fun startSale(key: String) {
        if (key != sale) { sale = key; reset() }
    }
}

/**
 * Grey rows the size of a product row, for a list whose first page is still loading — so that
 * "loading" never looks like "no products" and the rows appear in place rather than out of nothing.
 */
fun LazyListScope.productRowSkeletons(count: Int = 6) {
    items(count) { ProductRowSkeleton() }
}

@Composable
private fun ProductRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.SurfaceSunken))
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.65f).height(14.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken))
            Spacer(Modifier.height(DsSpacing.sm))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Box(Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.SurfaceSunken))
    }
}
