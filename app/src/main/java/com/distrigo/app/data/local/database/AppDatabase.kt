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

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clients ADD COLUMN secteur_id INTEGER")
        db.execSQL("ALTER TABLE clients ADD COLUMN secteur_name TEXT")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `retour_fournisseur` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplier_id` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `motif` TEXT,
                `note` TEXT,
                `total` REAL NOT NULL,
                `created_at` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `retour_fournisseur_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `retour_id` INTEGER NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_name` TEXT NOT NULL,
                `unit_type` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit_price` REAL NOT NULL,
                `total_price` REAL NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `retour_client` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `client_id` INTEGER NOT NULL,
                `tournee_id` INTEGER,
                `date` TEXT NOT NULL,
                `motif` TEXT,
                `note` TEXT,
                `total` REAL NOT NULL,
                `created_at` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `retour_client_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `retour_id` INTEGER NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_name` TEXT NOT NULL,
                `unit_type` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit_price` REAL NOT NULL,
                `total_price` REAL NOT NULL
            )
        """.trimIndent())
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
        StockMovementEntity::class,
        SecteurEntity::class,
        RetourFournisseurEntity::class,
        RetourFournisseurItemEntity::class,
        RetourClientEntity::class,
        RetourClientItemEntity::class,
    ],
    version = 27,
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

    abstract fun secteurDao(): SecteurDao

    abstract fun retourFournisseurDao(): RetourFournisseurDao
    abstract fun retourClientDao(): RetourClientDao



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
                    .addMigrations(MIGRATION_24_25, MIGRATION_26_27)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }


}