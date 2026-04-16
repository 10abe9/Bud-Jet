package com.abe.bud_jet.ui.goals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.databinding.ItemLimitBinding
import com.abe.bud_jet.utils.CurrencyFormatter

data class Limit(
    val goalId: Long,
    val categoryId: Long,
    val category: String,
    val spent: Double,
    val limit: Double,
    val hint: String
)

class LimitAdapter(
    private val onClick: (Limit) -> Unit = {},
    private val onLongClick: (Limit) -> Unit = {}
) : RecyclerView.Adapter<LimitAdapter.VH>() {

    private var items: List<Limit> = emptyList()
    var currencyCode: String = "USD"

    fun submit(list: List<Limit>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemLimitBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            ItemLimitBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        val percent = if (item.limit > 0) ((item.spent / item.limit) * 100).toInt() else 0

        holder.binding.tvCategory.text = item.category
        holder.binding.tvAmount.text = "${CurrencyFormatter.format(item.spent.toDouble(), currencyCode)} / ${CurrencyFormatter.format(item.limit.toDouble(), currencyCode)}"
        holder.binding.tvHint.text = item.hint
        holder.binding.progress.progress = percent.coerceIn(0, 100)
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount() = items.size
}