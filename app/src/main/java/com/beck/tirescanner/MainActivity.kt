package com.beck.tirescanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var clearButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyHistoryText: TextView
    private lateinit var historyCountText: TextView
    private lateinit var scanHistoryAdapter: ScanHistoryAdapter
    private var isDialogShowing = false

    companion object {
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
        clearButton = findViewById(R.id.clearButton)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)
        historyCountText = findViewById(R.id.historyCountText)

        scanHistoryAdapter = ScanHistoryAdapter()
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = scanHistoryAdapter

        // Restore history that survived activity recreation
        if (scanHistoryAdapter.getCount() > 0) {
            emptyHistoryText.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            historyCountText.text = "${scanHistoryAdapter.getCount()} scan${if (scanHistoryAdapter.getCount() == 1) "" else "s"}"
            scanHistoryAdapter.notifyDataSetChanged()
        }

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
        lifecycleScope.launch {
            syncManager.pullFromWeb { result ->
                Log.d("SyncManager", "Pull: $result")
            }
        }
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

    private fun setScannerEnabled(enabled: Boolean) {
        val bundle = Bundle()
        bundle.putString("PROFILE_NAME", "BarcodeScannerProfile")
        bundle.putString("PROFILE_ENABLED", "true")
        bundle.putString("CONFIG_MODE", "UPDATE")
        val barcodeConfig = Bundle()
        barcodeConfig.putString("PLUGIN_NAME", "BARCODE")
        barcodeConfig.putString("RESET_CONFIG", "false")
        val barcodeParams = Bundle()
        barcodeParams.putString("scanner_input_enabled", if (enabled) "true" else "false")
        barcodeConfig.putBundle("PARAM_LIST", barcodeParams)
        bundle.putBundle("PLUGIN_CONFIG", barcodeConfig)
        val dwIntent = Intent()
        dwIntent.action = "com.symbol.datawedge.api.ACTION"
        dwIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", bundle)
        sendBroadcast(dwIntent)
    }

    private fun handleBarcodeScanned(barcode: String) {
        runOnUiThread { barcodeText.text = barcode }
        sendBarcodeToAPI(barcode)
    }

    private fun sendBarcodeToAPI(barcode: String) {
        if (isDialogShowing) return
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
                        Toast.makeText(this@MainActivity, "Could not detect brand and size", Toast.LENGTH_SHORT).show()
                        showManualEntryDialog(barcode, null)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                Glide.with(this@MainActivity)
                    .load(imageUrl)
                    .error(R.drawable.placeholder)
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.placeholder)
            }

            dialogView.findViewById<TextView>(R.id.tvTitle).text =
                TireEntry.extractNameFromTitle(product.title, product.mpn) ?: product.title
            dialogView.findViewById<TextView>(R.id.tvSize).text = product.size
            dialogView.findViewById<TextView>(R.id.tvBrand).text = product.brand

            val dialog = AlertDialog.Builder(this@MainActivity).setView(dialogView).create()

            setScannerEnabled(false)
            isDialogShowing = true
            dialog.setOnDismissListener { setScannerEnabled(true); isDialogShowing = false }

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
            askQuantityToRemove(existingTire, product)
        } else {
            Toast.makeText(this, "Tire not found in inventory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askQuantityToRemove(existingTire: TireEntry, product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.tire_amount, null)
        val input = dialogView.findViewById<EditText>(R.id.etAmount)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        dialogView.findViewById<TextView>(R.id.etTitle).text = "Remove Quantity"
        input.hint = "Max: ${existingTire.quantity}"

        confirmButton.isEnabled = false
        confirmButton.alpha = 0.5f

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val valid = s.toString().toIntOrNull()?.let { it > 0 } ?: false
                confirmButton.isEnabled = valid
                confirmButton.alpha = if (valid) 1.0f else 0.5f
            }
        })

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        setScannerEnabled(false)
        isDialogShowing = true
        dialog.setOnDismissListener { setScannerEnabled(true); isDialogShowing = false }
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
                        val label = buildTireLabel(product)
                        addScanHistoryEntry("OUT", amountToRemove, label)

                        if (newTotal == 0) Toast.makeText(this, "Removed all tires. Entry deleted.", Toast.LENGTH_LONG).show()
                        else Toast.makeText(this, "Removed $amountToRemove tires. Remaining: $newTotal", Toast.LENGTH_LONG).show()

                        lifecycleScope.launch {
                            syncManager.postScanHistory(
                                syncId = existingTire.syncId,
                                barcode = existingTire.barcode,
                                name = existingTire.name,
                                brand = existingTire.brand,
                                size = existingTire.size,
                                action = "OUT",
                                quantity = amountToRemove
                            )
                            syncManager.syncToWeb { result ->
                                Log.d("MainActivity", "Web sync: $result")
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error removing tires", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showManualEntryDialog(barcode: String, product: Product?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null)
        val brandInput = dialogView.findViewById<EditText>(R.id.brandInput)
        val tireModeGroup = dialogView.findViewById<RadioGroup>(R.id.tireModeGroup)
        val radioMetric = dialogView.findViewById<RadioButton>(R.id.radioMetric)
        val radioCommercial = dialogView.findViewById<RadioButton>(R.id.radioCommercial)
        val rowPrefix = dialogView.findViewById<LinearLayout>(R.id.rowPrefix)
        val rowRatio = dialogView.findViewById<LinearLayout>(R.id.rowRatio)
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

        val loadRanges = arrayOf("None", "C (6PR)", "D (8PR)", "E (10PR)", "F (12PR)", "G (14PR)")
        plyRatingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, loadRanges)

        fun applyMode(isCommercial: Boolean) {
            rowPrefix.visibility = if (isCommercial) View.GONE else View.VISIBLE
            rowRatio.visibility = if (isCommercial) View.GONE else View.VISIBLE
        }

        var metricValues = mapOf<String, String>()
        var commercialValues = mapOf<String, String>()

        fun saveMetric() {
            metricValues = mapOf(
                "prefix" to tirePrefixSpinner.selectedItemPosition.toString(),
                "width" to tireWidthInput.text.toString(),
                "ratio" to tireRatioInput.text.toString(),
                "construction" to tireConstructionSpinner.selectedItemPosition.toString(),
                "diameter" to tireDiameterInput.text.toString(),
                "loadIndex" to tireLoadIndexInput.text.toString(),
                "speedRating" to tireSpeedRatingInput.text.toString(),
                "loadRange" to plyRatingSpinner.selectedItemPosition.toString()
            )
        }

        fun saveCommercial() {
            commercialValues = mapOf(
                "width" to tireWidthInput.text.toString(),
                "construction" to tireConstructionSpinner.selectedItemPosition.toString(),
                "diameter" to tireDiameterInput.text.toString(),
                "loadIndex" to tireLoadIndexInput.text.toString(),
                "speedRating" to tireSpeedRatingInput.text.toString(),
                "loadRange" to plyRatingSpinner.selectedItemPosition.toString()
            )
        }

        fun restoreMetric() {
            tirePrefixSpinner.setSelection(metricValues["prefix"]?.toIntOrNull() ?: 0)
            tireWidthInput.setText(metricValues["width"] ?: "")
            tireRatioInput.setText(metricValues["ratio"] ?: "")
            tireConstructionSpinner.setSelection(metricValues["construction"]?.toIntOrNull() ?: 0)
            tireDiameterInput.setText(metricValues["diameter"] ?: "")
            tireLoadIndexInput.setText(metricValues["loadIndex"] ?: "")
            tireSpeedRatingInput.setText(metricValues["speedRating"] ?: "")
            plyRatingSpinner.setSelection(metricValues["loadRange"]?.toIntOrNull() ?: 0)
        }

        fun restoreCommercial() {
            tireWidthInput.setText(commercialValues["width"] ?: "")
            tireConstructionSpinner.setSelection(commercialValues["construction"]?.toIntOrNull() ?: 0)
            tireDiameterInput.setText(commercialValues["diameter"] ?: "")
            tireLoadIndexInput.setText(commercialValues["loadIndex"] ?: "")
            tireSpeedRatingInput.setText(commercialValues["speedRating"] ?: "")
            plyRatingSpinner.setSelection(commercialValues["loadRange"]?.toIntOrNull() ?: 0)
        }

        tireModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioCommercial) {
                saveMetric()
                applyMode(true)
                restoreCommercial()
            } else {
                saveCommercial()
                applyMode(false)
                restoreMetric()
            }
        }

        val size = product?.size.orEmpty()

        val isCommercial = size.isNotEmpty() &&
                Regex("""^\d{2,3}R\d{2}""", RegexOption.IGNORE_CASE).containsMatchIn(size) &&
                !Regex("""^\d{3}/""").containsMatchIn(size)

        if (isCommercial) {
            radioCommercial.isChecked = true
            applyMode(true)
        } else {
            radioMetric.isChecked = true
            applyMode(false)
        }

        if (product != null) {
            val parsedName = TireEntry.extractNameFromTitle(product.title, product.mpn)
                ?: tireRepository.getTireByBarcode(product.barcode ?: "")?.name
                ?: product.brand.orEmpty()
            brandInput.setText(parsedName)

            Log.d("TireSize", "size to parse: '$size'")

            if (isCommercial) {
                val commercialRegex = Regex(
                    """^(P|LT|ST|C)?\s*(\d{2,3})(R|D|B)(\d{2}(?:\.\d)?)\s*(\d{2,3}(?:/\d{2,3})?)?([A-Z]{1,2})?\s*([C-G])?$""",
                    RegexOption.IGNORE_CASE
                )
                val cm = commercialRegex.find(size.trim())
                if (cm != null) {
                    tireWidthInput.setText(cm.groupValues[2])
                    val constructionIndex = constructions.indexOfFirst { it.equals(cm.groupValues[3], ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    tireConstructionSpinner.setSelection(constructionIndex)
                    tireDiameterInput.setText(cm.groupValues[4])
                    tireLoadIndexInput.setText(cm.groupValues[5])
                    var speedRatingVal = cm.groupValues[6]
                    var loadRangeVal = cm.groupValues[7]
                    if (loadRangeVal.isEmpty() && speedRatingVal.matches(Regex("[C-G]", RegexOption.IGNORE_CASE))) {
                        loadRangeVal = speedRatingVal.uppercase()
                        speedRatingVal = ""
                    }
                    tireSpeedRatingInput.setText(speedRatingVal.uppercase())
                    val loadRangeIndex = loadRanges.indexOfFirst { it.startsWith(loadRangeVal, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    plyRatingSpinner.setSelection(loadRangeIndex)
                }
            } else {
                val sizeRegex = Regex(
                    """(P|LT|ST|C)?\s*(\d{3})/(\d{2})(R|D|B)(\d{2}(?:\.\d)?)\s*(\d{2,3}(?:/\d{2,3})?)?\s*([A-Z]{1,2})?\s*([C-G])?""",
                    RegexOption.IGNORE_CASE
                )
                val match = sizeRegex.find(size)
                if (match != null) {
                    val prefixIndex = prefixes.indexOfFirst { it.equals(match.groupValues[1], ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    tirePrefixSpinner.setSelection(prefixIndex)
                    tireWidthInput.setText(match.groupValues[2])
                    tireRatioInput.setText(match.groupValues[3])
                    val constructionIndex = constructions.indexOfFirst { it.equals(match.groupValues[4], ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    tireConstructionSpinner.setSelection(constructionIndex)
                    tireDiameterInput.setText(match.groupValues[5])
                    var speedRatingVal = match.groupValues[7]
                    var loadRangeVal = match.groupValues[8]
                    if (loadRangeVal.isEmpty() && speedRatingVal.matches(Regex("[C-G]", RegexOption.IGNORE_CASE)) && match.groupValues[6].isEmpty()) {
                        loadRangeVal = speedRatingVal.uppercase()
                        speedRatingVal = ""
                    }
                    tireLoadIndexInput.setText(match.groupValues[6])
                    tireSpeedRatingInput.setText(speedRatingVal.uppercase())
                    val loadRangeIndex = loadRanges.indexOfFirst { it.startsWith(loadRangeVal, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    plyRatingSpinner.setSelection(loadRangeIndex)
                }
            }
        }

        val dialog = AlertDialog.Builder(this).setTitle("Enter Tire Information").setView(dialogView).create()

        setScannerEnabled(false)
        isDialogShowing = true
        dialog.setOnDismissListener { setScannerEnabled(true); isDialogShowing = false }

        cancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            val name = brandInput.text.toString()
            val width = tireWidthInput.text.toString()
            val diameter = tireDiameterInput.text.toString()
            val construction = tireConstructionSpinner.selectedItem.toString()
            val loadIndex = tireLoadIndexInput.text.toString()
            val speedRating = tireSpeedRatingInput.text.toString().uppercase()
            val loadRange = plyRatingSpinner.selectedItem.toString()
            val loadRangeCode = if (loadRange != "None") loadRange.substringBefore(" ") else ""

            val fullSize = if (radioCommercial.isChecked) {
                buildString {
                    append("$width$construction$diameter")
                    if (loadIndex.isNotEmpty()) append(" $loadIndex")
                    if (speedRating.isNotEmpty()) append(speedRating)
                    if (loadRangeCode.isNotEmpty()) append(" $loadRangeCode")
                }
            } else {
                val prefix = tirePrefixSpinner.selectedItem.toString()
                val ratio = tireRatioInput.text.toString()
                buildString {
                    if (prefix != "None") append("$prefix ")
                    if (ratio.isNotEmpty()) append("$width/$ratio$construction$diameter")
                    else append("$width$construction$diameter")
                    if (loadIndex.isNotEmpty()) append(" $loadIndex")
                    if (speedRating.isNotEmpty()) append(speedRating)
                    if (loadRangeCode.isNotEmpty()) append(" $loadRangeCode")
                }
            }

            if (name.isNotEmpty() && width.isNotEmpty() && diameter.isNotEmpty()) {
                val manualProduct = Product(
                    title = name,
                    brand = product?.brand ?: name,
                    size = fullSize,
                    images = product?.images,
                    barcode = barcode,
                    mpn = product?.mpn
                )
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

        confirmButton.isEnabled = false
        confirmButton.alpha = 0.5f

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val valid = s.toString().toIntOrNull()?.let { it > 0 } ?: false
                confirmButton.isEnabled = valid
                confirmButton.alpha = if (valid) 1.0f else 0.5f
            }
        })

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        setScannerEnabled(false)
        isDialogShowing = true
        dialog.setOnDismissListener { setScannerEnabled(true); isDialogShowing = false }
        dialog.show()

        cancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            val amount = input.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                dialog.dismiss()
                saveTireToInventory(product, barcode, amount)
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
                val label = buildTireLabel(product)
                addScanHistoryEntry("IN", quantity, label)
                Toast.makeText(this, "Added $quantity tires. Total: $newTotal", Toast.LENGTH_LONG).show()
            }
        } else {
            val id = tireRepository.insertTire(product, barcode, quantity)
            if (id > 0) {
                val label = buildTireLabel(product)
                addScanHistoryEntry("IN", quantity, label)
                Toast.makeText(this, "Added $quantity new tires", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error saving tire", Toast.LENGTH_SHORT).show()
                return
            }
        }

        lifecycleScope.launch {
            syncManager.postScanHistory(
                syncId = tireRepository.getTireByDetails(barcode, product.brand, product.size)?.syncId,
                barcode = barcode,
                name = TireEntry.extractNameFromTitle(product.title, product.mpn) ?: product.brand,
                brand = product.brand,
                size = product.size,
                action = "IN",
                quantity = quantity
            )
            syncManager.syncToWeb { result ->
                Log.d("MainActivity", "Web sync: $result")
            }
        }
    }

    // ── History helpers ──────────────────────────────────────────────────────

    private fun buildTireLabel(product: Product): String {
        val name = TireEntry.extractNameFromTitle(product.title, product.mpn)
            ?: product.title?.takeIf { it.isNotEmpty() }
            ?: product.brand
            ?: "Unknown Tire"
        val size = product.size?.takeIf { it.isNotEmpty() }
        return if (size != null) "$size $name" else name
    }

    private fun addScanHistoryEntry(mode: String, quantity: Int, tireLabel: String) {
        runOnUiThread {
            scanHistoryAdapter.addEntry(
                ScanEntry(mode = mode, quantity = quantity, tireDescription = tireLabel)
            )
            emptyHistoryText.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            historyCountText.text = "${scanHistoryAdapter.getCount()} scan${if (scanHistoryAdapter.getCount() == 1) "" else "s"}"
        }
    }

    private fun clearDisplay() {
        barcodeText.text = "No barcode scanned"
        scanHistoryAdapter.clear()
        emptyHistoryText.visibility = View.VISIBLE
        historyRecyclerView.visibility = View.GONE
        historyCountText.text = "0 scans"
    }
}