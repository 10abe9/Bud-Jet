package com.abe.bud_jet.ui.goals

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.FragmentGoalsBinding

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private lateinit var binding: FragmentGoalsBinding
    private val adapter = LimitAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentGoalsBinding.bind(view)

        binding.rvLimits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLimits.adapter = adapter

        setupUI()
    }

    private fun setupUI() {
        val saved = 320f
        val target = 500f
        val percent = ((saved / target) * 100).toInt()

        binding.tvSavingAmount.text = "$${saved.toInt()} / $${target.toInt()}"
        binding.progressSaving.progress = percent

        val limits = listOf(
            Limit("Food", 250f, 300f),
            Limit("Transport", 180f, 200f),
            Limit("Shopping", 220f, 250f)
        )

        adapter.submit(limits)
    }
}