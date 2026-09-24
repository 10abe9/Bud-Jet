package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.databinding.BottomSheetAddTransactionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.ui.common.CategoryChips
import com.abe.bud_jet.utils.AmountParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AddTransactionBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetAddTransactionBinding? = null
    private val binding get() = _binding!!

    private var categoriesJob: Job? = null

    private val repository by lazy {
        FinanceRepositoryProvider.get(requireContext())
    }

    private var isIncomeCurrent: Boolean = false
    private var selectedCategoryId: Long? = null
    private var isSaving = false

    /** Set when confirming a payment captured from a notification. */
    private var pendingId: Long? = null

    private fun prefillFromArguments() {
        val args = arguments ?: return
        args.getDouble(ARG_AMOUNT, 0.0).takeIf { it > 0.0 }?.let {
            binding.etAmount.setText(AmountParser.toEditable(it))
        }
        args.getString(ARG_NOTE)?.let { binding.etNote.setText(it) }
    }

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

        selectedCategoryId = arguments?.getLong(ARG_CATEGORY_ID, -1L)?.takeIf { it >= 0L }
        pendingId = arguments?.getLong(ARG_PENDING_ID, -1L)?.takeIf { it >= 0L }
        if (pendingId != null) binding.tvTitle.text = getString(R.string.capture_confirm_title)
        if (savedInstanceState == null) prefillFromArguments()
        setupTypeToggle()
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, _ ->
            selectedCategoryId = CategoryChips.selectedId(group)
        }
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
            val isIncome = checkedId == binding.btnIncome.id
            if (isIncome == isIncomeCurrent) return@addOnButtonCheckedListener
            isIncomeCurrent = isIncome
            // Categories of the other type cannot stay selected.
            selectedCategoryId = null
            observeCategories()
        }
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        // Tied to the view lifecycle so emissions never reach a destroyed binding.
        categoriesJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.observeCategoriesByType(isIncomeCurrent).collect { list ->
                CategoryChips.render(binding.chipGroupCategories, list, selectedCategoryId)
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            if (isSaving) return@setOnClickListener
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            if (amountText.isEmpty()) {
                binding.etAmount.error = getString(R.string.common_enter_amount)
                return@setOnClickListener
            }

            val amount = AmountParser.parse(amountText)
            if (amount == null || amount <= 0) {
                binding.etAmount.error = getString(R.string.common_invalid_amount)
                return@setOnClickListener
            }

            val type = if (isIncomeCurrent) TransactionType.INCOME else TransactionType.EXPENSE
            val note = binding.etNote.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val timestamp = System.currentTimeMillis()
            val categoryId = CategoryChips.selectedId(binding.chipGroupCategories)

            isSaving = true
            binding.btnSave.isEnabled = false
            val pending = pendingId
            lifecycleScope.launch {
                if (pending != null) {
                    FinanceRepositoryProvider.capture(requireContext()).confirmPending(
                        pendingId = pending,
                        amount = amount,
                        isIncome = type == TransactionType.INCOME,
                        categoryId = categoryId,
                        note = note
                    )
                } else if (type == TransactionType.INCOME) {
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
        categoriesJob?.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_IS_INCOME_DEFAULT = "is_income_default"
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_PENDING_ID = "pending_id"
        private const val ARG_AMOUNT = "amount"
        private const val ARG_NOTE = "note"

        /** Opens the form prefilled from a captured notification the parser was unsure about. */
        fun newPendingConfirmation(
            pendingId: Long,
            amount: Double?,
            isIncome: Boolean,
            categoryId: Long?,
            note: String?
        ): AddTransactionBottomSheet {
            return newInstance(isIncomeDefault = isIncome, categoryId = categoryId).apply {
                requireArguments().apply {
                    putLong(ARG_PENDING_ID, pendingId)
                    putDouble(ARG_AMOUNT, amount ?: 0.0)
                    putString(ARG_NOTE, note)
                }
            }
        }

        fun newInstance(isIncomeDefault: Boolean, categoryId: Long? = null): AddTransactionBottomSheet {
            return AddTransactionBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_INCOME_DEFAULT, isIncomeDefault)
                    putLong(ARG_CATEGORY_ID, categoryId ?: -1L)
                }
            }
        }
    }
}
