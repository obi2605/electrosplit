package com.example.electrosplitapp.utils

import kotlin.math.min

object BillCalculator {
    // Updated tariff slab definition
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

    data class TariffSlab(
        val fromUnit: Int,
        val toUnit: Int,
        val rate: Float,
        val maxUnits: Int
    )

    fun parseReading(reading: String): Float {
        return try {
            reading.replace("[^0-9.]".toRegex(), "").toFloat()
        } catch (e: NumberFormatException) {
            0f
        }
    }

    private fun calculateBill(units: Float): Float {
        if (units <= 0) return 0f

        var remainingUnits = units
        var totalAmount = 0f

        for (slab in tariffSlabs) {
            if (remainingUnits <= 0) break

            val slabUnits = when {
                remainingUnits <= slab.fromUnit -> 0f
                else -> minOf(
                    (slab.toUnit - slab.fromUnit + 1).toFloat(),
                    slab.maxUnits.toFloat(),
                    remainingUnits
                )
            }

            if (slabUnits > 0) {
                totalAmount += slabUnits * slab.rate
                remainingUnits -= slabUnits
            }
        }

        return totalAmount
    }

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
        fun getFormattedBreakdown(): String {
            return buildString {
                appendLine("=== Bill Calculation Breakdown ===")
                appendLine("Total Bill Amount: ₹${"%.2f".format(totalBillAmount)}")
                appendLine("Total Units: ${"%.2f".format(totalUnits)} kWh")
                appendLine("Common Consumption: ${"%.2f".format(commonConsumption)} kWh")
                appendLine("Common Share (per member): ${"%.2f".format(commonSharePerMember)} kWh")
                appendLine()
                appendLine("--- Individual Shares ---")
                individualBills.forEachIndexed { index, bill ->
                    appendLine("Member ${index + 1}:")
                    appendLine("  Individual Reading: ${"%.2f".format(bill.individualReading)} kWh")
                    appendLine("  Amount To Pay: ₹${"%.2f".format(bill.amountToPay)}")
                }
            }
        }
    }
}