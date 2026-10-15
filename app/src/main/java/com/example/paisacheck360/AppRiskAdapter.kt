package com.example.paisacheck360

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AppRiskAdapter(
    private val apps: List<AppRiskInfo>,
    private val onUninstallRequested: ((AppRiskInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppRiskAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val packageName: TextView = view.findViewById(R.id.packageName)
        val riskBadge: LinearLayout = view.findViewById(R.id.riskBadge)
        val riskEmoji: TextView = view.findViewById(R.id.riskEmoji)
        val riskScore: TextView = view.findViewById(R.id.riskScore)
        val riskLevel: TextView = view.findViewById(R.id.riskLevel)
        val dangerousPermissions: TextView = view.findViewById(R.id.dangerousPermissions)
        val totalPermissions: TextView = view.findViewById(R.id.totalPermissions)
        val systemApp: TextView = view.findViewById(R.id.systemApp)
        val detailsContainer: LinearLayout = view.findViewById(R.id.detailsContainer)
        val mainContent: View = view.findViewById(R.id.mainContent)
        val expandArrow: ImageView = view.findViewById(R.id.expandArrow)
        val viewDetailsBtn: View = view.findViewById(R.id.viewDetailsBtn)
        val uninstallBtn: View = view.findViewById(R.id.uninstallBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_risk, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        // Set app info
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName

        // Risk badge
        holder.riskBadge.setBackgroundColor(app.riskLevel.color)
        holder.riskEmoji.text = app.riskLevel.emoji
        holder.riskScore.text = app.riskScore.toString()

        // Risk label
        holder.riskLevel.text = "${app.riskLevel.emoji} ${app.riskLevel.displayName}"
        holder.riskLevel.setTextColor(app.riskLevel.color)

        // Build dangerous permissions text with Phase-3 info
        val dp = app.dangerousPermissions
        val newDp = app.newDangerousPermissions

        if (dp.isEmpty() && newDp.isEmpty()) {
            holder.dangerousPermissions.text = "✅ None detected"
            holder.dangerousPermissions.setTextColor(0xFF4CAF50.toInt())
        } else {
            val builder = StringBuilder()

            if (dp.isNotEmpty()) {
                builder.append("Dangerous permissions:\n")
                builder.append(dp.joinToString("\n"))
            }

            if (newDp.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append("⚠ Newly added risky permissions:\n")
                builder.append(newDp.joinToString("\n"))
            }

            holder.dangerousPermissions.text = builder.toString()
            holder.dangerousPermissions.setTextColor(0xFF666666.toInt())
        }

        // Other details
        holder.totalPermissions.text = app.totalPermissions.toString()
        holder.systemApp.text = if (app.isSystemApp) "Yes" else "No"

        // Reset details state on bind
        holder.detailsContainer.visibility = View.GONE
        holder.expandArrow.rotation = 0f

        // Toggle details on click (only header and arrow, not entire item view)
        val toggleDetails = View.OnClickListener {
            val isVisible = holder.detailsContainer.visibility == View.VISIBLE
            holder.detailsContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
            holder.expandArrow.animate().rotation(if (isVisible) 0f else 180f).setDuration(200).start()
        }
        holder.mainContent.setOnClickListener(toggleDetails)
        holder.expandArrow.setOnClickListener(toggleDetails)

        // View Details action
        val handleViewDetails = View.OnClickListener {
            val context = holder.itemView.context
            android.util.Log.e("APP_RISK_CLICK", "View Details clicked for ${app.packageName}")
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("APP_RISK_CLICK", "Error opening settings", e)
                Toast.makeText(context, "Cannot open app settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        holder.viewDetailsBtn.setOnClickListener(handleViewDetails)
        holder.itemView.findViewById<View>(R.id.viewDetailsText)?.setOnClickListener(handleViewDetails)

        // Uninstall action
        val handleUninstall = View.OnClickListener {
            val context = holder.itemView.context
            android.util.Log.e("APP_RISK_CLICK", "Uninstall clicked for ${app.packageName}, isSystem=${app.isSystemApp}")
            if (app.isSystemApp) {
                Toast.makeText(
                    context,
                    "⚠️ ${app.appName} is a system app and cannot be uninstalled directly.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("APP_RISK_CLICK", "Error opening system app settings", e)
                }
            } else {
                onUninstallRequested?.invoke(app)
                try {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("APP_RISK_CLICK", "Error launching uninstaller", e)
                    Toast.makeText(
                        context,
                        "Failed to launch uninstaller: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        holder.uninstallBtn.setOnClickListener(handleUninstall)
        holder.itemView.findViewById<View>(R.id.uninstallText)?.setOnClickListener(handleUninstall)
    }

    override fun getItemCount() = apps.size
}
