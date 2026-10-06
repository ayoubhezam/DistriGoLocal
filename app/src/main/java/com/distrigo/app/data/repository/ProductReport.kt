package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange

/**
 * One product's sales over a report's period: its quantity in its own unit — quantities are never
 * added across products, a carton and a kilo do not sum — what it sold for, and what it had cost.
 */
data class ProductSales(
    val productId: Int,
    val name: String,
    val unit: String,
    val imageUri: String?,
    val category: String?,
    val brand: String?,
    val supplier: String?,
    val quantity: Double,
    val total: Double,
    val cost: Double,
) {
    val margin: Double get() = total - cost
    /** On the purchase price, as the Direction du Commerce counts it; null without a cost. */
    val marginRate: Double? get() = if (cost > 0) margin / cost else null
}

/** A product in stock that sold nothing over the period, and what that stock cost. */
data class DormantProduct(val productId: Int, val name: String, val unit: String, val imageUri: String?, val stock: Double, val value: Double)

/** How a report's products are ranked. */
enum class ProductRanking(val label: String) { CA("CA"), MARGE("Marge"), QUANTITE("Quantité") }

/** What sales are grouped by. */
enum class ProductGrouping(val label: String) { CATEGORIE("Catégorie"), MARQUE("Marque"), FOURNISSEUR("Fournisseur") }

/** One group's share of the sales: a category, a brand or a supplier — or none, for products without one. */
data class GroupShare(val name: String, val total: Double, val products: Int)

/**
 * ABC: the products ranked by what they sold, A the ones that make the first 80 % of the sales, B the
 * next 15 %, C the last 5 %. A product straddling a boundary belongs to the class it starts in.
 */
data class AbcClass(val label: String, val products: Int, val total: Double)

/** The Produits report for one filter. */
data class ProductReport(
    val range: ReportRange,
    val lines: List<ProductSales>,
    val dormant: List<DormantProduct>,
) {
    val total: Double get() = lines.sumOf { it.total }
    val cost: Double get() = lines.sumOf { it.cost }
    val margin: Double get() = total - cost
    val marginRate: Double? get() = if (cost > 0) margin / cost else null
    val dormantValue: Double get() = dormant.sumOf { it.value }

    fun ranked(by: ProductRanking): List<ProductSales> = when (by) {
        ProductRanking.CA -> lines.sortedByDescending { it.total }
        ProductRanking.MARGE -> lines.sortedByDescending { it.margin }
        ProductRanking.QUANTITE -> lines.sortedByDescending { it.quantity }
    }

    /** The sales by [by], biggest first; products without a value fall under "Sans …". */
    fun groups(by: ProductGrouping): List<GroupShare> {
        val (key, none) = when (by) {
            ProductGrouping.CATEGORIE -> ({ p: ProductSales -> p.category }) to "Sans catégorie"
            ProductGrouping.MARQUE -> ({ p: ProductSales -> p.brand }) to "Sans marque"
            ProductGrouping.FOURNISSEUR -> ({ p: ProductSales -> p.supplier }) to "Sans fournisseur"
        }
        return lines.groupBy { key(it)?.trim()?.takeIf(String::isNotEmpty) ?: none }
            .map { (name, ofGroup) -> GroupShare(name, ofGroup.sumOf { it.total }, ofGroup.size) }
            .sortedByDescending { it.total }
    }

    val abc: List<AbcClass> get() {
        val counts = IntArray(3); val totals = DoubleArray(3)
        val all = total
        var before = 0.0
        for (p in lines.sortedByDescending { it.total }) {
            val share = if (all > 0) before / all else 1.0
            val k = when { share < 0.80 -> 0; share < 0.95 -> 1; else -> 2 }
            counts[k]++; totals[k] += p.total; before += p.total
        }
        return listOf("A", "B", "C").mapIndexed { i, l -> AbcClass(l, counts[i], totals[i]) }
    }
}
