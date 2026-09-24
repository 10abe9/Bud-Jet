package com.abe.bud_jet.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategorizerTest {

    @Test
    fun guessesBuiltInCategories() {
        assertEquals("transport", MerchantCategorizer.guessDefaultKey("YANDEX*GO"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("PYATEROCHKA 1234"))
        assertEquals("food", MerchantCategorizer.guessDefaultKey("Biedronka"))
        assertEquals("health", MerchantCategorizer.guessDefaultKey("Аптека Ригла"))
        assertNull(MerchantCategorizer.guessDefaultKey("Netflix.com"))
    }

    @Test
    fun merchantKeyIgnoresStoreNumbersAndCase() {
        assertEquals(
            MerchantCategorizer.merchantKey("PYATEROCHKA 1234"),
            MerchantCategorizer.merchantKey("Pyaterochka 5678")
        )
    }
}
