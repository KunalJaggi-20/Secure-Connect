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
    originalList: List<ScamData>,
    private val onBlock: (ScamData) -> Unit,
    private val onReport: (ScamData) -> Unit
) : RecyclerView.Adapter<ScamAdapter.ScamViewHolder>() {

    private val masterList = mutableListOf<ScamData>()
    private val filteredList = mutableListOf<ScamData>()

    init {
        masterList.addAll(originalList)
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
                holder.actionButtonsLayout?.visibility = View.VISIBLE
            }
            "HIGH" -> {
                holder.riskBadge.text = "🟠 HIGH RISK"
                holder.riskBadge.setTextColor(Color.parseColor("#EA580C"))
                holder.actionButtonsLayout?.visibility = View.VISIBLE
            }
            "MEDIUM" -> {
                holder.riskBadge.text = "🟡 MEDIUM RISK"
                holder.riskBadge.setTextColor(Color.parseColor("#D97706"))
                holder.actionButtonsLayout?.visibility = View.VISIBLE
            }
            else -> {
                holder.riskBadge.text = "🟢 SAFE MESSAGE"
                holder.riskBadge.setTextColor(Color.parseColor("#16A34A"))
                holder.actionButtonsLayout?.visibility = View.GONE
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

    /**
     * Safely updates data avoiding reference aliasing bugs.
     */
    fun updateData(newList: List<ScamData>) {
        val copy = ArrayList(newList)
        masterList.clear()
        masterList.addAll(copy)
        filteredList.clear()
        filteredList.addAll(copy)
        notifyDataSetChanged()
    }

    fun filterByRisk(level: String) {
        filter("", level)
    }

    fun filter(query: String, riskLevel: String) {
        filteredList.clear()
        val q = query.trim().lowercase()

        val matching = masterList.filter { scam ->
            val matchesRisk = when (riskLevel.trim().lowercase()) {
                "all" -> true
                "critical" -> scam.risk.equals("Critical", ignoreCase = true)
                "high" -> scam.risk.equals("High", ignoreCase = true)
                "medium", "med" -> scam.risk.equals("Medium", ignoreCase = true)
                "safe" -> scam.risk.equals("Safe", ignoreCase = true)
                else -> true
            }
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
        val actionButtonsLayout: View? = view.findViewById(R.id.actionButtonsLayout)
        val reportBtn: Button = view.findViewById(R.id.reportBtn)
        val blockBtn: Button = view.findViewById(R.id.blockBtn)
    }
}
