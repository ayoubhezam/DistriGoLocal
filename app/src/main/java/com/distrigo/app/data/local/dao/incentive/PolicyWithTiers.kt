package com.distrigo.app.data.local.dao.incentive

import androidx.room.Embedded
import androidx.room.Relation
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity

data class PolicyWithTiers(
    @Embedded val policy: TargetPolicyEntity,
    @Relation(parentColumn = "id", entityColumn = "policy_id")
    val tiers: List<PolicyTierEntity>
)