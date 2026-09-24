package com.abe.bud_jet.ui.hello

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.FragmentHello1Binding
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.ui.dashboard.DashboardFragment
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.VibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception

class Hello1Fragment : Fragment() {

    private var  _binding : FragmentHello1Binding? = null
    private val binding get() = _binding!!
    private lateinit var vibrator: VibrationManager
    // Read once: the demo animator may still tick after the view is gone.
    private var demoCurrency: String = "USD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHello1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vibrator = VibrationManager.get()
        demoCurrency = PreferenceManager.getInstance(requireContext()).getCurrencyCode()

        playDemoAnimation()
        setupButtons()
    }

    private fun setupButtons(){
        binding.addIncomeButton.setOnClickListener {
            vibrator.success()
        }

        binding.addExpenceButton.setOnClickListener {
            vibrator.success()
        }

        binding.buttonGetStarted.setOnClickListener {
            vibrator.success()
            findNavController().navigate(R.id.action_hello1Fragment_to_hello2Fragment)
        }
        binding.buttonSkip.setOnClickListener {
            // Skipping is a valid choice, so no "error" haptic here.
            vibrator.tap()
            PreferenceManager.getInstance(requireContext()).setIsFirstInit(false)
            findNavController().getBackStackEntry(R.id.mobile_navigation)
                .savedStateHandle[DashboardFragment.KEY_PROMPT_NOTIFICATIONS_AFTER_ONBOARDING] = true
            findNavController().navigate(R.id.action_hello1Fragment_to_navigation_dashboard,
                null,
                NavOptions.Builder()
                    .setPopUpTo(R.id.hello1Fragment, true)
                    .build())
        }
    }

    private fun animateBalance(
        textView: TextView,
        from: Float,
        to: Float,
        duration: Long = 800
    ) {
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = duration

        animator.addUpdateListener {
            val value = it.animatedValue as Float
            textView.text = CurrencyFormatter.format(value.toDouble(), demoCurrency)
        }

        animator.start()
    }

    private fun playDemoAnimation() {

        val balanceView = binding.textBalance
        val statusView = binding.textBalanceStatus

        viewLifecycleOwner.lifecycleScope.launch {

            var currentBalance = 4250.80f

            // Demo events (amounts only). Labels are not shown in this onboarding animation.
            val events = listOf(-120.40f, 240f, -86.40f, 1250f)

            delay(1000)

            for (amount in events) {

                val newBalance = currentBalance + amount

                animateBalance(binding.textBalance, currentBalance, newBalance)

                binding.textBalanceStatus.text =
                    CurrencyFormatter.formatDelta(amount.toDouble(), demoCurrency)

                binding.textBalanceStatus.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (amount > 0) R.color.finance_income
                        else R.color.finance_expense
                    )
                )

                currentBalance = newBalance

                delay(2000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}