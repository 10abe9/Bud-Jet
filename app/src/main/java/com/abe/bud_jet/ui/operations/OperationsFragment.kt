package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.adapters.TransactionsAdapter
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.databinding.FragmentOperationsBinding
import com.abe.bud_jet.utils.collectWithLifecycle

class OperationsFragment : Fragment() {

    private var _binding: FragmentOperationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TransactionsAdapter

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
        setupRecycler()
        setupClicks()
        observeData()
    }

    private fun setupRecycler() {
        adapter = TransactionsAdapter()

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
    }

    private fun observeData() {
        viewModel.transactions
            .collectWithLifecycle(viewLifecycleOwner) { list ->
                adapter.submitList(list)
            }
    }

    private fun setupClicks() {

        binding.btnSearch.setOnClickListener {
            // TODO: добавить UI поиска (диалог/поисковую строку)
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