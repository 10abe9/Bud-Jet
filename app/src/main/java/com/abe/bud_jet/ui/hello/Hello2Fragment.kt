package com.abe.bud_jet.ui.hello

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.FragmentHello2Binding
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.utils.VibrationManager
import com.google.android.material.chip.Chip

class Hello2Fragment : Fragment() {

    private var _binding : FragmentHello2Binding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHello2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager.getInstance(requireContext())
        preselectSavedCurrency()
        setupCurrency()
        setupGoals()
        updateContinueAction(hasCurrencySelected())
    }

    private fun hasCurrencySelected(): Boolean {
        return binding.chipCurrencyGroup.checkedChipId != View.NO_ID
    }

    private fun updateContinueAction(canContinue: Boolean) {
        if (!canContinue) {
            binding.buttonContinue.setOnClickListener {
                VibrationManager.get().error()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.hello2_choose_currency),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        binding.buttonContinue.setOnClickListener {
            saveSelectedCurrency()
            VibrationManager.get().success()
            findNavController().navigate(
                R.id.action_hello2Fragment_to_navigation_dashboard,
                null,
                NavOptions.Builder()
                    .setPopUpTo(R.id.hello2Fragment, true)
                    .setPopUpTo(R.id.hello1Fragment, true)
                    .build()
            )
        }
    }

    private fun setupCurrency(){
        binding.chipCurrencyGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            updateContinueAction(checkedIds.isNotEmpty())
        }
    }

    private fun saveSelectedCurrency() {
        val checkedId = binding.chipCurrencyGroup.checkedChipId
        if (checkedId == View.NO_ID) return
        val selectedChip = binding.chipCurrencyGroup.findViewById<Chip>(checkedId) ?: return
        val currencyCode = selectedChip.text.toString().trim().take(3).uppercase()
        preferenceManager.setCurrencyCode(currencyCode)
    }

    private fun preselectSavedCurrency() {
        val saved = preferenceManager.getCurrencyCode()
        for (i in 0 until binding.chipCurrencyGroup.childCount) {
            val chip = binding.chipCurrencyGroup.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString().startsWith(saved)) {
                chip.isChecked = true
                return
            }
        }
    }
    private fun setupGoals(){
        binding.chipGoalsGroup.setOnCheckedStateChangeListener { group, checkedIds ->

            if (checkedIds.isEmpty()) {
                binding.selectedGoalsLabel.visibility = View.GONE
                binding.textSelectedGoals.visibility = View.GONE
            } else {

                val selected = checkedIds.map {
                    val chip = group.findViewById<Chip>(it)
                    chip.text.toString()
                }

                binding.textSelectedGoals.text = selected.joinToString(", ")

                binding.selectedGoalsLabel.visibility = View.VISIBLE
                binding.textSelectedGoals.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}