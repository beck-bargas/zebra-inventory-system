package com.beck.tirescanner.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class TireSizeTextWatcher(private val editText: EditText) : TextWatcher {
    private var isFormatting = false
    private var deletingSlash = false
    private var deletingR = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (!isFormatting && count > 0) {
            val deletedChar = s?.getOrNull(start)
            deletingSlash = deletedChar == '/'
            deletingR = deletedChar == 'R'
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return

        isFormatting = true

        // Extract only digits
        val digits = s.toString().replace(Regex("[^0-9]"), "")

        // Handle backspace over separators
        val adjustedDigits = if (deletingSlash && digits.length >= 3) {
            digits.substring(0, digits.length - 1)
        } else if (deletingR && digits.length >= 5) {
            digits.substring(0, digits.length - 1)
        } else {
            digits
        }

        // Format the tire size
        val formatted = formatTireSize(adjustedDigits)

        editText.setText(formatted)
        editText.setSelection(formatted.length)

        deletingSlash = false
        deletingR = false
        isFormatting = false
    }

    private fun formatTireSize(digits: String): String {
        return when {
            digits.isEmpty() -> ""
            digits.length <= 3 -> digits
            digits.length <= 5 -> "${digits.substring(0, 3)}/${digits.substring(3)}"
            else -> {
                val width = digits.substring(0, 3)
                val aspect = digits.substring(3, 5)
                val diameter = digits.substring(5, minOf(7, digits.length))
                "$width/$aspect R$diameter"
            }
        }
    }
}