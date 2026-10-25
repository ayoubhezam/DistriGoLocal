package com.distrigo.app.ui.goal

import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.PeriodType
import com.distrigo.app.data.model.incentive.CommissionPeriod
import com.distrigo.app.data.model.incentive.CommissionRules
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.PaceStatus
import com.distrigo.app.data.model.incentive.RewardResult
import com.distrigo.app.data.model.incentive.TierRule
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The words of « Mon objectif » (docs/design/dashboard_objectif_plan.md, Part 2 Steps 3–6, D20): the rule in plain
 * French, the next step in one sentence, the bonus — in Paramètres' own words, « Palier », « Seuil », « Taux ». With
 * the policy's « Afficher la prime » off, no amount or rate of a bonus is ever said: thresholds only.
 */
object GoalWording {

    private val FR = Locale.FRENCH

    /** « octobre », « 4e trimestre », « 2026 ». */
    fun periodName(period: CommissionPeriod): String = when (period.type) {
        PeriodType.MONTHLY -> period.start.month.getDisplayName(TextStyle.FULL, FR)
        PeriodType.QUARTERLY -> "${(period.start.monthValue - 1) / 3 + 1}e trimestre"
        PeriodType.YEARLY -> period.start.year.toString()
    }

    /** A past period with its year: « Septembre 2026 », « 3e trimestre 2026 », « 2025 ». */
    fun pastPeriodName(period: CommissionPeriod): String = when (period.type) {
        PeriodType.YEARLY -> period.start.year.toString()
        else -> periodName(period).replaceFirstChar { it.uppercase() } + " " + period.start.year
    }

    /** « ce mois-ci », « ce trimestre », « cette année ». */
    fun thisPeriod(type: PeriodType): String = when (type) {
        PeriodType.MONTHLY -> "ce mois-ci"
        PeriodType.QUARTERLY -> "ce trimestre"
        PeriodType.YEARLY -> "cette année"
    }

    /** « fin du mois », « fin du trimestre », « fin de l'année ». */
    fun periodEnd(type: PeriodType): String = when (type) {
        PeriodType.MONTHLY -> "fin du mois"
        PeriodType.QUARTERLY -> "fin du trimestre"
        PeriodType.YEARLY -> "fin de l'année"
    }

