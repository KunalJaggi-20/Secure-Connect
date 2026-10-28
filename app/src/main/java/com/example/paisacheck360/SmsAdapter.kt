package com.example.paisacheck360

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScamAdapter(
    private val originalList: MutableList<ScamData>,
    private val onBlock: (ScamData) -> Unit,
    private val onReport: (ScamData) -> Unit
) : RecyclerView.Adapter<ScamAdapter.ScamViewHolder>() {

    private var filteredList = mutableListOf<ScamData>()

    init {
        filteredList.addAll(originalList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scam_card, parent, false)
        return ScamViewHolder(view)
    }

    override fun getItemCount(): Int = filteredList.size

    override fun onBindViewHolder(holder: ScamViewHolder, position: Int) {
        val scam = filteredList[position]
        holder.sender.text = if (scam.sender.isNotBlank()) scam.sender else "Unknown Sender"
        holder.message.text = scam.body

        when (scam.risk.uppercase()) {
            "CRITICAL" -> {
                holder.riskBadge.text = "🔴 CRITICAL RISK"
                holder.riskBadge.setTextColor(Color.parseColor("#DC2626"))
            }
            "HIGH" -> {
                holder.riskBadge.text = "🟠 HIGH RISK"
                holder.riskBadge.setTextColor(Color.parseColor("#EA580C"))
            }
            "MEDIUM" -> {
                holder.riskBadge.text = "🟡 MEDIUM RISK"
                holder.riskBadge.setTextColor(Color.parseColor("#D97706"))
            }
            else -> {
                holder.riskBadge.text = "🟢 SAFE MESSAGE"
                holder.riskBadge.setTextColor(Color.parseColor("#16A34A"))
            }
        }

        val timeSpan = DateUtils.getRelativeTimeSpanString(
            scam.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.timeText?.text = timeSpan

        holder.reportBtn.setOnClickListener { onReport(scam) }
        holder.blockBtn.setOnClickListener { onBlock(scam) }
    }

    fun updateData(newList: List<ScamData>) {
        originalList.clear()
        originalList.addAll(newList)
        filteredList.clear()
        filteredList.addAll(newList)
        notifyDataSetChanged()
    }

    fun filterByRisk(level: String) {
        filter("", level)
    }

    fun filter(query: String, riskLevel: String) {
        filteredList.clear()
        val q = query.trim().lowercase()

        val matching = originalList.filter { scam ->
            val matchesRisk = riskLevel.equals("All", ignoreCase = true) ||
                    scam.risk.equals(riskLevel, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                    scam.sender.lowercase().contains(q) ||
                    scam.body.lowercase().contains(q)
            matchesRisk && matchesQuery
        }

        filteredList.addAll(matching)
        notifyDataSetChanged()
    }

    class ScamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.senderText)
        val message: TextView = view.findViewById(R.id.messageText)
        val riskBadge: TextView = view.findViewById(R.id.riskBadge)
        val timeText: TextView? = view.findViewById(R.id.timeText)
        val reportBtn: Button = view.findViewById(R.id.reportBtn)
        val blockBtn: Button = view.findViewById(R.id.blockBtn)
    }
}
