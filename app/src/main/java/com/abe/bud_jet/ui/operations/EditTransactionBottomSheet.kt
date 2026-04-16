package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.databinding.BottomSheetAddTransactionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EditTransactionBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetAddTransactionBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }
    private var categoriesJob: Job? = null

    private var selectedCategoryId: Long? = null
    private var transactionId: Long = 0L
    private var originalTimestamp: Long = 0L
    private var isIncomeCurrent: Boolean = false

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

        transactionId = requireArguments().getLong(ARG_ID)
        val amount = requireArguments().getDouble(ARG_AMOUNT)
        val note = requireArguments().getString(ARG_NOTE)
        val categoryId = requireArguments().getLong(ARG_CATEGORY_ID).takeIf { it != -1L }
        originalTimestamp = requireArguments().getLong(ARG_TIMESTAMP)
        isIncomeCurrent = requireArguments().getBoolean(ARG_IS_INCOME)
        selectedCategoryId = categoryId

        binding.tvTitle.text = getString(R.string.edit_transaction_title)
        binding.btnSave.text = getString(R.string.edit_transaction_update)
        binding.etAmount.setText(amount.toString())
        binding.etNote.setText(note.orEmpty())
        binding.toggleType.check(if (isIncomeCurrent) binding.btnIncome.id else binding.btnExpense.id)

        observeCategories()
        setupTypeToggle()
        setupSave()
        setupDelete()
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.observeCategoriesByType(isIncomeCurrent).collect { categories ->
                renderCategories(categories)
            }
        }
    }

    private fun renderCategories(categories: List<CategoryEntity>) {
        binding.chipGroupCategories.removeAllViews()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                tag = category.id
                isCheckable = true
                if (selectedCategoryId == category.id) isChecked = true
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun setupTypeToggle() {
        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isIncomeCurrent = checkedId == binding.btnIncome.id
            selectedCategoryId = null
            observeCategories()
        }
    }

    private fun setupSave() {
        binding.btnSave.setOnClickListener {
            val amount = binding.etAmount.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.etAmount.error = getString(R.string.edit_transaction_invalid_amount)
                return@setOnClickListener
            }
            val note = binding.etNote.text?.toString()?.takeIf { it.isNotBlank() }
            val checkedId = binding.chipGroupCategories.checkedChipId
            selectedCategoryId = binding.chipGroupCategories.findViewById<Chip?>(checkedId)?.tag as? Long

            viewLifecycleOwner.lifecycleScope.launch {
                repository.updateTransaction(
                    id = transactionId,
                    amount = amount,
                    isIncome = isIncomeCurrent,
                    categoryId = selectedCategoryId,
                    note = note,
                    timestamp = originalTimestamp
                )
                dismissAllowingStateLoss()
            }
        }
    }

    private fun setupDelete() {
        val deleteChip = Chip(requireContext()).apply {
            text = getString(R.string.common_delete)
            isCheckable = false
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.card)
            )
            chipStrokeColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.finance_expense)
            )
            setTextColor(requireContext().getColor(R.color.finance_expense))
        }
        binding.chipGroupCategories.addView(deleteChip)
        deleteChip.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val deleted = repository.deleteTransaction(transactionId)
                Toast.makeText(
                    requireContext(),
                    if (deleted) getString(R.string.edit_transaction_transaction_deleted)
                    else getString(R.string.edit_transaction_unable_to_delete),
                    Toast.LENGTH_SHORT
                ).show()
                if (deleted) dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        categoriesJob?.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_IS_INCOME = "arg_is_income"
        private const val ARG_CATEGORY_ID = "arg_category_id"
        private const val ARG_NOTE = "arg_note"
        private const val ARG_TIMESTAMP = "arg_timestamp"

        fun newInstance(
            id: Long,
            amount: Double,
            isIncome: Boolean,
            categoryId: Long?,
            note: String?,
            timestamp: Long
        ): EditTransactionBottomSheet {
            return EditTransactionBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, id)
                    putDouble(ARG_AMOUNT, amount)
                    putBoolean(ARG_IS_INCOME, isIncome)
                    putLong(ARG_CATEGORY_ID, categoryId ?: -1L)
                    putString(ARG_NOTE, note)
                    putLong(ARG_TIMESTAMP, timestamp)
                }
            }
        }
    }
}
