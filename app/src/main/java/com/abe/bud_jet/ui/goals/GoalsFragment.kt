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
import com.abe.bud_jet.utils.VibrationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private lateinit var binding: FragmentGoalsBinding
    private val vibrator: VibrationManager by lazy { VibrationManager.get() }
    private lateinit var preferenceManager: PreferenceManager
    private var currencyCode: String = "USD"
    private val viewModel: GoalsViewModel by viewModels {
        GoalsViewModelFactory(
            FinanceRepositoryProvider.get(requireContext()),
            requireContext().resources,
            PreferenceManager.getInstance(requireContext()).observeCurrencyCode()
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
            val goalId = viewModel.uiState.value.savingCard.goalId ?: return@setOnClickListener
            // Limits ask for confirmation before deletion; the saving goal now does too.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.goals_delete_goal))
                .setMessage(getString(R.string.bottomsheet_delete_undo_message))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                    viewModel.deleteGoal(goalId)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.goals_saving_goal_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        }
        binding.btnAddLimit.setOnClickListener {
            showLimitBottomSheet(prefill = null)
        }
        binding.btnOnboardingCreateSavings.setOnClickListener {
            vibrator.tap()
            val saving = viewModel.uiState.value.savingCard
            showSavingGoalBottomSheet(
                currentAmount = saving.targetAmount.takeIf { saving.hasGoal },
                currentDeadline = saving.deadline
            )
        }
        binding.btnOnboardingAddCategoryLimit.setOnClickListener {
            vibrator.tap()
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

            val showGoalsOnboarding = !saving.hasGoal && state.limits.isEmpty()
            binding.cardGoalsOnboarding.visibility =
                if (showGoalsOnboarding) View.VISIBLE else View.GONE
            binding.tvLimitsEmpty.visibility = when {
                state.limits.isNotEmpty() -> View.GONE
                showGoalsOnboarding -> View.GONE
                else -> View.VISIBLE
            }

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