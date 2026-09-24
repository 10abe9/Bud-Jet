package com.abe.bud_jet.ui.common

import android.content.res.ColorStateList
import android.graphics.Color
import com.abe.bud_jet.R
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.utils.CategoryPalette
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** Renders selectable category chips with the category's own color, keeping the selection. */
object CategoryChips {

    fun render(chipGroup: ChipGroup, categories: List<CategoryEntity>, selectedId: Long?) {
        val context = chipGroup.context
        chipGroup.removeAllViews()
        categories.sortedBy { it.id }.forEach { category ->
            val chip = Chip(context).apply {
                id = android.view.View.generateViewId()
                text = category.name
                tag = category.id
                isCheckable = true
                chipBackgroundColor = ColorStateList.valueOf(context.getColor(R.color.card))
                chipStrokeWidth = 2f
                chipStrokeColor = ColorStateList.valueOf(
                    Color.parseColor(CategoryPalette.colorFor(category.id, category.color))
                )
                setTextColor(context.getColor(R.color.text_primary))
            }
            chipGroup.addView(chip)
            if (category.id == selectedId) chipGroup.check(chip.id)
        }
    }

    fun selectedId(chipGroup: ChipGroup): Long? {
        val checkedId = chipGroup.checkedChipId
        if (checkedId == android.view.View.NO_ID) return null
        return chipGroup.findViewById<Chip>(checkedId)?.tag as? Long
    }
}
