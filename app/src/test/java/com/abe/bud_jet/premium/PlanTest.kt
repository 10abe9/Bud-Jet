package com.abe.bud_jet.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {

    @Test
    fun noPurchasesIsFree() {
        assertEquals(Tier.FREE, Tier.fromProducts(emptyList()))
        assertEquals(Tier.FREE, Tier.fromProducts(listOf("something_else")))
    }

    @Test
    fun productsMapToTiers() {
        assertEquals(Tier.BASIC, Tier.fromProducts(listOf("bud_jet_base")))
        // The original Premium product is Pro: early subscribers keep the AI assistant.
        assertEquals(Tier.PRO, Tier.fromProducts(listOf("bud_jet_premium")))
    }

    @Test
    fun highestTierWins() {
        // Briefly both are active while Play replaces Basic with Pro.
        assertEquals(Tier.PRO, Tier.fromProducts(listOf("bud_jet_base", "bud_jet_premium")))
    }

    @Test
    fun featuresPerTier() {
        assertFalse(Tier.FREE.hasPaidFeatures)
        assertTrue(Tier.BASIC.hasPaidFeatures)
        assertFalse(Tier.BASIC.hasAiAssistant)
        assertTrue(Tier.PRO.hasPaidFeatures)
        assertTrue(Tier.PRO.hasAiAssistant)
    }

    @Test
    fun parseAndCycle() {
        assertEquals(Tier.BASIC, Tier.parse("BASIC"))
        assertNull(Tier.parse(null))
        assertNull(Tier.parse("premium"))
        assertEquals(Tier.BASIC, Tier.next(Tier.FREE))
        assertEquals(Tier.PRO, Tier.next(Tier.BASIC))
        assertEquals(Tier.FREE, Tier.next(Tier.PRO))
    }
}
