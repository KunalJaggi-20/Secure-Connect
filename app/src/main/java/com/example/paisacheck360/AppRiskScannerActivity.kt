package com.example.paisacheck360

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AppRiskScannerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var appCount: TextView
    private lateinit var criticalCount: TextView
    private lateinit var highCount: TextView
    private lateinit var mediumCount: TextView
    private lateinit var lowCount: TextView

    private var pendingUninstallPackage: String? = null
    private var pendingUninstallAppName: String? = null

    // ----------------------------------------
    // 🔥 PHASE-4: Network Threat Receiver
    // ----------------------------------------
    private val networkThreatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val domain = intent.getStringExtra("domain") ?: return
            val appPackage = intent.getStringExtra("appPackage")
            val suspicious = intent.getBooleanExtra("suspicious", false)

            if (suspicious) {
                Toast.makeText(
                    this@AppRiskScannerActivity,
                    "⚠️ Network Threat Detected: $domain",
                    Toast.LENGTH_LONG
                ).show()

                Log.w(
                    "SecureConnect-Network",
                    "Threat detected → Domain=$domain | App=$appPackage"
                )
            }
        }
    }

    // ----------------------------------------
    // Activity Lifecycle
    // ----------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AppRiskScanner", "📱 Activity onCreate")

        setContentView(R.layout.activity_app_risk_scanner)

        Toast.makeText(this, "🔍 Scanning apps...", Toast.LENGTH_SHORT).show()

        initializeViews()
        startScanning()
    }

    override fun onResume() {
        super.onResume()

        // Register threat listener safely
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                networkThreatReceiver,
                IntentFilter("com.secureconnect.NETWORK_THREAT"),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("SecureConnect-Network", "Receiver registered for AppRiskScannerActivity")
        } catch (e: Exception) {
            Log.e("SecureConnect-Network", "Receiver error: ${e.message}")
        }

        // Check if an app uninstallation completed
        pendingUninstallPackage?.let { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                // App is still present (user canceled uninstall)
            } catch (_: Exception) {
                // App was uninstalled!
                val name = pendingUninstallAppName ?: "App"
                Toast.makeText(this, "✅ $name uninstalled successfully", Toast.LENGTH_SHORT).show()
                pendingUninstallPackage = null
                pendingUninstallAppName = null
                startScanning()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // unregister receiver safely
        try {
            unregisterReceiver(networkThreatReceiver)
            Log.d("SecureConnect-Network", "Receiver unregistered")
        } catch (_: IllegalArgumentException) {
            // Ignore: receiver not registered
        }
    }

    // ----------------------------------------
    // UI + Scanner Logic
    // ----------------------------------------

    private fun initializeViews() {
        Log.d("AppRiskScanner", "🎨 Initializing views")

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        appCount = findViewById(R.id.appCount)
        criticalCount = findViewById(R.id.criticalCount)
        highCount = findViewById(R.id.highCount)
        mediumCount = findViewById(R.id.mediumCount)
        lowCount = findViewById(R.id.lowCount)

        findViewById<View>(R.id.backButton)?.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.refreshButton)?.setOnClickListener {
            startScanning()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.isNestedScrollingEnabled = false

        Log.d("AppRiskScanner", "✅ Views initialized")
    }

    private fun startScanning() {
        Log.d("AppRiskScanner", "🔍 Starting full scan...")

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                loadingText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE

                val scanner = AppScanner(this@AppRiskScannerActivity)

                val apps = scanner.scanAllApps()
                val stats = AppScanStatistics(
                    totalApps = apps.size,
                    criticalApps = apps.count { it.riskLevel == RiskLevel.CRITICAL },
                    highRiskApps = apps.count { it.riskLevel == RiskLevel.HIGH },
                    mediumRiskApps = apps.count { it.riskLevel == RiskLevel.MEDIUM },
                    lowRiskApps = apps.count { it.riskLevel == RiskLevel.LOW }
                )

                updateStatistics(stats)
                displayApps(apps)

                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                Toast.makeText(
                    this@AppRiskScannerActivity,
                    "✅ Scanned ${apps.size} apps successfully!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Log.e("AppRiskScanner", "❌ ERROR: ${e.message}", e)
                loadingText.text = "❌ Error: ${e.message}"
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateStatistics(stats: AppScanStatistics) {
        appCount.text = "${stats.totalApps} apps"
        criticalCount.text = stats.criticalApps.toString()
        highCount.text = stats.highRiskApps.toString()
        mediumCount.text = stats.mediumRiskApps.toString()
        lowCount.text = stats.lowRiskApps.toString()
    }

    private fun displayApps(apps: List<AppRiskInfo>) {
        Log.d("AppRiskScanner", "📋 Displaying ${apps.size} apps in RecyclerView")
        recyclerView.adapter = AppRiskAdapter(apps) { app ->
            pendingUninstallPackage = app.packageName
            pendingUninstallAppName = app.appName
        }
    }
}
