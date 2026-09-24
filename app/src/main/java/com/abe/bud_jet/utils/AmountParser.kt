package com.abe.bud_jet.utils

import java.math.BigDecimal

/**
 * Parses user-entered amounts regardless of the keyboard/locale decimal separator.
 * Accepts "12.50", "12,50", "1 250,50", "1,250.50", "1.250,50" and "-3,5".
 */
object AmountParser {

    fun parse(raw: String?): Double? {
        if (raw == null) return null
        val compact = raw.trim()
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace("'", "")
        if (compact.isEmpty()) return null

        val lastDot = compact.lastIndexOf('.')
        val lastComma = compact.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                // The separator that appears last is the decimal one, the other groups thousands.
                if (lastComma > lastDot) {
                    compact.replace(".", "").replace(',', '.')
                } else {
                    compact.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                if (compact.count { it == ',' } > 1) compact.replace(",", "") else compact.replace(',', '.')
            }
            lastDot >= 0 && compact.count { it == '.' } > 1 -> compact.replace(".", "")
            else -> compact
        }

        if (!normalized.matches(Regex("^-?\\d*\\.?\\d*$"))) return null
        if (normalized.none { it.isDigit() }) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Plain editable representation (no exponent, no grouping, '.' separator). */
    fun toEditable(value: Double, maxFractionDigits: Int = 2): String {
        if (!value.isFinite()) return ""
        return BigDecimal.valueOf(value)
            .setScale(maxFractionDigits, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
