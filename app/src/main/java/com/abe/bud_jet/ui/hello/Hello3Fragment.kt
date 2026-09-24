package com.abe.bud_jet.ui.hello

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.capture.CaptureAccess
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentHello3Binding
import com.abe.bud_jet.ui.capture.CaptureRationale
import com.abe.bud_jet.ui.dashboard.DashboardFragment
import com.abe.bud_jet.utils.VibrationManager

/** Onboarding step offering automatic capture; manual entry stays a full alternative. */
class Hello3Fragment : Fragment(R.layout.fragment_hello3) {

    private var openedSettings = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHello3Binding.bind(view)
        openedSettings = savedInstanceState?.getBoolean(KEY_OPENED_SETTINGS) ?: false

        binding.buttonEnable.setOnClickListener {
            VibrationManager.get().tap()
            if (CaptureAccess.isAccessGranted(requireContext())) {
                finishOnboarding()
            } else {
                openedSettings = true
                CaptureRationale.show(requireContext())
            }
        }
        binding.buttonManual.setOnClickListener {
            VibrationManager.get().tap()
            finishOnboarding()
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
    }

    private fun finishOnboarding() {
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
    }
}
