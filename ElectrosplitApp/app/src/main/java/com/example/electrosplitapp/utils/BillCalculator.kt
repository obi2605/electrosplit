package com.example.electrosplitapp.utils

object BillCalculator {
    // Tariff slabs for total consumption ≤ 500 units
    private val tariffSlabsUnder500 = listOf(
        TariffSlab(1, 100, 0f),
        TariffSlab(101, 200, 2.35f),
        TariffSlab(201, 400, 4.7f),
        TariffSlab(401, 500, 6.3f)
    )

    // Tariff slabs for total consumption > 500 units
    private val tariffSlabsOver500 = listOf(
        TariffSlab(1, 100, 0f),
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
        fun getFormattedBreakdown(target: MemberBill? = null): String {
            val bill = target ?: individualBills.firstOrNull()
            if (bill == null) return "No breakdown available"

            val allReadings = individualBills.map { it.individualReading }

            return buildString {
                appendLine("=== Bill Calculation Breakdown ===")
                appendLine("Total Bill Amount: ₹${"%.2f".format(totalBillAmount)}")
                appendLine("Total Units: ${"%.2f".format(totalUnits)} kWh")

                appendLine("\nYour Reading: ${"%.2f".format(bill.individualReading)} kWh")
                appendLine(getSlabBreakdown(bill.individualReading, totalUnits, allReadings))

                if (commonAmount > 0) {
                    appendLine("\nCommon Amount: ₹${"%.2f".format(commonAmount)}")
                    appendLine("Your Common Share: ₹${"%.2f".format(commonSharePerMember)}")
                }

                appendLine("\nTotal Amount: ₹${"%.2f".format(bill.amountToPay)}")
            }
        }
    }

    fun calculateSplit(
        totalBillAmount: Float,
        totalUnits: Float,
        individualReadings: List<Float>,
        groupSize: Int
    ): SplitResult {
        require(groupSize > 0) { "Group size must be positive" }

        val slabs = if (totalUnits <= 500) tariffSlabsUnder500 else tariffSlabsOver500

        val memberRemaining = individualReadings.toMutableList()
        val memberSlabUnits = Array(individualReadings.size) { FloatArray(slabs.size) }

        var remainingUnits = totalUnits

        for ((slabIndex, slab) in slabs.withIndex()) {
            if (remainingUnits <= 0f) break

            val slabWidth = (slab.toUnit - slab.fromUnit + 1).toFloat()
            val slabUnits = minOf(remainingUnits, slabWidth)
            remainingUnits -= slabUnits

            val baseShare = slabUnits / groupSize
            var unassigned = slabUnits

            // First pass: assign baseShare or less (capped by remaining reading)
            for (i in individualReadings.indices) {
                val toAssign = minOf(memberRemaining[i], baseShare)
                memberSlabUnits[i][slabIndex] += toAssign
                memberRemaining[i] -= toAssign
                unassigned -= toAssign
            }

            // Second pass: assign leftover units to those who can still take
            while (unassigned > 0.01f) {
                var assignedAny = false
                for (i in individualReadings.indices) {
                    if (memberRemaining[i] > 0f) {
                        val give = minOf(1f, memberRemaining[i], unassigned)
                        memberSlabUnits[i][slabIndex] += give
                        memberRemaining[i] -= give
                        unassigned -= give
                        assignedAny = true
                        if (unassigned <= 0.01f) break
                    }
                }
                if (!assignedAny) break
            }
        }

        val individualAmounts = memberSlabUnits.mapIndexed { _, units ->
            var sum = 0f
            for (j in units.indices) {
                sum += units[j] * slabs[j].rate
            }
            sum
        }

        val sumIndividual = individualAmounts.sum()
        val commonAmount = (totalBillAmount - sumIndividual).coerceAtLeast(0f)
        val commonPerMember = commonAmount / groupSize

        val finalBills = individualReadings.zip(individualAmounts) { reading, cost ->
            MemberBill(reading, cost + commonPerMember)
        }

        return SplitResult(
            individualBills = finalBills,
            commonAmount = commonAmount,
            commonSharePerMember = commonPerMember,
            totalBillAmount = totalBillAmount,
            totalUnits = totalUnits
        )
    }

    fun getSlabBreakdown(units: Float, totalUnits: Float, allReadings: List<Float>): String {
        if (units <= 0) return "No consumption"

        val slabs = if (totalUnits <= 500) tariffSlabsUnder500 else tariffSlabsOver500
        val groupSize = allReadings.size
        val userIndex = allReadings.indexOfFirst { it == units }
        val allRemaining = allReadings.toMutableList()
        val memberSlabUnits = Array(allReadings.size) { FloatArray(slabs.size) }

        var remainingUnits = totalUnits

        for ((slabIndex, slab) in slabs.withIndex()) {
            if (remainingUnits <= 0f) break

            val slabWidth = (slab.toUnit - slab.fromUnit + 1).toFloat()
            val slabUnits = minOf(remainingUnits, slabWidth)
            remainingUnits -= slabUnits

            val baseShare = slabUnits / groupSize
            var unassigned = slabUnits

            for (i in allReadings.indices) {
                val assign = minOf(allRemaining[i], baseShare)
                memberSlabUnits[i][slabIndex] += assign
                allRemaining[i] -= assign
                unassigned -= assign
            }

            while (unassigned > 0.01f) {
                var assignedAny = false
                for (i in allReadings.indices) {
                    if (allRemaining[i] > 0f) {
                        val give = minOf(1f, allRemaining[i], unassigned)
                        memberSlabUnits[i][slabIndex] += give
                        allRemaining[i] -= give
                        unassigned -= give
                        assignedAny = true
                        if (unassigned <= 0.01f) break
                    }
                }
                if (!assignedAny) break
            }
        }

        val breakdown = StringBuilder()
        var subtotal = 0f

        for (j in slabs.indices) {
            val kwh = memberSlabUnits[userIndex][j]
            if (kwh > 0f) {
                val rate = slabs[j].rate
                val cost = kwh * rate
                subtotal += cost
                breakdown.appendLine(
                    "${"%.2f".format(kwh)}kWh × ₹${rate}/kWh = ₹${"%.2f".format(cost)} (Slab ${slabs[j].fromUnit}-${slabs[j].toUnit})"
                )
            }
        }

        breakdown.appendLine("Subtotal: ₹${"%.2f".format(subtotal)}")
        return breakdown.toString()
    }
}
