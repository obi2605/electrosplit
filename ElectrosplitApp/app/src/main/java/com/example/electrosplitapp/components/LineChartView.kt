package com.example.electrosplitapp.components

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun LineChartView(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                val entries = values.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }

                val dataSet = LineDataSet(entries, "Predicted Bill Share").apply {
                    color = Color.rgb(33, 150, 243)
                    valueTextColor = Color.DKGRAY
                    circleRadius = 6f
                    setCircleColor(Color.rgb(233, 30, 99))
                    lineWidth = 3f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    valueTextSize = 12f
                    setDrawValues(true)
                    setDrawFilled(true)
                    fillDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        colors = intArrayOf(
                            Color.argb(120, 33, 150, 243),
                            Color.TRANSPARENT
                        )
                    }
                }

                data = LineData(dataSet)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    textColor = Color.YELLOW
                    textSize = 12f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return labels.getOrNull(value.toInt()) ?: value.toInt().toString()
                        }
                    }
                }

                axisRight.isEnabled = false
                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = Color.YELLOW
                    textSize = 12f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "%.0f kWh".format(value)
                        }
                    }
                }

                setExtraOffsets(16f, 16f, 16f, 16f)
                description = Description().apply { text = "" }
                legend.isEnabled = false
                animateX(1000)
            }
        },
        modifier = modifier
    )
}