    /** « Ventes facturées » or « Recouvrement », as Paramètres names the measure. */
    fun measure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> "Ventes facturées"
        CalculationSource.COLLECTED_CASH -> "Recouvrement"
    }

    /** « de vos ventes facturées », « de votre recouvrement ». */
    private fun ofMeasure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> "de vos ventes facturées"
        CalculationSource.COLLECTED_CASH -> "de votre recouvrement"
    }

    /** 3 → « 3 % », 2.5 → « 2,5 % ». */
    fun rate(value: Double): String =
        if (value == floor(value)) "${value.toLong()} %" else String.format(FR, "%.1f %%", value).replace('.', ',')

    /** What a tier pays: « 2,5 % de vos ventes facturées » or « une prime de 20 000 DA ». */
    private fun pays(tier: TierRule, source: CalculationSource, money: MoneyFormatter): String =
        tier.rate?.let { "${rate(it)} ${ofMeasure(source)}" } ?: "une prime de ${money.da(tier.fixedBonus ?: 0.0)}"

    /** « de ventes facturées », « de recouvrement »: what a threshold is counted in. */
    private fun inMeasure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> "de ventes facturées"
        CalculationSource.COLLECTED_CASH -> "de recouvrement"
    }

    /**
     * The policy in one plain sentence: « Vous touchez 2 % de vos ventes facturées dès 600 000 DA ce mois-ci, 2,5 %
     * dès 900 000 DA… ». Without the bonus shown, only what is to reach. [freeGood] names the product of a « Gratuité ».
     */
    fun rule(rules: CommissionRules, type: PeriodType, money: MoneyFormatter, showReward: Boolean, freeGood: String? = null): String {
        val period = thisPeriod(type)
        val tiers = rules.sortedTiers
        if (tiers.isNotEmpty()) {
            if (!showReward) {
                val thresholds = tiers.map { money.da(it.threshold) }
                val listed = if (thresholds.size == 1) thresholds.single() else thresholds.dropLast(1).joinToString(", ") + " et " + thresholds.last()
                return "${count(tiers.size)} ${inMeasure(rules.source)} $period : $listed."
            }
            val first = tiers.first()
            val rest = tiers.drop(1).joinToString("") { tier ->
                val what = tier.rate?.let { rate(it) } ?: "une prime de ${money.da(tier.fixedBonus ?: 0.0)}"
                ", $what dès ${money.da(tier.threshold)}"
            }
            return "Vous touchez ${pays(first, rules.source, money)} dès ${money.da(first.threshold)} $period$rest."
        }
        val objective = "${money.da(rules.objective)} ${inMeasure(rules.source)} $period"
        if (!showReward) return "Votre objectif : $objective."
        return when (rules.type) {
            IncentiveType.FIXED_RATE -> "Vous touchez ${rate(rules.rate ?: 0.0)} ${ofMeasure(rules.source)} dès ${money.da(rules.objective)} $period."
            IncentiveType.FIXED_BONUS -> "Vous touchez une prime de ${money.da(rules.fixedBonus ?: 0.0)} dès $objective."
            IncentiveType.FREE_GOODS -> "Vous recevez ${rules.freeGoodQuantity ?: 0} ${freeGood?.let { "« $it »" } ?: "unités"} offerts dès $objective."
            IncentiveType.PROGRESSIVE_TIERS -> "Votre objectif : $objective."
        }
    }

    private fun count(n: Int): String = when (n) {
        1 -> "Un palier"
        2 -> "Deux paliers"
        3 -> "Trois paliers"
        4 -> "Quatre paliers"
        5 -> "Cinq paliers"
        else -> "$n paliers"
    }

    private fun RewardResult.cash(): Double = (this as? RewardResult.CashReward)?.amount ?: 0.0

    private fun days(n: Int) = if (n <= 1) "$n jour" else "$n jours"

    /** The one sentence about the next step (Step 4): always the nearest milestone, not the final goal. */
    fun nextSentence(goal: GoalProgress, money: MoneyFormatter): String {
        val tiers = goal.rules.sortedTiers
        val next = goal.next ?: return if (tiers.isNotEmpty()) "Palier max atteint" else "Objectif atteint"
        if (tiers.isNotEmpty()) {
            val position = tiers.indexOfFirst { it.threshold == next.threshold } + 1
            return if (goal.showReward) {
                "Plus que ${money.da(next.gap)} pour le palier $position : votre prime passe de " +
                    "${money.da(goal.reward.cash())} à ${money.da(next.reward.cash())}"
            } else {
                "Plus que ${money.da(next.gap)} pour le palier $position"
            }
        }
        val pace = goal.pace ?: return "Plus que ${money.da(next.gap)}"
        return "Plus que ${money.da(next.gap)} en ${days(pace.daysLeft)} · environ ${money.da(pace.neededPerDay)} par jour"
    }

    /** The gap per day as a number of sales, with the average sale (Step 4); only for « Ventes facturées ». */
    fun salesPerDay(goal: GoalProgress): Int? {
        if (goal.rules.source != CalculationSource.INVOICED_SALES || goal.next == null) return null
        val needed = goal.pace?.neededPerDay ?: return null
        val basket = goal.averageSale?.takeIf { it > 0 } ?: return null
        return ceil(needed / basket).toInt().takeIf { it > 0 }
    }

    /**
     * The bonus now, in Paramètres' words (Step 3), and whether it is earned: « Prime : 39 000 DA », « Prime à débloquer :
     * 3 % de vos ventes »… Null when the policy hides it.
     */
    fun reward(goal: GoalProgress, money: MoneyFormatter, freeGood: String? = null): Pair<String, Boolean>? {
        if (!goal.showReward) return null
        val r = goal.rules
        val goods = "${r.freeGoodQuantity ?: 0} ${freeGood?.let { "« $it »" } ?: "unités"}"
        return when (r.type) {
            IncentiveType.FIXED_RATE ->
                if (goal.reached) "Prime : ${money.da(goal.reward.cash())}" to true
                else "Prime à débloquer : ${rate(r.rate ?: 0.0)} ${ofMeasure(r.source)}" to false
            IncentiveType.FIXED_BONUS ->
                if (goal.reached) "Prime forfaitaire : ${money.da(r.fixedBonus ?: 0.0)}" to true
                else "Prime forfaitaire de ${money.da(r.fixedBonus ?: 0.0)} à débloquer" to false
            IncentiveType.FREE_GOODS ->
                if (goal.reached) "Gratuité : $goods offerts" to true
                else "Gratuité à débloquer : $goods offerts" to false
            IncentiveType.PROGRESSIVE_TIERS -> {
                val current = r.tierAt(goal.value)
                if (current == null) "Prime à débloquer dès le palier 1 (${money.da(r.sortedTiers.firstOrNull()?.threshold ?: 0.0)})" to false
                else {
                    val position = r.sortedTiers.indexOf(current) + 1
                    "Palier $position · prime : ${money.da(goal.reward.cash())}" to true
                }
            }
        }
    }

    // ── The navy card and its companions (D21) ──

    /** `888 000 DA`: rounded to the dinar, in the user's separators, for the big figures. */
    fun whole(value: Double, money: MoneyFormatter): String =
        money.da(Math.round(value).toDouble()).replace("${money.format.decimal}00", "")

    /** A bar's label: `238K`, `1,2M`. */
    fun compact(value: Double): String = when {
        value >= 1_000_000 -> String.format(FR, "%.1fM", value / 1_000_000).replace('.', ',').replace(",0M", "M")
        value >= 1_000 -> "${Math.round(value / 1_000)}K"
        else -> "${Math.round(value)}"
    }

    /** « le palier 2 » or « votre objectif »: what the measure aims at now. */
    fun aim(goal: GoalProgress): String = goal.target.tier?.let { "le palier $it" } ?: "votre objectif"

    /** « du palier 2 » or « de l'objectif ». */
    fun ofAim(goal: GoalProgress): String = goal.target.tier?.let { "du palier $it" } ?: "de l'objectif"

    /** « Réalisé en octobre », « Réalisé ce trimestre », « Réalisé en 2026 ». */
    fun achievedIn(period: CommissionPeriod): String = when (period.type) {
        PeriodType.MONTHLY -> "Réalisé en ${periodName(period)}"
        PeriodType.QUARTERLY -> "Réalisé ce trimestre"
        PeriodType.YEARLY -> "Réalisé en ${period.start.year}"
    }

    /**
     * The navy card's line, from the pace — never from the percent alone, which said « dans le bon rythme » at 70 %
     * with two days left.
     */
    fun encouragement(goal: GoalProgress): String {
        if (goal.next == null) {
            return if (goal.rules.sortedTiers.isNotEmpty()) "Palier max atteint. Continuez sur cette lancée."
            else "Objectif atteint. Continuez sur cette lancée."
        }
        return when (goal.pace?.status) {
            PaceStatus.AHEAD -> "Vous êtes en avance : à ce rythme, vous dépassez ${aim(goal)}."
            PaceStatus.ON_TRACK -> "Vous êtes dans le bon rythme pour atteindre ${aim(goal)}."
            PaceStatus.BEHIND -> "Il faut accélérer : un effort de plus chaque jour vous rapproche ${ofAim(goal)}."
            else -> "Un effort régulier chaque jour vous rapproche ${ofAim(goal)}."
        }
    }

    /** The policy in a few words, under « Commission estimée »: « taux fixe 3 % », « paliers »… */
    fun policyLine(rules: CommissionRules): String = when (rules.type) {
        IncentiveType.FIXED_RATE -> "taux fixe ${rate(rules.rate ?: 0.0)}"
        IncentiveType.FIXED_BONUS -> "prime forfaitaire"
        IncentiveType.FREE_GOODS -> "gratuité"
        IncentiveType.PROGRESSIVE_TIERS -> "paliers"
    }

    /** What a [RewardResult] gives: `17 760 DA`, `6 « Café » offerts`, `0 DA` while not earned. */
    fun rewardValue(reward: RewardResult, money: MoneyFormatter, freeGood: String? = null): String = when (reward) {
        is RewardResult.CashReward -> whole(reward.amount, money)
        is RewardResult.FreeGoodsReward -> "${reward.quantity} ${freeGood?.let { "« $it »" } ?: "unités"} offerts"
        else -> whole(0.0, money)
    }

    /**
     * The line under « Commission estimée »: « Taux actuel · 3 % » once a rate pays, else what will pay and from
     * when — « 3 % dès l'objectif », « Dès le palier 1 » — or the policy's kind for a bonus or free goods.
     */
    fun commissionNote(goal: GoalProgress): String {
        val r = goal.rules
        return when (r.type) {
            IncentiveType.FIXED_RATE ->
                if (goal.reached) "Taux actuel · ${rate(r.rate ?: 0.0)}" else "${rate(r.rate ?: 0.0)} dès l'objectif"
            IncentiveType.PROGRESSIVE_TIERS -> {
                val tier = r.tierAt(goal.value) ?: return "Dès le palier 1"
                tier.rate?.let { "Taux actuel · ${rate(it)}" } ?: "Palier ${r.sortedTiers.indexOf(tier) + 1}"
            }
            IncentiveType.FIXED_BONUS -> if (goal.reached) "Prime forfaitaire" else "Prime forfaitaire dès l'objectif"
            IncentiveType.FREE_GOODS -> if (goal.reached) "Gratuité" else "Gratuité dès l'objectif"
        }
    }

    /** What a tier pays, for the ladder; null when the bonus is hidden. */
    fun tierPays(tier: TierRule, showReward: Boolean, money: MoneyFormatter): String? =
        if (!showReward) null else tier.rate?.let { rate(it) } ?: money.da(tier.fixedBonus ?: 0.0)
}
