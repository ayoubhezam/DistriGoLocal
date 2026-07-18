// package com.distrigo.app.data.model.incentive

package com.distrigo.app.data.model.incentive

data class DistributorPerformance(
    val netSales: Double,      // Σ ventes.total (source="camion") خلال الفترة
    val collectedCash: Double  // Σ montant_paye + مدفوعات منفصلة خلال الفترة
)

sealed interface RewardResult {
    data class CashReward(val amount: Double, val achievedPercent: Int, val sourceTierOrder: Int? = null) : RewardResult
    data class FreeGoodsReward(val productId: Int, val quantity: Int, val achievedPercent: Int) : RewardResult
    data class NotAchieved(val achievedPercent: Int) : RewardResult
    data object NoPolicyConfigured : RewardResult
}