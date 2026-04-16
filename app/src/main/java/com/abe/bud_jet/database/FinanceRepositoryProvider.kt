package com.abe.bud_jet.database

import com.abe.bud_jet.R
import android.content.Context

object FinanceRepositoryProvider {

    @Volatile
    private var INSTANCE: FinanceRepository? = null

    fun get(context: Context): FinanceRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildRepository(context).also { INSTANCE = it }
        }
    }

    fun clearInstance() {
        INSTANCE = null
    }

    private fun buildRepository(context: Context): FinanceRepository {
        val database = AppDatabase.getInstance(context)

        val expenseDefaults = listOf(
            context.getString(R.string.placeholder_food),
            context.getString(R.string.default_category_health),
            context.getString(R.string.default_category_transport)
        )
        val incomeDefaults = listOf(
            context.getString(R.string.default_category_salary),
            context.getString(R.string.default_category_gift),
            context.getString(R.string.default_category_freelance)
        )
        return FinanceRepository(
            transactionsDao = database.transactionsDao(),
            categoryDao = database.categoryDao(),
            goalsDao = database.goalsDao(),
            defaultExpenseCategories = expenseDefaults,
            defaultIncomeCategories = incomeDefaults
        )
    }
}

