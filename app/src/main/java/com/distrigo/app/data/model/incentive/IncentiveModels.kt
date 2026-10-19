// package com.distrigo.app.data.model.incentive

package com.distrigo.app.data.model.incentive

/** What a measure earns under the commission policy — see CommissionRules.rewardAt. */
sealed interface RewardResult {
    data class CashReward(val amount: Double, val achievedPercent: Int, val sourceTierOrder: Int? = null) : RewardResult
    data class FreeGoodsReward(val productId: Int, val quantity: Int, val achievedPercent: Int) : RewardResult
    data class NotAchieved(val achievedPercent: Int) : RewardResult
    data object NoPolicyConfigured : RewardResult
}