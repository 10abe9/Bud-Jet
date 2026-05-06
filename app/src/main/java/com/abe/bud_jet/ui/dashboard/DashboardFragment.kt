package com.abe.bud_jet.ui.dashboard

import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentDashboardBinding
import com.abe.bud_jet.R
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.notifications.NotificationReminderScheduler
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.abe.bud_jet.ui.profile.CurrentBalanceBottomSheet
import kotlinx.coroutines.flow.combine

class DashboardFragment : Fragment() {
    companion object {
        const val KEY_PROMPT_NOTIFICATIONS_AFTER_ONBOARDING = "prompt_notifications_after_onboarding"
    }

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
    private lateinit var preferenceManager: PreferenceManager
    private var currencyCode: String = "USD"
    private var initialBalance: Double = 0.0
    private var lastDashboardState: DashboardUiState? = null
    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }
    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            repository
        )
    }

    private val requestNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            preferenceManager.setNotificationPermissionRequested(true)
            preferenceManager.setNotificationsEnabled(granted)
            if (granted) {
                NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
            }
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
        preferenceManager = PreferenceManager.getInstance(requireContext())
        currencyCode = preferenceManager.getCurrencyCode()
        initialBalance = preferenceManager.getInitialBalance()

        // Remember last time the user opened Dashboard (used to decide whether to remind).
        preferenceManager.setLastDashboardVisitTime(System.currentTimeMillis())
        maybePromptNotificationsAfterOnboarding()
        if (preferenceManager.isNotificationsEnabled() && hasNotificationPermission()) {
            NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
        }

        setupResults()
        setupUi()
        setupButtons()
        observeCurrency()
    }

    private fun setupResults() {
        setFragmentResultListener(CurrentBalanceBottomSheet.RESULT_KEY) { _, bundle ->
            val balance = bundle.getDouble(CurrentBalanceBottomSheet.RESULT_BALANCE, initialBalance)
            initialBalance = balance
            preferenceManager.setInitialBalance(balance)
            lastDashboardState?.let { renderBalanceSection(it) }
        }
    }

    private fun maybePromptNotificationsAfterOnboarding() {
        val backStackEntry = findNavController().getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
        val shouldPrompt = backStackEntry.savedStateHandle
            .remove<Boolean>(KEY_PROMPT_NOTIFICATIONS_AFTER_ONBOARDING) == true
        if (!shouldPrompt) return
        if (preferenceManager.isNotificationPermissionRequested()) return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.notifications_reminder_title))
            .setMessage(getString(R.string.notifications_opt_in_message))
            .setNegativeButton(getString(R.string.notifications_opt_in_later)) { _, _ ->
                preferenceManager.setNotificationPermissionRequested(true)
                preferenceManager.setNotificationsEnabled(false)
            }
            .setPositiveButton(getString(R.string.notifications_opt_in_enable)) { _, _ ->
                if (hasNotificationPermission()) {
                    preferenceManager.setNotificationPermissionRequested(true)
                    preferenceManager.setNotificationsEnabled(true)
                    NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
                } else {
                    requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(requireContext().getColor(R.color.brand_primary))
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(requireContext().getColor(R.color.text_secondary))
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupUi() {
        // Activation for onboarding: any transaction hides the dashboard banner (repository source of truth).
        combine(
            dashboardViewModel.uiState,
            repository.observeRecentTransactions(1)
        ) { state, recentSample ->
            state to recentSample.isEmpty()
        }.collectWithLifecycle(viewLifecycleOwner) { (state, noTransactionsYet) ->
            lastDashboardState = state
            binding.cardOnboardingStart.visibility =
                if (noTransactionsYet) View.VISIBLE else View.GONE

            renderBalanceSection(state)
            val colorRes = when {
                state.monthDeltaRaw > 0 -> com.abe.bud_jet.R.color.finance_income
                state.monthDeltaRaw < 0 -> com.abe.bud_jet.R.color.finance_expense
                else -> com.abe.bud_jet.R.color.text_primary
            }
            binding.textBalanceStatus.setTextColor(requireContext().getColor(colorRes))
            binding.btnSetInitialBalance.visibility = if (state.hasIncomeTransactions) View.GONE else View.VISIBLE

            renderRecentTransactions(state.recentChips, suppressBuiltInEmpty = noTransactionsYet)
            renderCategories(
                expenseCategories = state.expenseCategories,
                incomeCategories = state.incomeCategories
            )
        }
    }

    private fun renderBalanceSection(state: DashboardUiState) {
        val effectiveBalance = state.balance + initialBalance
        binding.textBalance.text = CurrencyFormatter.formatSigned(effectiveBalance, currencyCode)
        binding.textBalanceStatus.text = CurrencyFormatter.formatDelta(state.monthDelta, currencyCode)
    }

    private fun renderRecentTransactions(
        chips: List<RecentTransactionChip>,
        suppressBuiltInEmpty: Boolean
    ) {
        val chipGroup = binding.chipGroupRecentTransactions
        val emptyContainer = binding.layoutRecentEmpty
        chipGroup.removeAllViews()

        if (chips.isEmpty()) {
            emptyContainer.visibility = if (suppressBuiltInEmpty) View.GONE else View.VISIBLE
            return
        }
        emptyContainer.visibility = View.GONE

        chips.take(3).forEach { tx ->
            val chip = layoutInflater.inflate(
                com.abe.bud_jet.R.layout.item_dashboard_recent_chip,
                chipGroup,
                false
            ) as Chip

            chip.apply {
                val amount = String.format("%.2f", kotlin.math.abs(tx.amount))
                val symbol = CurrencyFormatter.symbolFor(currencyCode)
                text = if (tx.isIncome) "+$symbol$amount" else "-$symbol$amount"
                isCheckable = false
                isClickable = false
                val strokeColor = if (tx.isIncome) {
                    requireContext().getColor(com.abe.bud_jet.R.color.finance_income)
                } else {
                    requireContext().getColor(com.abe.bud_jet.R.color.finance_expense)
                }
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                setTextColor(requireContext().getColor(com.abe.bud_jet.R.color.text_primary))
                setOnClickListener {
                    val navController = findNavController()
                    runCatching {
                        navController.getBackStackEntry(com.abe.bud_jet.R.id.mobile_navigation)
                            .savedStateHandle["focus_transaction_id"] = tx.id
                        requireActivity()
                            .findViewById<BottomNavigationView>(com.abe.bud_jet.R.id.nav_view)
                            .selectedItemId = com.abe.bud_jet.R.id.navigation_operations
                    }.onFailure {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dashboard_unable_to_open_transaction),
                            Toast.LENGTH_SHORT
                        ).show()
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
        val chip = layoutInflater.inflate(
            com.abe.bud_jet.R.layout.item_dashboard_category_chip,
            binding.chipGroupExpenseCategories,
            false
        ) as Chip

        return chip.apply {
            text = category.name
            isCheckable = false
            isClickable = true
            chipBackgroundColor =
                ColorStateList.valueOf(requireContext().getColor(com.abe.bud_jet.R.color.card))

            val colorHex = category.colorHex ?: fixedCategoryPalette.first()
            runCatching {
                val parsed = Color.parseColor(colorHex)
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
        binding.btnSetInitialBalance.setOnClickListener {
            showInitialBalanceDialog()
        }

        binding.btnOnboardingAddExpense.setOnClickListener {
            vibrator.tap()
            AddTransactionBottomSheet.newInstance(isIncomeDefault = false)
                .show(parentFragmentManager, "onboarding_dashboard_add_expense")
        }
        binding.btnOnboardingSetBalance.setOnClickListener {
            vibrator.tap()
            showInitialBalanceDialog()
        }
    }

    private fun showAddCategoryDialog() {
        AddCategoryBottomSheet()
            .show(parentFragmentManager, "add_category")
    }

    private fun observeCurrency() {
        preferenceManager.observeCurrencyCode().collectWithLifecycle(viewLifecycleOwner) { code ->
            currencyCode = code
            lastDashboardState?.let { renderBalanceSection(it) }
        }
    }

    private fun showInitialBalanceDialog() {
        CurrentBalanceBottomSheet
            .newInstance(initialBalance)
            .show(parentFragmentManager, "current_balance_sheet_from_dashboard")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}