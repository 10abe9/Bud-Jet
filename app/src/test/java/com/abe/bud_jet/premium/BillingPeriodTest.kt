package com.abe.bud_jet.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingPeriodTest {

    @Test
    fun parsesPlayPeriods() {
        assertEquals(30, BillingPeriod.toDays("P1M"))
        assertEquals(30, BillingPeriod.toDays("P30D"))
        assertEquals(7, BillingPeriod.toDays("P1W"))
        assertEquals(365, BillingPeriod.toDays("P1Y"))
        assertNull(BillingPeriod.toDays("garbage"))
    }
}
