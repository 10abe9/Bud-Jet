package com.abe.bud_jet.ui.profile

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentProfileBinding
import com.abe.bud_jet.utils.LocaleManager
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.CurrencyRateProvider
import com.abe.bud_jet.utils.collectWithLifecycle
import com.abe.bud_jet.notifications.NotificationReminderScheduler
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var preferenceManager: PreferenceManager
    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }

    private var currentCurrency = "USD"
    private var pendingCurrencyTarget: String? = null
    private var notificationsEnabled = false
    private var currentInitialBalance = 0.0

    private val requestNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                preferenceManager.setNotificationsEnabled(true)
                NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
                notificationsEnabled = true
                binding.switchNotifications.isChecked = true
            } else {
                preferenceManager.setNotificationsEnabled(false)
                notificationsEnabled = false
                binding.switchNotifications.isChecked = false
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProfileBinding.bind(view)
        preferenceManager = PreferenceManager.getInstance(requireContext())
        currentInitialBalance = preferenceManager.getInitialBalance()

        setupUI()
        setupResults()
        setupClicks()
        observeCurrency()
        observeLanguage()
    }

    private fun setupUI() {
        val symbol = CurrencyFormatter.symbolFor(currentCurrency)
        binding.tvSummary.text = getString(R.string.profile_summary_template, symbol)

        binding.tvCurrency.text = currentCurrency
        binding.tvLanguage.text = LocaleManager.displayNameForLanguage(
            requireContext(),
            preferenceManager.getAppLanguage()
        )
        notificationsEnabled = preferenceManager.isNotificationsEnabled()
        binding.switchNotifications.isChecked = notificationsEnabled
    }

    private fun setupResults() {
        setFragmentResultListener(CurrencyPickerBottomSheet.RESULT_KEY) { _, bundle ->
            val code = bundle.getString(CurrencyPickerBottomSheet.RESULT_CODE).orEmpty()
            handleCurrencyChange(code)
        }
        setFragmentResultListener(CurrencyConversionBottomSheet.RESULT_KEY) { _, bundle ->
            val rate = bundle.getDouble(CurrencyConversionBottomSheet.RESULT_RATE)
            val toCurrency = pendingCurrencyTarget ?: return@setFragmentResultListener
            applyCurrencyConversion(currentCurrency, toCurrency, rate)
            pendingCurrencyTarget = null
        }
        setFragmentResultListener(LanguagePickerBottomSheet.RESULT_KEY) { _, bundle ->
            val language = bundle.getString(LanguagePickerBottomSheet.RESULT_LANGUAGE).orEmpty()
            if (language.isBlank()) return@setFragmentResultListener
            val current = preferenceManager.getAppLanguage()
            if (language == current) return@setFragmentResultListener

            preferenceManager.setAppLanguage(language)
            LocaleManager.applyAppLanguage(language)
            // AppCompatDelegate applies locale and recreates activities when needed.
        }
        setFragmentResultListener(CurrentBalanceBottomSheet.RESULT_KEY) { _, bundle ->
            val balance = bundle.getDouble(CurrentBalanceBottomSheet.RESULT_BALANCE, currentInitialBalance)
            currentInitialBalance = balance
            preferenceManager.setInitialBalance(balance)
            Toast.makeText(
                requireContext(),
                getString(R.string.profile_current_balance_saved),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupClicks() {
        binding.rowCurrency.setOnClickListener {
            CurrencyPickerBottomSheet.newInstance(currentCurrency)
                .show(parentFragmentManager, "currency_picker_sheet")
        }
        binding.rowLanguage.setOnClickListener {
            LanguagePickerBottomSheet
                .newInstance(preferenceManager.getAppLanguage())
                .show(parentFragmentManager, "language_picker_sheet")
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                preferenceManager.setNotificationsEnabled(false)
                NotificationReminderScheduler.cancelDailyExpenseReminder(requireContext())
                notificationsEnabled = false
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_notifications_off),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnCheckedChangeListener
            }

            // User is turning it ON.
            if (hasNotificationPermission()) {
                preferenceManager.setNotificationsEnabled(true)
                NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
                notificationsEnabled = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_notifications_on),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
                requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnExport.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.profile_export_coming_soon), Toast.LENGTH_SHORT).show()
        }
        binding.btnSetCurrentBalance.setOnClickListener {
            CurrentBalanceBottomSheet
                .newInstance(currentInitialBalance)
                .show(parentFragmentManager, "current_balance_sheet_from_profile")
        }

        binding.btnReset.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_reset_dialog_title))
                .setMessage(getString(R.string.profile_reset_dialog_message))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.profile_reset_data)) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferenceManager.resetUserDataToDefaults()
                        LocaleManager.applyAppLanguage(preferenceManager.getAppLanguage())

                        // Re-create repository so localized starter category names are correct.
                        FinanceRepositoryProvider.clearInstance()
                        val repo = FinanceRepositoryProvider.get(requireContext())
                        repo.resetAllUserDataAndReseedDefaults()

                        Toast.makeText(
                            requireContext(),
                            getString(R.string.profile_reset_success_toast),
                            Toast.LENGTH_SHORT
                        ).show()

                        // Restart to re-run onboarding / seeding.
                        requireActivity().recreate()
                    }
                }
                .show()
        }

        binding.cardPremium.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.profile_premium_coming_soon), Toast.LENGTH_SHORT).show()
        }

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun observeCurrency() {
        preferenceManager.observeCurrencyCode().collectWithLifecycle(viewLifecycleOwner) { code ->
            currentCurrency = code
            binding.tvCurrency.text = code
            setupUI()
        }
    }

    private fun observeLanguage() {
        preferenceManager.observeAppLanguage().collectWithLifecycle(viewLifecycleOwner) { code ->
            binding.tvLanguage.text = LocaleManager.displayNameForLanguage(requireContext(), code)
        }
    }

    private fun handleCurrencyChange(targetCurrency: String) {
        if (targetCurrency == currentCurrency) return

        viewLifecycleOwner.lifecycleScope.launch {
            val txCount = repository.getTransactionsCount()
            if (txCount == 0) {
                preferenceManager.setCurrencyCode(targetCurrency)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_currency_changed_to, targetCurrency),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            showConversionDialog(fromCurrency = currentCurrency, toCurrency = targetCurrency)
        }
    }

    private fun showConversionDialog(fromCurrency: String, toCurrency: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            pendingCurrencyTarget = toCurrency
            val hasInternet = hasInternetConnection()
            val savedRate = preferenceManager.getConversionRate(fromCurrency, toCurrency)
            val onlineRate = if (hasInternet) {
                CurrencyRateProvider.fetchRateOrNull(fromCurrency, toCurrency)
            } else {
                null
            }
            val fallback = CurrencyRateProvider.fallbackRate(fromCurrency, toCurrency)
            val suggestedRate = onlineRate ?: savedRate ?: fallback

            val sourceLabel = when {
                onlineRate != null -> getString(R.string.profile_rate_source_live)
                savedRate != null -> getString(R.string.profile_rate_source_saved)
                else -> getString(R.string.profile_rate_source_fallback)
            }
            CurrencyConversionBottomSheet.newInstance(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                sourceLabel = sourceLabel,
                suggestedRate = suggestedRate
            ).show(parentFragmentManager, "currency_conversion_sheet")
        }
    }

    private fun applyCurrencyConversion(fromCurrency: String, toCurrency: String, rate: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.convertAllTransactions(rate)
            preferenceManager.setConversionRate(fromCurrency, toCurrency, rate)
            if (rate != 0.0) {
                preferenceManager.setConversionRate(toCurrency, fromCurrency, 1.0 / rate)
            }
            preferenceManager.setCurrencyCode(toCurrency)
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.profile_converted_and_switched,
                    String.format("%.4f", rate),
                    toCurrency
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hasInternetConnection(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}