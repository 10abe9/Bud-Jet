package com.abe.bud_jet.database

import androidx.room.TypeConverter
import com.abe.bud_jet.database.entities.TransactionType

class AppTypeConverters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.valueOf(value)
}

