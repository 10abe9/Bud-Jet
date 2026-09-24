package com.abe.bud_jet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    @Test
    fun parsesDotAndCommaDecimals() {
        assertEquals(12.5, AmountParser.parse("12.5")!!, 0.0)
        assertEquals(12.5, AmountParser.parse("12,5")!!, 0.0)
        assertEquals(0.92, AmountParser.parse("0,92")!!, 0.0)
    }

    @Test
    fun parsesGroupedThousands() {
        assertEquals(1250.5, AmountParser.parse("1 250,50")!!, 0.0)
        assertEquals(1250.5, AmountParser.parse("1\u00A0250,50")!!, 0.0)
        assertEquals(1250.5, AmountParser.parse("1,250.50")!!, 0.0)
        assertEquals(1250.5, AmountParser.parse("1.250,50")!!, 0.0)
        assertEquals(1250000.0, AmountParser.parse("1,250,000")!!, 0.0)
    }

    @Test
    fun parsesSignedAndEdgeValues() {
        assertEquals(-3.5, AmountParser.parse("-3,5")!!, 0.0)
        assertEquals(5.0, AmountParser.parse(" 5 ")!!, 0.0)
        assertEquals(0.5, AmountParser.parse(",5")!!, 0.0)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(AmountParser.parse(null))
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("abc"))
        assertNull(AmountParser.parse(","))
        assertNull(AmountParser.parse("1-2"))
    }

    @Test
    fun editableHasNoExponent() {
        assertEquals("10000000", AmountParser.toEditable(1.0E7))
        assertEquals("12.5", AmountParser.toEditable(12.5))
        assertEquals("0.9235", AmountParser.toEditable(0.923456, maxFractionDigits = 4))
        assertEquals("0", AmountParser.toEditable(0.0))
    }

    @Test
    fun editableRoundTripsThroughParser() {
        listOf(0.01, 99.99, 1234567.89).forEach { value ->
            assertEquals(value, AmountParser.parse(AmountParser.toEditable(value))!!, 0.0)
        }
    }
}
