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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        RetrofitClient.API_KEY = BuildConfig.BARCODE_API_KEY

        tireRepository = TireRepository(this)
        tireRepository.backfillSkus()
        syncManager = SyncManager(this, tireRepository, BuildConfig.SYNC_TOKEN)
        syncManager.startServer()

        totalTiresText = findViewById(R.id.totalTiresText)
        uniqueTypesText = findViewById(R.id.uniqueTypesText)

        updateInventoryStats()

        // IN button - opens MainActivity in IN mode
        findViewById<MaterialCardView>(R.id.inCard).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("MODE", "IN")
            startActivity(intent)
        }

        // OUT button - opens MainActivity in OUT mode
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
        autoSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.stopServer()
    }

    private fun autoSync() {
        lifecycleScope.launch {
            syncManager.discoverAndSync {
                updateInventoryStats()
            }
        }
    }
    private fun updateInventoryStats() {
        val totalTires = tireRepository.getTotalTireCount()
        val uniqueTypes = tireRepository.getAllTires().size

        totalTiresText.text = "$totalTires Total Tires"
        uniqueTypesText.text = "$uniqueTypes Different Types"
    }
}