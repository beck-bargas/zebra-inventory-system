package com.beck.tirescanner

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanEntry(
    val mode: String,
    val quantity: Int,
    val tireDescription: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ScanHistoryAdapter : RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modeBadge: TextView = view.findViewById(R.id.modeBadge)
        val tireDescText: TextView = view.findViewById(R.id.tireDescText)
        val quantityText: TextView = view.findViewById(R.id.quantityText)
        val timeText: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        holder.modeBadge.text = entry.mode
        holder.modeBadge.backgroundTintList = ColorStateList.valueOf(
            if (entry.mode == "IN") Color.parseColor("#2E7D32")
            else Color.parseColor("#C62828")
        )

        holder.tireDescText.text = entry.tireDescription
        holder.quantityText.text = "Qty: ${entry.quantity}"
        holder.timeText.text = timeFormat.format(Date(entry.timestamp))
    }

    override fun getItemCount() = entries.size

    fun addEntry(entry: ScanEntry) {
        entries.add(0, entry)
        notifyItemInserted(0)
    }

    fun clear() {
        val size = entries.size
        entries.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getCount() = entries.size

    companion object {
        // Persists across activity recreation for the lifetime of the app process
        val entries = mutableListOf<ScanEntry>()
    }
}