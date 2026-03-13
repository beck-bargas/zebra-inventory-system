package com.beck.tirescanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.beck.tirescanner.database.TireEntry

class TireAdapter(
    private var tires: List<TireEntry>,
    private val onLongClick: (TireEntry) -> Unit
) : RecyclerView.Adapter<TireAdapter.TireViewHolder>() {

    class TireViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBrand: TextView = view.findViewById(R.id.tvBrand)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val tvBarcode: TextView = view.findViewById(R.id.tvBarcode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TireViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tire, parent, false)
        return TireViewHolder(view)
    }

    override fun onBindViewHolder(holder: TireViewHolder, position: Int) {
        val tire = tires[position]
        holder.tvBrand.text = tire.brand
        holder.tvSize.text = tire.size
        holder.tvQuantity.text = tire.quantity.toString()
        holder.tvBarcode.text = "Barcode: ${tire.barcode}"

        holder.itemView.setOnLongClickListener {
            onLongClick(tire)
            true
        }
    }

    override fun getItemCount() = tires.size

    fun updateTires(newTires: List<TireEntry>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = tires.size
            override fun getNewListSize() = newTires.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = tires[oldPos].id == newTires[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = tires[oldPos] == newTires[newPos]
        }
        val diff = DiffUtil.calculateDiff(diffCallback)
        tires = newTires
        diff.dispatchUpdatesTo(this)
    }
}