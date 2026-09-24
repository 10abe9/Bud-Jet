package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.BottomSheetAddTransactionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.ui.common.CategoryChips
import com.abe.bud_jet.utils.AmountParser
import com.abe.bud_jet.utils.CurrencyFormatter
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
    private var originalAmount: Double = 0.0
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
        originalAmount = requireArguments().getDouble(ARG_AMOUNT)
        val note = requireArguments().getString(ARG_NOTE)
        val categoryId = requireArguments().getLong(ARG_CATEGORY_ID).takeIf { it != -1L }
        originalTimestamp = requireArguments().getLong(ARG_TIMESTAMP)
        isIncomeCurrent = requireArguments().getBoolean(ARG_IS_INCOME)
        selectedCategoryId = categoryId

        binding.tvTitle.text = getString(R.string.edit_transaction_title)
        binding.btnSave.text = getString(R.string.edit_transaction_update)
        // toEditable avoids "1.0E7" style output of Double.toString().
        binding.etAmount.setText(AmountParser.toEditable(originalAmount))
        binding.etNote.setText(note.orEmpty())
        binding.toggleType.check(if (isIncomeCurrent) binding.btnIncome.id else binding.btnExpense.id)
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, _ ->
            selectedCategoryId = CategoryChips.selectedId(group)
        }

        observeCategories()
        setupTypeToggle()
        setupSave()
        setupDelete()
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.observeCategoriesByType(isIncomeCurrent).collect { categories ->
                CategoryChips.render(binding.chipGroupCategories, categories, selectedCategoryId)
            }
        }
    }

    private fun setupTypeToggle() {
        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val isIncome = checkedId == binding.btnIncome.id
            if (isIncome == isIncomeCurrent) return@addOnButtonCheckedListener
            isIncomeCurrent = isIncome
            selectedCategoryId = null
            observeCategories()
        }
    }

    private fun setupSave() {
        binding.btnSave.setOnClickListener {
            val amount = AmountParser.parse(binding.etAmount.text?.toString())
            if (amount == null || amount <= 0) {
                binding.etAmount.error = getString(R.string.edit_transaction_invalid_amount)
                return@setOnClickListener
            }
            val note = binding.etNote.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val categoryId = CategoryChips.selectedId(binding.chipGroupCategories)
            binding.btnSave.isEnabled = false

            lifecycleScope.launch {
                repository.updateTransaction(
                    id = transactionId,
                    amount = amount,
                    isIncome = isIncomeCurrent,
                    categoryId = categoryId,
                    note = note,
                    timestamp = originalTimestamp
                )
                dismissAllowingStateLoss()
            }
        }
    }

    private fun setupDelete() {
        binding.btnDelete.visibility = View.VISIBLE
        binding.btnDelete.setOnClickListener {
            val isIncome = requireArguments().getBoolean(ARG_IS_INCOME)
            val currencyCode = PreferenceManager.getInstance(requireContext()).getCurrencyCode()
            // Same confirmation as the long-press delete in the list.
            DeleteTransactionBottomSheet.newInstance(
                id = transactionId,
                message = getString(
                    R.string.operations_delete_transaction_message,
                    if (isIncome) "+" else "-",
                    CurrencyFormatter.format(originalAmount, currencyCode)
                )
            ).show(parentFragmentManager, "delete_transaction")
            dismissAllowingStateLoss()
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
