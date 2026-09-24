package com.abe.bud_jet.ui.dashboard

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.BottomSheetAddCategoryBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
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
        // Selected half is tinted by the Segment style's checked-state colors.
        binding.toggleType.check(binding.btnExpense.id)

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
}
