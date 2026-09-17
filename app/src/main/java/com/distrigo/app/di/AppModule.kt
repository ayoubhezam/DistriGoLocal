package com.distrigo.app.di

import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.repository.BusinessSettingsRepository
import android.content.Context
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.repository.ChargeRepository
import com.distrigo.app.data.repository.IncentiveRepository
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.PurchaseDraftRepository
import com.distrigo.app.data.repository.ChargementDraftRepository
import com.distrigo.app.data.repository.TourneeVenteDraftRepository
import com.distrigo.app.data.repository.VenteDraftRepository
import com.distrigo.app.data.repository.RetourClientRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideBackupCreator(@ApplicationContext context: Context, db: AppDatabase): BackupCreator =
        BackupCreator.forApp(context, db)

    @Provides
    @Singleton
    fun provideBusinessSettingsRepository(db: AppDatabase, @ApplicationContext context: Context): BusinessSettingsRepository =
        BusinessSettingsRepository(db = db, context = context)

    @Provides
    @Singleton
    fun provideProductRepository(db: AppDatabase): ProductRepository =
        ProductRepository(
            productDao  = db.productDao(),
            categoryDao = db.categoryDao(),
            supplierDao = db.supplierDao(),
            db          = db
        )

    @Provides
    @Singleton
    fun providePurchaseDraftRepository(db: AppDatabase): PurchaseDraftRepository =
        PurchaseDraftRepository(db = db)

    @Provides
    @Singleton
    fun provideVenteDraftRepository(db: AppDatabase): VenteDraftRepository =
        VenteDraftRepository(db = db)

    @Provides
    @Singleton
    fun provideTourneeVenteDraftRepository(db: AppDatabase): TourneeVenteDraftRepository =
        TourneeVenteDraftRepository(db = db)

    @Provides
    @Singleton
    fun provideChargementDraftRepository(db: AppDatabase): ChargementDraftRepository =
        ChargementDraftRepository(db = db)

    @Provides
    @Singleton
    fun provideChargeRepository(db: AppDatabase): ChargeRepository =
        ChargeRepository(chargeDao = db.chargeDao())

    @Provides
    @Singleton
    fun providePerteRepository(db: AppDatabase): PerteRepository =
        PerteRepository(db = db)

    @Provides
    @Singleton
    fun provideInventoryRepository(db: AppDatabase): InventoryRepository =
        InventoryRepository(db = db)

    @Provides
    @Singleton
    fun provideIncentiveRepository(db: AppDatabase): IncentiveRepository =
        IncentiveRepository(db = db)

    @Provides
    @Singleton
    fun provideRetourClientRepository(db: AppDatabase): RetourClientRepository =
        RetourClientRepository(db = db)

    @Provides
    @Singleton
    fun provideRetourFournisseurRepository(db: AppDatabase): RetourFournisseurRepository =
        RetourFournisseurRepository(db = db)
}
