package com.abe.bud_jet.ui.capture

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abe.bud_jet.R
import com.abe.bud_jet.database.entities.CaptureSourceEntity
import com.abe.bud_jet.database.entities.PendingCaptureEntity
import com.abe.bud_jet.databinding.ItemCaptureSourceBinding
import com.abe.bud_jet.databinding.ItemPendingCaptureBinding
import com.abe.bud_jet.utils.CurrencyFormatter

class PendingCaptureAdapter(
    private val onConfirm: (PendingCaptureEntity) -> Unit,
    private val onDismiss: (PendingCaptureEntity) -> Unit
) : RecyclerView.Adapter<PendingCaptureAdapter.VH>() {

    private var items: List<PendingCaptureEntity> = emptyList()
    var currencyCode: String = "USD"

    fun submit(list: List<PendingCaptureEntity>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemPendingCaptureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPendingCaptureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        holder.binding.tvApp.text = item.appLabel ?: item.packageName
        holder.binding.tvText.text = item.text
        holder.binding.tvAmount.text = item.amount?.let { amount ->
            // Show the currency from the notification when it differs from the app currency.
            val currency = item.currencyCode ?: currencyCode
            val formatted = CurrencyFormatter.format(amount, currency)
            when (item.isIncome) {
                true -> "+$formatted"
                false -> "-$formatted"
                null -> formatted
            }
        } ?: context.getString(R.string.capture_pending_no_amount)
        holder.binding.btnConfirm.setOnClickListener { onConfirm(item) }
        holder.binding.btnDismiss.setOnClickListener { onDismiss(item) }
    }

    override fun getItemCount() = items.size
}

class CaptureSourceAdapter(
    private val onToggle: (CaptureSourceEntity, Boolean) -> Unit
) : RecyclerView.Adapter<CaptureSourceAdapter.VH>() {

    private var items: List<CaptureSourceEntity> = emptyList()

    fun submit(list: List<CaptureSourceEntity>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemCaptureSourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCaptureSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        holder.binding.tvLabel.text = item.appLabel
        holder.binding.tvInfo.text = if (item.enabled) {
            context.getString(R.string.capture_source_tracked)
        } else {
            context.resources.getQuantityString(
                R.plurals.capture_source_detected,
                item.detectedCount,
                item.detectedCount
            )
        }
        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        holder.binding.switchEnabled.isChecked = item.enabled
        holder.binding.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }
    }

    override fun getItemCount() = items.size
}
