package com.example.electrosplitapp.utils


object BillCalculator {
    // Tariff slabs for total consumption ≤ 500 units
    private val tariffSlabsUnder500 = listOf(
        TariffSlab(1, 100, 0f),    // First 100 units free
        TariffSlab(101, 200, 2.35f),
        TariffSlab(201, 400, 4.7f),
        TariffSlab(401, 500, 6.3f)
    )

    // Tariff slabs for total consumption > 500 units
    private val tariffSlabsOver500 = listOf(
        TariffSlab(1, 100, 0f),    // First 100 units free
        TariffSlab(101, 400, 4.7f),
        TariffSlab(401, 500, 6.3f),
        TariffSlab(501, 600, 8.4f),
        TariffSlab(601, 800, 9.45f),
        TariffSlab(801, 1000, 10.5f),
        TariffSlab(1001, Int.MAX_VALUE, 11.55f)
    )

    data class TariffSlab(
        val fromUnit: Int,
        val toUnit: Int,
        val rate: Float
    )

    fun parseReading(reading: String): Float {
        return try {
            reading.replace("[^0-9.]".toRegex(), "").takeIf { it.isNotEmpty() }?.toFloat() ?: 0f
        } catch (e: NumberFormatException) {
            0f
        }
    }

    private fun calculateIndividualAmount(units: Float, totalUnits: Float): Float {
        if (units <= 0) return 0f

        val slabs = if (totalUnits <= 500) tariffSlabsUnder500 else tariffSlabsOver500
        var remainingUnits = units
        var totalAmount = 0f

        for (slab in slabs) {
            if (remainingUnits <= 0) break

            val slabWidth = (slab.toUnit - slab.fromUnit + 1).toFloat()
            val unitsInSlab = minOf(remainingUnits, slabWidth)

            if (unitsInSlab > 0) {
                totalAmount += unitsInSlab * slab.rate
                remainingUnits -= unitsInSlab
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

        // 1. Calculate all individual amounts using slab rates
        val individualAmounts = individualReadings.map { reading ->
            calculateIndividualAmount(reading, totalUnits)
        }

        // 2. Calculate common amount
        val sumIndividualAmounts = individualAmounts.sum()
        val commonAmount = (totalBillAmount - sumIndividualAmounts).coerceAtLeast(0f)
        val commonShare = commonAmount / groupSize

        // 3. Combine for final amounts
        val finalBills = individualReadings.zip(individualAmounts) { reading, amount ->
            MemberBill(reading, amount + commonShare)
        }

        return SplitResult(
            individualBills = finalBills,
            commonAmount = commonAmount,
            commonSharePerMember = commonShare,
            totalBillAmount = totalBillAmount,
            totalUnits = totalUnits
        )
    }

    fun getSlabBreakdown(units: Float, totalUnits: Float): String {
        if (units <= 0) return "No consumption"

        val slabs = if (totalUnits <= 500) tariffSlabsUnder500 else tariffSlabsOver500
        val breakdown = StringBuilder()
        var remaining = units
        var total = 0f

        slabs.forEach { slab ->
            if (remaining <= 0) return@forEach

            val slabWidth = (slab.toUnit - slab.fromUnit + 1).toFloat()
            val unitsInSlab = minOf(remaining, slabWidth)

            if (unitsInSlab > 0) {
                val slabAmount = unitsInSlab * slab.rate
                breakdown.appendLine(
                    "${"%.2f".format(unitsInSlab)}kWh × ₹${slab.rate}/kWh = ₹${"%.2f".format(slabAmount)} " +
                            "(Slab ${slab.fromUnit}-${slab.toUnit})"
                )
                total += slabAmount
                remaining -= unitsInSlab
            }
        }

        breakdown.appendLine("Subtotal: ₹${"%.2f".format(total)}")
        return breakdown.toString()
    }

    data class MemberBill(
        val individualReading: Float,
        val amountToPay: Float
    )

    data class SplitResult(
        val individualBills: List<MemberBill>,
        val commonAmount: Float,
        val commonSharePerMember: Float,
        val totalBillAmount: Float,
        val totalUnits: Float
    ) {
        fun getFormattedBreakdown(): String {
            return buildString {
                appendLine("=== Bill Calculation Breakdown ===")
                appendLine("Total Bill Amount: ₹${"%.2f".format(totalBillAmount)}")
                appendLine("Total Units: ${"%.2f".format(totalUnits)} kWh")

                if (individualBills.size == 1) {
                    val bill = individualBills.first()

                    // Individual calculation
                    appendLine("\nYour Reading: ${"%.2f".format(bill.individualReading)} kWh")
                    appendLine(getSlabBreakdown(bill.individualReading, totalUnits))

                    // Common calculation
                    if (commonAmount > 0) {
                        appendLine("\nCommon Amount: ₹${"%.2f".format(commonAmount)}")
                        appendLine("Your Common Share: ₹${"%.2f".format(commonSharePerMember)}")
                    }

                    // Final total
                    appendLine("\nTotal Amount: ₹${"%.2f".format(bill.amountToPay)}")
                }
            }
        }
    }
}