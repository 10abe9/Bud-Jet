package com.abe.bud_jet.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.FragmentProfileBinding

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding

    private var currentCurrency = "USD"
    private var notificationsEnabled = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProfileBinding.bind(view)

        setupUI()
        setupClicks()
    }

    private fun setupUI() {
        binding.tvSummary.text = "$2450 this month"
        binding.tvGoalSummary.text = "Saving $320 / $500"

        // 💱 Currency
        binding.tvCurrency.text = currentCurrency

        // 🔔 Notifications
        binding.switchNotifications.isChecked = notificationsEnabled
    }

    private fun setupClicks() {

        // 💱 Currency click (row)
        binding.rowCurrency.setOnClickListener {
            val currencies = arrayOf("USD", "EUR", "RUB", "KZT")

            AlertDialog.Builder(requireContext())
                .setTitle("Select currency")
                .setItems(currencies) { _, which ->
                    currentCurrency = currencies[which]
                    binding.tvCurrency.text = currentCurrency
                }
                .show()
        }

        // 🔔 Notifications switch
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            notificationsEnabled = isChecked

            Toast.makeText(
                requireContext(),
                if (isChecked) "Notifications ON" else "Notifications OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnExport.setOnClickListener {
            Toast.makeText(requireContext(), "Export coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            Toast.makeText(requireContext(), "Reset coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.cardPremium.setOnClickListener {
            Toast.makeText(requireContext(), "Premium coming soon 💸", Toast.LENGTH_SHORT).show()
        }

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}