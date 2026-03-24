package com.abe.bud_jet.ui.dashboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.BottomSheetAddCategoryBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AddCategoryBottomSheet : BottomSheetDialogFragment() {

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

        binding.btnSaveCategory.setOnClickListener {
            val rawName = binding.etCategoryName.text?.toString().orEmpty()
            val normalized = rawName.trim().replace(Regex("\\s+"), " ")
            val isIncome = binding.toggleType.checkedButtonId == binding.btnIncome.id

            viewLifecycleOwner.lifecycleScope.launch {
                when (repository.addCustomCategory(normalized, isIncome = isIncome)) {
                    FinanceRepository.AddCategoryResult.SUCCESS -> dismissAllowingStateLoss()
                    FinanceRepository.AddCategoryResult.EMPTY_NAME -> {
                        binding.etCategoryName.error = "Enter category name"
                    }
                    FinanceRepository.AddCategoryResult.DUPLICATE -> {
                        Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
                    }
                    FinanceRepository.AddCategoryResult.LIMIT_REACHED -> {
                        val limit = if (isIncome) {
                            FinanceRepository.MAX_INCOME_CATEGORIES
                        } else {
                            FinanceRepository.MAX_EXPENSE_CATEGORIES
                        }
                        Toast.makeText(
                            requireContext(),
                            "Limit reached: max $limit categories",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?.background = ColorDrawable(Color.TRANSPARENT)
        (view?.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
