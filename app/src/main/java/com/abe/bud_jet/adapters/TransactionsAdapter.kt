package com.abe.bud_jet.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.R
import com.abe.bud_jet.database.models.Transaction
import com.abe.bud_jet.databinding.ItemTransactionBinding

class TransactionsAdapter : RecyclerView.Adapter<TransactionsAdapter.ViewHolder>() {

    private val items = mutableListOf<Transaction>()

    fun submitList(list: List<Transaction>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Transaction) {
            binding.tvCategory.text = item.title
            binding.tvDate.text = item.date

            val amountText = if (item.isIncome) {
                "+$${item.amount}"
            } else {
                "-$${item.amount}"
            }

            binding.tvAmount.text = amountText

            val color = if (item.isIncome) {
                binding.root.context.getColor(R.color.finance_income)
            } else {
                binding.root.context.getColor(R.color.finance_expense)
            }

            binding.tvAmount.setTextColor(color)

            binding.root.setOnClickListener {
                it.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(80)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).duration = 80
                    }
            }
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