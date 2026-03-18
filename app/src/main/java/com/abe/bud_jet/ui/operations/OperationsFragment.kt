package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.adapters.TransactionsAdapter
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.databinding.FragmentOperationsBinding

class OperationsFragment : Fragment() {

    private var _binding: FragmentOperationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TransactionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecycler()
        setupClicks()
        loadMockData()
    }

    private fun setupRecycler() {
        adapter = TransactionsAdapter()

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
    }

    private fun loadMockData() {
        val list = listOf(
            Transaction("Salary", 1200.0, true, "Today, 09:00"),
            Transaction("Groceries", 54.2, false, "Today, 14:20"),
            Transaction("Taxi", 12.5, false, "Yesterday"),
            Transaction("Freelance", 300.0, true, "Mar 12")
        )

        adapter.submitList(list)
    }

    private fun setupClicks() {

        binding.btnSearch.setOnClickListener {
            // TODO
        }

        binding.btnFilter.setOnClickListener {
            // TODO
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}