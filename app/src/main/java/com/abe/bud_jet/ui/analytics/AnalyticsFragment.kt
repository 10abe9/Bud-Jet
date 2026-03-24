package com.abe.bud_jet.ui.analytics

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.FragmentAnalyticsBinding
import com.abe.bud_jet.utils.collectWithLifecycle

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var binding: FragmentAnalyticsBinding
    private val adapter = CategoryStatsAdapter()
    private var monthSelectorAdapter: ArrayAdapter<String>? = null
    private val viewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModelFactory(
            FinanceRepositoryProvider.get(requireContext())
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentAnalyticsBinding.bind(view)

        binding.rvStats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStats.adapter = adapter

        setupMonthSelector()
        observeUiState()
    }

    private fun setupMonthSelector() {
        binding.monthSelector.setOnItemClickListener { _, _, position, _ ->
            viewModel.onMonthSelected(position)
        }
    }

    private fun observeUiState() {
        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state ->
            val stats = state.stats
            binding.tvTotal.text = "$${state.totalExpense.toInt()}"
            binding.donutChart.setData(stats)
            adapter.submit(stats)

            if (monthSelectorAdapter == null || monthSelectorAdapter?.count != state.monthOptions.size) {
                monthSelectorAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    state.monthOptions
                )
                binding.monthSelector.setAdapter(monthSelectorAdapter)
            }

            val selectedMonth = state.monthOptions.getOrNull(state.selectedMonthIndex).orEmpty()
            if (binding.monthSelector.text?.toString() != selectedMonth) {
                binding.monthSelector.setText(selectedMonth, false)
            }

            val hasStats = stats.isNotEmpty()
            binding.rvStats.visibility = if (hasStats) View.VISIBLE else View.GONE
            binding.tvEmptyState.visibility = if (hasStats) View.GONE else View.VISIBLE
            binding.tvEmptyState.text = state.emptyMessage ?: "No data for selected period."
        }
    }
}