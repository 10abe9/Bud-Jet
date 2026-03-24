package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.adapters.TransactionsAdapter
import com.abe.bud_jet.databinding.FragmentOperationsBinding
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.datepicker.MaterialDatePicker

class OperationsFragment : Fragment() {

    private var _binding: FragmentOperationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TransactionsAdapter
    private var pendingFocusTransactionId: Long? = null

    private val viewModel: OperationsViewModel by viewModels {
        OperationsViewModelFactory(
            FinanceRepositoryProvider.get(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pendingFocusTransactionId = arguments?.getLong(ARG_FOCUS_TRANSACTION_ID)?.takeIf { it > 0L }
            ?: findNavController().getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
                .savedStateHandle
                .get<Long>("focus_transaction_id")
        setupRecycler()
        setupClicks()
        observeData()
    }

    private fun setupRecycler() {
        adapter = TransactionsAdapter { transaction ->
            EditTransactionBottomSheet.newInstance(
                id = transaction.id,
                amount = transaction.amount,
                isIncome = transaction.isIncome,
                categoryId = transaction.categoryId,
                note = transaction.note,
                timestamp = transaction.timestamp
            ).show(parentFragmentManager, "edit_transaction")
        }

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
    }

    private fun observeData() {
        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state ->
            adapter.submitList(state.transactions)
            binding.tvExpensesAmount.text = state.expensesFormatted
            binding.tvExpensesPeriod.text = state.periodLabel
            when (state.selectedPeriod) {
                OperationsPeriod.WEEK -> binding.chipGroupFilters.check(binding.chipWeek.id)
                OperationsPeriod.MONTH -> binding.chipGroupFilters.check(binding.chipMonth.id)
                OperationsPeriod.YEAR -> binding.chipGroupFilters.check(binding.chipYear.id)
                OperationsPeriod.CUSTOM -> binding.chipGroupFilters.check(binding.chipCustom.id)
            }
            focusIfNeeded()
        }
    }

    private fun focusIfNeeded() {
        val targetId = pendingFocusTransactionId ?: return
        val position = adapter.findPositionById(targetId)
        if (position >= 0) {
            binding.rvTransactions.scrollToPosition(position)
            binding.rvTransactions.postDelayed({
                adapter.highlightTransaction(targetId)
            }, 120L)
            pendingFocusTransactionId = null
            findNavController().getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
                .savedStateHandle
                .remove<Long>("focus_transaction_id")
        }
    }

    private fun setupClicks() {

        binding.btnSearch.setOnClickListener {
            // TODO: добавить UI поиска (диалог/поисковую строку)
        }

        binding.btnFilter.setOnClickListener {
            // TODO
        }

        binding.chipWeek.setOnClickListener {
            viewModel.setPeriod(OperationsPeriod.WEEK)
        }
        binding.chipMonth.setOnClickListener {
            viewModel.setPeriod(OperationsPeriod.MONTH)
        }
        binding.chipYear.setOnClickListener {
            viewModel.setPeriod(OperationsPeriod.YEAR)
        }
        binding.chipCustom.setOnClickListener {
            openCustomRangePicker()
        }
    }

    private fun openCustomRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select period")
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first ?: return@addOnPositiveButtonClickListener
            val end = range.second ?: return@addOnPositiveButtonClickListener
            // Expand to whole selected days
            val from = start
            val to = end + 86_399_999L
            viewModel.setCustomRange(from, to)
        }
        picker.show(parentFragmentManager, "operations_custom_range")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_FOCUS_TRANSACTION_ID = "focus_transaction_id"
    }
}