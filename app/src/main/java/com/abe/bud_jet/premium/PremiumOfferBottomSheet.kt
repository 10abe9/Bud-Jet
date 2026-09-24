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
import com.abe.bud_jet.utils.collectWithLifecycle

/** Paywall: two plans, plus the user's own savings math when there is enough data. */
class PremiumOfferBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetPremiumOfferBinding? = null
    private val binding get() = _binding!!

    private lateinit var planPicker: PlanPicker

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
        val price = PremiumPricing.monthlyPrice(currency, SavingsOfferSource.PITCH_PLAN)
        if (Plan.PRO !in PremiumManager.availablePlans) {
            binding.tvGenericBody.setText(R.string.premium_sheet_generic_body_basic)
        }

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

        // Basic users can only move up, so Pro is preselected for them.
        val requested = Plan.entries.getOrNull(args.getInt(ARG_PLAN, Plan.BASIC.ordinal)) ?: Plan.BASIC
        val restored = savedInstanceState?.getInt(KEY_SELECTED_PLAN, -1)?.let { Plan.entries.getOrNull(it) }
        val initial = restored ?: if (PremiumManager.tier.value == Tier.BASIC) Plan.PRO else requested
        planPicker = PlanPicker(
            allOptions = mapOf(Plan.BASIC to binding.planBasic, Plan.PRO to binding.planPro),
            initial = initial,
            onSelected = { renderPurchase() }
        )
        PremiumManager.offers.collectWithLifecycle(viewLifecycleOwner) { renderPurchase() }
        PremiumManager.tier.collectWithLifecycle(viewLifecycleOwner) { renderPurchase() }
        binding.btnNotNow.setOnClickListener { dismissAllowingStateLoss() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::planPicker.isInitialized) outState.putInt(KEY_SELECTED_PLAN, planPicker.selected.ordinal)
    }

    /** Button and terms follow the selected plan, the live Play offer and the current plan. */
    private fun renderPurchase() {
        if (_binding == null) return
        val offers = PremiumManager.offers.value
        val tier = PremiumManager.tier.value
        planPicker.update(offers, tier)
        val plan = planPicker.selected
        val offer = offers[plan]

        if (plan.tier == tier) {
            binding.tvTerms.text = getString(R.string.premium_active_terms)
            binding.btnBuy.text = getString(R.string.premium_manage_subscription)
            binding.btnBuy.setOnClickListener {
                openUrl(PremiumManager.manageSubscriptionUrl(requireContext().packageName))
                dismissAllowingStateLoss()
            }
            return
        }

        val planName = getString(PlanPicker.nameRes(plan))
        val trialDays = offer?.trialDays
        binding.tvTerms.text = when {
            offer == null -> getString(R.string.premium_unavailable)
            tier == Tier.BASIC -> getString(R.string.plan_upgrade_terms, offer.formattedPrice)
            tier == Tier.PRO -> getString(R.string.plan_downgrade_terms, offer.formattedPrice)
            trialDays != null -> {
                val firstCharge = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
                    .format(java.util.Date(System.currentTimeMillis() + trialDays * DAY_MILLIS))
                getString(R.string.premium_trial_terms, firstCharge, offer.formattedPrice)
            }
            else -> getString(R.string.premium_terms, offer.formattedPrice)
        }
        binding.btnBuy.text = when {
            offer == null -> getString(R.string.plan_buy_generic, planName)
            tier != Tier.FREE -> getString(R.string.plan_switch_to, planName, offer.formattedPrice)
            trialDays != null -> getString(R.string.premium_try_free, trialDays)
            else -> getString(R.string.plan_buy, planName, offer.formattedPrice)
        }
        binding.btnBuy.setOnClickListener {
            VibrationManager.get().success()
            if (!PremiumManager.launchPurchase(requireActivity(), plan)) {
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
        private const val ARG_PLAN = "arg_plan"
        private const val KEY_SELECTED_PLAN = "selected_plan"

        /** [plan] is preselected: Pro when the user came for the AI assistant. */
        fun newInstance(
            currencyCode: String,
            offer: SavingsOffer?,
            plan: Plan = Plan.BASIC
        ): PremiumOfferBottomSheet {
            return PremiumOfferBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENCY, currencyCode)
                    putInt(ARG_PLAN, plan.ordinal)
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
