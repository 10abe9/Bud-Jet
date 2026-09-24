package com.abe.bud_jet.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    /** Currencies whose symbol is conventionally written after the number ("1 250,00 ₽"). */
    private val suffixCurrencies = setOf("RUB", "PLN", "KZT")

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
            else -> runCatching { Currency.getInstance(code.uppercase()).getSymbol(Locale.getDefault()) }
                .getOrDefault(code.uppercase())
        }
    }

    fun format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        return withSymbol(formatNumber(amount, locale), currencyCode)
    }

    fun formatSigned(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val sign = if (amount < 0) "-" else ""
        return sign + withSymbol(formatNumber(abs(amount), locale), currencyCode)
    }

    fun formatDelta(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val sign = when {
            amount > 0 -> "+"
            amount < 0 -> "-"
            else -> ""
        }
        return sign + withSymbol(formatNumber(abs(amount), locale), currencyCode)
    }

    /** Short form for tight spaces: "12.5K ₽", "$1.2M". Falls back to [format] below 1000. */
    fun formatCompact(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val absValue = abs(amount)
        val sign = if (amount < 0) "-" else ""
        val (value, suffix) = when {
            absValue >= 1_000_000_000 -> absValue / 1_000_000_000.0 to "B"
            absValue >= 1_000_000 -> absValue / 1_000_000.0 to "M"
            absValue >= 1_000 -> absValue / 1_000.0 to "K"
            else -> return format(amount, currencyCode, locale)
        }
        val number = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale)).format(value) + suffix
        return sign + withSymbol(number, currencyCode)
    }

    private fun formatNumber(amount: Double, locale: Locale): String {
        return DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale)).format(amount)
    }

    private fun withSymbol(number: String, currencyCode: String): String {
        val symbol = symbolFor(currencyCode)
        // Non-breaking space keeps the symbol on the same line as the number.
        return if (currencyCode.uppercase() in suffixCurrencies) "$number\u00A0$symbol" else symbol + number
    }
}
