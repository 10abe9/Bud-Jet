package com.abe.bud_jet.utils

import kotlin.math.abs

object CurrencyFormatter {

    fun symbolFor(code: String): String {
        return when (code.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "PLN" -> "zł"
            "MXN" -> "$"
            "BRL" -> "R$"
            "INR" -> "₹"
            "RUB" -> "₽"
            "KZT" -> "₸"
            else -> "$"
        }
    }

    fun format(amount: Double, currencyCode: String): String {
        val symbol = symbolFor(currencyCode)
        return symbol + String.format("%.2f", amount)
    }

    fun formatSigned(amount: Double, currencyCode: String): String {
        val sign = if (amount < 0) "-" else ""
        val symbol = symbolFor(currencyCode)
        return sign + symbol + String.format("%.2f", abs(amount))
    }

    fun formatDelta(amount: Double, currencyCode: String): String {
        val sign = when {
            amount > 0 -> "+"
            amount < 0 -> "-"
            else -> ""
        }
        val symbol = symbolFor(currencyCode)
        return sign + symbol + String.format("%.2f", abs(amount))
    }
}
