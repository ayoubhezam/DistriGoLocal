package com.distrigo.app.data.local.database

import android.content.Context
import com.distrigo.app.data.device.DeviceIdentity
import com.distrigo.app.data.backup.RestoreInstaller
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
/**
 * The schema version this build writes. A backup records it, and a backup from a later version is refused
 * rather than opened: Room cannot downgrade a database.
 */
const val DATABASE_VERSION = 52

/** The app's database file, in `databases/`. */
const val DATABASE_NAME = "distrigo"

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
        TourneeSecteurEntity::class,
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
        SousCategorieEntity::class,
        MarqueEntity::class,
        PurchaseDraftEntity::class,
        VenteDraftEntity::class,
        ProductImageEntity::class,
        TourneeVenteDraftEntity::class,
        ChargementDraftEntity::class,
        TombstoneEntity::class,
        AppMetaEntity::class,
        BusinessSettingsEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)

@TypeConverters(IncentiveConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseDraftDao(): PurchaseDraftDao
    abstract fun venteDraftDao(): VenteDraftDao
    abstract fun tourneeVenteDraftDao(): TourneeVenteDraftDao
    abstract fun chargementDraftDao(): ChargementDraftDao

    abstract fun chargementDao(): ChargementDao

    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun clientPaymentDao(): ClientPaymentDao

    abstract fun clientDao(): ClientDao
    abstract fun venteDao(): VenteDao
    abstract fun tourneeDao(): TourneeDao

    abstract fun tourneeClientDao(): TourneeClientDao
    abstract fun tourneeSecteurDao(): TourneeSecteurDao
    abstract fun targetPolicyDao(): TargetPolicyDao

    abstract fun chargeDao(): ChargeDao
    abstract fun perteDao(): PerteDao
    abstract fun inventoryDao(): InventoryDao

    abstract fun stockMovementDao(): StockMovementDao

    abstract fun secteurDao(): SecteurDao

    abstract fun retourFournisseurDao(): RetourFournisseurDao
    abstract fun retourClientDao(): RetourClientDao

    abstract fun sousCategorieDao(): SousCategorieDao
    abstract fun marqueDao(): MarqueDao

    /** The product photo gallery — see ProductImageEntity. */
    abstract fun productImageDao(): ProductImageDao

    /** Used once, to move base64 payloads out to files — see ImageBackfill. */
    abstract fun imageBackfillDao(): ImageBackfillDao

    /** The business identity receipts print — see BusinessSettingsEntity. */
    abstract fun businessSettingsDao(): BusinessSettingsDao

    /** Facts about the database itself, such as its id and the last backup — see AppMetaEntity. */
    abstract fun appMetaDao(): AppMetaDao



    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Application.onCreate has already done this; repeated here so no path opens the database
                // before a waiting restore is installed. A no-op when none is waiting.
                RestoreInstaller.forApp(context).installPending()
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .withMigrationPolicy()
                    .withChangeTracking()
                    .withDeviceIdentity { DeviceIdentity.id(context) }
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }


}

/**
 * How this database is allowed to change version. Shared with the migration tests, so what they
 * check is what ships.
 *
 * Every registered path migrates. Only a pre-32 install, which has no path, is recreated. Anything
 * else throws on open and leaves the file untouched: a version from 32 on with no registered
 * migration, or a downgrade, such as an older build installed over a newer one.
 *
 * This replaces an unconditional `fallbackToDestructiveMigration()`, which wiped the database in
 * all of those cases. A forgotten migration would have deleted every sale, payment and stock
 * movement on the first launch after an update, and nothing would have reported it.
 *
 * `dropAllTables = false` keeps the pre-32 wipe exactly as it was: Room drops only the tables it
 * knows before recreating them.
 */
internal fun RoomDatabase.Builder<AppDatabase>.withMigrationPolicy(): RoomDatabase.Builder<AppDatabase> =
    addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigrationFrom(false, *DESTRUCTIVE_MIGRATION_VERSIONS)