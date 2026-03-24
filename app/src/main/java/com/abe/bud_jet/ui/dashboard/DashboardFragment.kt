package com.abe.bud_jet.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.databinding.FragmentDashboardBinding
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardFragment : Fragment() {
    private val fixedCategoryPalette = listOf(
        "#F59E0B",
        "#3B82F6",
        "#10B981",
        "#8B5CF6",
        "#EF4444",
        "#06B6D4",
        "#F97316",
        "#84CC16",
        "#EC4899",
        "#6366F1"
    )

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var vibrator: VibrationManager
    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }
    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            repository
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

                renderRecentTransactions(state.recentChips)
                renderCategories(
                    expenseCategories = state.expenseCategories,
                    incomeCategories = state.incomeCategories
                )
            }
    }

    private fun renderRecentTransactions(chips: List<RecentTransactionChip>) {
        val chipGroup = binding.chipGroupRecentTransactions
        val emptyContainer = binding.layoutRecentEmpty
        chipGroup.removeAllViews()

        if (chips.isEmpty()) {
            emptyContainer.visibility = View.VISIBLE
            return
        }
        emptyContainer.visibility = View.GONE

        chips.take(3).forEach { tx ->
            val chip = Chip(requireContext()).apply {
                val amount = String.format("%.2f", kotlin.math.abs(tx.amount))
                text = if (tx.isIncome) "+$$amount" else "-$$amount"
                isCheckable = false
                isClickable = false
                chipBackgroundColor =
                    ColorStateList.valueOf(requireContext().getColor(com.abe.bud_jet.R.color.card))
                chipStrokeWidth = 1.5f
                val strokeColor = if (tx.isIncome) {
                    requireContext().getColor(com.abe.bud_jet.R.color.finance_income)
                } else {
                    requireContext().getColor(com.abe.bud_jet.R.color.finance_expense)
                }
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                setTextColor(strokeColor)
                setOnClickListener {
                    val navController = findNavController()
                    runCatching {
                        navController.getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
                            .savedStateHandle["focus_transaction_id"] = tx.id
                        requireActivity()
                            .findViewById<BottomNavigationView>(com.abe.bud_jet.R.id.nav_view)
                            .selectedItemId = com.abe.bud_jet.R.id.navigation_operations
                    }.onFailure {
                        Toast.makeText(requireContext(), "Unable to open transaction", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun renderCategories(
        expenseCategories: List<DashboardCategoryChip>,
        incomeCategories: List<DashboardCategoryChip>
    ) {
        val ordered = (expenseCategories + incomeCategories)
            .sortedBy { it.id }
            .mapIndexed { index, chip ->
                chip.copy(colorHex = fixedCategoryPalette[index % fixedCategoryPalette.size])
            }
        val colorsById = ordered.associateBy({ it.id }, { it.colorHex })
        val expenseColored = expenseCategories.map { it.copy(colorHex = colorsById[it.id]) }
        val incomeColored = incomeCategories.map { it.copy(colorHex = colorsById[it.id]) }

        renderChipRow(binding.chipGroupExpenseCategories, expenseColored)
        renderChipRow(binding.chipGroupIncomeCategories, incomeColored)
    }

    private fun renderChipRow(
        chipGroup: com.google.android.material.chip.ChipGroup,
        categories: List<DashboardCategoryChip>
    ) {
        chipGroup.removeAllViews()
        categories.forEach { category -> chipGroup.addView(buildCategoryChip(category)) }
    }

    private fun buildCategoryChip(category: DashboardCategoryChip): Chip {
        return Chip(requireContext()).apply {
            text = category.name
            isCheckable = false
            isClickable = true
            chipBackgroundColor = ColorStateList.valueOf(requireContext().getColor(com.abe.bud_jet.R.color.card))

            val colorHex = category.colorHex ?: fixedCategoryPalette.first()
            runCatching {
                val parsed = Color.parseColor(colorHex)
                chipStrokeWidth = 1.5f
                chipStrokeColor = ColorStateList.valueOf(parsed)
                setTextColor(requireContext().getColor(com.abe.bud_jet.R.color.text_primary))
            }

            setOnLongClickListener {
                showDeleteCategoryDialog(category)
                true
            }
        }
    }

    private fun showDeleteCategoryDialog(category: DashboardCategoryChip) {
        DeleteCategoryBottomSheet.newInstance(
            categoryId = category.id,
            categoryName = category.name
        ).show(parentFragmentManager, "delete_category")
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

        binding.btnAddCategoryDashboard.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        AddCategoryBottomSheet()
            .show(parentFragmentManager, "add_category")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}