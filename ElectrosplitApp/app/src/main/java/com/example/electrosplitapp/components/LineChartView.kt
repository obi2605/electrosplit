package com.example.electrosplitapp.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LineChartView(
    historyData: List<Pair<Date, Double>>,
    predictedPoint: Pair<Date, Double>,
    isIncrease: Boolean,
    modifier: Modifier = Modifier
) {
    key(historyData, predictedPoint, isIncrease) {   // 🟢 Trigger refresh on changes
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                    val allDates = historyData.map { it.first.time } + predictedPoint.first.time
                    val minTime = allDates.minOrNull() ?: 0L

                    val historyEntries = historyData.map {
                        Entry((it.first.time - minTime).toFloat(), it.second.toFloat())
                    }
                    val predictionEntry = Entry(
                        (predictedPoint.first.time - minTime).toFloat(),
                        predictedPoint.second.toFloat()
                    )

                    val historySet = LineDataSet(historyEntries, "History").apply {
                        color = Color.MAGENTA
                        setCircleColor(Color.MAGENTA)
                        lineWidth = 2f
                        circleRadius = 5f
                        setDrawValues(false)
                    }

                    val lastHistoryEntry = historyEntries.maxByOrNull { it.x }

                    val predictionSet = LineDataSet(
                        listOfNotNull(lastHistoryEntry, predictionEntry),
                        "Prediction"
                    ).apply {
                        color = if (isIncrease) Color.RED else Color.GREEN
                        setCircleColor(color)
                        lineWidth = 2f
                        circleRadius = 7f
                        setDrawValues(false)
                    }

                    data = LineData(historySet, predictionSet)

                    val calendarStart = Calendar.getInstance().apply { timeInMillis = minTime }
                    val calendarEnd = Calendar.getInstance().apply { time = predictedPoint.first }

                    val monthDiff = (calendarEnd.get(Calendar.YEAR) - calendarStart.get(Calendar.YEAR)) * 12 +
                            (calendarEnd.get(Calendar.MONTH) - calendarStart.get(Calendar.MONTH)) + 1

                    val labelCount = monthDiff.coerceAtLeast(2)  // Ensure at least 2 labels


                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        textColor = Color.WHITE
                        setAvoidFirstLastClipping(true)
                        setLabelCount(labelCount, true)   // Dynamically set label count based on months

                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val date = Date(minTime + value.toLong())
                                return monthFormat.format(date)
                            }
                        }
                    }

                    axisRight.isEnabled = false
                    axisLeft.textColor = Color.WHITE

                    description = Description().apply { text = "" }
                    legend.isEnabled = true
                    setTouchEnabled(true)
                    setPinchZoom(true)
                    animateX(1000)
                }
            },
            modifier = modifier
        )
    }
}

