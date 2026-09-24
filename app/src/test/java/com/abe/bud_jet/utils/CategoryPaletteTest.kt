package com.abe.bud_jet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPaletteTest {

    @Test
    fun keepsValidStoredColor() {
        assertEquals("#123456", CategoryPalette.colorFor(3, "#123456"))
    }

    @Test
    fun fallbackIsStablePerCategory() {
        assertEquals(CategoryPalette.colors[3], CategoryPalette.colorFor(3, null))
        assertEquals(CategoryPalette.colors[3], CategoryPalette.colorFor(13, "not-a-color"))
        assertEquals(CategoryPalette.colors[0], CategoryPalette.colorFor(null, null))
    }
}
