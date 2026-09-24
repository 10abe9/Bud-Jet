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
        }

        renderPurchase(PremiumManager.offer.value)
        binding.btnNotNow.setOnClickListener { dismissAllowingStateLoss() }
    }

    /** Button and terms follow the live Play offer; Premium users get "manage" instead. */
    private fun renderPurchase(offer: PremiumManager.SubscriptionOffer?) {
        if (PremiumManager.isPremium.value) {
            binding.tvTerms.text = getString(R.string.premium_active_terms)
            binding.btnBuy.text = getString(R.string.premium_manage_subscription)
            binding.btnBuy.setOnClickListener {
                openUrl(PremiumManager.manageSubscriptionUrl(requireContext().packageName))
                dismissAllowingStateLoss()
            }
            return
        }
        val trialDays = offer?.trialDays
        binding.tvTerms.text = when {
            offer == null -> getString(R.string.premium_unavailable)
            trialDays != null -> {
                val firstCharge = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
                    .format(java.util.Date(System.currentTimeMillis() + trialDays * DAY_MILLIS))
                getString(R.string.premium_trial_terms, firstCharge, offer.formattedPrice)
            }
            else -> getString(R.string.premium_terms, offer.formattedPrice)
        }
        binding.btnBuy.text = when {
            offer == null -> getString(R.string.premium_sheet_buy_generic)
            trialDays != null -> getString(R.string.premium_try_free, trialDays)
            else -> getString(R.string.premium_sheet_buy, offer.formattedPrice)
        }
        binding.btnBuy.setOnClickListener {
            VibrationManager.get().success()
            if (!PremiumManager.launchPurchase(requireActivity())) {
                Toast.makeText(requireContext(), getString(R.string.premium_unavailable), Toast.LENGTH_LONG).show()
            }
            dismissAllowingStateLoss()
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
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
