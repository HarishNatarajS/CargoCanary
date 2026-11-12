package com.example.cargocanary.presentation.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.*

@Composable
fun AnalyticsScreen(
    viewModel: CargoViewModel,
    onBack: (() -> Unit)? = null
) {
    val logs by viewModel.retrievedLogs.collectAsState()
    val shipment by viewModel.currentShipment.collectAsState()
    val limits = shipment.limits

    val temps = logs.map { it.temperature }
    val hums = logs.map { it.humidity }
    val shocks = logs.map { it.accelZ }

    val tempBreaches = temps.count { it < limits.minTemp || it > limits.maxTemp }
    val humBreaches = hums.count { it < limits.minHum || it > limits.maxHum }
    val shockBreaches = shocks.count { it > limits.maxShock }
    val totalBreaches = tempBreaches + humBreaches + shockBreaches

    CanaryScreen(
        title = "Journey report",
        subtitle = shipment.journeyId.ifBlank { "No active journey" },
        onBack = onBack
    ) {
        VerdictPanel(recordCount = logs.size, breaches = totalBreaches)

        ChartCard(
            title = "Temperature",
            unit = "°C",
            data = temps,
            breaches = tempBreaches,
            color = ChartTemp,
            minLimit = limits.minTemp,
            maxLimit = limits.maxTemp
        )
        ChartCard(
            title = "Humidity",
            unit = "%",
            data = hums,
            breaches = humBreaches,
            color = ChartHumidity,
            minLimit = limits.minHum,
            maxLimit = limits.maxHum
        )
        ChartCard(
            title = "Shock (Z axis)",
            unit = "g",
            data = shocks,
            breaches = shockBreaches,
            color = ChartShock,
            minLimit = 0f,
            maxLimit = limits.maxShock
        )

        PrimaryAction(
            text = "Generate PDF report",
            icon = Icons.Default.PictureAsPdf,
            enabled = logs.isNotEmpty(),
            onClick = { viewModel.generatePdfReport() }
        )
    }
}

@Composable
private fun VerdictPanel(recordCount: Int, breaches: Int) {
    val status = MaterialTheme.status
    val (container, accent, icon, title, detail) = when {
        recordCount == 0 -> Verdict(
            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Sensors,
            "No data yet", "Retrieve the transit logs to see charts and generate a report."
        )
        breaches == 0 -> Verdict(
            status.okContainer, status.ok, Icons.Default.CheckCircle,
            "Within limits", "All $recordCount records stayed inside the configured limits."
        )
        else -> Verdict(
            status.dangerContainer, status.danger, Icons.Default.Warning,
            "$breaches readings out of range", "Found across $recordCount records. Check the charts below."
        )
    }
    Panel(containerColor = container, borderColor = accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, tint = Color.White, background = accent)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Verdict(
    val container: Color,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val detail: String
)

@Composable
fun ChartCard(
    title: String,
    unit: String,
    data: List<Float>,
    breaches: Int,
    color: Color,
    minLimit: Float,
    maxLimit: Float
) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Limit ${fmt(minLimit)} to ${fmt(maxLimit)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (data.isNotEmpty()) {
                if (breaches == 0) StatusPill("In range", MaterialTheme.status.ok)
                else StatusPill("$breaches out", MaterialTheme.status.danger)
            }
        }
        Spacer(Modifier.height(16.dp))
        TimeSeriesLineChart(data = data, lineColor = color, minLimit = minLimit, maxLimit = maxLimit)
        if (data.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Min", "${fmt(data.minOrNull() ?: 0f)} $unit", Modifier.weight(1f))
                Stat("Average", "${fmt(data.average().toFloat())} $unit", Modifier.weight(1f))
                Stat("Max", "${fmt(data.maxOrNull() ?: 0f)} $unit", Modifier.weight(1f))
            }
        }
    }
}

private fun fmt(v: Float): String = "%.1f".format(v)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.tabular())
    }
}

/**
 * Line chart with the safe range shaded, dashed limit lines, a soft area fill,
 * red dots on out-of-range readings and a marker on the latest reading.
 */
@Composable
fun TimeSeriesLineChart(
    data: List<Float>,
    lineColor: Color,
    minLimit: Float,
    maxLimit: Float,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("No readings recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val lo0 = minOf(data.minOrNull() ?: 0f, minLimit)
    val hi0 = maxOf(data.maxOrNull() ?: 1f, maxLimit)
    val span = (hi0 - lo0).let { if (it == 0f) 1f else it }
    val lo = lo0 - span * 0.08f
    val hi = hi0 + span * 0.08f
    val range = hi - lo

    val ok = MaterialTheme.status.ok
    val danger = MaterialTheme.status.danger
    val surface = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisFmt: (Float) -> String = { if (range < 10f) "%.1f".format(it) else "%.0f".format(it) }

    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(30.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(axisFmt(hi), style = labelStyle, color = labelColor)
            Text(axisFmt(lo), style = labelStyle, color = labelColor)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width
            val h = size.height
            fun yFor(v: Float) = (1f - (v - lo) / range) * h
            val stepX = if (data.size > 1) w / (data.size - 1) else 0f
            fun xFor(i: Int) = if (data.size > 1) i * stepX else w / 2f

            // Safe range band and dashed limit lines
            drawRect(
                color = ok.copy(alpha = 0.09f),
                topLeft = Offset(0f, yFor(maxLimit)),
                size = Size(w, yFor(minLimit) - yFor(maxLimit))
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            listOf(minLimit, maxLimit).forEach { limit ->
                drawLine(ok.copy(alpha = 0.7f), Offset(0f, yFor(limit)), Offset(w, yFor(limit)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            }

            // Series line + area fill
            val line = Path()
            val area = Path()
            data.forEachIndexed { i, v ->
                val x = xFor(i)
                val y = yFor(v)
                if (i == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, h)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(xFor(data.size - 1), h)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Out-of-range readings (skipped on very dense series to keep the chart clean)
            if (data.size <= 150) {
                data.forEachIndexed { i, v ->
                    if (v < minLimit || v > maxLimit) drawCircle(danger, radius = 3.5.dp.toPx(), center = Offset(xFor(i), yFor(v)))
                }
            }

            // Latest reading
            val lastCenter = Offset(xFor(data.size - 1), yFor(data.last()))
            drawCircle(surface, radius = 6.dp.toPx(), center = lastCenter)
            drawCircle(lineColor, radius = 4.dp.toPx(), center = lastCenter)
        }
    }
}
