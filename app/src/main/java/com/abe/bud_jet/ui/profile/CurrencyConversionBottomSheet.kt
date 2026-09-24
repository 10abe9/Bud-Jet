package com.abe.bud_jet.ui.profile

import com.abe.bud_jet.utils.AmountParser
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetProfileCurrencyConversionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment

class CurrencyConversionBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetProfileCurrencyConversionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProfileCurrencyConversionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val from = requireArguments().getString(ARG_FROM).orEmpty()
        val to = requireArguments().getString(ARG_TO).orEmpty()
        val source = requireArguments().getString(ARG_SOURCE).orEmpty()
        val rate = requireArguments().getDouble(ARG_RATE)

        binding.tvCurrencyPair.text = getString(R.string.profile_convert_pair, from, to)
        binding.tvRateSource.text = source
        binding.etConversionRate.setText(AmountParser.toEditable(rate, maxFractionDigits = 6))

        binding.btnCancelConvert.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnConvert.setOnClickListener {
            val parsed = AmountParser.parse(binding.etConversionRate.text?.toString())
            if (parsed == null || parsed <= 0.0) {
                binding.etConversionRate.error = getString(R.string.profile_invalid_rate)
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putDouble(RESULT_RATE, parsed) }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "currency_conversion_result"
        const val RESULT_RATE = "result_rate"
        private const val ARG_FROM = "arg_from"
        private const val ARG_TO = "arg_to"
        private const val ARG_SOURCE = "arg_source"
        private const val ARG_RATE = "arg_rate"

        fun newInstance(
            fromCurrency: String,
            toCurrency: String,
            sourceLabel: String,
            suggestedRate: Double
        ): CurrencyConversionBottomSheet {
            return CurrencyConversionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM, fromCurrency)
                    putString(ARG_TO, toCurrency)
                    putString(ARG_SOURCE, sourceLabel)
                    putDouble(ARG_RATE, suggestedRate)
                }
            }
        }
    }
}
