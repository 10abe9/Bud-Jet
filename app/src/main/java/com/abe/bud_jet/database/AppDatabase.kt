package com.abe.bud_jet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.abe.bud_jet.database.dao.CategoryDao
import com.abe.bud_jet.database.dao.GoalsDao
import com.abe.bud_jet.database.dao.TransactionsDao
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        GoalEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionsDao(): TransactionsDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalsDao(): GoalsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bud_jet.db"
                )
                    .addMigrations(*Migrations.ALL)
                    .build().also { INSTANCE = it }
            }
        }
    }
}

