package com.abe.bud_jet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CurrencyFormatterTest {

    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun groupsThousandsForLocale() {
        assertEquals("$1,250.50", CurrencyFormatter.format(1250.5, "USD", Locale.US))
        assertEquals("€0.00", CurrencyFormatter.format(0.0, "EUR", Locale.US))
    }

    @Test
    fun suffixCurrenciesGoAfterNumber() {
        val formatted = CurrencyFormatter.format(1250.5, "RUB", ru)
        // JDKs differ in the grouping space (NBSP vs narrow NBSP), so normalize it.
        assertEquals("1\u00A0250,50\u00A0₽", formatted.replace('\u202F', '\u00A0'))
        assertEquals("12.00\u00A0zł", CurrencyFormatter.format(12.0, "PLN", Locale.US))
    }

    @Test
    fun signs() {
        assertEquals("-$5.00", CurrencyFormatter.formatSigned(-5.0, "USD", Locale.US))
        assertEquals("+$5.00", CurrencyFormatter.formatDelta(5.0, "USD", Locale.US))
        assertEquals("-$5.00", CurrencyFormatter.formatDelta(-5.0, "USD", Locale.US))
        assertEquals("$0.00", CurrencyFormatter.formatDelta(0.0, "USD", Locale.US))
    }

    @Test
    fun compact() {
        assertEquals("$999.00", CurrencyFormatter.formatCompact(999.0, "USD", Locale.US))
        assertEquals("$12.5K", CurrencyFormatter.formatCompact(12_500.0, "USD", Locale.US))
        assertEquals("$1.2M", CurrencyFormatter.formatCompact(1_200_000.0, "USD", Locale.US))
    }

    @Test
    fun unknownCurrencyFallsBackToCode() {
        assertEquals("XYZ1.00", CurrencyFormatter.format(1.0, "XYZ", Locale.US))
    }
}
