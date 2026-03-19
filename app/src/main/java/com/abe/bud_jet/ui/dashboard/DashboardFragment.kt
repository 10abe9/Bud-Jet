package com.abe.bud_jet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.FragmentDashboardBinding
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle
import com.abe.bud_jet.database.models.Transaction
import android.view.LayoutInflater as AndroidLayoutInflater
import com.abe.bud_jet.databinding.ItemTransactionBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var vibrator: VibrationManager
    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            FinanceRepositoryProvider.get(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vibrator = VibrationManager.get()

        setupUi()
        setupButtons()
    }

    private fun setupUi() {
        dashboardViewModel.uiState
            .collectWithLifecycle(viewLifecycleOwner) { state ->
                binding.textBalance.text = state.balanceFormatted
                binding.textBalanceStatus.text = state.monthDeltaFormatted
                val colorRes = when {
                    state.monthDeltaRaw > 0 -> com.abe.bud_jet.R.color.finance_income
                    state.monthDeltaRaw < 0 -> com.abe.bud_jet.R.color.finance_expense
                    else -> com.abe.bud_jet.R.color.text_primary
                }
                binding.textBalanceStatus.setTextColor(requireContext().getColor(colorRes))

                updateRecentTransactions(state.recentTransactions)
            }
    }

    private fun updateRecentTransactions(transactions: List<Transaction>) {
        val listContainer = binding.layoutRecentList
        val emptyContainer = binding.layoutRecentEmpty

        listContainer.removeAllViews()

        if (transactions.isEmpty()) {
            emptyContainer.visibility = View.VISIBLE
            listContainer.visibility = View.GONE
            return
        }

        emptyContainer.visibility = View.GONE
        listContainer.visibility = View.VISIBLE

        val inflater = AndroidLayoutInflater.from(requireContext())
        transactions.take(3).forEach { tx ->
            val itemBinding = ItemTransactionBinding.inflate(inflater, listContainer, false)
            val amountText = if (tx.isIncome) {
                "+$${tx.amount}"
            } else {
                "-$${tx.amount}"
            }
            itemBinding.tvCategory.text = tx.title
            itemBinding.tvAmount.text = amountText
            val color = if (tx.isIncome) {
                requireContext().getColor(com.abe.bud_jet.R.color.finance_income)
            } else {
                requireContext().getColor(com.abe.bud_jet.R.color.finance_expense)
            }
            itemBinding.tvAmount.setTextColor(color)
            // В simple версии используем только отформатированную дату
            itemBinding.tvTime.text = tx.date
            itemBinding.tvDate.text = ""

            listContainer.addView(itemBinding.root)
        }
    }

    private fun setupButtons() {
        binding.addIncomeButton.setOnClickListener {
            vibrator.tap()
            AddTransactionBottomSheet.newInstance(isIncomeDefault = true)
                .show(parentFragmentManager, "add_income")
        }
        binding.addExpenceButton.setOnClickListener {
            vibrator.tap()
            AddTransactionBottomSheet.newInstance(isIncomeDefault = false)
                .show(parentFragmentManager, "add_expense")
        }

        binding.btnRecentAdd.setOnClickListener {
            vibrator.tap()
            AddTransactionBottomSheet.newInstance(isIncomeDefault = false)
                .show(parentFragmentManager, "add_from_recent")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}