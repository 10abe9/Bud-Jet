package com.abe.bud_jet.ui.analytics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.database.models.CategoryStat
import com.abe.bud_jet.databinding.ItemCategoryStatBinding

class CategoryStatsAdapter :
    RecyclerView.Adapter<CategoryStatsAdapter.VH>() {

    private var items: List<CategoryStat> = emptyList()

    private val colors = listOf(
        "#FF9800",
        "#2196F3",
        "#E91E63",
        "#4CAF50",
        "#9C27B0",
        "#FFC107"
    )

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

        val percent = ((item.total / total) * 100).toInt()

        holder.binding.tvName.text = item.category
        holder.binding.tvAmount.text = "$${item.total.toInt()}"
        holder.binding.tvPercent.text = "$percent%"

        holder.binding.viewColor.setBackgroundColor(
            Color.parseColor(colors[position % colors.size])
        )
    }

    override fun getItemCount() = items.size
}