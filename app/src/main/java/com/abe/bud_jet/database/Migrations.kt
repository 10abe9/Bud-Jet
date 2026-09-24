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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Keys for existing built-in categories are assigned by name on the next start
            // (FinanceRepository.syncDefaultCategories).
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `defaultKey` TEXT")
        }
    }

    /** Transactions captured from payment notifications and their supporting tables. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source_app` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `merchant` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `external_id` TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_external_id` " +
                    "ON `transactions` (`external_id`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_captures` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`externalId` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT, " +
                    "`text` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, `amount` REAL, " +
                    "`currencyCode` TEXT, `isIncome` INTEGER, `merchant` TEXT)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_captures_externalId` " +
                    "ON `pending_captures` (`externalId`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `capture_sources` (" +
                    "`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `detectedCount` INTEGER NOT NULL, " +
                    "`lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                    "`merchantKey` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`merchantKey`))"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
