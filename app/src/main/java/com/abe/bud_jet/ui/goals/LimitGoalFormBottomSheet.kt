package com.abe.bud_jet.ui.goals

import com.abe.bud_jet.utils.AmountParser
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetLimitGoalFormBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LimitGoalFormBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetLimitGoalFormBinding? = null
    private val binding get() = _binding!!

    private data class ChipCategory(
        val id: Long,
        val name: String,
        val color: String?
    )

    private var categories: List<ChipCategory> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLimitGoalFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCategories()
        binding.btnSaveCategoryLimit.setOnClickListener {
            val selectedCategoryId = binding.chipCategories.checkedChipIds.firstOrNull()
                ?.let { chipId -> binding.chipCategories.findViewById<Chip>(chipId)?.tag as? Long }
            val amount = AmountParser.parse(binding.etLimitAmount.text?.toString())

            if (selectedCategoryId == null) {
                binding.tvCategoryError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            binding.tvCategoryError.visibility = View.GONE
            if (amount == null || amount <= 0) {
                binding.etLimitAmount.error = getString(R.string.common_enter_valid_amount)
                return@setOnClickListener
            }

            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putLong(RESULT_CATEGORY_ID, selectedCategoryId)
                    putDouble(RESULT_LIMIT, amount)
                }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadCategories() {
        // View-scoped: the sheet can be dismissed before categories load.
        viewLifecycleOwner.lifecycleScope.launch {
            val context = context ?: return@launch
            val loaded = withContext(Dispatchers.IO) {
                FinanceRepositoryProvider.get(context)
                    .observeCategoriesByType(false)
                    .first()
                    .map { ChipCategory(id = it.id, name = it.name, color = it.color) }
            }
            categories = loaded
            binding.chipCategories.removeAllViews()
            categories.forEach { category ->
                val chipColor = parseCategoryColor(category.color)
                val chip = Chip(requireContext()).apply {
                    text = category.name
                    isCheckable = true
                    isClickable = true
                    isCheckedIconVisible = false
                    setEnsureMinTouchTargetSize(false)
                    tag = category.id
                    chipStrokeWidth = 2f
                    chipStrokeColor = ColorStateList.valueOf(chipColor)
                    chipBackgroundColor = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(
                            chipColor,
                            ColorUtils.setAlphaComponent(chipColor, 20)
                        )
                    )
                    setTextColor(
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf()
                            ),
                            intArrayOf(Color.WHITE, chipColor)
                        )
                    )
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
                }
                binding.chipCategories.addView(chip)
            }

            val preselectedId = arguments?.getLong(ARG_CATEGORY_ID)?.takeIf { it > 0L }
            if (preselectedId != null) {
                for (i in 0 until binding.chipCategories.childCount) {
                    val chip = binding.chipCategories.getChildAt(i) as? Chip ?: continue
                    if ((chip.tag as? Long) == preselectedId) {
                        chip.isChecked = true
                        break
                    }
                }
            }

            val amount = arguments?.getDouble(ARG_LIMIT) ?: 0.0
            if (amount > 0) binding.etLimitAmount.setText(AmountParser.toEditable(amount))
        }
    }

    companion object {
        const val RESULT_KEY = "limit_goal_form_result"
        const val RESULT_CATEGORY_ID = "result_category_id"
        const val RESULT_LIMIT = "result_limit"

        private const val ARG_CATEGORY_ID = "arg_category_id"
        private const val ARG_LIMIT = "arg_limit"

        fun newInstance(categoryId: Long?, limitAmount: Double?): LimitGoalFormBottomSheet {
            return LimitGoalFormBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CATEGORY_ID, categoryId ?: 0L)
                    putDouble(ARG_LIMIT, limitAmount ?: 0.0)
                }
            }
        }
    }

    private fun parseCategoryColor(rawColor: String?): Int {
        return try {
            if (rawColor.isNullOrBlank()) {
                ContextCompat.getColor(requireContext(), R.color.brand_primary)
            } else {
                Color.parseColor(rawColor)
            }
        } catch (_: IllegalArgumentException) {
            ContextCompat.getColor(requireContext(), R.color.brand_primary)
        }
    }
}
