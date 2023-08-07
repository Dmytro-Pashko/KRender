package com.dpashko.krender.common

import kotlin.math.roundToInt

object MemoryFormatter {

    // Precomputed constants for faster division and multiplication
    private const val BYTES_IN_MB = 1024 * 1024
    private const val BYTES_IN_MB_INV = 1 / BYTES_IN_MB
    private const val MB_PREFIX = " MB"

    // Reusable StringBuilder for building the output string
    private val stringBuilder = StringBuilder(8)

    // Round the mbValue to two decimal places and format the output directly into the StringBuilder.
    // This avoids unnecessary string concatenations and formatting overhead, resulting in improved performance.
    fun convertToMB(bytes: Long): String {
        val mbValue = bytes.toDouble() / BYTES_IN_MB

        // Reuse the StringBuilder and clear its contents
        stringBuilder.clear()

        // Perform manual rounding and formatting
        val roundedValue = (mbValue * 100).roundToInt() // Round to two decimal places
        val integerPart = roundedValue / 100
        val decimalPart = roundedValue % 100

        stringBuilder
            .append(integerPart)
            .append('.')
            .append(decimalPart)
            .append(MB_PREFIX)

        return stringBuilder.toString()
    }
}