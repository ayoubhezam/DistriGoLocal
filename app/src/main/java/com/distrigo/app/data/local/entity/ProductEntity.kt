package com.distrigo.app.data.local.entity // أو com.distrigo.app.data.local.entity حسب رغبتك

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // نستخدم 0 كقيمة افتراضية لكي يقوم Room بتوليد ID تلقائي عند الإضافة
    val name: String,
    val barcode: String?,
    val selling_price: Double,
    val purchase_price: Double,
    val stock: Int,
    val min_stock: Int,
    val unit_type: String,
    val packages: Int,
    val pack_size: Int,
    val has_expiry: Int,
    val expiry_date: String?,
    val image_uri: String?,

    // ملاحظة هامة حول العلاقات (Relations):
    // في API كنت تستقبل اسم الفئة والمورد مباشرة (category_name, supplier_name).
    // في قواعد البيانات العلاقية (Room)، يجب أن نعتمد فقط على الـ ID (category_id, supplier_id).
    // لجلب الأسماء لاحقاً، سنستخدم استعلامات (JOINs) في الـ DAO.
    // لكن حالياً، ولكي لا نكسر الواجهات التي قد تعتمد على هذه الحقول، سنبقيها كأعمدة عادية قابلة للـ null.
    val category_name: String?,
    val category_id: Int?,
    val supplier_name: String?,
    val supplier_id: Int?,

    val camion_stock: Int = 0
)