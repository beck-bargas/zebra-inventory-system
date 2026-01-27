package com.beck.tirescanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beck.tirescanner.models.Product
import com.beck.tirescanner.network.RetrofitClient
import com.beck.tirescanner.utils.TireSizeTextWatcher
import com.bumptech.glide.Glide
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
                    val imageView = dialogView.findViewById<ImageView>(R.id.tireImage)
                    val yesButton = dialogView.findViewById<Button>(R.id.yesButton)
                    val noButton = dialogView.findViewById<Button>(R.id.noButton)

                    // Set image in ImageView
                    val imageUrl = product.images?.firstOrNull()
                    if (imageUrl != null) {
                        Glide.with(this@MainActivity)
                            .load(product.images[0])
                            .into(imageView)
                    } else {
                        imageView.setImageResource(R.drawable.placeholder)
                    }

                    dialogView.findViewById<TextView>(R.id.tvTitle).text = "${product.title}"
                    dialogView.findViewById<TextView>(R.id.tvSize).text = "Size: ${product.size}"
                    dialogView.findViewById<TextView>(R.id.tvBrand).text = "Brand: ${product.brand}"


                    val dialog = AlertDialog.Builder(this@MainActivity)
                        .setView(dialogView)
                        .create()

                    yesButton.setOnClickListener {
                        dialog.dismiss()
                        askAmount()
                    }

                    noButton.setOnClickListener {
                        dialog.dismiss()
                        showManualEntryDialog(product)
                    }

                    dialog.show()
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

    private fun showManualEntryDialog(product: Product?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null)

        val brandInput = dialogView.findViewById<EditText>(R.id.brandInput)
        val tirePrefixSpinner = dialogView.findViewById<Spinner>(R.id.tirePrefixSpinner)
        val tireSizeInput = dialogView.findViewById<EditText>(R.id.tireSizeInput)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        // Setup spinner with tire prefixes
        val prefixes = arrayOf("None", "P", "LT", "ST")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, prefixes)
        tirePrefixSpinner.adapter = adapter

        // Add TextWatcher for auto-formatting
        tireSizeInput.addTextChangedListener(TireSizeTextWatcher(tireSizeInput))

        // Pre-fill if API had partial data
        if (product != null) {
            brandInput.setText(product.brand.orEmpty())

            // Try to parse prefix from size if it exists
            val sizeWithPrefix = product.size.orEmpty()
            if (sizeWithPrefix.startsWith("P ")) {
                tirePrefixSpinner.setSelection(1)
                tireSizeInput.setText(sizeWithPrefix.substring(2))
            } else if (sizeWithPrefix.startsWith("LT ")) {
                tirePrefixSpinner.setSelection(2)
                tireSizeInput.setText(sizeWithPrefix.substring(3))
            } else {
                tireSizeInput.setText(sizeWithPrefix)
            }
        }

        // Create dialog without default buttons
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Tire Information")
            .setView(dialogView)
            .create()

        // Setup custom button clicks
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            val brand = brandInput.text.toString()
            val prefix = tirePrefixSpinner.selectedItem.toString()
            val sizeNumbers = tireSizeInput.text.toString()

            val fullSize = if (prefix == "None") {
                sizeNumbers
            } else {
                "$prefix $sizeNumbers"
            }

            if (brand.isNotEmpty() && sizeNumbers.isNotEmpty()) {
                askAmount()
                dialog.dismiss()
            }
        }

        dialog.show()
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
                    askVendor()
                }
            }
            .setNegativeButton("Cancel") {dialog, which ->
                dialog.dismiss()
            }
            .show()
    }

    // Ask for Vendor
    private fun askVendor() {
        val dialogView = layoutInflater.inflate(R.layout.vendor_info, null)
        val listView = dialogView.findViewById<ListView>(R.id.vendorListView)

        val vendors = arrayOf("None","NTW", "K&M", "BFS", "Discount Tire", "Hesselbein", "USAutoforce", "ATD")

        var selectedVendor: String = "None"  // Default to "None"

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, vendors)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Save into database
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Pre-select "None" (position 0) after dialog is shown
        listView.post {
            listView.getChildAt(0)?.setBackgroundColor(Color.parseColor("#30000000"))
        }

        listView.setOnItemClickListener { _, view, position, _ ->
            selectedVendor = vendors[position]

            // Clear previous selection and highlight new one
            for (i in 0 until listView.childCount) {
                listView.getChildAt(i)?.setBackgroundColor(Color.TRANSPARENT)
            }

            view?.setBackgroundColor(Color.parseColor("#30000000"))
            Toast.makeText(this, "Saved: $selectedVendor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearDisplay() {
        barcodeText.text = "No barcode scanned"
        responseText.text = "Waiting for scan..."
    }
}