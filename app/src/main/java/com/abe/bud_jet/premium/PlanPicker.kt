package com.abe.bud_jet.premium

import android.view.View
import androidx.core.content.ContextCompat
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.ItemPlanOptionBinding

/**
 * Selectable plan cards (paywall and onboarding). Cards of plans not on sale
 * ([PremiumManager.availablePlans]) are hidden.
 */
class PlanPicker(
    allOptions: Map<Plan, ItemPlanOptionBinding>,
    initial: Plan,
    private val onSelected: (Plan) -> Unit
) {

    private val options = allOptions.filterKeys { it in PremiumManager.availablePlans }

    var selected: Plan = initial.takeIf { it in options } ?: options.keys.first()
        private set

    private var offers: Map<Plan, PremiumManager.SubscriptionOffer> = emptyMap()
    private var currentTier: Tier = Tier.FREE

    init {
        allOptions.forEach { (plan, binding) ->
            binding.root.visibility = if (plan in options) View.VISIBLE else View.GONE
        }
        options.forEach { (plan, binding) ->
            binding.tvPlanName.setText(nameRes(plan))
            binding.tvPlanFeatures.setText(featuresRes(plan))
            binding.root.setOnClickListener { select(plan) }
        }
        render()
    }

    fun select(plan: Plan) {
        if (plan == selected) return
        selected = plan
        render()
        onSelected(plan)
    }

    fun update(offers: Map<Plan, PremiumManager.SubscriptionOffer>, currentTier: Tier) {
        this.offers = offers
        this.currentTier = currentTier
        render()
    }

    private fun render() {
        options.forEach { (plan, binding) ->
            val context = binding.root.context
            val isSelected = plan == selected
            binding.root.isSelected = isSelected
            binding.root.strokeWidth = context.resources.getDimensionPixelSize(
                if (isSelected) R.dimen.plan_stroke_selected else R.dimen.plan_stroke
            )
            binding.root.strokeColor = ContextCompat.getColor(context, if (isSelected) R.color.brand_text else R.color.border)
            binding.root.setCardBackgroundColor(
                ContextCompat.getColor(context, if (isSelected) R.color.primary_container else R.color.card)
            )
            binding.ivPlanCheck.setImageResource(if (isSelected) R.drawable.ic_radio_on else R.drawable.ic_radio_off)

            val offer = offers[plan]
            binding.tvPlanPrice.text = offer?.let { context.getString(R.string.premium_per_month, it.formattedPrice) }.orEmpty()

            val badge = when {
                plan.tier == currentTier -> context.getString(R.string.plan_badge_current)
                plan == Plan.PRO -> context.getString(R.string.plan_badge_ai)
                else -> null
            }
            binding.tvPlanBadge.text = badge
            binding.tvPlanBadge.visibility = if (badge == null) View.GONE else View.VISIBLE
        }
    }

    companion object {
        fun nameRes(plan: Plan): Int = when (plan) {
            Plan.BASIC -> R.string.plan_basic_name
            Plan.PRO -> R.string.plan_pro_name
        }

        fun featuresRes(plan: Plan): Int = when (plan) {
            Plan.BASIC -> R.string.plan_basic_features
            Plan.PRO -> R.string.plan_pro_features
        }
    }
}
