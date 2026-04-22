package com.abe.bud_jet.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentAnalyticsBinding
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.collectWithLifecycle
import com.abe.bud_jet.utils.VibrationManager
import java.util.Locale

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var binding: FragmentAnalyticsBinding
    private lateinit var preferenceManager: PreferenceManager
    private var currencyCode: String = "USD"
    private val categoryAdapter = CategoryStatsAdapter()
    private val monthChartsAdapter = MonthChartsAdapter()
    private val viewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModelFactory(
            FinanceRepositoryProvider.get(requireContext()),
            requireContext().resources
        )
    }

    private val vibrator: VibrationManager by lazy { VibrationManager.get() }
    private var vibrateOnNextPageSelected: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentAnalyticsBinding.bind(view)
        preferenceManager = PreferenceManager.getInstance(requireContext())
        currencyCode = preferenceManager.getCurrencyCode()
        categoryAdapter.currencyCode = currencyCode

        binding.rvStats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStats.adapter = categoryAdapter

        binding.vpMonthCharts.adapter = monthChartsAdapter
        binding.vpMonthCharts.offscreenPageLimit = 1

        binding.vpMonthCharts.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.onMonthSelected(position)
                if (vibrateOnNextPageSelected) {
                    vibrator.duoLongSuccess()
                    vibrateOnNextPageSelected = false
                }
            }
        })

        setupMonthArrows()
        observeUiState()
        observeCurrency()
    }

    private fun setupMonthArrows() {
        binding.btnPrevMonth.setOnClickListener {
            val itemCount = binding.vpMonthCharts.adapter?.itemCount ?: 0
            if (itemCount <= 0) return@setOnClickListener

            val nextIndex = (binding.vpMonthCharts.currentItem - 1).coerceAtLeast(0)
            vibrateOnNextPageSelected = true
            viewModel.onMonthSelected(nextIndex)
            binding.vpMonthCharts.setCurrentItem(nextIndex, true)
        }

        binding.btnNextMonth.setOnClickListener {
            val itemCount = binding.vpMonthCharts.adapter?.itemCount ?: 0
            if (itemCount <= 0) return@setOnClickListener

            val lastIndex = itemCount - 1
            val nextIndex = (binding.vpMonthCharts.currentItem + 1).coerceAtMost(lastIndex)
            vibrateOnNextPageSelected = true
            viewModel.onMonthSelected(nextIndex)
            binding.vpMonthCharts.setCurrentItem(nextIndex, true)
        }
    }

    private fun observeUiState() {
        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state ->
            binding.tvMonthTitle.text = state.monthOptions.getOrNull(state.selectedMonthIndex).orEmpty()
            binding.tvTotal.text = formatCenterTotal(state.totalExpense.toDouble())

            categoryAdapter.submit(state.stats)

            val hasStats = state.stats.isNotEmpty()
            binding.rvStats.visibility = if (hasStats) View.VISIBLE else View.GONE

            binding.tvEmptyState.visibility = if (hasStats) View.GONE else View.VISIBLE
            binding.tvEmptyState.text =
                state.emptyMessage ?: getString(R.string.analytics_no_data_for_selected_period)

            monthChartsAdapter.submit(state.monthCharts)

            if (state.monthCharts.isNotEmpty() &&
                binding.vpMonthCharts.currentItem != state.selectedMonthIndex
            ) {
                binding.vpMonthCharts.setCurrentItem(state.selectedMonthIndex, false)
            }
        }
    }

    private fun observeCurrency() {
        preferenceManager.observeCurrencyCode().collectWithLifecycle(viewLifecycleOwner) { code ->
            currencyCode = code
            categoryAdapter.currencyCode = code
            categoryAdapter.notifyDataSetChanged()
            binding.tvTotal.text = formatCenterTotal(viewModel.uiState.value.totalExpense.toDouble())
        }
    }

    private fun formatCenterTotal(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val symbol = CurrencyFormatter.symbolFor(currencyCode)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000_000 -> String.format(Locale.US, "%s%s%.1fB", sign, symbol, abs / 1_000_000_000.0)
            abs >= 1_000_000 -> String.format(Locale.US, "%s%s%.1fM", sign, symbol, abs / 1_000_000.0)
            abs >= 1_000 -> String.format(Locale.US, "%s%s%.1fK", sign, symbol, abs / 1_000.0)
            else -> CurrencyFormatter.format(amount, currencyCode)
        }
    }

    private class MonthChartsAdapter : RecyclerView.Adapter<MonthChartsAdapter.VH>() {
        private var items: List<AnalyticsMonthChartUi> = emptyList()

        fun submit(list: List<AnalyticsMonthChartUi>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_analytics_month_chart, parent, false)
            val donut = view.findViewById<DonutChartView>(R.id.donutChart)
            return VH(view, donut)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val stats = items.getOrNull(position)?.stats.orEmpty()
            holder.donut.setData(stats)
        }

        override fun getItemCount(): Int = items.size

        inner class VH(itemView: View, val donut: DonutChartView) : RecyclerView.ViewHolder(itemView)
    }
}