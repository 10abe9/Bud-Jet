package com.abe.bud_jet.ui.capture

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.abe.bud_jet.R
import com.abe.bud_jet.capture.CaptureAccess
import com.abe.bud_jet.capture.CaptureLog
import com.abe.bud_jet.capture.CaptureNotifier
import com.abe.bud_jet.capture.NotificationParser
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.PendingCaptureEntity
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentAutoCaptureBinding
import com.abe.bud_jet.premium.PremiumManager
import com.abe.bud_jet.premium.PremiumOfferBottomSheet
import com.abe.bud_jet.ui.operations.AddTransactionBottomSheet
import com.abe.bud_jet.utils.collectWithLifecycle
import kotlinx.coroutines.launch

/** Settings and inbox for automatic capture of payment notifications. */
class AutoCaptureFragment : Fragment(R.layout.fragment_auto_capture) {

    private var _binding: FragmentAutoCaptureBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.capture(requireContext()) }
    private val preferences by lazy { PreferenceManager.getInstance(requireContext()) }

    private val pendingAdapter = PendingCaptureAdapter(
        onConfirm = ::confirmPending,
        onDismiss = { pending ->
            viewLifecycleOwner.lifecycleScope.launch { repository.dismissPending(pending.id) }
        }
    )
    private val sourceAdapter = CaptureSourceAdapter { source, enabled ->
        viewLifecycleOwner.lifecycleScope.launch { repository.setSourceEnabled(source.packageName, enabled) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAutoCaptureBinding.bind(view)

        binding.buttonBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnBattery.setOnClickListener { CaptureAccess.openBatterySettings(requireContext()) }
        binding.btnTest.setOnClickListener { runParserTest() }
        binding.btnLogRefresh.setOnClickListener { renderLog() }
        binding.btnLogClear.setOnClickListener {
            CaptureLog.clear(requireContext())
            renderLog()
        }

        binding.rvPending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPending.adapter = pendingAdapter
        binding.rvSources.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSources.adapter = sourceAdapter
        pendingAdapter.currencyCode = preferences.getCurrencyCode()

        repository.observePending().collectWithLifecycle(viewLifecycleOwner) { pending ->
            pendingAdapter.submit(pending)
            binding.tvPendingEmpty.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
            if (pending.isEmpty()) CaptureNotifier.cancel(requireContext())
        }
        repository.observeSources().collectWithLifecycle(viewLifecycleOwner) { sources ->
            sourceAdapter.submit(sources)
        }
        // A purchase made from this screen unlocks it right away.
        PremiumManager.isPremium.collectWithLifecycle(viewLifecycleOwner) { renderStatus() }
    }

    override fun onResume() {
        super.onResume()
        // Access and battery settings are changed outside the app; refresh on return.
        renderStatus()
        renderLog()
    }

    /** Shows what the parser would do with a pasted notification text. */
    private fun runParserTest() {
        val text = binding.etTestText.text?.toString().orEmpty()
        if (text.isBlank()) return
        val result = NotificationParser.parse(null, text, preferences.getCurrencyCode())
        val verdict = getString(
            when (result) {
                is NotificationParser.ParseResult.Recognized -> R.string.capture_test_result_added
                is NotificationParser.ParseResult.Uncertain -> R.string.capture_test_result_confirm
                NotificationParser.ParseResult.Ignored -> R.string.capture_test_result_ignored
            }
        )
        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = if (result is NotificationParser.ParseResult.Ignored) {
            verdict
        } else {
            "$verdict\n${CaptureLog.describe(result)}"
        }
    }

    private fun renderLog() {
        val entries = CaptureLog.read(requireContext())
        if (entries.isEmpty()) {
            binding.tvLog.text = getString(R.string.capture_log_empty)
            return
        }
        val timeFormat = java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.getDefault())
        binding.tvLog.text = entries.joinToString("\n\n") { entry ->
            val header = "${timeFormat.format(java.util.Date(entry.time))} · ${entry.app}\n${getString(eventLabel(entry.event))}"
            if (entry.detail.isBlank()) header else "$header\n${entry.detail}"
        }
    }

    private fun eventLabel(event: CaptureLog.Event): Int = when (event) {
        CaptureLog.Event.CONNECTED -> R.string.capture_log_connected
        CaptureLog.Event.DISCONNECTED -> R.string.capture_log_disconnected
        CaptureLog.Event.ADDED -> R.string.capture_log_added
        CaptureLog.Event.TO_CONFIRM -> R.string.capture_log_to_confirm
        CaptureLog.Event.DUPLICATE -> R.string.capture_log_duplicate
        CaptureLog.Event.IGNORED -> R.string.capture_log_ignored
        CaptureLog.Event.NOT_TRACKED -> R.string.capture_log_not_tracked
    }

    private fun renderStatus() {
        val context = requireContext()
        val granted = CaptureAccess.isAccessGranted(context)
        binding.tvAccessStatus.text = getString(if (granted) R.string.capture_access_on else R.string.capture_access_off)
        binding.tvAccessStatus.setTextColor(
            ContextCompat.getColor(context, if (granted) R.color.finance_income else R.color.text_secondary)
        )
        // Automatic tracking is part of Premium: without it the button opens the paywall.
        val premium = PremiumManager.isPremium.value
        binding.tvPremiumRequired.visibility = if (premium) View.GONE else View.VISIBLE
        binding.btnAccess.text = getString(
            when {
                !premium -> R.string.capture_get_premium
                granted -> R.string.capture_access_manage
                else -> R.string.capture_access_enable
            }
        )
        binding.btnAccess.setOnClickListener {
            when {
                !premium -> PremiumOfferBottomSheet.newInstance(preferences.getCurrencyCode(), null)
                    .show(parentFragmentManager, "premium_offer")
                granted -> CaptureAccess.openAccessSettings(context)
                else -> CaptureRationale.show(context)
            }
        }

        val now = System.currentTimeMillis()
        val lastCaptured = preferences.getCaptureLastCapturedAt()
        binding.tvLastActivity.visibility = if (granted) View.VISIBLE else View.GONE
        binding.tvLastActivity.text = if (lastCaptured > 0) {
            getString(
                R.string.capture_last_captured,
                DateUtils.getRelativeTimeSpanString(lastCaptured, now, DateUtils.MINUTE_IN_MILLIS)
            )
        } else {
            getString(R.string.capture_nothing_captured_yet)
        }

        // The listener reports a heartbeat on every notification; long silence while access is
        // on usually means the system stopped it to save battery.
        val aliveAt = preferences.getCaptureListenerAliveAt()
        val stale = granted && aliveAt > 0 && now - aliveAt > STALE_AFTER_MILLIS
        binding.tvStaleWarning.visibility = if (stale) View.VISIBLE else View.GONE
        if (stale) CaptureAccess.requestRebind(context)

        binding.cardBattery.visibility =
            if (granted && !CaptureAccess.isIgnoringBatteryOptimizations(context)) View.VISIBLE else View.GONE
    }

    private fun confirmPending(pending: PendingCaptureEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isIncome = pending.isIncome ?: false
            val categoryId = repository.suggestCategoryId(pending.merchant, isIncome)
            AddTransactionBottomSheet.newPendingConfirmation(
                pendingId = pending.id,
                amount = pending.amount,
                isIncome = isIncome,
                categoryId = categoryId,
                note = pending.merchant
            ).show(parentFragmentManager, "confirm_pending_capture")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
    }
}
