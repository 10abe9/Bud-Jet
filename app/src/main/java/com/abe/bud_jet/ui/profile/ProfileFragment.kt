package com.abe.bud_jet.ui.profile

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.net.Uri
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
import com.abe.bud_jet.database.BackupSnapshot
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.CategoryEntity
import com.abe.bud_jet.database.entities.GoalEntity
import com.abe.bud_jet.database.entities.TransactionEntity
import com.abe.bud_jet.database.entities.TransactionType
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentProfileBinding
import com.abe.bud_jet.utils.LocaleManager
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.CurrencyRateProvider
import com.abe.bud_jet.utils.collectWithLifecycle
import com.abe.bud_jet.notifications.NotificationReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var preferenceManager: PreferenceManager
    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }

    private var currentCurrency = "USD"
    private var pendingCurrencyTarget: String? = null
    private var notificationsEnabled = false
    private var currentInitialBalance = 0.0
    private val fileNameFormatter = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

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

    private val createCsvDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri ?: return@registerForActivityResult
            exportTransactionsCsv(uri)
        }

    private val createBackupDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            exportBackupJson(uri)
        }

    private val restoreBackupDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            confirmRestoreFromBackup(uri)
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

        binding.tvCurrency.text = currentCurrency
        binding.tvLanguage.text = LocaleManager.displayNameForLanguage(
            requireContext(),
            preferenceManager.getAppLanguage()
        )
        notificationsEnabled = preferenceManager.isNotificationsEnabled()
        binding.switchNotifications.isChecked = notificationsEnabled
        updateExportAvailabilityUi()
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
            if (!preferenceManager.isPremiumEnabled()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_export_premium_required),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val fileName = "bud-jet-transactions-${fileNameFormatter.format(System.currentTimeMillis())}.csv"
            createCsvDocumentLauncher.launch(fileName)
        }
        binding.btnBackup.setOnClickListener {
            val fileName = "bud-jet-backup-${fileNameFormatter.format(System.currentTimeMillis())}.json"
            createBackupDocumentLauncher.launch(fileName)
        }
        binding.btnRestore.setOnClickListener {
            restoreBackupDocumentLauncher.launch("application/json")
        }
        binding.btnPrivacy.setOnClickListener {
            showPrivacyInfoDialog()
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

    private fun updateExportAvailabilityUi() {
        val isPremium = preferenceManager.isPremiumEnabled()
        binding.btnExport.isEnabled = isPremium
        binding.btnExport.isClickable = isPremium
        binding.btnExport.alpha = if (isPremium) 1f else 0.55f
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

    private fun showPrivacyInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_privacy_title))
            .setMessage(getString(R.string.profile_privacy_message))
            .setPositiveButton(getString(R.string.common_continue), null)
            .show()
    }

    private fun exportTransactionsCsv(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) { repository.createBackupSnapshot() }
                val csv = buildTransactionsCsv(snapshot)
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        requireNotNull(writer) { "Output stream unavailable" }
                        writer.write(csv)
                    }
                }
            }
            Toast.makeText(
                requireContext(),
                if (result.isSuccess) getString(R.string.profile_export_success) else getString(R.string.profile_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun exportBackupJson(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) { repository.createBackupSnapshot() }
                val json = buildBackupJson(snapshot)
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        requireNotNull(writer) { "Output stream unavailable" }
                        writer.write(json)
                    }
                }
            }
            Toast.makeText(
                requireContext(),
                if (result.isSuccess) getString(R.string.profile_backup_success) else getString(R.string.profile_backup_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmRestoreFromBackup(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_restore_confirm_title))
            .setMessage(getString(R.string.profile_restore_confirm_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.common_apply)) { _, _ ->
                restoreBackupJson(uri)
            }
            .show()
    }

    private fun restoreBackupJson(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val parsed = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                        requireNotNull(reader) { "Input stream unavailable" }
                        val json = reader.readText()
                        parseBackupJson(json)
                    }
                }
                withContext(Dispatchers.IO) {
                    repository.restoreBackupSnapshot(parsed.snapshot)
                }
                applyBackupPreferences(parsed.preferences)
            }
            if (result.isSuccess) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_restore_success),
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().recreate()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_restore_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildTransactionsCsv(snapshot: BackupSnapshot): String {
        val categoriesById = snapshot.categories.associateBy({ it.id }, { it.name })
        val header = "id,type,amount,category,note,timestamp_ms\n"
        val body = snapshot.transactions.joinToString(separator = "\n") { tx ->
            val category = tx.categoryId?.let { categoriesById[it] }.orEmpty()
            listOf(
                tx.id.toString(),
                tx.type.name,
                tx.amount.toString(),
                csvEscape(category),
                csvEscape(tx.note.orEmpty()),
                tx.timestamp.toString()
            ).joinToString(",")
        }
        return if (body.isBlank()) header else header + body + "\n"
    }

    private fun csvEscape(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun buildBackupJson(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put(
            "preferences",
            JSONObject().apply {
                put("currencyCode", preferenceManager.getCurrencyCode())
                put("appLanguage", preferenceManager.getAppLanguage())
                put("notificationsEnabled", preferenceManager.isNotificationsEnabled())
                put("initialBalance", preferenceManager.getInitialBalance())
            }
        )
        root.put(
            "categories",
            JSONArray().apply {
                snapshot.categories.forEach { category ->
                    put(
                        JSONObject().apply {
                            put("id", category.id)
                            put("name", category.name)
                            put("icon", category.icon)
                            put("color", category.color)
                            put("isDefault", category.isDefault)
                            put("isIncome", category.isIncome)
                            put("isCustom", category.isCustom)
                        }
                    )
                }
            }
        )
        root.put(
            "goals",
            JSONArray().apply {
                snapshot.goals.forEach { goal ->
                    put(
                        JSONObject().apply {
                            put("id", goal.id)
                            put("categoryId", goal.categoryId)
                            put("targetAmount", goal.targetAmount)
                            put("currentAmount", goal.currentAmount)
                            put("deadline", goal.deadline)
                        }
                    )
                }
            }
        )
        root.put(
            "transactions",
            JSONArray().apply {
                snapshot.transactions.forEach { tx ->
                    put(
                        JSONObject().apply {
                            put("id", tx.id)
                            put("amount", tx.amount)
                            put("type", tx.type.name)
                            put("categoryId", tx.categoryId)
                            put("note", tx.note)
                            put("timestamp", tx.timestamp)
                        }
                    )
                }
            }
        )
        return root.toString(2)
    }

    private data class BackupPreferences(
        val currencyCode: String,
        val appLanguage: String,
        val notificationsEnabled: Boolean,
        val initialBalance: Double
    )

    private data class ParsedBackup(
        val snapshot: BackupSnapshot,
        val preferences: BackupPreferences
    )

    private fun parseBackupJson(json: String): ParsedBackup {
        val root = JSONObject(json)
        val preferencesJson = root.optJSONObject("preferences") ?: JSONObject()

        val categories = root.optJSONArray("categories").toCategoryEntities()
        val goals = root.optJSONArray("goals").toGoalEntities()
        val transactions = root.optJSONArray("transactions").toTransactionEntities()

        val preferences = BackupPreferences(
            currencyCode = preferencesJson.optString("currencyCode", "USD"),
            appLanguage = preferencesJson.optString("appLanguage", "en"),
            notificationsEnabled = preferencesJson.optBoolean("notificationsEnabled", false),
            initialBalance = preferencesJson.optDouble("initialBalance", 0.0)
        )

        return ParsedBackup(
            snapshot = BackupSnapshot(
                transactions = transactions,
                categories = categories,
                goals = goals
            ),
            preferences = preferences
        )
    }

    private fun JSONArray?.toCategoryEntities(): List<CategoryEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    CategoryEntity(
                        id = item.optLong("id", 0L),
                        name = item.optString("name"),
                        icon = item.optString("icon").takeIf { item.has("icon") && !item.isNull("icon") },
                        color = item.optString("color").takeIf { item.has("color") && !item.isNull("color") },
                        isDefault = item.optBoolean("isDefault", true),
                        isIncome = item.optBoolean("isIncome", false),
                        isCustom = item.optBoolean("isCustom", false)
                    )
                )
            }
        }
    }

    private fun JSONArray?.toGoalEntities(): List<GoalEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    GoalEntity(
                        id = item.optLong("id", 0L),
                        categoryId = item.optLong("categoryId", 0L).takeIf { item.has("categoryId") && !item.isNull("categoryId") },
                        targetAmount = item.optDouble("targetAmount", 0.0),
                        currentAmount = item.optDouble("currentAmount", 0.0),
                        deadline = item.optLong("deadline", 0L).takeIf { item.has("deadline") && !item.isNull("deadline") }
                    )
                )
            }
        }
    }

    private fun JSONArray?.toTransactionEntities(): List<TransactionEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val type = runCatching {
                    TransactionType.valueOf(item.optString("type", TransactionType.EXPENSE.name))
                }.getOrDefault(TransactionType.EXPENSE)
                add(
                    TransactionEntity(
                        id = item.optLong("id", 0L),
                        amount = item.optDouble("amount", 0.0),
                        type = type,
                        categoryId = item.optLong("categoryId", 0L).takeIf { item.has("categoryId") && !item.isNull("categoryId") },
                        note = item.optString("note").takeIf { item.has("note") && !item.isNull("note") },
                        timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun applyBackupPreferences(preferences: BackupPreferences) {
        preferenceManager.setCurrencyCode(preferences.currencyCode)
        preferenceManager.setAppLanguage(preferences.appLanguage)
        preferenceManager.setInitialBalance(preferences.initialBalance)
        preferenceManager.setNotificationsEnabled(preferences.notificationsEnabled)
        LocaleManager.applyAppLanguage(preferences.appLanguage)
        if (preferences.notificationsEnabled) {
            NotificationReminderScheduler.scheduleDailyExpenseReminder(requireContext())
        } else {
            NotificationReminderScheduler.cancelDailyExpenseReminder(requireContext())
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