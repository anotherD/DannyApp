package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Career

class CareerAdapter(
    private var careers: List<Career>,
    private val onItemClick: (Career) -> Unit,
    private val onItemLongClick: (Career) -> Unit
) : RecyclerView.Adapter<CareerAdapter.CareerViewHolder>() {

    class CareerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCareerName: TextView = view.findViewById(R.id.tvCareerName)
        val tvCareerLevel: TextView = view.findViewById(R.id.tvCareerLevel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CareerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_career, parent, false)
        return CareerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CareerViewHolder, position: Int) {
        val career = careers[position]
        holder.tvCareerName.text = career.name
        holder.tvCareerLevel.text = holder.itemView.context.getString(R.string.career_level_format, career.level)
        holder.itemView.setOnClickListener { onItemClick(career) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(career)
            true
        }
    }

    override fun getItemCount(): Int = careers.size

    fun updateData(newCareers: List<Career>) {
        careers = newCareers
        notifyDataSetChanged()
    }
}
