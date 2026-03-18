package com.abe.bud_jet.ui.analytics

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.R
import com.abe.bud_jet.database.models.CategoryStat
import com.abe.bud_jet.databinding.FragmentAnalyticsBinding

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var binding: FragmentAnalyticsBinding
    private val adapter = CategoryStatsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentAnalyticsBinding.bind(view)

        binding.rvStats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStats.adapter = adapter

        val stats = listOf(
            CategoryStat("Food", 400f),
            CategoryStat("Transport", 250f),
            CategoryStat("Shopping", 200f),
            CategoryStat("Other", 150f)
        )

        setupUI(stats)
    }

    private fun setupUI(stats: List<CategoryStat>) {
        val total = stats.sumOf { it.total.toDouble() }.toFloat()

        binding.tvTotal.text = "$${total.toInt()}"
        binding.donutChart.setData(stats)

        adapter.submit(stats)
    }
}