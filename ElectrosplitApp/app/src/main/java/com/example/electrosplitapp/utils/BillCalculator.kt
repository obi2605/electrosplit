package com.example.electrosplitapp.utils

import kotlin.math.min

object BillCalculator {
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

    fun calculateBill(units: Float): Float {
        var remainingUnits = units
        var totalAmount = 0f

        for (slab in tariffSlabs) {
            if (remainingUnits <= 0) break

            val slabRange = slab.toUnit - slab.fromUnit + 1
            val slabUnits = min(
                min(slabRange.toFloat(), slab.maxUnits.toFloat()),
                remainingUnits
            )

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
    ): List<Float> {
        val totalIndividualConsumption = individualReadings.sum()
        val commonConsumption = totalUnits - totalIndividualConsumption

        if (commonConsumption <= 0 || groupSize == 0) {
            return individualReadings.map { calculateBill(it) }
        }

        val commonShare = commonConsumption / groupSize
        return individualReadings.map { reading ->
            calculateBill(reading + commonShare)
        }
    }
}