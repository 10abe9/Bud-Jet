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
import com.abe.bud_jet.capture.CaptureAccess
import com.abe.bud_jet.capture.RecurringDetector
import com.abe.bud_jet.databinding.ItemRecurringPaymentBinding
import com.abe.bud_jet.premium.PremiumManager
import com.abe.bud_jet.ui.common.PromptBottomSheet
import com.abe.bud_jet.premium.PremiumOfferBottomSheet
import com.abe.bud_jet.premium.PremiumPromoPolicy
import com.abe.bud_jet.premium.SavingsOffer
import com.abe.bud_jet.utils.CategoryPalette
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.notifications.NotificationReminderScheduler
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.abe.bud_jet.ui.profile.CurrentBalanceBottomSheet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class DashboardFragment : Fragment() {
    companion object {
        const val KEY_PROMPT_NOTIFICATIONS_AFTER_ONBOARDING = "prompt_notifications_after_onboarding"
        private const val MAX_RECURRING_ROWS = 5
        private const val AUTO_CAPTURE_OFFER_AFTER = 3
        private const val REQUEST_NOTIFICATIONS_PROMPT = "prompt_notifications"
        private const val REQUEST_CAPTURE_OFFER = "prompt_capture_offer"
    }

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
            repository,
            PreferenceManager.getInstance(requireContext()).observeCurrencyCode()
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
        observePremiumOffer()
        observeAutoCapture()
        observeRecurringPayments()
    }

    override fun onResume() {
        super.onResume()
        maybeOfferAutoCapture()
    }

    private fun observeAutoCapture() {
        FinanceRepositoryProvider.capture(requireContext()).observePending()
            .collectWithLifecycle(viewLifecycleOwner) { pending ->
                binding.cardCapturePending.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
                binding.tvCapturePending.text = resources.getQuantityString(
                    R.plurals.capture_pending_count,
                    pending.size,
                    pending.size
                )
            }
        binding.cardCapturePending.setOnClickListener { openAutoCapture() }
        PromptBottomSheet.listen(this, REQUEST_CAPTURE_OFFER) { accepted ->
            if (!accepted) return@listen
            if (PremiumManager.isPremium.value) {
                openAutoCapture()
            } else {
                PremiumOfferBottomSheet.newInstance(currencyCode, dashboardViewModel.premiumOffer.value)
                    .show(parentFragmentManager, "premium_offer")
            }
        }
        PromptBottomSheet.listen(this, REQUEST_NOTIFICATIONS_PROMPT, ::onNotificationsPromptAnswered)
    }

    private fun openAutoCapture() {
        findNavController().navigate(R.id.navigation_auto_capture)
    }

    private fun observeRecurringPayments() {
        dashboardViewModel.recurringPayments.collectWithLifecycle(viewLifecycleOwner) { payments ->
            renderRecurringPayments(payments)
        }
    }

    private fun renderRecurringPayments(payments: List<RecurringDetector.RecurringPayment>) {
        val container = binding.layoutRecurringRows
        container.removeAllViews()
        binding.cardRecurring.visibility = if (payments.isEmpty()) View.GONE else View.VISIBLE
        val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        payments.take(MAX_RECURRING_ROWS).forEach { payment ->
            val row = ItemRecurringPaymentBinding.inflate(layoutInflater, container, false)
            row.tvMerchant.text = payment.merchant
            row.tvNextCharge.text = getString(
                R.string.recurring_next_charge,
                dateFormat.format(java.util.Date(payment.nextChargeAt))
            )
            row.tvAmount.text = CurrencyFormatter.format(payment.lastAmount, currencyCode)
            container.addView(row.root)
        }
    }

    private fun observePremiumOffer() {
        dashboardViewModel.premiumOffer.collectWithLifecycle(viewLifecycleOwner) { offer ->
            renderPremiumOffer(offer)
        }
        binding.btnPremiumOfferCta.setOnClickListener {
            vibrator.tap()
            PremiumOfferBottomSheet
                .newInstance(currencyCode, dashboardViewModel.premiumOffer.value)
                .show(parentFragmentManager, "premium_offer")
        }
        binding.btnPremiumOfferDismiss.setOnClickListener {
            val dismissCount = preferenceManager.getPremiumPromoDismissCount() + 1
            preferenceManager.snoozePremiumPromo(
                dismissCount = dismissCount,
                snoozedUntil = PremiumPromoPolicy.snoozeUntil(dismissCount)
            )
            binding.cardPremiumOffer.visibility = View.GONE
        }
    }

    private fun renderPremiumOffer(offer: SavingsOffer?) {
        val visible = PremiumPromoPolicy.shouldShow(
            isPremium = preferenceManager.isPremiumEnabled(),
            hasOffer = offer != null,
            dismissCount = preferenceManager.getPremiumPromoDismissCount(),
            snoozedUntil = preferenceManager.getPremiumPromoSnoozedUntil()
        )
        binding.cardPremiumOffer.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible || offer == null) return

        val savings = CurrencyFormatter.format(offer.monthlySavings, currencyCode)
        val price = CurrencyFormatter.format(offer.monthlyPrice, currencyCode)
        binding.tvPremiumOfferTitle.text = getString(R.string.premium_offer_title, savings)
        binding.tvPremiumOfferBody.text = getString(
            R.string.premium_offer_body,
            CurrencyFormatter.format(offer.monthlySpend, currencyCode),
            savings,
            resources.getQuantityString(
                R.plurals.premium_payback,
                offer.paybackMultiple,
                offer.paybackMultiple,
                price
            )
        )
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

        PromptBottomSheet.show(
            fragmentManager = parentFragmentManager,
            requestKey = REQUEST_NOTIFICATIONS_PROMPT,
            icon = R.drawable.ic_notifications_black_24dp,
            title = R.string.notifications_reminder_title,
            message = R.string.notifications_opt_in_message,
            positive = R.string.notifications_opt_in_enable,
            negative = R.string.notifications_opt_in_later
        )
    }

    private fun onNotificationsPromptAnswered(accepted: Boolean) {
        if (!accepted) {
            preferenceManager.setNotificationPermissionRequested(true)
            preferenceManager.setNotificationsEnabled(false)
        } else if (hasNotificationPermission()) {
            preferenceManager.setNotificationPermissionRequested(true)
            preferenceManager.setNotificationsEnabled(true)
            NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
        } else {
            requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Offers automatic tracking once, after the user has entered a few expenses by hand
     * (so the benefit is obvious), instead of a permanent card on the dashboard.
     */
    private fun maybeOfferAutoCapture() {
        if (preferenceManager.isCapturePromoDismissed()) return
        if (preferenceManager.getIsFirstInit()) return
        if (CaptureAccess.isAccessGranted(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (repository.getTransactionsCount() < AUTO_CAPTURE_OFFER_AFTER) return@launch
            if (parentFragmentManager.findFragmentByTag(REQUEST_NOTIFICATIONS_PROMPT) != null) return@launch
            preferenceManager.setCapturePromoDismissed(true)
            PromptBottomSheet.show(
                fragmentManager = parentFragmentManager,
                requestKey = REQUEST_CAPTURE_OFFER,
                icon = R.drawable.ic_operations,
                title = R.string.capture_promo_title,
                message = R.string.capture_promo_body,
                positive = R.string.capture_promo_try,
                negative = R.string.capture_rationale_not_now
            )
        }
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
                text = CurrencyFormatter.formatDelta(
                    if (tx.isIncome) tx.amount else -tx.amount,
                    currencyCode
                )
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
        renderChipRow(binding.chipGroupExpenseCategories, expenseCategories)
        renderChipRow(binding.chipGroupIncomeCategories, incomeCategories)
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

            val colorHex = CategoryPalette.colorFor(category.id, category.colorHex)
            runCatching {
                val parsed = Color.parseColor(colorHex)
                chipStrokeColor = ColorStateList.valueOf(parsed)
                setTextColor(requireContext().getColor(com.abe.bud_jet.R.color.text_primary))
            }

            // Tap = quick add with this category preselected; long press = delete.
            setOnClickListener {
                vibrator.tap()
                AddTransactionBottomSheet.newInstance(
                    isIncomeDefault = category.isIncome,
                    categoryId = category.id
                ).show(parentFragmentManager, "add_from_category")
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