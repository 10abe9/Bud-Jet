package com.abe.bud_jet.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.abe.bud_jet.utils.DateRanges

/**
 * Every schema change must ship with a migration here, otherwise Room fails to open
 * the existing database on update and users lose access to their data.
 * Schemas are exported to app/schemas — commit them after each build that changes them.
 */
object Migrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            // Existing saving goals used to track the current month only; start them there
            // so the visible progress does not jump after the update.
            val monthStart = DateRanges.month().from
            db.execSQL("UPDATE `goals` SET `createdAt` = $monthStart")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3)
}
