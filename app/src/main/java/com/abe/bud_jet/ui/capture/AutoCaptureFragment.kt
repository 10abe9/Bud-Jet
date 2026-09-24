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
import com.abe.bud_jet.capture.CaptureNotifier
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.database.entities.PendingCaptureEntity
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentAutoCaptureBinding
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
    }

    override fun onResume() {
        super.onResume()
        // Access and battery settings are changed outside the app; refresh on return.
        renderStatus()
    }

    private fun renderStatus() {
        val context = requireContext()
        val granted = CaptureAccess.isAccessGranted(context)
        binding.tvAccessStatus.text = getString(if (granted) R.string.capture_access_on else R.string.capture_access_off)
        binding.tvAccessStatus.setTextColor(
            ContextCompat.getColor(context, if (granted) R.color.finance_income else R.color.text_secondary)
        )
        binding.btnAccess.text = getString(if (granted) R.string.capture_access_manage else R.string.capture_access_enable)
        binding.btnAccess.setOnClickListener {
            if (granted) CaptureAccess.openAccessSettings(context) else CaptureRationale.show(context)
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
