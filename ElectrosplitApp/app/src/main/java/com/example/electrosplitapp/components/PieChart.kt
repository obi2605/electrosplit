package com.example.electrosplitapp.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun PieChart(
    data: Map<String, Float>,
    modifier: Modifier = Modifier
) { key(data) {
    val colors = listOf(
        Color(0xFFEF5350),
        Color(0xFFAB47BC),
        Color(0xFF42A5F5),
        Color(0xFF26A69A),
        Color(0xFFFFA726),
        Color(0xFF66BB6A),
        Color(0xFFD4E157),
        Color(0xFF5C6BC0),
        Color(0xFFEC407A),
        Color(0xFF7E57C2)
    )

    val total = data.values.sum().takeIf { it > 0 } ?: return

    var startAngle = -90f

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Bill Share Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val diameter = size.minDimension
            val radius = diameter / 2f
            val rect =
                Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            val entries = data.entries.toList()

            entries.forEachIndexed { index, entry ->
                val angleSweep = (entry.value / total) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = angleSweep,
                    useCenter = true,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height)
                )
                startAngle += angleSweep
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.entries.forEachIndexed { index, entry ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(colors[index % colors.size])
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(entry.key, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "₹${"%.2f".format(entry.value)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
  }
}
