package com.beck.tirescanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beck.tirescanner.database.TireEntry
import com.beck.tirescanner.database.TireRepository
import com.beck.tirescanner.network.RetrofitClient
import com.beck.tirescanner.network.SyncManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var tireRepository: TireRepository
    private lateinit var syncManager: SyncManager
    private lateinit var totalTiresText: TextView
    private lateinit var uniqueTypesText: TextView

    companion object {
        private const val DATAWEDGE_INTENT_ACTION = "com.beck.tirescanner.SCAN"
        private const val DATAWEDGE_INTENT_CATEGORY = "android.intent.category.DEFAULT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        RetrofitClient.RAINFOREST_API_KEY = BuildConfig.RAINFOREST_API_KEY

        tireRepository = TireRepository(this)
        tireRepository.backfillSkus()
        syncManager = SyncManager(this, tireRepository, BuildConfig.SYNC_TOKEN)

        totalTiresText = findViewById(R.id.totalTiresText)
        uniqueTypesText = findViewById(R.id.uniqueTypesText)

        updateInventoryStats()
        configureDataWedge()

        findViewById<MaterialCardView>(R.id.inCard).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("MODE", "IN")
            startActivity(intent)
        }

        findViewById<MaterialCardView>(R.id.outCard).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("MODE", "OUT")
            startActivity(intent)
        }

        findViewById<Button>(R.id.viewInventoryButton).setOnClickListener {
            val intent = Intent(this, InventoryActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateInventoryStats()
    }

    private fun updateInventoryStats() {
        val totalTires = tireRepository.getTotalTireCount()
        val uniqueTypes = tireRepository.getAllTires().size
        totalTiresText.text = "$totalTires Total Tires"
        uniqueTypesText.text = "$uniqueTypes Different Types"
    }

    private fun configureDataWedge() {
        val deleteIntent = android.content.Intent()
        deleteIntent.action = "com.symbol.datawedge.api.ACTION"
        deleteIntent.putExtra("com.symbol.datawedge.api.DELETE_PROFILE", arrayOf("BarcodeScannerProfile"))
        sendBroadcast(deleteIntent)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val createIntent = android.content.Intent()
            createIntent.action = "com.symbol.datawedge.api.ACTION"
            createIntent.putExtra("com.symbol.datawedge.api.CREATE_PROFILE", "BarcodeScannerProfile")
            sendBroadcast(createIntent)

            val intentBundle = android.os.Bundle()
            intentBundle.putString("PROFILE_NAME", "BarcodeScannerProfile")
            intentBundle.putString("PROFILE_ENABLED", "true")
            intentBundle.putString("CONFIG_MODE", "UPDATE")

            val appConfig = android.os.Bundle()
            appConfig.putString("PACKAGE_NAME", packageName)
            appConfig.putStringArray("ACTIVITY_LIST", arrayOf("com.beck.tirescanner.MainActivity"))
            intentBundle.putParcelableArray("APP_LIST", arrayOf(appConfig))

            val intentConfig = android.os.Bundle()
            intentConfig.putString("PLUGIN_NAME", "INTENT")
            intentConfig.putString("RESET_CONFIG", "true")
            val intentProps = android.os.Bundle()
            intentProps.putString("intent_output_enabled", "true")
            intentProps.putString("intent_action", DATAWEDGE_INTENT_ACTION)
            intentProps.putString("intent_category", DATAWEDGE_INTENT_CATEGORY)
            intentProps.putInt("intent_delivery", 0)
            intentConfig.putBundle("PARAM_LIST", intentProps)
            intentBundle.putBundle("PLUGIN_CONFIG", intentConfig)

            val intentProfileIntent = android.content.Intent()
            intentProfileIntent.action = "com.symbol.datawedge.api.ACTION"
            intentProfileIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", intentBundle)
            sendBroadcast(intentProfileIntent)

            val barcodeBundle = android.os.Bundle()
            barcodeBundle.putString("PROFILE_NAME", "BarcodeScannerProfile")
            barcodeBundle.putString("PROFILE_ENABLED", "true")
            barcodeBundle.putString("CONFIG_MODE", "UPDATE")

            val barcodeConfig = android.os.Bundle()
            barcodeConfig.putString("PLUGIN_NAME", "BARCODE")
            barcodeConfig.putString("RESET_CONFIG", "true")
            val barcodeParams = android.os.Bundle()
            barcodeParams.putString("scanner_selection", "auto")
            barcodeParams.putString("scanner_input_enabled", "true")
            barcodeParams.putString("decoder_upca", "true")
            barcodeParams.putString("decoder_upce", "true")
            barcodeParams.putString("decoder_ean13", "true")
            barcodeParams.putString("decoder_ean8", "true")
            barcodeParams.putString("decoder_code128", "true")
            barcodeParams.putString("decoder_code39", "true")
            barcodeParams.putString("decoder_qrcode", "false")
            barcodeParams.putString("decoder_datamatrix", "false")
            barcodeParams.putString("decoder_pdf417", "false")
            barcodeParams.putString("decoder_aztec", "false")
            barcodeParams.putString("decoder_maxicode", "false")
            barcodeConfig.putBundle("PARAM_LIST", barcodeParams)
            barcodeBundle.putBundle("PLUGIN_CONFIG", barcodeConfig)

            val barcodeProfileIntent = android.content.Intent()
            barcodeProfileIntent.action = "com.symbol.datawedge.api.ACTION"
            barcodeProfileIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", barcodeBundle)
            sendBroadcast(barcodeProfileIntent)
        }, 200)
    }
}