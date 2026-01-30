package com.beck.tirescanner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.beck.tirescanner.database.TireRepository
import com.google.android.material.card.MaterialCardView

class HomeActivity : AppCompatActivity() {

    private lateinit var tireRepository: TireRepository
    private lateinit var totalTiresText: TextView
    private lateinit var uniqueTypesText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialize repository
        tireRepository = TireRepository(this)

        // Initialize UI
        totalTiresText = findViewById(R.id.totalTiresText)
        uniqueTypesText = findViewById(R.id.uniqueTypesText)

        // Update stats
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

        // View Inventory button
        findViewById<Button>(R.id.viewInventoryButton).setOnClickListener {
            // TODO: Show inventory list
        }
    }

    override fun onResume() {
        super.onResume()
        // Update stats when coming back from scanner
        updateInventoryStats()
    }

    private fun updateInventoryStats() {
        val totalTires = tireRepository.getTotalTireCount()
        val uniqueTypes = tireRepository.getAllTires().size

        totalTiresText.text = "$totalTires Total Tires"
        uniqueTypesText.text = "$uniqueTypes Different Types"
    }
}