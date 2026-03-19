package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.databinding.BottomSheetAddTransactionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddTransactionBinding? = null
    private val binding get() = _binding!!

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

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

    override fun onStart() {
        super.onStart()

        // Чтобы bottom sheet «поднимался» при открытии клавиатуры
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Фон всего окна делаем прозрачным, чтобы не было системных скруглений поверх нашей карточки
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Убираем стандартный фон нижнего листа, чтобы не было двойных углов за нашей карточкой
        val bottomSheet =
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)

        // Дополнительно убираем фон у родительского контейнера, если он задаёт скругления
        (view?.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
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
        scope.launch {
            repository.observeCategoriesByType(isIncomeCurrent).collect { list ->
                renderCategoryChips(list)
            }
        }
    }

    private fun renderCategoryChips(categories: List<CategoryEntity>) {
        binding.chipGroupCategories.removeAllViews()
        selectedCategoryId = null

        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                tag = category.id
            }
            binding.chipGroupCategories.addView(chip)
        }

        // Chip for adding new category
        val addChip = Chip(requireContext()).apply {
            text = "+ New"
            isCheckable = false
        }
        addChip.setOnClickListener {
            showAddCategoryDialog()
        }
        binding.chipGroupCategories.addView(addChip)
    }

    private fun showAddCategoryDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.hint = "Category name"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isIncomeCurrent) "New income category" else "New expense category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString().orEmpty()
                scope.launch {
                    repository.addCustomCategory(name, isIncomeCurrent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            if (amountText.isEmpty()) {
                binding.etAmount.error = "Enter amount"
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.etAmount.error = "Invalid amount"
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

