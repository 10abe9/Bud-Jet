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
import com.abe.bud_jet.utils.VibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception

class Hello1Fragment : Fragment() {

    private var  _binding : FragmentHello1Binding? = null
    private val binding get() = _binding!!
    private lateinit var vibrator: VibrationManager

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
            vibrator.error()
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
            textView.text = "$" + String.format("%,.2f", value)
        }

        animator.start()
    }

    private fun playDemoAnimation() {

        val balanceView = binding.textBalance
        val statusView = binding.textBalanceStatus

        viewLifecycleOwner.lifecycleScope.launch {

            var currentBalance = 4250.80f

            val events = listOf(
                Pair(-120.40f, "Uber"),
                Pair(240f, "Deal"),
                Pair(-86.40f, "Food"),
                Pair(1250f, "Salary")
            )

            delay(1000)

            for ((amount, label) in events) {

                val newBalance = currentBalance + amount

                animateBalance(binding.textBalance, currentBalance, newBalance)

                binding.textBalanceStatus.text =
                    (if (amount > 0) "+ $" else "- $") +
                            String.format("%.2f", kotlin.math.abs(amount))

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