package com.beck.tirescanner

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
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
        val tvSku: TextView = view.findViewById(R.id.tvSku)
        val pressOverlay: View = view.findViewById(R.id.pressOverlay)
        var fillAnimator: ValueAnimator? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TireViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tire, parent, false)
        return TireViewHolder(view)
    }

    override fun onBindViewHolder(holder: TireViewHolder, position: Int) {
        val tire = tires[position]
        holder.tvBrand.text = tire.name
        holder.tvSize.text = tire.size
        holder.tvQuantity.text = tire.quantity.toString()
        holder.tvSku.text = "SKU: ${tire.sku}"

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val cardHeight = holder.itemView.height.takeIf { it > 0 } ?: 200
                    holder.pressOverlay.visibility = View.VISIBLE
                    holder.fillAnimator?.cancel()
                    holder.fillAnimator = ValueAnimator.ofInt(0, cardHeight).apply {
                        duration = 700
                        addUpdateListener { anim ->
                            val h = anim.animatedValue as Int
                            val params = holder.pressOverlay.layoutParams
                            params.height = h
                            holder.pressOverlay.layoutParams = params
                        }
                        start()
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holder.fillAnimator?.cancel()
                    holder.pressOverlay.visibility = View.INVISIBLE
                    val params = holder.pressOverlay.layoutParams
                    params.height = 0
                    holder.pressOverlay.layoutParams = params
                    v.performClick()
                    false
                }
                else -> false
            }
        }

        holder.itemView.setOnLongClickListener {
            val cardHeight = holder.itemView.height.takeIf { it > 0 } ?: 200
            holder.fillAnimator?.cancel()
            holder.pressOverlay.visibility = View.VISIBLE
            ValueAnimator.ofInt(holder.pressOverlay.height, cardHeight).apply {
                duration = 150
                addUpdateListener { anim ->
                    val h = anim.animatedValue as Int
                    val params = holder.pressOverlay.layoutParams
                    params.height = h
                    holder.pressOverlay.layoutParams = params
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        holder.pressOverlay.visibility = View.INVISIBLE
                        val params = holder.pressOverlay.layoutParams
                        params.height = 0
                        holder.pressOverlay.layoutParams = params
                        onLongClick(tire)
                    }
                })
                start()
            }
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