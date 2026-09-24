package com.abe.bud_jet.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultCategoriesTest {

    @Test
    fun recognizesBuiltInNamesInEveryLanguage() {
        assertEquals("food", DefaultCategories.keyForName("Еда", isIncome = false))
        assertEquals("food", DefaultCategories.keyForName("Comida", isIncome = false))
        assertEquals("health", DefaultCategories.keyForName("zdrowie", isIncome = false))
        assertEquals("salary", DefaultCategories.keyForName(" Зарплата ", isIncome = true))
        assertEquals("freelance", DefaultCategories.keyForName("Freelancer", isIncome = true))
    }

    @Test
    fun typeMustMatch() {
        assertNull(DefaultCategories.keyForName("Salary", isIncome = false))
        assertNull(DefaultCategories.keyForName("Food", isIncome = true))
    }

    @Test
    fun userCategoriesAreNotRecognized() {
        assertNull(DefaultCategories.keyForName("Кафе", isIncome = false))
    }

    @Test
    fun everyDefinitionHasKnownNames() {
        DefaultCategories.all.forEach { definition ->
            assert(DefaultCategories.knownNames[definition.key].orEmpty().isNotEmpty()) { definition.key }
            assertEquals(definition.isIncome, DefaultCategories.isIncome(definition.key))
        }
    }
}
