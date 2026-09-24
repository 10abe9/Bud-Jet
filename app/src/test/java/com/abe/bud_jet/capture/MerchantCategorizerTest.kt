package com.abe.bud_jet.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategorizerTest {

    @Test
    fun guessesBuiltInCategories() {
        assertEquals("transport", MerchantCategorizer.guessDefaultKey("UBER *TRIP"))
        assertEquals("transport", MerchantCategorizer.guessDefaultKey("SHELL OIL 57442"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("WHOLE FOODS MKT #10234"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("STARBUCKS STORE 0412"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("WALMART"))
        assertEquals("health", MerchantCategorizer.guessDefaultKey("CVS/PHARMACY #1234"))
        assertNull(MerchantCategorizer.guessDefaultKey("Netflix.com"))
    }

    @Test
    fun avoidsCommonFalsePositives() {
        assertNull(MerchantCategorizer.guessDefaultKey("T-MOBILE AUTOPAY"))
        assertNull(MerchantCategorizer.guessDefaultKey("METROPCS"))
        assertNull(MerchantCategorizer.guessDefaultKey("MARCOS"))
    }

    @Test
    fun uberEatsIsFoodNotTransport() {
        assertEquals("food", MerchantCategorizer.guessDefaultKey("UBER *EATS"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("Uber Eats"))
    }

    @Test
    fun merchantKeyIgnoresStoreNumbersAndCase() {
        assertEquals(
            MerchantCategorizer.merchantKey("PYATEROCHKA 1234"),
            MerchantCategorizer.merchantKey("Pyaterochka 5678")
        )
    }
}
