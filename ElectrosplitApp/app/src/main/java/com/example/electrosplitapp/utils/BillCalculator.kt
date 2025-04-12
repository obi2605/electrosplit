package com.example.electrosplitapp.utils

import kotlin.math.min

object BillCalculator {
    // Full tariff slab definition (exactly as in your original)
    private val tariffSlabs = listOf(
        TariffSlab(1, 100, 0f, 500),
        TariffSlab(101, 200, 2.35f, 500),
        TariffSlab(201, 400, 4.7f, 500),
        TariffSlab(401, 500, 6.3f, 500),
        TariffSlab(1, 100, 0f, Int.MAX_VALUE),
        TariffSlab(101, 400, 4.7f, Int.MAX_VALUE),
        TariffSlab(401, 500, 6.3f, Int.MAX_VALUE),
        TariffSlab(501, 600, 8.4f, Int.MAX_VALUE),
        TariffSlab(601, 800, 9.45f, Int.MAX_VALUE),
        TariffSlab(801, 1000, 10.5f, Int.MAX_VALUE),
        TariffSlab(1001, Int.MAX_VALUE, 11.55f, Int.MAX_VALUE)
    )

    // Original data class maintained exactly
    data class TariffSlab(
        val fromUnit: Int,
        val toUnit: Int,
        val rate: Float,
        val maxUnits: Int
    )

    /**
     * Parses a meter reading string into a float value
     * Handles leading zeros and decimal points
     * (New method to address your requirements)
     */
    fun parseReading(reading: String): Float {
        return try {
            // Remove all non-digit/non-decimal characters and parse
            reading.replace("[^0-9.]".toRegex(), "").toFloat()
        } catch (e: NumberFormatException) {
            0f // Return 0 if parsing fails
        }
    }

    /**
     * Original calculateBill function with improved decimal handling
     */
    private fun calculateBill(units: Float): Float {
        if (units <= 0) return 0f

        var remainingUnits = units
        var totalAmount = 0f

        for (slab in tariffSlabs) {
            if (remainingUnits <= 0) break

            // Calculate units in this slab range
            val slabUnits = when {
                remainingUnits <= slab.fromUnit -> 0f
                else -> minOf(
                    (slab.toUnit - slab.fromUnit + 1).toFloat(), // Slab range
                    slab.maxUnits.toFloat(),                      // Max units allowed
                    remainingUnits                                // Remaining units
                )
            }

            if (slabUnits > 0) {
                totalAmount += slabUnits * slab.rate
                remainingUnits -= slabUnits
            }
        }

        return totalAmount
    }

    /**
     * Enhanced version of your original calculateSplit function
     * Now properly uses the totalBillAmount parameter
     */
    fun calculateSplit(
        totalBillAmount: Float,
        totalUnits: Float,
        individualReadings: List<Float>,
        groupSize: Int
    ): SplitResult {
        require(groupSize > 0) { "Group size must be positive" }
        require(totalUnits >= 0) { "Total units cannot be negative" }

        val totalIndividualConsumption = individualReadings.sum()
        val commonConsumption = (totalUnits - totalIndividualConsumption).coerceAtLeast(0f)
        val commonShare = commonConsumption / groupSize

        val individualBills = individualReadings.map { reading ->
            val totalUnitsForMember = reading + commonShare
            val amount = calculateBill(totalUnitsForMember)
            MemberBill(reading, amount)
        }

        return SplitResult(
            individualBills = individualBills,
            commonConsumption = commonConsumption,
            commonSharePerMember = commonShare,
            totalBillAmount = totalBillAmount,
            totalUnits = totalUnits
        )
    }

    // Data classes for better structured returns
    data class MemberBill(
        val individualReading: Float,
        val amountToPay: Float
    )

    data class SplitResult(
        val individualBills: List<MemberBill>,
        val commonConsumption: Float,
        val commonSharePerMember: Float,
        val totalBillAmount: Float,
        val totalUnits: Float
    ) {
        /**
         * Formats the complete calculation breakdown
         * (New method to show calculation details)
         */
        fun getFormattedBreakdown(): String {
            return buildString {
                appendLine("=== Bill Calculation Breakdown ===")
                appendLine("Total Bill Amount: ₹${"%.2f".format(totalBillAmount)}")
                appendLine("Total Units: $totalUnits kWh")
                appendLine("Common Consumption: ${"%.2f".format(commonConsumption)} kWh")
                appendLine("Common Share (per member): ${"%.2f".format(commonSharePerMember)} kWh")
                appendLine()
                appendLine("--- Individual Shares ---")
                individualBills.forEachIndexed { index, bill ->
                    appendLine("Member ${index + 1}:")
                    appendLine("  Individual Reading: ${bill.individualReading} kWh")
                    appendLine("  Amount To Pay: ₹${"%.2f".format(bill.amountToPay)}")
                }
            }
        }
    }
}