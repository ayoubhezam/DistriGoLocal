package com.distrigo.app.ui.goal

import com.distrigo.app.R
import com.distrigo.app.core.format.percentText
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.lists.ListWords
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
 * words in the app's language (strings_goal), the next step in one sentence, the bonus — in Paramètres' own words, « Palier », « Seuil », « Taux ». With
 * the policy's « Afficher la prime » off, no amount or rate of a bonus is ever said: thresholds only.
 */
object GoalWording {

    private val FR = Locale.FRENCH

    private fun t(id: Int, vararg args: Any): String = AppStrings.write(UiText.Res(id, args.toList()))

    /** « octobre », « 4e trimestre », « 2026 »: the month in the app's language. */
    fun periodName(period: CommissionPeriod): String = when (period.type) {
        PeriodType.MONTHLY -> period.start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        PeriodType.QUARTERLY -> t(R.string.goal_quarter_n, (period.start.monthValue - 1) / 3 + 1)
        PeriodType.YEARLY -> period.start.year.toString()
    }

    /** A past period with its year: « Septembre 2026 », « 3e trimestre 2026 », « 2025 ». */
    fun pastPeriodName(period: CommissionPeriod): String = when (period.type) {
        PeriodType.YEARLY -> period.start.year.toString()
        else -> periodName(period).replaceFirstChar { it.uppercase() } + " " + period.start.year
    }

    /** « ce mois-ci », « ce trimestre », « cette année ». */
    fun thisPeriod(type: PeriodType): String = when (type) {
        PeriodType.MONTHLY -> t(R.string.goal_this_month)
        PeriodType.QUARTERLY -> t(R.string.goal_this_quarter)
        PeriodType.YEARLY -> t(R.string.goal_this_year)
    }

    /** « fin du mois », « fin du trimestre », « fin de l'année ». */
    fun periodEnd(type: PeriodType): String = when (type) {
        PeriodType.MONTHLY -> t(R.string.goal_end_month)
        PeriodType.QUARTERLY -> t(R.string.goal_end_quarter)
        PeriodType.YEARLY -> t(R.string.goal_end_year)
    }

