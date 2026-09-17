package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.distrigo.app.data.local.entity.BusinessSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessSettingsDao {

    @Query("SELECT * FROM business_settings WHERE id = 1")
    fun observe(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE id = 1")
    suspend fun get(): BusinessSettingsEntity?

    /** Creates the row unless it exists; an existing row is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: BusinessSettingsEntity): Long

    @Query("UPDATE business_settings SET business_name = :name, business_phone = :phone WHERE id = 1")
    suspend fun updateIdentity(name: String?, phone: String?)

    @Query("UPDATE business_settings SET logo_ref = :ref WHERE id = 1")
    suspend fun updateLogo(ref: String?)
}
