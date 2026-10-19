package com.distrigo.app.data.local.dao.incentive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.distrigo.app.data.local.entity.incentive.CommissionPeriodEntity
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity

@Dao
interface TargetPolicyDao {

    @Insert
    suspend fun insertPolicy(policy: TargetPolicyEntity): Long

    @Insert
    suspend fun insertTiers(tiers: List<PolicyTierEntity>)

    @Query("UPDATE target_policies SET is_active = 0 WHERE distributor_id IS :distributorId OR (distributor_id IS NULL AND :distributorId IS NULL)")
    suspend fun deactivateExistingPolicies(distributorId: Long?)

    @Query("SELECT * FROM target_policies WHERE is_active = 1 AND (distributor_id = :distributorId OR distributor_id IS NULL) ORDER BY distributor_id DESC LIMIT 1")
    suspend fun getActivePolicy(distributorId: Long? = null): TargetPolicyEntity?

    @Transaction
    @Query("SELECT * FROM target_policies WHERE id = :policyId")
    suspend fun getPolicyWithTiers(policyId: Long): PolicyWithTiers?

    @Query("SELECT * FROM target_policies ORDER BY created_at DESC")
    suspend fun getAllPolicies(): List<TargetPolicyEntity>

    @Query("SELECT * FROM policy_tiers WHERE policy_id = :policyId ORDER BY tier_order ASC")
    suspend fun getTiersForPolicy(policyId: Long): List<PolicyTierEntity>

    // ── Ended periods, their results frozen (see CommissionPeriodEntity) ──

    /** Ignores a period already frozen: the first result stands. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeriod(period: CommissionPeriodEntity): Long

    @Query("SELECT COUNT(*) FROM commission_periods WHERE period_start = :start AND period_end = :end")
    suspend fun countPeriod(start: String, end: String): Int

    /** Every frozen period, the latest first. */
    @Query("SELECT * FROM commission_periods ORDER BY period_end DESC, period_start DESC")
    suspend fun getPeriods(): List<CommissionPeriodEntity>
}