    /** « Ventes facturées » or « Recouvrement », as Paramètres names the measure. */
    fun measure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> t(R.string.settings_source_invoiced)
        CalculationSource.COLLECTED_CASH -> t(R.string.settings_source_collected)
    }

    /** « de vos ventes facturées », « de votre recouvrement ». */
    private fun ofMeasure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> t(R.string.goal_of_invoiced)
        CalculationSource.COLLECTED_CASH -> t(R.string.goal_of_collected)
    }

    /** 3 → « 3 % », 2.5 → « 2,5 % ». */
    fun rate(value: Double): String =
        percentText(if (value == floor(value)) "${value.toLong()}" else String.format(FR, "%.1f", value).replace('.', ','))

    /** What a tier pays: « 2,5 % de vos ventes facturées » or « une prime de 20 000 DA ». */
    private fun pays(tier: TierRule, source: CalculationSource, money: MoneyFormatter): String =
        tier.rate?.let { t(R.string.goal_rate_of, rate(it), ofMeasure(source)) } ?: t(R.string.goal_a_bonus_of, money.da(tier.fixedBonus ?: 0.0))

    /** « de ventes facturées », « de recouvrement »: what a threshold is counted in. */
    private fun inMeasure(source: CalculationSource): String = when (source) {
        CalculationSource.INVOICED_SALES -> t(R.string.goal_in_invoiced)
        CalculationSource.COLLECTED_CASH -> t(R.string.goal_in_collected)
    }

    /** « 6 « Café » », « 6 unités »: what a « Gratuité » gives. */
    private fun goods(quantity: Int, freeGood: String?): String =
        t(R.string.goal_goods, quantity, freeGood?.let { t(R.string.goal_named, it) } ?: t(R.string.goal_units))

    /**
     * The policy in one plain sentence: « Vous touchez 2 % de vos ventes facturées dès 600 000 DA ce mois-ci, 2,5 %
     * dès 900 000 DA… ». Without the bonus shown, only what is to reach. [freeGood] names the product of a « Gratuité ».
     */
    fun rule(rules: CommissionRules, type: PeriodType, money: MoneyFormatter, showReward: Boolean, freeGood: String? = null): String {
        val period = thisPeriod(type)
        val tiers = rules.sortedTiers
        if (tiers.isNotEmpty()) {
            if (!showReward) {
                val listed = ListWords.and(tiers.map { money.da(it.threshold) })
                return t(R.string.goal_rule_tiers_hidden, count(tiers.size), inMeasure(rules.source), period, listed)
            }
            val first = tiers.first()
            val rest = tiers.drop(1).joinToString("") { tier ->
                val what = tier.rate?.let { rate(it) } ?: t(R.string.goal_a_bonus_of, money.da(tier.fixedBonus ?: 0.0))
                t(R.string.goal_rule_more_tier, what, money.da(tier.threshold))
            }
            return t(R.string.goal_rule_tiers, pays(first, rules.source, money), money.da(first.threshold), period, rest)
        }
        val objective = t(R.string.goal_objective, money.da(rules.objective), inMeasure(rules.source), period)
        if (!showReward) return t(R.string.goal_your_objective, objective)
        return when (rules.type) {
            IncentiveType.FIXED_RATE -> t(R.string.goal_rule_rate, rate(rules.rate ?: 0.0), ofMeasure(rules.source), money.da(rules.objective), period)
            IncentiveType.FIXED_BONUS -> t(R.string.goal_rule_bonus, money.da(rules.fixedBonus ?: 0.0), objective)
            IncentiveType.FREE_GOODS -> t(R.string.goal_rule_goods, goods(rules.freeGoodQuantity ?: 0, freeGood), objective)
            IncentiveType.PROGRESSIVE_TIERS -> t(R.string.goal_your_objective, objective)
        }
    }

    private fun count(n: Int): String = when (n) {
        1 -> t(R.string.goal_tiers_1)
        2 -> t(R.string.goal_tiers_2)
        3 -> t(R.string.goal_tiers_3)
        4 -> t(R.string.goal_tiers_4)
        5 -> t(R.string.goal_tiers_5)
        else -> t(R.string.goal_tiers_n, n)
    }

    private fun RewardResult.cash(): Double = (this as? RewardResult.CashReward)?.amount ?: 0.0

    private fun days(n: Int) = AppStrings.write(UiText.Plural(R.plurals.goal_n_days, n))

    /** The one sentence about the next step (Step 4): always the nearest milestone, not the final goal. */
    fun nextSentence(goal: GoalProgress, money: MoneyFormatter): String {
        val tiers = goal.rules.sortedTiers
        val next = goal.next ?: return t(if (tiers.isNotEmpty()) R.string.goal_max_tier else R.string.goal_reached)
        if (tiers.isNotEmpty()) {
            val position = tiers.indexOfFirst { it.threshold == next.threshold } + 1
            return if (goal.showReward) {
                t(R.string.goal_next_tier_reward, money.da(next.gap), position, money.da(goal.reward.cash()), money.da(next.reward.cash()))
            } else {
                t(R.string.goal_next_tier, money.da(next.gap), position)
            }
        }
        val pace = goal.pace ?: return t(R.string.goal_next, money.da(next.gap))
        return t(R.string.goal_next_pace, money.da(next.gap), days(pace.daysLeft), money.da(pace.neededPerDay))
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
        val goods = goods(r.freeGoodQuantity ?: 0, freeGood)
        return when (r.type) {
            IncentiveType.FIXED_RATE ->
                if (goal.reached) t(R.string.goal_reward_cash, money.da(goal.reward.cash())) to true
                else t(R.string.goal_reward_to_unlock, rate(r.rate ?: 0.0), ofMeasure(r.source)) to false
            IncentiveType.FIXED_BONUS ->
                if (goal.reached) t(R.string.goal_reward_bonus, money.da(r.fixedBonus ?: 0.0)) to true
                else t(R.string.goal_reward_bonus_to_unlock, money.da(r.fixedBonus ?: 0.0)) to false
            IncentiveType.FREE_GOODS ->
                if (goal.reached) t(R.string.goal_reward_goods, goods) to true
                else t(R.string.goal_reward_goods_to_unlock, goods) to false
            IncentiveType.PROGRESSIVE_TIERS -> {
                val current = r.tierAt(goal.value)
                if (current == null) t(R.string.goal_reward_from_tier_1, money.da(r.sortedTiers.firstOrNull()?.threshold ?: 0.0)) to false
                else {
                    val position = r.sortedTiers.indexOf(current) + 1
                    t(R.string.goal_reward_tier, position, money.da(goal.reward.cash())) to true
                }
            }
        }
    }

    // ── The navy card and its companions (D21) ──

    /** `888 000 DA`: rounded to the dinar, in the user's separators, for the big figures. */
    fun whole(value: Double, money: MoneyFormatter): String =
        money.da(Math.round(value).toDouble()).replace("${money.format.decimal}00", "")

    /**
     * A rhythm bar's name in the app's language: a month's week « S1 » / « أسبوع 1 », a year's quarter « T1 » / « فصل 1 »
     * (owner, 2026-11-02), a quarter's month « Oct » / « أكتوبر ». The step holds the French name; this is display only.
     */
    fun stepLabel(step: com.distrigo.app.data.model.incentive.GoalStep): String {
        val code = STEP_CODE.matchEntire(step.label)
        return when (code?.groupValues?.get(1)) {
            "S" -> t(R.string.goal_step_week, code.groupValues[2].toInt())
            "T" -> t(R.string.goal_step_quarter, code.groupValues[2].toInt())
            else -> step.start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                .trimEnd('.').replaceFirstChar { it.uppercase() }
        }
    }

    private val STEP_CODE = Regex("([ST])(\\d)")

    /** A bar's label: `238K`, `1,2M`; in Arabic « 238 ألف », « 1,2 مليون » (owner, 2026-11-02). */
    fun compact(value: Double): String = when {
        value >= 1_000_000 -> t(R.string.goal_amount_millions, String.format(FR, "%.1f", value / 1_000_000).replace('.', ',').removeSuffix(",0"))
        value >= 1_000 -> t(R.string.goal_amount_thousands, "${Math.round(value / 1_000)}")
        else -> "${Math.round(value)}"
    }

    /** « le palier 2 » or « votre objectif »: what the measure aims at now. */
    fun aim(goal: GoalProgress): String = goal.target.tier?.let { t(R.string.goal_aim_tier, it) } ?: t(R.string.goal_aim)

    /** « du palier 2 » or « de l'objectif ». */
    fun ofAim(goal: GoalProgress): String = goal.target.tier?.let { t(R.string.goal_of_aim_tier, it) } ?: t(R.string.goal_of_aim)

    /** « Réalisé en octobre », « Réalisé ce trimestre », « Réalisé en 2026 ». */
    fun achievedIn(period: CommissionPeriod): String = when (period.type) {
        PeriodType.MONTHLY -> t(R.string.goal_achieved_in, periodName(period))
        PeriodType.QUARTERLY -> t(R.string.goal_achieved_quarter)
        PeriodType.YEARLY -> t(R.string.goal_achieved_in, period.start.year.toString())
    }

    /**
     * The navy card's line, from the pace — never from the percent alone, which said « dans le bon rythme » at 70 %
     * with two days left.
     */
    fun encouragement(goal: GoalProgress): String {
        if (goal.next == null) {
            return t(if (goal.rules.sortedTiers.isNotEmpty()) R.string.goal_keep_going_tier else R.string.goal_keep_going)
        }
        return when (goal.pace?.status) {
            PaceStatus.AHEAD -> t(R.string.goal_pace_ahead, aim(goal))
            PaceStatus.ON_TRACK -> t(R.string.goal_pace_on_track, aim(goal))
            PaceStatus.BEHIND -> t(R.string.goal_pace_behind, ofAim(goal))
            else -> t(R.string.goal_pace_steady, ofAim(goal))
        }
    }

    /** The policy in a few words, under « Commission estimée »: « taux fixe 3 % », « paliers »… */
    fun policyLine(rules: CommissionRules): String = when (rules.type) {
        IncentiveType.FIXED_RATE -> t(R.string.goal_policy_rate, rate(rules.rate ?: 0.0))
        IncentiveType.FIXED_BONUS -> t(R.string.goal_policy_bonus)
        IncentiveType.FREE_GOODS -> t(R.string.goal_policy_goods)
        IncentiveType.PROGRESSIVE_TIERS -> t(R.string.goal_policy_tiers)
    }

    /** What a [RewardResult] gives: `17 760 DA`, `6 « Café » offerts`, `0 DA` while not earned. */
    fun rewardValue(reward: RewardResult, money: MoneyFormatter, freeGood: String? = null): String = when (reward) {
        is RewardResult.CashReward -> whole(reward.amount, money)
        is RewardResult.FreeGoodsReward -> t(R.string.goal_goods_given, goods(reward.quantity, freeGood))
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
                if (goal.reached) t(R.string.goal_note_rate_now, rate(r.rate ?: 0.0)) else t(R.string.goal_note_rate_from, rate(r.rate ?: 0.0))
            IncentiveType.PROGRESSIVE_TIERS -> {
                val tier = r.tierAt(goal.value) ?: return t(R.string.goal_note_from_tier_1)
                tier.rate?.let { t(R.string.goal_note_rate_now, rate(it)) } ?: t(R.string.goal_note_tier, r.sortedTiers.indexOf(tier) + 1)
            }
            IncentiveType.FIXED_BONUS -> t(if (goal.reached) R.string.goal_note_bonus else R.string.goal_note_bonus_from)
            IncentiveType.FREE_GOODS -> t(if (goal.reached) R.string.goal_note_goods else R.string.goal_note_goods_from)
        }
    }

    /** What a tier pays, for the ladder; null when the bonus is hidden. */
    fun tierPays(tier: TierRule, showReward: Boolean, money: MoneyFormatter): String? =
        if (!showReward) null else tier.rate?.let { rate(it) } ?: money.da(tier.fixedBonus ?: 0.0)
}
