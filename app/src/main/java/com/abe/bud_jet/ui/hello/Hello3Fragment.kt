package com.abe.bud_jet.ui.hello

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.capture.CaptureAccess
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentHello3Binding
import com.abe.bud_jet.premium.PremiumManager
import com.abe.bud_jet.ui.capture.CaptureRationale
import com.abe.bud_jet.ui.dashboard.DashboardFragment
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle

/**
 * Onboarding step offering Premium (automatic tracking, optional AI assistant) with a free
 * first month. Continuing with manual entry is a full alternative, not a dead end.
 */
class Hello3Fragment : Fragment(R.layout.fragment_hello3) {

    private var openedSettings = false
    private var awaitingPurchase = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHello3Binding.bind(view)
        openedSettings = savedInstanceState?.getBoolean(KEY_OPENED_SETTINGS) ?: false
        awaitingPurchase = savedInstanceState?.getBoolean(KEY_AWAITING_PURCHASE) ?: false

        PremiumManager.offer.collectWithLifecycle(viewLifecycleOwner) { offer ->
            binding.tvTerms.text = when {
                offer == null -> getString(R.string.hello3_terms_generic)
                offer.trialDays != null -> getString(R.string.hello3_terms_trial, offer.formattedPrice)
                else -> getString(R.string.hello3_terms_paid, offer.formattedPrice)
            }
        }
        // After a successful purchase continue with notification access.
        PremiumManager.isPremium.collectWithLifecycle(viewLifecycleOwner) { premium ->
            if (premium && awaitingPurchase) {
                awaitingPurchase = false
                requestCaptureAccess()
            }
        }

        binding.buttonEnable.setOnClickListener {
            VibrationManager.get().tap()
            if (PremiumManager.isPremium.value) {
                requestCaptureAccess()
            } else if (PremiumManager.launchPurchase(requireActivity())) {
                awaitingPurchase = true
            } else {
                Toast.makeText(requireContext(), R.string.premium_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        binding.buttonManual.setOnClickListener {
            VibrationManager.get().tap()
            finishOnboarding()
        }
    }

    private fun requestCaptureAccess() {
        if (CaptureAccess.isAccessGranted(requireContext())) {
            finishOnboarding()
        } else {
            openedSettings = true
            CaptureRationale.show(requireContext(), onDeclined = ::finishOnboarding)
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from system settings with access granted: continue automatically.
        if (openedSettings && CaptureAccess.isAccessGranted(requireContext())) finishOnboarding()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPENED_SETTINGS, openedSettings)
        outState.putBoolean(KEY_AWAITING_PURCHASE, awaitingPurchase)
    }

    private fun finishOnboarding() {
        if (!isAdded) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.hello3Fragment) return
        PreferenceManager.getInstance(requireContext()).setIsFirstInit(false)
        navController.getBackStackEntry(R.id.mobile_navigation)
            .savedStateHandle[DashboardFragment.KEY_PROMPT_NOTIFICATIONS_AFTER_ONBOARDING] = true
        navController.navigate(
            R.id.action_hello3Fragment_to_navigation_dashboard,
            null,
            // Replace the whole onboarding stack with a single dashboard.
            NavOptions.Builder().setPopUpTo(R.id.navigation_dashboard, true).build()
        )
    }

    companion object {
        private const val KEY_OPENED_SETTINGS = "opened_settings"
        private const val KEY_AWAITING_PURCHASE = "awaiting_purchase"
    }
}
