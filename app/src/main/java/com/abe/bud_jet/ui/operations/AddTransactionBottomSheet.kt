package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.databinding.BottomSheetAddTransactionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AddTransactionBottomSheet : BaseBottomSheetDialogFragment() {
    private val fixedCategoryPalette = listOf(
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

    private var _binding: BottomSheetAddTransactionBinding? = null
    private val binding get() = _binding!!

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var categoriesJob: Job? = null

    private val repository by lazy {
        FinanceRepositoryProvider.get(requireContext())
    }

    private var isIncomeCurrent: Boolean = false
    private var selectedCategoryId: Long? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTypeToggle()
        observeCategories()
        setupSaveButton()
    }

    override fun configureBottomSheet(bottomSheet: FrameLayout?) {
        val margin = (16 * resources.displayMetrics.density).toInt()
        (bottomSheet?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.leftMargin = margin
            lp.rightMargin = margin
            lp.bottomMargin = 0
            bottomSheet.layoutParams = lp
            bottomSheet.requestLayout()
        }
    }

    private fun setupTypeToggle() {
        val isIncomeDefault = arguments?.getBoolean(ARG_IS_INCOME_DEFAULT, false) ?: false
        isIncomeCurrent = isIncomeDefault
        val targetId = if (isIncomeCurrent) binding.btnIncome.id else binding.btnExpense.id
        binding.toggleType.check(targetId)

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isIncomeCurrent = checkedId == binding.btnIncome.id
            observeCategories()
        }
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        categoriesJob = scope.launch {
            repository.observeCategoriesByType(isIncomeCurrent).collect { list ->
                renderCategoryChips(list)
            }
        }
    }

    private fun renderCategoryChips(categories: List<CategoryEntity>) {
        binding.chipGroupCategories.removeAllViews()
        selectedCategoryId = null

        categories
            .sortedBy { it.id }
            .forEachIndexed { index, category ->
            val colorHex = category.color
                ?.takeIf { it.startsWith("#") }
                ?: fixedCategoryPalette[index % fixedCategoryPalette.size]
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                tag = category.id
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(com.abe.bud_jet.R.color.card)
                )
                runCatching {
                    val parsed = Color.parseColor(colorHex)
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(parsed)
                    setTextColor(requireContext().getColor(com.abe.bud_jet.R.color.text_primary))
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            if (amountText.isEmpty()) {
                binding.etAmount.error = getString(R.string.common_enter_amount)
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.etAmount.error = getString(R.string.common_invalid_amount)
                return@setOnClickListener
            }

            val isIncome = binding.toggleType.checkedButtonId == binding.btnIncome.id
            val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

            val note = binding.etNote.text?.toString()?.takeIf { it.isNotBlank() }
            val timestamp = System.currentTimeMillis()

            val checkedId = binding.chipGroupCategories.checkedChipId
            selectedCategoryId = binding.chipGroupCategories.findViewById<Chip?>(checkedId)?.tag as? Long
            val categoryId: Long? = selectedCategoryId

            scope.launch {
                if (type == TransactionType.INCOME) {
                    repository.addIncome(
                        amount = amount,
                        categoryId = categoryId,
                        note = note,
                        timestamp = timestamp
                    )
                } else {
                    repository.addExpense(
                        amount = amount,
                        categoryId = categoryId,
                        note = note,
                        timestamp = timestamp
                    )
                }
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val ARG_IS_INCOME_DEFAULT = "is_income_default"

        fun newInstance(isIncomeDefault: Boolean): AddTransactionBottomSheet {
            return AddTransactionBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_INCOME_DEFAULT, isIncomeDefault)
                }
            }
        }
    }
}

