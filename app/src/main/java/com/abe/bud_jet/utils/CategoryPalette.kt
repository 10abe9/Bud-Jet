package com.abe.bud_jet.utils

/** Single source of truth for category colors so every screen shows the same color. */
object CategoryPalette {

    val colors: List<String> = listOf(
        "#F59E0B",
        "#3B82F6",
        "#10B981",
        "#8B5CF6",
        "#EF4444",
        "#06B6D4",
        "#F97316",
        "#84CC16",
        "#EC4899",
        "#6366F1"
    )

    private val hexColor = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    /** Stored color when it is valid, otherwise a stable fallback derived from the category id. */
    fun colorFor(categoryId: Long?, storedHex: String?): String {
        if (storedHex != null && hexColor.matches(storedHex)) return storedHex
        val index = ((categoryId ?: 0L) % colors.size).toInt().let { if (it < 0) it + colors.size else it }
        return colors[index]
    }
}
