package com.beck.tirescanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beck.tirescanner.network.RetrofitClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var barcodeText: TextView
    private lateinit var responseText: TextView
    private lateinit var clearButton: Button
    // DataWedge configuration
    private val DATAWEDGE_INTENT_ACTION = "com.beck.tirescanner.SCAN"
    private val DATAWEDGE_INTENT_CATEGORY = "android.intent.category.DEFAULT"

    // BroadcastReceiver for DataWedge scans
    private val localBarcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.beck.tirescanner.LOCAL_SCAN") {
                val barcode = intent.getStringExtra("barcode")
                val barcodeType = intent.getStringExtra("barcode_type")

                Log.d("BarcodeScanner", "MainActivity received: $barcode")

                if (barcode != null) {
                    handleBarcodeScanned(barcode, barcodeType ?: "Unknown")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        barcodeText = findViewById(R.id.barcodeText)
        responseText = findViewById(R.id.responseText)
        clearButton = findViewById(R.id.clearButton)

        clearButton.setOnClickListener {
            clearDisplay()
        }

        configureDataWedge()

        // Handle intent if launched by DataWedge
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Handle intent if launched by DataWedge
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.beck.tirescanner.SCAN") {
            val barcode = intent.getStringExtra("com.symbol.datawedge.data_string")
            val barcodeType = intent.getStringExtra("com.symbol.datawedge.label_type")

            Log.d("BarcodeScanner", "Received via startActivity: $barcode")

            if (barcode != null) {
                handleBarcodeScanned(barcode, barcodeType ?: "Unknown")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("BarcodeScanner", "Registering local receiver")

        val filter = IntentFilter("com.beck.tirescanner.LOCAL_SCAN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localBarcodeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(localBarcodeReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(localBarcodeReceiver)
        } catch (e: Exception) {
            // Receiver wasn't registered
        }
    }

    private fun configureDataWedge() {
        // Send intent to configure DataWedge profile
        val dwIntent = Intent()
        dwIntent.setAction("com.symbol.datawedge.api.ACTION")
        dwIntent.putExtra("com.symbol.datawedge.api.CREATE_PROFILE", "BarcodeScannerProfile")
        sendBroadcast(dwIntent)

        // Configure the profile
        val profileBundle = Bundle()
        profileBundle.putString("PROFILE_NAME", "BarcodeScannerProfile")
        profileBundle.putString("PROFILE_ENABLED", "true")
        profileBundle.putString("CONFIG_MODE", "UPDATE")

        // Associate app with profile
        val appConfig = Bundle()
        appConfig.putString("PACKAGE_NAME", packageName)
        appConfig.putStringArray("ACTIVITY_LIST", arrayOf("*"))
        profileBundle.putParcelableArray("APP_LIST", arrayOf(appConfig))

        // Configure intent output
        val intentConfig = Bundle()
        intentConfig.putString("PLUGIN_NAME", "INTENT")
        intentConfig.putString("RESET_CONFIG", "true")

        val intentProps = Bundle()
        intentProps.putString("intent_output_enabled", "true")
        intentProps.putString("intent_action", DATAWEDGE_INTENT_ACTION)
        intentProps.putString("intent_category", DATAWEDGE_INTENT_CATEGORY)
        intentProps.putInt("intent_delivery", 0) // Send via startActivity

        intentConfig.putBundle("PARAM_LIST", intentProps)
        profileBundle.putBundle("PLUGIN_CONFIG", intentConfig)

        val profileIntent = Intent()
        profileIntent.action = "com.symbol.datawedge.api.ACTION"
        profileIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", profileBundle)
        sendBroadcast(profileIntent)

    }

    private fun handleBarcodeScanned(barcode: String, barcodeType: String) {
        runOnUiThread {
            barcodeText.text = barcode
        }

        // Send barcode to API
        sendBarcodeToAPI(barcode, barcodeType)
    }

    private fun sendBarcodeToAPI(barcode: String, barcodeType: String) {
        // Make API call
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getProductInfo(
                    barcode = barcode,
                    apiKey = RetrofitClient.API_KEY
                )

                if (response.products.isNotEmpty()) {
                    val product = response.products[0]

                    // Show product info
                    val dialogView = layoutInflater.inflate(R.layout.dialog_tire_info, null)

                    dialogView.findViewById<TextView>(R.id.tvTitle).text = "${product.title}"
                    dialogView.findViewById<TextView>(R.id.tvSize).text = "Size: ${product.size}"
                    dialogView.findViewById<TextView>(R.id.tvBrand).text = "Brand: ${product.brand}"


                    AlertDialog.Builder(this@MainActivity)
                        .setView(dialogView)
                        .setPositiveButton("Yes") {dialog, which ->
                            dialog.dismiss()
                            askAmount()
                        }
                        .setNegativeButton("No") {dialog, which ->
                            dialog.dismiss()
                        }
                        .show()

                } else {
                    runOnUiThread {
                        responseText.text = "No product found for barcode: $barcode"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    responseText.text = "Error: ${e.message}"
                }
            }
        }
    }

    // Ask for amount
    private fun askAmount() {
        val dialogView = layoutInflater.inflate(R.layout.tire_amount, null)
        val input = dialogView.findViewById<EditText>(R.id.etAmount)

        AlertDialog.Builder(this@MainActivity)

            .setView(dialogView)
            .setPositiveButton("Submit") {dialog, which ->
                val amount = input.text.toString()
                if (amount.isNotEmpty()) {
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") {dialog, which ->
                dialog.dismiss()
            }
            .show()
    }
    private fun clearDisplay() {
        barcodeText.text = "No barcode scanned"
        responseText.text = "Waiting for scan..."
    }
}