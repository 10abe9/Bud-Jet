package com.abe.bud_jet.ui.goals

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentGoalsBinding
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.collectWithLifecycle

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private lateinit var binding: FragmentGoalsBinding
    private lateinit var preferenceManager: PreferenceManager
    private var currencyCode: String = "USD"
    private val viewModel: GoalsViewModel by viewModels {
        GoalsViewModelFactory(
            FinanceRepositoryProvider.get(requireContext()),
            requireContext().resources
        )
    }
    private val adapter = LimitAdapter(
        onClick = { showLimitBottomSheet(prefill = it) },
        onLongClick = { limit -> showDeleteLimitBottomSheet(limit) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentGoalsBinding.bind(view)
        preferenceManager = PreferenceManager.getInstance(requireContext())
        currencyCode = preferenceManager.getCurrencyCode()
        adapter.currencyCode = currencyCode

        binding.rvLimits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLimits.adapter = adapter

        setupClicks()
        setupResults()
        observeUi()
        observeCurrency()
    }

    private fun setupClicks() {
        binding.btnEditSavingGoal.setOnClickListener {
            val saving = viewModel.uiState.value.savingCard
            showSavingGoalBottomSheet(
                currentAmount = saving.targetAmount.takeIf { saving.hasGoal },
                currentDeadline = saving.deadline
            )
        }
        binding.btnDeleteSavingGoal.setOnClickListener {
            viewModel.uiState.value.savingCard.goalId?.let { goalId ->
                viewModel.deleteGoal(goalId)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.goals_saving_goal_deleted),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.btnAddLimit.setOnClickListener {
            showLimitBottomSheet(prefill = null)
        }
    }

    private fun setupResults() {
        setFragmentResultListener(SavingGoalFormBottomSheet.RESULT_KEY) { _, bundle ->
            val amount = bundle.getDouble(SavingGoalFormBottomSheet.RESULT_AMOUNT)
            val deadline = bundle.getLong(SavingGoalFormBottomSheet.RESULT_DEADLINE).takeIf { it > 0L }
            viewModel.saveSavingGoal(targetAmount = amount, deadline = deadline)
        }
        setFragmentResultListener(LimitGoalFormBottomSheet.RESULT_KEY) { _, bundle ->
            val categoryId = bundle.getLong(LimitGoalFormBottomSheet.RESULT_CATEGORY_ID)
            val amount = bundle.getDouble(LimitGoalFormBottomSheet.RESULT_LIMIT)
            viewModel.saveCategoryLimit(categoryId = categoryId, limitAmount = amount)
        }
        setFragmentResultListener(DeleteLimitBottomSheet.RESULT_KEY) { _, bundle ->
            val goalId = bundle.getLong(DeleteLimitBottomSheet.RESULT_GOAL_ID)
            viewModel.deleteGoal(goalId)
            Toast.makeText(
                requireContext(),
                getString(R.string.goals_limit_deleted),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeUi() {
        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { state ->
            val saving = state.savingCard
            binding.tvSavingAmount.text = if (saving.hasGoal) {
                "${CurrencyFormatter.format(saving.currentAmount, currencyCode)} / ${CurrencyFormatter.format(saving.targetAmount, currencyCode)}"
            } else {
                getString(R.string.goals_no_goal_set_yet)
            }
            binding.progressSaving.progress = saving.progressPercent
            binding.tvSavingHint.text = saving.planHint
            binding.btnEditSavingGoal.text = if (saving.hasGoal) {
                getString(R.string.goals_edit_goal)
            } else {
                getString(R.string.goals_set_goal)
            }
            binding.btnDeleteSavingGoal.visibility = if (saving.hasGoal) View.VISIBLE else View.GONE
            binding.tvLimitsEmpty.visibility = if (state.limits.isEmpty()) View.VISIBLE else View.GONE

            adapter.submit(
                state.limits.map {
                    Limit(
                        goalId = it.goalId,
                        categoryId = it.categoryId,
                        category = it.categoryName,
                        spent = it.spent,
                        limit = it.limit,
                        hint = it.hint
                    )
                }
            )
        }
    }

    private fun observeCurrency() {
        preferenceManager.observeCurrencyCode().collectWithLifecycle(viewLifecycleOwner) { code ->
            currencyCode = code
            adapter.currencyCode = code
            adapter.notifyDataSetChanged()
            val saving = viewModel.uiState.value.savingCard
            binding.tvSavingAmount.text = if (saving.hasGoal) {
                "${CurrencyFormatter.format(saving.currentAmount, currencyCode)} / ${CurrencyFormatter.format(saving.targetAmount, currencyCode)}"
            } else {
                getString(R.string.goals_no_goal_set_yet)
            }
        }
    }

    private fun showSavingGoalBottomSheet(currentAmount: Double?, currentDeadline: Long?) {
        SavingGoalFormBottomSheet
            .newInstance(amount = currentAmount, deadlineTimestamp = currentDeadline)
            .show(parentFragmentManager, "saving_goal_form_sheet")
    }

    private fun showLimitBottomSheet(prefill: Limit?) {
        LimitGoalFormBottomSheet
            .newInstance(categoryId = prefill?.categoryId, limitAmount = prefill?.limit)
            .show(parentFragmentManager, "limit_goal_form_sheet")
    }

    private fun showDeleteLimitBottomSheet(limit: Limit) {
        DeleteLimitBottomSheet
            .newInstance(goalId = limit.goalId, categoryName = limit.category)
            .show(parentFragmentManager, "delete_limit_sheet")
    }
}