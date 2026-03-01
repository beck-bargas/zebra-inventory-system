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
import com.beck.tirescanner.database.TireEntry
import com.beck.tirescanner.database.TireRepository
import com.beck.tirescanner.models.Product
import com.beck.tirescanner.network.RetrofitClient
import com.beck.tirescanner.utils.TireSizeTextWatcher
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {
    // Database
    private lateinit var tireRepository: TireRepository

    // UI Elements
    private lateinit var barcodeText: TextView
    private lateinit var responseText: TextView
    private lateinit var clearButton: Button

    // DataWedge configuration
    companion object {
        private const val DATAWEDGE_INTENT_ACTION = "com.beck.tirescanner.SCAN"
        private const val DATAWEDGE_INTENT_CATEGORY = "android.intent.category.DEFAULT"
    }
    // BroadcastReceiver for DataWedge scans
    private val localBarcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.beck.tirescanner.LOCAL_SCAN") {
                val barcode = intent.getStringExtra("barcode")

                if (barcode != null) {
                    handleBarcodeScanned(barcode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize repository
        tireRepository = TireRepository(this)

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

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.beck.tirescanner.SCAN") {
            val barcode = intent.getStringExtra("com.symbol.datawedge.data_string")

            if (barcode != null) {
                handleBarcodeScanned(barcode)
            }
        }
    }

    override fun onResume() {
        super.onResume()

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
        } catch (_: IllegalArgumentException) {
            // Receiver wasn't registered
        }
    }

    private fun configureDataWedge() {
        val dwIntent = Intent()
        dwIntent.setAction("com.symbol.datawedge.api.ACTION")
        dwIntent.putExtra("com.symbol.datawedge.api.CREATE_PROFILE", "BarcodeScannerProfile")
        sendBroadcast(dwIntent)

        val profileBundle = Bundle()
        profileBundle.putString("PROFILE_NAME", "BarcodeScannerProfile")
        profileBundle.putString("PROFILE_ENABLED", "true")
        profileBundle.putString("CONFIG_MODE", "UPDATE")

        val appConfig = Bundle()
        appConfig.putString("PACKAGE_NAME", packageName)
        appConfig.putStringArray("ACTIVITY_LIST", arrayOf("*"))
        profileBundle.putParcelableArray("APP_LIST", arrayOf(appConfig))

        val intentConfig = Bundle()
        intentConfig.putString("PLUGIN_NAME", "INTENT")
        intentConfig.putString("RESET_CONFIG", "true")

        val intentProps = Bundle()
        intentProps.putString("intent_output_enabled", "true")
        intentProps.putString("intent_action", DATAWEDGE_INTENT_ACTION)
        intentProps.putString("intent_category", DATAWEDGE_INTENT_CATEGORY)
        intentProps.putInt("intent_delivery", 0)

        intentConfig.putBundle("PARAM_LIST", intentProps)
        profileBundle.putBundle("PLUGIN_CONFIG", intentConfig)

        val profileIntent = Intent()
        profileIntent.action = "com.symbol.datawedge.api.ACTION"
        profileIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", profileBundle)
        sendBroadcast(profileIntent)
    }

    private fun handleBarcodeScanned(barcode: String) {
        runOnUiThread {
            barcodeText.text = barcode
        }
        sendBarcodeToAPI(barcode)
    }

    private fun sendBarcodeToAPI(barcode: String) {
        lifecycleScope.launch {
            try {
                // Check if tire exists in database
                val existingTireInDb = tireRepository.getTireByBarcode(barcode)

                // Try Barcode Lookup API
                var product = tryGetProduct(barcode, existingTireInDb, paid = true)

                // If no result or missing brand/size, try UPCitemdb free tier
                if (product == null || !product.hasBrandAndSize()) {
                    Log.d("BarcodeScanner", "Paid API had no/incomplete result, trying free tier...")
                    val freeProduct = tryGetProduct(barcode, existingTireInDb, paid = false)
                    if (freeProduct != null && freeProduct.hasBrandAndSize()) {
                        product = freeProduct
                    }
                }

                if (product != null) {
                    // If API missing brand/size, use database info if available
                    val finalProduct = if (!product.hasBrandAndSize() && existingTireInDb != null) {
                        Product(
                            title = product.title,
                            brand = product.brand?.takeIf { it.isNotEmpty() } ?: existingTireInDb.brand,
                            size = product.size?.takeIf { it.isNotEmpty() } ?: existingTireInDb.size,
                            images = product.images,
                            barcode = barcode
                        )
                    } else {
                        product
                    }

                    // Get the scan mode
                    val mode = intent.getStringExtra("MODE") ?: "IN"

                    if (mode == "IN") {
                        // Check if brand/size is missing after all fallbacks
                        if (!finalProduct.hasBrandAndSize()) {
                            runOnUiThread {
                                val missingFields = finalProduct.getMissingFields()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Could not detect $missingFields",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showManualEntryDialog(barcode, finalProduct)
                            }
                        } else {
                            // IN MODE: Show confirmation dialog
                            runOnUiThread {
                                showConfirmationDialog(finalProduct, barcode)
                            }
                        }
                    } else {
                        // OUT MODE: Show confirmation dialog (will handle if not in inventory)
                        runOnUiThread {
                            showConfirmationDialog(finalProduct, barcode)
                        }
                    }
                } else {
                    runOnUiThread {
                        responseText.text = "No product found for barcode: $barcode"
                        Toast.makeText(this@MainActivity, "Could not detect brand and size", Toast.LENGTH_SHORT).show()
                        showManualEntryDialog(barcode, null)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    responseText.text = "Error: ${e.message}"
                }
            }
        }
    }

    // Helper that calls either paid or free endpoint and returns a Product or null
    private suspend fun tryGetProduct(barcode: String, existingTireInDb: TireEntry?, paid: Boolean): Product? {
        return try {
            val products = if (paid) {
                RetrofitClient.apiService.getProductInfo(barcode, RetrofitClient.API_KEY).toProducts()
            } else {
                RetrofitClient.upcApiService.getProductInfo(barcode).toProducts()
            }

            if (products.isNotEmpty()) {
                products[0]
            } else if (existingTireInDb != null && paid) {
                Product(
                    title = "",
                    brand = existingTireInDb.brand,
                    size = existingTireInDb.size,
                    images = null,
                    barcode = barcode
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    private fun showConfirmationDialog(product: Product, barcode: String) {
        runOnUiThread {
            val dialogView = layoutInflater.inflate(R.layout.dialog_tire_info, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.tireImage)
            val titleView = dialogView.findViewById<TextView>(R.id.textView)
            val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)
            val editButton = dialogView.findViewById<Button>(R.id.editButton)

            // Get the scan mode
            val mode = intent.getStringExtra("MODE") ?: "IN"

            titleView.text = "Verify Tire Information"

            confirmButton.text = if (mode == "IN") "Add" else "Remove"

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
            dialogView.findViewById<TextView>(R.id.tvSize).text = product.size
            dialogView.findViewById<TextView>(R.id.tvBrand).text = product.brand

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setView(dialogView)
                .create()

            confirmButton.setOnClickListener {
                dialog.dismiss()
                if (mode == "IN") {
                    askAmount(product, barcode)
                } else {
                    // OUT mode - go straight to quantity removal
                    handleOutMode(product, barcode)
                }
            }

            editButton.setOnClickListener {
                dialog.dismiss()
                showManualEntryDialog(barcode, product)
            }

            dialog.show()
        }
    }

    private fun handleOutMode(product: Product, barcode: String) {
        val existingTire = tireRepository.getTireByDetails(
            barcode,
            product.brand,
            product.size
        )

        if (existingTire != null) {
            // Tire exists - ask how many to remove
            askQuantityToRemove(existingTire)
        } else {
            // Not in inventory
            Toast.makeText(this, "Tire not found in inventory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askQuantityToRemove(existingTire: TireEntry) {
        val dialogView = layoutInflater.inflate(R.layout.tire_amount, null)
        val input = dialogView.findViewById<EditText>(R.id.etAmount)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        dialogView.findViewById<TextView>(R.id.etTitle).text = "Remove Quantity"
        input.hint = "Max: ${existingTire.quantity}"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            val amountToRemove = input.text.toString().toIntOrNull()

            if (amountToRemove != null && amountToRemove > 0) {
                if (amountToRemove > existingTire.quantity) {
                    Toast.makeText(this, "Cannot remove more than ${existingTire.quantity}", Toast.LENGTH_SHORT).show()
                } else {
                    val success = tireRepository.decreaseQuantity(existingTire.id, amountToRemove)

                    if (success) {
                        val newTotal = existingTire.quantity - amountToRemove
                        if (newTotal == 0) {
                            Toast.makeText(this, "Removed all tires. Entry deleted.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Removed $amountToRemove tires. Remaining: $newTotal", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Error removing tires", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showManualEntryDialog(barcode: String, product: Product?) {
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Tire Information")
            .setView(dialogView)
            .create()

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
                val manualProduct = Product(
                    title = "",
                    brand = brand,
                    size = fullSize,
                    images = null,
                    barcode = barcode
                )
                askAmount(manualProduct, barcode)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun askAmount(product: Product, barcode: String) {
        val dialogView = layoutInflater.inflate(R.layout.tire_amount, null)
        val input = dialogView.findViewById<EditText>(R.id.etAmount)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            val amount = input.text.toString().toIntOrNull()

            if (amount != null && amount > 0) {
                askVendor(product, barcode, amount)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askVendor(product: Product, barcode: String, quantity: Int) {
        val dialogView = layoutInflater.inflate(R.layout.vendor_info, null)
        val listView = dialogView.findViewById<ListView>(R.id.vendorListView)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        val vendors = arrayOf("None","NTW", "K&M", "BFS", "Discount Tire", "Hesselbein", "USAutoforce", "ATD")

        var selectedVendor = "None"

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, vendors)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        // Pre-select "None"
        listView.post {
            listView.getChildAt(0)?.setBackgroundColor("#30000000".toColorInt())
        }

        listView.setOnItemClickListener { _, view, position, _ ->
            selectedVendor = vendors[position]

            // Clear previous selection and highlight new one
            for (i in 0 until listView.childCount) {
                listView.getChildAt(i)?.setBackgroundColor(Color.TRANSPARENT)
            }

            view?.setBackgroundColor("#30000000".toColorInt())
            Toast.makeText(this, "Selected: $selectedVendor", Toast.LENGTH_SHORT).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            saveTireToInventory(product, barcode, quantity, selectedVendor)
            dialog.dismiss()
        }
    }

    private fun saveTireToInventory(product: Product, barcode: String, quantity: Int, vendor: String?) {
        // Check if tire exists
        val existingTire = tireRepository.getTireByDetails(barcode, product.brand, product.size)

        if (existingTire != null) {
            // Tire exists - just add to existing quantity
            val success = tireRepository.increaseQuantity(existingTire.id, quantity)
            if (success) {
                val newTotal = existingTire.quantity + quantity
                Toast.makeText(this, "Added $quantity tires. Total: $newTotal", Toast.LENGTH_LONG).show()
            }
        } else {
            // New tire - insert
            val id = tireRepository.insertTire(product, barcode, quantity, vendor)
            if (id > 0) {
                Toast.makeText(this, "Added $quantity new tires", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error saving tire", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearDisplay() {
        barcodeText.text = "No barcode scanned"
        responseText.text = "Waiting for scan..."
    }
}