package com.beck.tirescanner

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

        tireRepository = TireRepository(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.inventoryRecyclerView)
        searchBar = findViewById(R.id.searchBar)
        emptyStateText = findViewById(R.id.emptyStateText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TireAdapter(emptyList()) { tire ->
            showRemoveDialog(tire)
        }
        recyclerView.adapter = adapter

        loadInventory()

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isEmpty()) loadInventory() else searchInventory(query)
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

    private fun showRemoveDialog(tire: com.beck.tirescanner.database.TireEntry) {
        AlertDialog.Builder(this)
            .setTitle("${tire.brand} ${tire.size}")
            .setMessage("Current quantity: ${tire.quantity}")
            .setPositiveButton("Remove All") { _, _ ->
                tireRepository.deleteTire(tire.id)
                loadInventory()
            }
            .setNegativeButton("Adjust Quantity") { _, _ ->
                showAdjustQuantityDialog(tire)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showAdjustQuantityDialog(tire: com.beck.tirescanner.database.TireEntry) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Current: ${tire.quantity}"

        AlertDialog.Builder(this)
            .setTitle("Adjust Quantity")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newQty = input.text.toString().toIntOrNull()
                if (newQty != null && newQty >= 0) {
                    if (newQty == 0) {
                        tireRepository.deleteTire(tire.id)
                    } else {
                        tireRepository.updateQuantity(tire.id, newQty)
                    }
                    loadInventory()
                } else {
                    Toast.makeText(this, "Enter a valid quantity", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}