package com.example.paisacheck360

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class CallHistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val callLogList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatically creating a simple layout if you don't have one
        listView = ListView(this)
        listView.setPadding(32, 32, 32, 32)
        setContentView(listView)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALL_LOG), 101)
        } else {
            loadCallLog()
        }
    }

    private fun loadCallLog() {
        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

            var count = 0
            while (it.moveToNext() && count < 20) { // Limit to last 20 calls
                val num = it.getString(numberIndex)
                val type = it.getString(typeIndex)
                val dir = when (type.toInt()) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                callLogList.add("📞 $num ($dir)")
                count++
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, callLogList)
        listView.adapter = adapter
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        } else {
            Toast.makeText(this, "Permission Denied to read Call Logs", Toast.LENGTH_SHORT).show()
        }
    }
}