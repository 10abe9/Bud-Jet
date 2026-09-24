package com.abe.bud_jet.database

import com.abe.bud_jet.R
import android.content.Context
import androidx.room.withTransaction

object FinanceRepositoryProvider {

    @Volatile
    private var INSTANCE: FinanceRepository? = null

    fun get(context: Context): FinanceRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildRepository(context).also { INSTANCE = it }
        }
    }

    /**
     * Built-in category names in the language of [context]. Pass an Activity context:
     * on Android < 13 only it carries the per-app language set through AppCompat.
     */
    fun localizedDefaultCategoryNames(context: Context): Map<String, String> = mapOf(
        "food" to context.getString(R.string.placeholder_food),
        "health" to context.getString(R.string.default_category_health),
        "transport" to context.getString(R.string.default_category_transport),
        "salary" to context.getString(R.string.default_category_salary),
        "gift" to context.getString(R.string.default_category_gift),
        "freelance" to context.getString(R.string.default_category_freelance)
    )

    private fun buildRepository(context: Context): FinanceRepository {
        val database = AppDatabase.getInstance(context)
        return FinanceRepository(
            transactionsDao = database.transactionsDao(),
            categoryDao = database.categoryDao(),
            goalsDao = database.goalsDao(),
            runInTransaction = { block -> database.withTransaction { block() } }
        )
    }
}

