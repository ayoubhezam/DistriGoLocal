package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.distrigo.app.data.local.entity.AppMetaEntity
import kotlinx.coroutines.flow.Flow

/** Facts about the database itself — see AppMetaEntity for the keys. */
@Dao
interface AppMetaDao {

    @Query("SELECT * FROM app_meta WHERE `key` IN (:keys)")
    fun observe(keys: List<String>): Flow<List<AppMetaEntity>>

    @Query("SELECT `value` FROM app_meta WHERE `key` = :key")
    fun get(key: String): String?

    /** Blocking, and in one transaction: callers are already off the main thread. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(rows: List<AppMetaEntity>)

    /**
     * Whether the business has recorded anything here yet. A fresh install has no products, clients, sales or
     * purchases: only then does the onboarding open by itself (docs/activation_codes.md, decision 6). Blocking.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM products) OR EXISTS(SELECT 1 FROM clients) OR EXISTS(SELECT 1 FROM ventes) OR EXISTS(SELECT 1 FROM purchase_orders)")
    fun hasBusinessData(): Boolean
}
