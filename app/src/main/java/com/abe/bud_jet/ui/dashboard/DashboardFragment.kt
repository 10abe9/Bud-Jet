package com.abe.bud_jet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abe.bud_jet.databinding.FragmentDashboardBinding
import com.abe.bud_jet.utils.VibrationManager

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var vibrator: VibrationManager
    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vibrator = VibrationManager.get()

        setupUi()
        setupButtons()
    }

    private fun setupUi() {
        updateTextBalance()
    }

    private fun updateTextBalance() {
        val textView: TextView = binding.textBalance
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
    }

    private fun setupButtons() {
        binding.addIncomeButton.setOnClickListener {
            vibrator.error()
        }
        binding.addExpenceButton.setOnClickListener {
            vibrator.tap()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}