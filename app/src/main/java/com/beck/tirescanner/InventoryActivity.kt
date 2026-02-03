package com.beck.tirescanner

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.beck.tirescanner.database.TireRepository
import com.google.android.material.appbar.MaterialToolbar

class InventoryActivity : AppCompatActivity() {
    private lateinit var tireRepository: TireRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TireAdapter
    private lateinit var searchBar: EditText
    private lateinit var emptyStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory)

        // Initialize repository
        tireRepository = TireRepository(this)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Initialize views
        recyclerView = findViewById(R.id.inventoryRecyclerView)
        searchBar = findViewById(R.id.searchBar)
        emptyStateText = findViewById(R.id.emptyStateText)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TireAdapter(emptyList())
        recyclerView.adapter = adapter

        // Load all tires
        loadInventory()

        // Setup search
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isEmpty()) {
                    loadInventory()
                } else {
                    searchInventory(query)
                }
            }
        })
    }

    private fun loadInventory() {
        val tires = tireRepository.getAllTires()

        if (tires.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            adapter.updateTires(tires)
        }
    }

    private fun searchInventory(query: String) {
        val tires = tireRepository.searchTires(query)

        if (tires.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "No tires found"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            adapter.updateTires(tires)
        }
    }
}