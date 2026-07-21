package com.distrigo.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.distrigo.app.data.local.dao.ProductDao
import com.distrigo.app.data.local.dao.CategoryDao
import com.distrigo.app.data.local.dao.SupplierDao
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.CategoryEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import com.distrigo.app.data.local.dao.ChargementDao
import com.distrigo.app.data.local.entity.ChargementSessionEntity
import com.distrigo.app.data.local.entity.ChargementEntity
import com.distrigo.app.data.local.entity.ChargementItemEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity
import com.distrigo.app.data.local.entity.PriceHistoryEntity
import com.distrigo.app.data.local.dao.PurchaseDao
import com.distrigo.app.data.local.dao.*
import com.distrigo.app.data.local.entity.*
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.dao.incentive.TargetPolicyDao
import com.distrigo.app.data.local.dao.mouvement.StockMovementDao
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_order_items ADD COLUMN has_expiry INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE purchase_order_items ADD COLUMN expiry_date TEXT")
    }
}
@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        SupplierEntity::class,
        ChargementSessionEntity::class,
        ChargementEntity::class,
        ChargementItemEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderItemEntity::class,
        PriceHistoryEntity::class,
        SupplierPaymentEntity::class,
        ClientEntity::class,
        VenteEntity::class,
        VenteItemEntity::class,
        TourneeEntity::class,
        ClientPaymentEntity::class,
        TourneeClientEntity::class,
        ChargeTypeEntity::class,
        ChargeSubTypeEntity::class,
        ChargeEntity::class,
        TargetPolicyEntity::class,
        PolicyTierEntity::class,
        PerteTypeEntity::class,
        PerteEntity::class,
        InventorySessionEntity::class,
        InventoryItemEntity::class,
        StockMovementEntity::class
    ],
    version = 24,
    exportSchema = false
)

@TypeConverters(IncentiveConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao

    abstract fun chargementDao(): ChargementDao

    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun clientPaymentDao(): ClientPaymentDao

    abstract fun clientDao(): ClientDao
    abstract fun venteDao(): VenteDao
    abstract fun tourneeDao(): TourneeDao

    abstract fun tourneeClientDao(): TourneeClientDao
    abstract fun targetPolicyDao(): TargetPolicyDao

    abstract fun chargeDao(): ChargeDao
    abstract fun perteDao(): PerteDao
    abstract fun inventoryDao(): InventoryDao

    abstract fun stockMovementDao(): StockMovementDao



    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "distrigo"
                )
                    .addMigrations(MIGRATION_20_21)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }


}