package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.databinding.BottomSheetFilterOperationsBinding
import com.google.android.material.chip.Chip
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import android.util.TypedValue
import com.abe.bud_jet.utils.CategoryPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FilterOperationsBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetFilterOperationsBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }
    private var categoriesJob: Job? = null

    private var selectedType: OperationsTypeFilter = OperationsTypeFilter.ALL
    private var selectedCategoryId: Long? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFilterOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedType = requireArguments().getInt(ARG_TYPE_ORDINAL, OperationsTypeFilter.ALL.ordinal)
            .let { OperationsTypeFilter.entries[it] }
        val initialCategoryId = requireArguments().getLong(ARG_CATEGORY_ID, -1L)
        selectedCategoryId = initialCategoryId.takeIf { it >= 0L }

        renderTypeSelection()
        renderCategoriesForCurrentType()

        binding.chipGroupType.setOnCheckedChangeListener { _, checkedId ->
            val newType = when (checkedId) {
                binding.chipTypeIncome.id -> OperationsTypeFilter.INCOME
                binding.chipTypeExpense.id -> OperationsTypeFilter.EXPENSE
                else -> OperationsTypeFilter.ALL
            }
            selectedType = newType
            // Clear category selection when changing type to avoid "hidden" selections.
            selectedCategoryId = null
            renderCategoriesForCurrentType()
        }

        binding.btnClear.setOnClickListener {
            selectedType = OperationsTypeFilter.ALL
            selectedCategoryId = null
            renderTypeSelection()
            renderCategoriesForCurrentType()
        }

        binding.btnApply.setOnClickListener {
            val checkedCategoryId = binding.chipGroupCategories.checkedChipId
                .takeIf { it != View.NO_ID }
                ?.let { chipId ->
                    val chip = binding.chipGroupCategories.findViewById<Chip>(chipId)
                    chip.tag as? Long
                }

            val categoryIdToSend = checkedCategoryId ?: selectedCategoryId
            val result = bundleOf(
                ARG_TYPE_ORDINAL to selectedType.ordinal,
                ARG_CATEGORY_ID to (categoryIdToSend ?: -1L)
            )
            parentFragmentManager.setFragmentResult(RESULT_KEY, result)
            dismissAllowingStateLoss()
        }

        binding.chipGroupCategories.setOnCheckedChangeListener { _, _ ->
            val checkedId = binding.chipGroupCategories.checkedChipId
            if (checkedId == View.NO_ID) {
                selectedCategoryId = null
            } else {
                val chip = binding.chipGroupCategories.findViewById<Chip>(checkedId)
                selectedCategoryId = chip.tag as? Long
            }
        }
    }

    private fun renderTypeSelection() {
        val chipId = when (selectedType) {
            OperationsTypeFilter.ALL -> binding.chipTypeAll.id
            OperationsTypeFilter.INCOME -> binding.chipTypeIncome.id
            OperationsTypeFilter.EXPENSE -> binding.chipTypeExpense.id
        }
        binding.chipGroupType.check(chipId)
    }

    private fun renderCategoriesForCurrentType() {
        categoriesJob?.cancel()
        categoriesJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = when (selectedType) {
                OperationsTypeFilter.ALL -> repository.observeCategories()
                OperationsTypeFilter.INCOME -> repository.observeCategoriesByType(isIncome = true)
                OperationsTypeFilter.EXPENSE -> repository.observeCategoriesByType(isIncome = false)
            }

            flow.collect { categories ->
                binding.chipGroupCategories.removeAllViews()
                categories.forEach { category ->
                    binding.chipGroupCategories.addView(buildCategoryChip(category))
                }

                // if previously selected category exists in the new list, restore it
                selectedCategoryId?.let { restoreId ->
                    val toSelect = categories.firstOrNull { it.id == restoreId }?.id ?: return@let
                    val viewToCheck = findChipByTag(binding.chipGroupCategories, toSelect)
                    viewToCheck?.id?.let { binding.chipGroupCategories.check(it) }
                }
            }
        }
    }

    private fun findChipByTag(chipGroup: com.google.android.material.chip.ChipGroup, tagValue: Long): Chip? {
        for (i in 0 until chipGroup.childCount) {
            val child = chipGroup.getChildAt(i)
            val chip = child as? Chip ?: continue
            val chipTag = chip.tag as? Long
            if (chipTag == tagValue) return chip
        }
        return null
    }

    private fun buildCategoryChip(category: CategoryEntity): Chip {
        val chip = Chip(requireContext()).apply {
            text = category.name
            tag = category.id
            isCheckable = true
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.card)
            )

            // Important: without explicit stroke width the border can be effectively invisible.
            chipStrokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1f,
                resources.displayMetrics
            )

            chipCornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                resources.displayMetrics
            )
        }

        val colorHex = CategoryPalette.colorFor(category.id, category.color)

        val parsed = Color.parseColor(colorHex)
        chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(parsed)

        return chip
    }

    override fun onDestroyView() {
        super.onDestroyView()
        categoriesJob?.cancel()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "operations_filter_result"
        private const val ARG_TYPE_ORDINAL = "arg_type_ordinal"
        private const val ARG_CATEGORY_ID = "arg_category_id"

        fun newInstance(
            type: OperationsTypeFilter,
            categoryId: Long?
        ): FilterOperationsBottomSheet {
            return FilterOperationsBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TYPE_ORDINAL, type.ordinal)
                    putLong(ARG_CATEGORY_ID, categoryId ?: -1L)
                }
            }
        }
    }
}

