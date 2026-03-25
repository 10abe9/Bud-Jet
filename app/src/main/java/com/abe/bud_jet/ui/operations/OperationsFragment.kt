package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.ui.operations.FilterOperationsBottomSheet.Companion.RESULT_KEY
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.adapters.TransactionsAdapter
import com.abe.bud_jet.databinding.FragmentOperationsBinding
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.chip.Chip
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import kotlin.math.max

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

        // If dashboard updates focus_transaction_id while this fragment is already created,
        // we still must pick it up and scroll/highlight accordingly.
        val backStackEntry = findNavController().getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
        backStackEntry.savedStateHandle.getLiveData<Long>("focus_transaction_id")
            .observe(viewLifecycleOwner) { newId ->
                val id = newId?.takeIf { it > 0L } ?: return@observe
                pendingFocusTransactionId = id
            }

        setupRecycler()
        setupClicks()
        setupFilterResultListener()
        setupSearchResultListener()
        observeData()
    }

    private fun setupRecycler() {
        adapter = TransactionsAdapter(
            onTransactionClick = { transaction ->
                EditTransactionBottomSheet.newInstance(
                    id = transaction.id,
                    amount = transaction.amount,
                    isIncome = transaction.isIncome,
                    categoryId = transaction.categoryId,
                    note = transaction.note,
                    timestamp = transaction.timestamp
                ).show(parentFragmentManager, "edit_transaction")
            },
            onTransactionLongClick = { transaction ->
                val sign = if (transaction.isIncome) "+" else "-"
                val amount = String.format("%.2f", kotlin.math.abs(transaction.amount))
                DeleteTransactionBottomSheet.newInstance(
                    id = transaction.id,
                    message = "Delete transaction $sign$$amount?"
                ).show(parentFragmentManager, "delete_transaction")
            }
        )

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
    }

    private fun observeData() {
        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state ->
            adapter.submitList(state.transactions)
            binding.tvExpensesAmount.text = state.totalsFormatted
            binding.tvExpensesPeriod.text = state.periodLabel
            binding.tvExpensesTitle.text = when (state.totalsMode) {
                OperationsTotalsMode.INCOME -> "Income"
                OperationsTotalsMode.EXPENSES -> "Expenses"
            }
            binding.tvExpensesTitle.setTextColor(
                when (state.totalsMode) {
                    OperationsTotalsMode.INCOME -> ContextCompat.getColor(requireContext(), com.abe.bud_jet.R.color.finance_income)
                    OperationsTotalsMode.EXPENSES -> ContextCompat.getColor(requireContext(), com.abe.bud_jet.R.color.finance_expense)
                }
            )
            when (state.selectedPeriod) {
                OperationsPeriod.WEEK -> binding.chipGroupFilters.check(binding.chipWeek.id)
                OperationsPeriod.MONTH -> binding.chipGroupFilters.check(binding.chipMonth.id)
                OperationsPeriod.YEAR -> binding.chipGroupFilters.check(binding.chipYear.id)
                OperationsPeriod.CUSTOM -> binding.chipGroupFilters.check(binding.chipCustom.id)
            }
            applyPeriodChipHighlight(state.selectedPeriod)
            renderActiveQueryChips(state.activeFilterText, state.activeSearchText)
            focusIfNeeded()
        }
    }

    private fun renderActiveQueryChips(filterText: String?, searchText: String?) {
        val chipGroup = binding.chipGroupActiveQuery
        chipGroup.removeAllViews()

        val ctx = requireContext()
        val bgColor = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.card)
        val strokeColor = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.brand_primary)
        val textColor = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.text_primary)

        fun createChip(text: String, onClose: () -> Unit): Chip {
            val chip = Chip(ctx).apply {
                this.text = text
                isCheckable = false
                setCloseIconVisible(true)
                chipBackgroundColor = ColorStateList.valueOf(bgColor)
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                setTextColor(textColor)
                setOnCloseIconClickListener {
                    onClose()
                }
            }
            return chip
        }

        filterText?.takeIf { it.isNotBlank() }?.let {
            chipGroup.addView(createChip("Filter: $it") { viewModel.clearFilters() })
        }
        searchText?.takeIf { it.isNotBlank() }?.let {
            chipGroup.addView(createChip("Search: $it") { viewModel.clearSearch() })
        }
    }

    private fun applyPeriodChipHighlight(period: OperationsPeriod) {
        val chipList = listOf(binding.chipWeek, binding.chipMonth, binding.chipYear, binding.chipCustom)
        val ctx = requireContext()

        val inactiveBg = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.card)
        val inactiveStroke = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.border)
        val activeBg = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.primary_container)
        val activeStroke = ContextCompat.getColor(ctx, com.abe.bud_jet.R.color.brand_primary)

        chipList.forEach { chip ->
            val isActive = when (chip.id) {
                binding.chipWeek.id -> period == OperationsPeriod.WEEK
                binding.chipMonth.id -> period == OperationsPeriod.MONTH
                binding.chipYear.id -> period == OperationsPeriod.YEAR
                binding.chipCustom.id -> period == OperationsPeriod.CUSTOM
                else -> false
            }

            val bgColor = if (isActive) activeBg else inactiveBg
            val strokeColor = if (isActive) activeStroke else inactiveStroke
            chip.chipBackgroundColor = ColorStateList.valueOf(bgColor)
            chip.chipStrokeColor = ColorStateList.valueOf(strokeColor)
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
            showSearchBottomSheet()
        }

        binding.btnFilter.setOnClickListener {
            showFilterBottomSheet()
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

        // Tap on total value to toggle between Expenses and Income.
        binding.tvExpensesAmount.setOnClickListener {
            val nextMode = when (viewModel.totalsMode.value) {
                OperationsTotalsMode.EXPENSES -> OperationsTotalsMode.INCOME
                OperationsTotalsMode.INCOME -> OperationsTotalsMode.EXPENSES
            }
            viewModel.setTotalsMode(nextMode)
        }
    }

    private fun showSearchBottomSheet() {
        SearchOperationsBottomSheet
            .newInstance(viewModel.searchQuery.value)
            .show(parentFragmentManager, "operations_search")
    }

    private fun setupSearchResultListener() {
        parentFragmentManager.setFragmentResultListener(
            SearchOperationsBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val query = bundle.getString(SearchOperationsBottomSheet.ARG_QUERY).orEmpty()
            viewModel.setSearchQuery(query)
        }
    }

    private fun showFilterBottomSheet() {
        val type = viewModel.typeFilter.value
        val categoryId = viewModel.categoryIdFilter.value

        FilterOperationsBottomSheet.newInstance(type, categoryId)
            .show(parentFragmentManager, "operations_filter")
    }

    private fun setupFilterResultListener() {
        parentFragmentManager.setFragmentResultListener(
            RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val ordinal = bundle.getInt("arg_type_ordinal", OperationsTypeFilter.ALL.ordinal)
            val type = OperationsTypeFilter.entries.getOrNull(ordinal) ?: OperationsTypeFilter.ALL
            val categoryIdRaw = bundle.getLong("arg_category_id", -1L)
            val categoryId = categoryIdRaw.takeIf { it >= 0L }

            viewModel.setTypeFilter(type)
            viewModel.setCategoryFilter(categoryId)
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