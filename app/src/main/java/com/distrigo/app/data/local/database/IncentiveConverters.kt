// package com.distrigo.app.data.local.database

package com.distrigo.app.data.local.database

import androidx.room.TypeConverter
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.PeriodType

class IncentiveConverters {
    @TypeConverter
    fun fromIncentiveType(value: IncentiveType): String = value.name
    @TypeConverter
    fun toIncentiveType(value: String): IncentiveType = IncentiveType.valueOf(value)

    @TypeConverter
    fun fromPeriodType(value: PeriodType): String = value.name
    @TypeConverter
    fun toPeriodType(value: String): PeriodType = PeriodType.valueOf(value)

    @TypeConverter
    fun fromCalculationSource(value: CalculationSource): String = value.name
    @TypeConverter
    fun toCalculationSource(value: String): CalculationSource = CalculationSource.valueOf(value)
}