package com.abe.bud_jet.ui.analytics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.database.models.CategoryStat
import com.abe.bud_jet.databinding.ItemCategoryStatBinding
import com.abe.bud_jet.utils.CurrencyFormatter

class CategoryStatsAdapter :
    RecyclerView.Adapter<CategoryStatsAdapter.VH>() {

    private var items: List<CategoryStat> = emptyList()
    var currencyCode: String = "USD"

    fun submit(list: List<CategoryStat>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemCategoryStatBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            ItemCategoryStatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val total = items.sumOf { it.total.toDouble() }.toFloat()

        val percent = if (total > 0f) ((item.total / total) * 100).toInt() else 0

        holder.binding.tvName.text = item.category
        holder.binding.tvAmount.text = CurrencyFormatter.format(item.total.toDouble(), currencyCode)
        holder.binding.tvPercent.text = "$percent%"

        holder.binding.viewColor.setBackgroundColor(
            runCatching { Color.parseColor(item.colorHex) }.getOrDefault(Color.GRAY)
        )
    }

    override fun getItemCount() = items.size
}