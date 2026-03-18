package com.abe.bud_jet.ui.goals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.databinding.ItemLimitBinding

data class Limit(
    val category: String,
    val spent: Float,
    val limit: Float
)

class LimitAdapter : RecyclerView.Adapter<LimitAdapter.VH>() {

    private var items: List<Limit> = emptyList()

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

        val percent = ((item.spent / item.limit) * 100).toInt()

        holder.binding.tvCategory.text = item.category
        holder.binding.tvAmount.text = "$${item.spent.toInt()} / $${item.limit.toInt()}"
        holder.binding.progress.progress = percent
    }

    override fun getItemCount() = items.size
}