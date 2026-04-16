package com.abe.bud_jet.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.R
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.databinding.ItemTransactionBinding
import com.abe.bud_jet.utils.CurrencyFormatter
import com.google.android.material.card.MaterialCardView

class TransactionsAdapter(
    private val onTransactionClick: (Transaction) -> Unit = {},
    private val onTransactionLongClick: (Transaction) -> Unit = {}
) : RecyclerView.Adapter<TransactionsAdapter.ViewHolder>() {

    private val items = mutableListOf<Transaction>()
    private var highlightedTransactionId: Long? = null
    var currencyCode: String = "USD"

    fun submitList(list: List<Transaction>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun findPositionById(id: Long): Int = items.indexOfFirst { it.id == id }

    fun highlightTransaction(id: Long) {
        highlightedTransactionId = id
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Transaction) {
            binding.tvCategory.text = displayTitle(item)
            binding.tvDate.text = item.date

            val amountText = if (item.isIncome) {
                "+${CurrencyFormatter.format(item.amount, currencyCode)}"
            } else {
                "-${CurrencyFormatter.format(item.amount, currencyCode)}"
            }

            binding.tvAmount.text = amountText

            val color = if (item.isIncome) {
                binding.root.context.getColor(R.color.finance_income)
            } else {
                binding.root.context.getColor(R.color.finance_expense)
            }

            binding.tvAmount.setTextColor(color)
            binding.categoryTicket.setColorFilter(resolveCategoryTicketColor(item))

            val card = binding.root as? MaterialCardView
            card?.strokeWidth = 1
            card?.strokeColor = binding.root.context.getColor(R.color.border)

            if (highlightedTransactionId == item.id) {
                card?.strokeWidth = 2
                card?.strokeColor = binding.root.context.getColor(R.color.brand_primary)
                binding.root.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(120)
                    .withEndAction {
                        binding.root.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
                highlightedTransactionId = null
            }

            binding.root.setOnClickListener {
                it.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(80)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).duration = 80
                        onTransactionClick(item)
                    }
            }

            binding.root.setOnLongClickListener {
                onTransactionLongClick(item)
                true
            }
        }

        private fun displayTitle(item: Transaction): String {
            val note = item.note?.trim().orEmpty()
            if (note.isBlank()) return item.title
            return if (note.length > 15) note.take(15) + "..." else note
        }

        private fun resolveCategoryTicketColor(item: Transaction): Int {
            val fallback = binding.root.context.getColor(R.color.black)
            val hex = item.categoryColorHex?.takeIf { it.startsWith("#") } ?: return fallback
            return runCatching { Color.parseColor(hex) }.getOrElse { fallback }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}