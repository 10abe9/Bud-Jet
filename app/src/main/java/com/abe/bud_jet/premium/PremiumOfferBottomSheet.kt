package com.abe.bud_jet.premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetPremiumOfferBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.utils.CurrencyFormatter
import com.abe.bud_jet.utils.VibrationManager

/** Paywall: shows the user's own savings math when there is enough data. */
class PremiumOfferBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetPremiumOfferBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPremiumOfferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val currency = args.getString(ARG_CURRENCY).orEmpty()
        val price = PremiumPricing.monthlyPrice(currency)
        val priceText = getString(R.string.premium_per_month, CurrencyFormatter.format(price, currency))

        if (args.getBoolean(ARG_HAS_OFFER)) {
            val spend = args.getDouble(ARG_SPEND)
            val savings = args.getDouble(ARG_SAVINGS)
            binding.tvGenericBody.visibility = View.GONE
            binding.layoutSavingsMath.visibility = View.VISIBLE
            binding.tvSpendValue.text =
                getString(R.string.premium_per_month, CurrencyFormatter.format(spend, currency))
            binding.tvSavingsValue.text =
                getString(R.string.premium_per_month, CurrencyFormatter.formatDelta(savings, currency))
            binding.tvPriceValue.text =
                getString(R.string.premium_per_month, CurrencyFormatter.formatDelta(-price, currency))
            binding.tvBenefitValue.text =
                getString(R.string.premium_per_month, CurrencyFormatter.formatDelta(savings - price, currency))
            binding.btnBuy.text = getString(R.string.premium_sheet_buy, CurrencyFormatter.format(price, currency))
        } else {
            binding.btnBuy.text = "${getString(R.string.premium_sheet_buy_generic)} · $priceText"
        }

        binding.btnBuy.setOnClickListener {
            VibrationManager.get().success()
            // Google Play Billing purchase flow plugs in here (next step).
            Toast.makeText(
                requireContext(),
                getString(R.string.profile_premium_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
            dismissAllowingStateLoss()
        }
        binding.btnNotNow.setOnClickListener { dismissAllowingStateLoss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CURRENCY = "arg_currency"
        private const val ARG_HAS_OFFER = "arg_has_offer"
        private const val ARG_SPEND = "arg_spend"
        private const val ARG_SAVINGS = "arg_savings"

        fun newInstance(currencyCode: String, offer: SavingsOffer?): PremiumOfferBottomSheet {
            return PremiumOfferBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENCY, currencyCode)
                    putBoolean(ARG_HAS_OFFER, offer != null)
                    offer?.let {
                        putDouble(ARG_SPEND, it.monthlySpend)
                        putDouble(ARG_SAVINGS, it.monthlySavings)
                    }
                }
            }
        }
    }
}
