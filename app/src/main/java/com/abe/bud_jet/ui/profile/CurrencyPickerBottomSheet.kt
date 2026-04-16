package com.abe.bud_jet.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.databinding.BottomSheetProfileCurrencyPickerBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.utils.CurrencyFormatter
import com.google.android.material.chip.Chip

class CurrencyPickerBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetProfileCurrencyPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProfileCurrencyPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val current = requireArguments().getString(ARG_CURRENT).orEmpty()
        CURRENCIES.forEach { code ->
            val chip = Chip(requireContext()).apply {
                text = "${code} ${CurrencyFormatter.symbolFor(code)}"
                isCheckable = true
                isCheckedIconVisible = false
                tag = code
            }
            binding.chipCurrencies.addView(chip)
            if (code == current) chip.isChecked = true
        }

        binding.btnCancelCurrency.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnSaveCurrency.setOnClickListener {
            val selected = binding.chipCurrencies.checkedChipId
            val code = binding.chipCurrencies.findViewById<Chip>(selected)?.tag as? String ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString(RESULT_CODE, code) }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "currency_picker_result"
        const val RESULT_CODE = "currency_code"
        private const val ARG_CURRENT = "arg_current"
        private val CURRENCIES = listOf("USD", "EUR", "PLN", "MXN", "BRL", "INR", "RUB")

        fun newInstance(currentCurrency: String): CurrencyPickerBottomSheet {
            return CurrencyPickerBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT, currentCurrency) }
            }
        }
    }
}
