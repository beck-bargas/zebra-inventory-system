package com.beck.tirescanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
import com.beck.tirescanner.network.SyncManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var tireRepository: TireRepository
    private lateinit var syncManager: SyncManager
    private lateinit var barcodeText: TextView
    private lateinit var responseText: TextView
    private lateinit var clearButton: Button

    companion object {
        private const val DATAWEDGE_INTENT_ACTION = "com.beck.tirescanner.SCAN"
        private const val DATAWEDGE_INTENT_CATEGORY = "android.intent.category.DEFAULT"
        private const val DEFAULT_BARCODE_IMAGE = "https://images.barcodelookup.com/17601/176010350-1.jpg"
    }

    private val localBarcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.beck.tirescanner.LOCAL_SCAN") {
                val barcode = intent.getStringExtra("barcode")
                if (barcode != null) handleBarcodeScanned(barcode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tireRepository = TireRepository(this)
        syncManager = SyncManager(this, tireRepository, BuildConfig.SYNC_TOKEN)
        barcodeText = findViewById(R.id.barcodeText)
        responseText = findViewById(R.id.responseText)
        clearButton = findViewById(R.id.clearButton)
        clearButton.setOnClickListener { clearDisplay() }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.beck.tirescanner.SCAN") {
            val barcode = intent.getStringExtra("com.symbol.datawedge.data_string")
            if (barcode != null) handleBarcodeScanned(barcode)
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
        try { unregisterReceiver(localBarcodeReceiver) } catch (_: IllegalArgumentException) {}
    }

    private fun handleBarcodeScanned(barcode: String) {
        runOnUiThread { barcodeText.text = barcode }
        sendBarcodeToAPI(barcode)
    }

    private fun sendBarcodeToAPI(barcode: String) {
        lifecycleScope.launch {
            try {
                val mode = intent.getStringExtra("MODE") ?: "IN"

                val cached = tireRepository.getCachedTire(barcode)
                if (cached != null) {
                    val cachedProduct = Product(
                        title = cached.title,
                        brand = cached.brand,
                        size = cached.size,
                        images = cached.imageUrl?.takeIf { it.isNotEmpty() }?.let { listOf(it) },
                        barcode = barcode
                    )
                    runOnUiThread { showConfirmationDialog(cachedProduct, barcode) }
                    return@launch
                }

                val existingTireInDb = tireRepository.getTireByBarcode(barcode)
                var product = tryGetProduct(barcode, existingTireInDb, paid = true)

                if (product == null || !product.hasBrandAndSize()) {
                    Log.d("BarcodeScanner", "Paid API had no/incomplete result, trying free tier...")
                    val freeProduct = tryGetProduct(barcode, existingTireInDb, paid = false)
                    if (freeProduct != null && freeProduct.hasBrandAndSize()) {
                        product = freeProduct
                    }
                }

                if (product != null) {
                    val finalProduct = (if (!product.hasBrandAndSize() && existingTireInDb != null) {
                        Product(
                            title = product.title,
                            brand = product.brand?.takeIf { it.isNotEmpty() } ?: existingTireInDb.brand,
                            size = product.size?.takeIf { it.isNotEmpty() } ?: existingTireInDb.size,
                            images = product.images,
                            barcode = barcode
                        )
                    } else product).let { p ->
                        p.copy(images = p.images?.filter { it != DEFAULT_BARCODE_IMAGE }?.takeIf { it.isNotEmpty() })
                    }

                    if (mode == "IN") {
                        if (!finalProduct.hasBrandAndSize()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Could not detect ${finalProduct.getMissingFields()}", Toast.LENGTH_SHORT).show()
                                showManualEntryDialog(barcode, finalProduct)
                            }
                        } else {
                            runOnUiThread { showConfirmationDialog(finalProduct, barcode) }
                        }
                    } else {
                        runOnUiThread { showConfirmationDialog(finalProduct, barcode) }
                    }
                } else {
                    runOnUiThread {
                        responseText.text = "No product found for barcode: $barcode"
                        Toast.makeText(this@MainActivity, "Could not detect brand and size", Toast.LENGTH_SHORT).show()
                        showManualEntryDialog(barcode, null)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { responseText.text = "Error: ${e.message}" }
            }
        }
    }

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
                Product(title = "", brand = existingTireInDb.brand, size = existingTireInDb.size, images = null, barcode = barcode)
            } else null
        } catch (_: Exception) { null }
    }

    private fun showConfirmationDialog(product: Product, barcode: String) {
        runOnUiThread {
            val dialogView = layoutInflater.inflate(R.layout.dialog_tire_info, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.tireImage)
            val titleView = dialogView.findViewById<TextView>(R.id.textView)
            val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)
            val editButton = dialogView.findViewById<Button>(R.id.editButton)

            val mode = intent.getStringExtra("MODE") ?: "IN"
            titleView.text = "Verify Tire Information"
            confirmButton.text = if (mode == "IN") "Add" else "Remove"

            val imageUrl = product.images?.firstOrNull()
            if (imageUrl != null) {
                Glide.with(this@MainActivity).load(imageUrl).into(imageView)
            } else {
                imageView.setImageResource(R.drawable.placeholder)
            }

            dialogView.findViewById<TextView>(R.id.tvTitle).text =
                TireEntry.extractNameFromTitle(product.title, product.mpn) ?: product.title
            dialogView.findViewById<TextView>(R.id.tvSize).text = product.size
            dialogView.findViewById<TextView>(R.id.tvBrand).text = product.brand

            val dialog = AlertDialog.Builder(this@MainActivity).setView(dialogView).create()

            confirmButton.setOnClickListener {
                dialog.dismiss()
                if (mode == "IN") askAmount(product, barcode)
                else handleOutMode(product, barcode)
            }

            editButton.setOnClickListener {
                dialog.dismiss()
                showManualEntryDialog(barcode, product)
            }

            dialog.show()
        }
    }

    private fun handleOutMode(product: Product, barcode: String) {
        val existingTire = tireRepository.getTireByDetails(barcode, product.brand, product.size)
        if (existingTire != null) {
            askQuantityToRemove(existingTire)
        } else {
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

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        cancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            val amountToRemove = input.text.toString().toIntOrNull()
            if (amountToRemove != null && amountToRemove > 0) {
                if (amountToRemove > existingTire.quantity) {
                    Toast.makeText(this, "Cannot remove more than ${existingTire.quantity}", Toast.LENGTH_SHORT).show()
                } else {
                    val success = tireRepository.decreaseQuantity(existingTire.id, amountToRemove)
                    if (success) {
                        val newTotal = existingTire.quantity - amountToRemove
                        if (newTotal == 0) Toast.makeText(this, "Removed all tires. Entry deleted.", Toast.LENGTH_LONG).show()
                        else Toast.makeText(this, "Removed $amountToRemove tires. Remaining: $newTotal", Toast.LENGTH_LONG).show()
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
        val tireWidthInput = dialogView.findViewById<EditText>(R.id.tireWidthInput)
        val tireRatioInput = dialogView.findViewById<EditText>(R.id.tireRatioInput)
        val tireConstructionSpinner = dialogView.findViewById<Spinner>(R.id.tireConstructionSpinner)
        val tireDiameterInput = dialogView.findViewById<EditText>(R.id.tireDiameterInput)
        val tireLoadIndexInput = dialogView.findViewById<EditText>(R.id.tireLoadIndexInput)
        val tireSpeedRatingInput = dialogView.findViewById<EditText>(R.id.tireSpeedRatingInput)
        val plyRatingSpinner = dialogView.findViewById<Spinner>(R.id.plyRatingSpinner)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        val prefixes = arrayOf("None", "P", "LT", "ST", "C")
        tirePrefixSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, prefixes)

        val constructions = arrayOf("R", "D", "B")
        tireConstructionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, constructions)

        val loadRanges = arrayOf("None", "C", "D", "E", "F")
        plyRatingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, loadRanges)

        if (product != null) {
            val parsedName = TireEntry.extractNameFromTitle(product.title, product.mpn)
                ?: tireRepository.getTireByBarcode(barcode)?.name
                ?: product.brand.orEmpty()
            brandInput.setText(parsedName)

            val size = product.size.orEmpty()
            val sizeRegex = Regex("""(P|LT|ST|C)?\s*(\d{3})/(\d{2})(R|D|B)(\d{2}(?:\.\d)?)\s*(\d{2,3}(?:/\d{2,3})?)?([A-Z]{1,2})?\s*([C-F])?""", RegexOption.IGNORE_CASE)
            val match = sizeRegex.find(size)
            if (match != null) {
                val typeStr = match.groupValues[1]
                val width = match.groupValues[2]
                val ratio = match.groupValues[3]
                val construction = match.groupValues[4]
                val diameter = match.groupValues[5]
                val loadIndex = match.groupValues[6]
                val speedRating = match.groupValues[7]
                val loadRange = match.groupValues[8]
                val prefixIndex = prefixes.indexOfFirst { it.equals(typeStr, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                tirePrefixSpinner.setSelection(prefixIndex)
                tireWidthInput.setText(width)
                tireRatioInput.setText(ratio)
                val constructionIndex = constructions.indexOfFirst { it.equals(construction, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                tireConstructionSpinner.setSelection(constructionIndex)
                tireDiameterInput.setText(diameter)
                var speedRatingVal = speedRating
                var loadRangeVal = loadRange
                if (loadRangeVal.isEmpty() && speedRatingVal.matches(Regex("[C-F]", RegexOption.IGNORE_CASE)) && loadIndex.isEmpty()) {
                    loadRangeVal = speedRatingVal.uppercase()
                    speedRatingVal = ""
                }
                tireLoadIndexInput.setText(loadIndex)
                tireSpeedRatingInput.setText(speedRatingVal.uppercase())
                val loadRangeIndex = loadRanges.indexOfFirst { it.equals(loadRangeVal, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                plyRatingSpinner.setSelection(loadRangeIndex)
            }
        }

        val dialog = AlertDialog.Builder(this).setTitle("Enter Tire Information").setView(dialogView).create()

        cancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            val name = brandInput.text.toString()
            val prefix = tirePrefixSpinner.selectedItem.toString()
            val width = tireWidthInput.text.toString()
            val ratio = tireRatioInput.text.toString()
            val construction = tireConstructionSpinner.selectedItem.toString()
            val diameter = tireDiameterInput.text.toString()
            val loadIndex = tireLoadIndexInput.text.toString()
            val speedRating = tireSpeedRatingInput.text.toString().uppercase()
            val loadRange = plyRatingSpinner.selectedItem.toString()

            val fullSize = buildString {
                if (prefix != "None") append("$prefix ")
                append("$width/$ratio$construction$diameter")
                if (loadIndex.isNotEmpty()) append(" $loadIndex")
                if (speedRating.isNotEmpty()) append(speedRating)
                if (loadRange != "None") append(" $loadRange")
            }

            if (name.isNotEmpty() && width.isNotEmpty() && diameter.isNotEmpty()) {
                val manualProduct = Product(title = name, brand = product?.brand ?: name, size = fullSize, images = product?.images, barcode = barcode, mpn = product?.mpn)
                askAmount(manualProduct, barcode)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter at least name, width, and diameter", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun askAmount(product: Product, barcode: String) {
        val dialogView = layoutInflater.inflate(R.layout.tire_amount, null)
        val input = dialogView.findViewById<EditText>(R.id.etAmount)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        cancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            val amount = input.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                dialog.dismiss()
                saveTireToInventory(product, barcode, amount)
            } else {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTireToInventory(product: Product, barcode: String, quantity: Int) {
        val existingTire = tireRepository.getTireByDetails(barcode, product.brand, product.size)

        tireRepository.saveToCache(barcode, product.brand ?: "", product.size ?: "", product.images?.firstOrNull(), product.title)

        if (existingTire != null) {
            val success = tireRepository.increaseQuantity(existingTire.id, quantity)
            if (success) {
                val newTotal = existingTire.quantity + quantity
                Toast.makeText(this, "Added $quantity tires. Total: $newTotal", Toast.LENGTH_LONG).show()
            }
        } else {
            val id = tireRepository.insertTire(product, barcode, quantity)
            if (id > 0) {
                Toast.makeText(this, "Added $quantity new tires", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error saving tire", Toast.LENGTH_SHORT).show()
                return
            }
        }

        lifecycleScope.launch {
            syncManager.syncToWeb { result ->
                Log.d("MainActivity", "Web sync: $result")
            }
        }
    }

    private fun clearDisplay() {
        barcodeText.text = "No barcode scanned"
        responseText.text = "Waiting for scan..."
    }
}