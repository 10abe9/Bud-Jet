package com.abe.bud_jet.database

import android.content.Context

object FinanceRepositoryProvider {

    @Volatile
    private var INSTANCE: FinanceRepository? = null

    fun get(context: Context): FinanceRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildRepository(context).also { INSTANCE = it }
        }
    }

    private fun buildRepository(context: Context): FinanceRepository {
        val database = AppDatabase.getInstance(context)
        return FinanceRepository(
            transactionsDao = database.transactionsDao(),
            categoryDao = database.categoryDao(),
            goalsDao = database.goalsDao()
        )
    }
}

