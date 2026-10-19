package com.distrigo.app.data.local.entity.incentive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.distrigo.app.data.local.entity.newRowUuid

/**
 * One ended period of the commission policy, its result frozen (D13, 2026-10-17): a later edit to an old sale can
 * no longer change a bonus already paid, and the rows are the history of « Mon objectif ».
 *
 * A period is frozen the first time it is read after it ended, and just before a policy is replaced, so each one
 * keeps the rules it ran under; the policy's own fields are copied, so the row reads on its own once that policy
 * is gone. Days are local, both ends included. Added in version 62.
 */
@Entity(
    tableName = "commission_periods",
    indices = [Index(value = ["period_start", "period_end"], unique = true), Index(value = ["uuid"], unique = true)]
)
data class CommissionPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The policy whose rules gave this result. */
    val policy_uuid: String,
    val incentive_type: IncentiveType,
    val period_type: PeriodType,
    val calculation_source: CalculationSource,
    /** The local days the period covers, both included: `2026-09-01`, `2026-09-30`. */
    val period_start: String,
    val period_end: String,
    /** The measure reached over the period, and the objective it was measured against. */
    val achieved_value: Double,
    val objective: Double,
    /** Rounded down: 100 only when the objective was reached. */
    val achieved_percent: Int,
    val reached: Boolean,
    /** The cash bonus earned, or the goods; all null when nothing was earned. */
    val reward_amount: Double? = null,
    val free_good_product_id: Int? = null,
    val free_good_quantity: Int? = null,
    /** The tier reached, for a policy in « Paliers ». */
    val tier_order: Int? = null,
    /** When the result was frozen, an ISO instant. */
    val closed_at: String,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row. */
    val origin_device_id: String? = null
)
