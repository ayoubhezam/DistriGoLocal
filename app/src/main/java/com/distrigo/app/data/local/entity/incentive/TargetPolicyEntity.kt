// package com.distrigo.app.data.local.entity.incentive

package com.distrigo.app.data.local.entity.incentive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.distrigo.app.data.local.entity.newRowUuid

@Entity(tableName = "target_policies", indices = [Index(value = ["uuid"], unique = true)])
data class TargetPolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val nom: String,                              // مثال: "Politique Q3 2026"
    val incentive_type: IncentiveType,
    val period_type: PeriodType,
    val calculation_source: CalculationSource,

    val target_value: Double,                     // الهدف الأساسي (DZD) — يُستخدم في الأنواع الأربعة كلها كعتبة الانطلاق
    val reward_rate: Double? = null,               // نسبة % — لـ FIXED_RATE فقط
    val fixed_bonus_amount: Double? = null,        // مبلغ ثابت DZD — لـ FIXED_BONUS فقط
    val free_good_product_id: Int? = null,         // لـ FREE_GOODS فقط
    val free_good_quantity: Int? = null,

    @ColumnInfo(defaultValue = "1")
    val is_active: Boolean = true,
    val distributor_id: Long? = null,              // null = يطبَّق على هذا الجهاز افتراضيًا
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)

@Entity(tableName = "policy_tiers", indices = [Index(value = ["uuid"], unique = true)])
data class PolicyTierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val policy_id: Long,                           // FK منطقي إلى target_policies.id (بلا @ForeignKey صارم — انظر الملاحظة أسفله)
    val min_threshold: Double,                     // الحد الأدنى لهذه الشريحة (شامل)
    val max_threshold: Double? = null,              // null = بلا حد أعلى (آخر شريحة)
    val reward_rate: Double? = null,                // % عمولة لهذه الشريحة
    val fixed_bonus: Double? = null,                // أو مبلغ ثابت عند بلوغ هذه الشريحة
    val tier_order: Int,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)