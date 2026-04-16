package com.abe.bud_jet.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.BottomSheetAddCategoryBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AddCategoryBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetAddCategoryBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etCategoryName.filters =
            arrayOf(InputFilter.LengthFilter(FinanceRepository.MAX_CATEGORY_NAME_LENGTH))
        binding.toggleType.check(binding.btnExpense.id)
        applyTypeToggleStyle(binding.btnExpense.id)
        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            applyTypeToggleStyle(checkedId)
        }

        binding.btnSaveCategory.setOnClickListener {
            val rawName = binding.etCategoryName.text?.toString().orEmpty()
            val normalized = rawName.trim().replace(Regex("\\s+"), " ")
            val isIncome = binding.toggleType.checkedButtonId == binding.btnIncome.id

            viewLifecycleOwner.lifecycleScope.launch {
                when (repository.addCustomCategory(normalized, isIncome = isIncome)) {
                    FinanceRepository.AddCategoryResult.SUCCESS -> dismissAllowingStateLoss()
                    FinanceRepository.AddCategoryResult.EMPTY_NAME -> {
                        binding.etCategoryName.error = getString(R.string.common_enter_category_name)
                    }
                    FinanceRepository.AddCategoryResult.DUPLICATE -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dashboard_category_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    FinanceRepository.AddCategoryResult.LIMIT_REACHED -> {
                        val limit = if (isIncome) {
                            FinanceRepository.MAX_INCOME_CATEGORIES
                        } else {
                            FinanceRepository.MAX_EXPENSE_CATEGORIES
                        }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dashboard_limit_reached_max_categories, limit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTypeToggleStyle(checkedId: Int) {
        val expenseSelected = checkedId == binding.btnExpense.id
        styleTypeButton(
            button = binding.btnExpense,
            selected = expenseSelected,
            accentColorRes = R.color.finance_expense
        )
        styleTypeButton(
            button = binding.btnIncome,
            selected = !expenseSelected,
            accentColorRes = R.color.finance_income
        )
    }

    private fun styleTypeButton(button: MaterialButton, selected: Boolean, accentColorRes: Int) {
        val ctx = requireContext()
        val accent = ContextCompat.getColor(ctx, accentColorRes)
        val border = ContextCompat.getColor(ctx, R.color.border)
        val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
        val card = ContextCompat.getColor(ctx, R.color.card)

        button.strokeWidth = 1
        button.strokeColor = android.content.res.ColorStateList.valueOf(if (selected) accent else border)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) accent else card)
        button.setTextColor(if (selected) Color.WHITE else textPrimary)
    }
}
