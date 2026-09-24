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
import com.abe.bud_jet.premium.Plan
import com.abe.bud_jet.premium.PlanPicker
import com.abe.bud_jet.premium.PremiumManager
import com.abe.bud_jet.ui.capture.CaptureRationale
import com.abe.bud_jet.ui.dashboard.DashboardFragment
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle

/**
 * Onboarding step offering the two plans (Basic: automatic tracking; Pro: plus the AI
 * assistant) with a free first month. Continuing with manual entry is a full alternative.
 */
class Hello3Fragment : Fragment(R.layout.fragment_hello3) {

    private var openedSettings = false
    private var awaitingPurchase = false
    private var planPicker: PlanPicker? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHello3Binding.bind(view)
        openedSettings = savedInstanceState?.getBoolean(KEY_OPENED_SETTINGS) ?: false
        awaitingPurchase = savedInstanceState?.getBoolean(KEY_AWAITING_PURCHASE) ?: false

        val initial = savedInstanceState?.getInt(KEY_SELECTED_PLAN, -1)
            ?.let { Plan.entries.getOrNull(it) } ?: Plan.BASIC
        lateinit var picker: PlanPicker
        fun renderTerms() {
            picker.update(PremiumManager.offers.value, PremiumManager.tier.value)
            val offer = PremiumManager.offers.value[picker.selected]
            binding.tvTerms.text = when {
                offer == null -> getString(R.string.hello3_terms_generic)
                offer.trialDays != null -> getString(R.string.hello3_terms_trial, offer.formattedPrice)
                else -> getString(R.string.hello3_terms_paid, offer.formattedPrice)
            }
        }
        picker = PlanPicker(
            allOptions = mapOf(Plan.BASIC to binding.planBasic, Plan.PRO to binding.planPro),
            initial = initial,
            onSelected = { renderTerms() }
        )
        planPicker = picker
        PremiumManager.offers.collectWithLifecycle(viewLifecycleOwner) { renderTerms() }
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
            } else if (PremiumManager.launchPurchase(requireActivity(), picker.selected)) {
                awaitingPurchase = true
            } else {
                Toast.makeText(requireContext(), R.string.premium_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        CaptureRationale.listen(this, onDeclined = ::finishOnboarding)
        binding.buttonManual.setOnClickListener {
            VibrationManager.get().tap()
            finishOnboarding()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        planPicker = null
    }

    private fun requestCaptureAccess() {
        if (CaptureAccess.isAccessGranted(requireContext())) {
            finishOnboarding()
        } else {
            openedSettings = true
            CaptureRationale.show(this)
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
        planPicker?.let { outState.putInt(KEY_SELECTED_PLAN, it.selected.ordinal) }
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
        private const val KEY_SELECTED_PLAN = "selected_plan"
    }
}
