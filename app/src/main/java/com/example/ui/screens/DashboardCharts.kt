package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.data.BinMatrixRow
import com.example.data.DailyUsagePoint
import com.example.data.ModelRealization
import com.example.data.MonthCatalog
import com.example.data.SprModel
import com.example.ui.theme.AlarmCrimson
import com.example.ui.theme.GasTeal
import com.example.ui.theme.IndustrialNavy
import com.example.ui.theme.OverTargetAmber
import com.example.ui.theme.SheetCellMonoStyle
import com.example.ui.theme.TargetSlate
import java.util.Locale
import kotlin.math.max

@Composable
fun ChartPanel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (headerAction != null) {
                    Spacer(Modifier.width(8.dp))
                    headerAction()
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun DailyTargetVsActualChart(
    dailyPoints: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
) {
    var selectedDay by remember(dailyPoints) {
        mutableStateOf(dailyPoints.lastOrNull { it.entryCount > 0 }?.dayOfMonth)
    }

    val maxVal = remember(dailyPoints) {
        dailyPoints.maxOfOrNull { max(it.targetM3, it.actualM3) }?.coerceAtLeast(10.0) ?: 100.0
    }
    val selectedPoint = dailyPoints.firstOrNull { it.dayOfMonth == selectedDay }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val primarySelection = MaterialTheme.colorScheme.primary

    ChartPanel(
        title = "Pemakaian gas harian: target vs aktual (m³)",
        subtitle = "Ketuk batang tanggal untuk melihat rincian m³",
        modifier = modifier.testTag("daily_usage_chart"),
    ) {
        if (selectedPoint != null && selectedPoint.entryCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tgl ${selectedPoint.dayOfMonth} (${selectedPoint.entryCount} input)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Tgt ${formatM3(selectedPoint.targetM3)}",
                            style = SheetCellMonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Akt ${formatM3(selectedPoint.actualM3)}",
                            style = SheetCellMonoStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedPoint.actualM3 <= selectedPoint.targetM3) GasTeal else AlarmCrimson,
                        )
                        Text(
                            text = "${formatPct(selectedPoint.realizationPercent)}%",
                            style = SheetCellMonoStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        val count = dailyPoints.size.coerceAtLeast(1)
        val scrollState = rememberScrollState()
        val barGroupWidth = 22.dp
        val chartTotalWidth = barGroupWidth * count

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartTotalWidth)
                    .height(140.dp)
                    .pointerInput(dailyPoints) {
                        detectTapGestures { offset ->
                            val groupPx = size.width / count.toFloat()
                            val index = (offset.x / groupPx).toInt().coerceIn(0, dailyPoints.lastIndex)
                            selectedDay = dailyPoints[index].dayOfMonth
                        }
                    },
            ) {
                val bottomPadding = 20f
                val topPadding = 10f
                val usableHeight = size.height - bottomPadding - topPadding
                val groupWidthPx = size.width / count.toFloat()

                // Horizontal grid lines
                for (step in 0..3) {
                    val y = topPadding + usableHeight * (step / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                dailyPoints.forEachIndexed { idx, pt ->
                    val groupStart = idx * groupWidthPx
                    val isSelected = pt.dayOfMonth == selectedDay

                    if (isSelected) {
                        drawRoundRect(
                            color = primarySelection.copy(alpha = 0.08f),
                            topLeft = Offset(groupStart + 1f, 2f),
                            size = Size(groupWidthPx - 2f, size.height - 4f),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }

                    val barW = (groupWidthPx * 0.36f).coerceAtLeast(4f)
                    val gap = (groupWidthPx * 0.06f).coerceAtLeast(1.5f)
                    val startX = groupStart + (groupWidthPx - (barW * 2 + gap)) / 2f
                    val baseLineY = size.height - bottomPadding

                    val targetH = ((pt.targetM3 / maxVal) * usableHeight).toFloat().coerceAtLeast(
                        if (pt.targetM3 > 0.0) 3f else 0f,
                    )
                    val actualH = ((pt.actualM3 / maxVal) * usableHeight).toFloat().coerceAtLeast(
                        if (pt.actualM3 > 0.0) 3f else 0f,
                    )

                    if (targetH > 0f) {
                        drawRoundRect(
                            color = TargetSlate,
                            topLeft = Offset(startX, baseLineY - targetH),
                            size = Size(barW, targetH),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                    if (actualH > 0f) {
                        val actualColor = if (pt.actualM3 <= pt.targetM3 + 0.001) GasTeal else OverTargetAmber
                        drawRoundRect(
                            color = actualColor,
                            topLeft = Offset(startX + barW + gap, baseLineY - actualH),
                            size = Size(barW, actualH),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                }
            }

            // Day labels row aligned with bar groups
            Row(modifier = Modifier.width(chartTotalWidth)) {
                dailyPoints.forEach { pt ->
                    val isSelected = pt.dayOfMonth == selectedDay
                    Box(
                        modifier = Modifier
                            .width(barGroupWidth)
                            .clickable { selectedDay = pt.dayOfMonth },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = pt.dayOfMonth.toString(),
                            style = SheetCellMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(color = TargetSlate, label = "Target")
            Spacer(Modifier.width(14.dp))
            LegendDot(color = GasTeal, label = "Aktual (Hemat)")
            Spacer(Modifier.width(14.dp))
            LegendDot(color = OverTargetAmber, label = "Over Target")
        }
    }
}

@Composable
fun DailyAlarmLineChart(
    dailyPoints: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
) {
    val activePoints = remember(dailyPoints) { dailyPoints.filter { it.entryCount > 0 } }
    var selectedDay by remember(activePoints) { mutableStateOf(activePoints.lastOrNull()?.dayOfMonth) }
    val selected = activePoints.firstOrNull { it.dayOfMonth == selectedDay }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    ChartPanel(
        title = "Alarm % harian",
        subtitle = if (selected != null) {
            "Tgl ${selected.dayOfMonth}: ${formatPct(selected.alarmPercent)}% rata-rata"
        } else {
            "Tren persentase alarm harian"
        },
        modifier = modifier.testTag("daily_alarm_chart"),
    ) {
        if (activePoints.isEmpty()) {
            Text(
                text = "Belum ada data alarm pada periode ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ChartPanel
        }

        val maxAlarm = remember(activePoints) {
            (activePoints.maxOfOrNull { it.alarmPercent } ?: 5.0).coerceAtLeast(5.0) * 1.15
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .pointerInput(activePoints) {
                    detectTapGestures { offset ->
                        if (activePoints.size == 1) {
                            selectedDay = activePoints.first().dayOfMonth
                        } else {
                            val stepX = (size.width - 28f) / (activePoints.size - 1).coerceAtLeast(1)
                            val idx = ((offset.x - 14f) / stepX).toInt().coerceIn(0, activePoints.lastIndex)
                            selectedDay = activePoints[idx].dayOfMonth
                        }
                    }
                },
        ) {
            val padX = 14f
            val padTop = 10f
            val padBottom = 14f
            val usableW = (size.width - padX * 2).coerceAtLeast(1f)
            val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)
            val baseY = size.height - padBottom

            for (step in 0..2) {
                val y = padTop + usableH * (step / 2f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            val coords = activePoints.mapIndexed { idx, pt ->
                val x = if (activePoints.size == 1) {
                    size.width / 2f
                } else {
                    padX + usableW * (idx.toFloat() / (activePoints.size - 1).toFloat())
                }
                val y = baseY - ((pt.alarmPercent / maxAlarm).toFloat() * usableH)
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(coords.first().x, baseY)
                    coords.forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, baseY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(AlarmCrimson.copy(alpha = 0.22f), Color.Transparent),
                        startY = padTop,
                        endY = baseY,
                    ),
                )

                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        lineTo(coords[i].x, coords[i].y)
                    }
                }
                drawPath(
                    path = strokePath,
                    color = AlarmCrimson,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            coords.forEachIndexed { idx, point ->
                val isSelected = activePoints[idx].dayOfMonth == selectedDay
                drawCircle(
                    color = if (isSelected) AlarmCrimson else Color.White,
                    radius = if (isSelected) 7f else 4.5f,
                    center = point,
                )
                drawCircle(
                    color = AlarmCrimson,
                    radius = if (isSelected) 7f else 4.5f,
                    center = point,
                    style = Stroke(width = 2.5f),
                )
            }
        }
    }
}

@Composable
fun ModelRealizationCard(
    models: List<ModelRealization>,
    modifier: Modifier = Modifier,
) {
    ChartPanel(
        title = "Realisasi vs target per model",
        subtitle = "Perbandingan efisiensi SPR Pennar & SPR Peanut",
        modifier = modifier.testTag("model_realization_chart"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            models.forEach { item ->
                val ratio = (item.realizationPercent / 100.0).coerceIn(0.0, 1.25)
                val barColor = if (item.realizationPercent <= 100.0) IndustrialNavy else AlarmCrimson
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.model.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${formatPct(item.realizationPercent)}%  •  ${item.unitsWithinTarget}/${item.model.binCount} bin ≤ target",
                            style = SheetCellMonoStyle,
                            color = if (item.realizationPercent <= 100.0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                AlarmCrimson
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((ratio / 1.15).toFloat().coerceIn(0.02f, 1f))
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Aktual ${formatM3(item.actualM3)} m³",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Target ${formatM3(item.targetM3)} m³",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BinDailyMatrixCard(
    year: Int,
    month: Int,
    matrixRows: List<BinMatrixRow>,
    maxCellM3: Double,
    onQuickInputCell: (dateIso: String, model: SprModel, binNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysInMonth = MonthCatalog.daysInMonth(year, month)
    var showAllBins by remember { mutableStateOf(false) }
    var selectedCell by remember(year, month, matrixRows) { mutableStateOf<Triple<BinMatrixRow, Int, Double>?>(null) }

    val visibleRows = remember(matrixRows, showAllBins) {
        if (showAllBins) matrixRows else matrixRows.take(15)
    }

    ChartPanel(
        title = "Matriks harian semua bin (Sheet Matriks_Harian)",
        subtitle = "Makin hijau = makin banyak gas. Ketuk sel untuk input/edit bin.",
        modifier = modifier.testTag("bin_matrix_card"),
        headerAction = if (matrixRows.size > 15) {
            {
                Text(
                    text = if (showAllBins) "Ringkas" else "Lihat ${matrixRows.size} bin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showAllBins = !showAllBins }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            null
        },
    ) {
        if (selectedCell != null) {
            val (row, day, actual) = selectedCell!!
            val target = row.dailyTargets[day] ?: row.model.defaultTargetM3
            val dateIso = "%04d-%02d-%02d".format(year, month, day)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${row.binCode}  •  $dateIso",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (actual > 0.0) {
                                "Aktual ${formatM3(actual)} m³ / Target ${formatM3(target)} m³"
                            } else {
                                "Belum ada data input pada tanggal ini"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onQuickInputCell(dateIso, row.model, row.binNumber) },
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.testTag("matrix_quick_input_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Input sel bin",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Input", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        val hScroll = rememberScrollState()
        val cellSize = 18.dp
        val binColWidth = 74.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll),
        ) {
            // Day header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Bin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(binColWidth),
                )
                for (d in 1..daysInMonth) {
                    Box(
                        modifier = Modifier.width(cellSize + 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = d.toString().padStart(2, '0'),
                            style = SheetCellMonoStyle.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            visibleRows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp),
                ) {
                    Text(
                        text = row.binCode,
                        style = SheetCellMonoStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(binColWidth),
                    )
                    for (d in 1..daysInMonth) {
                        val valM3 = row.dailyActuals[d] ?: 0.0
                        val tgtM3 = row.dailyTargets[d] ?: 0.0
                        val intensity = if (valM3 <= 0.0) {
                            0.08f
                        } else {
                            (0.22f + (valM3 / maxCellM3).toFloat() * 0.78f).coerceIn(0.22f, 1f)
                        }
                        val isOver = valM3 > 0.0 && tgtM3 > 0.0 && valM3 > tgtM3 + 0.001
                        val cellColor = when {
                            valM3 <= 0.0 -> GasTeal.copy(alpha = 0.08f)
                            isOver -> AlarmCrimson.copy(alpha = intensity.coerceAtLeast(0.45f))
                            else -> GasTeal.copy(alpha = intensity)
                        }
                        val isSelected = selectedCell?.first?.binCode == row.binCode && selectedCell?.second == d
                        Box(
                            modifier = Modifier
                                .padding(1.dp)
                                .size(cellSize)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cellColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { selectedCell = Triple(row, d, valM3) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Baris = bin (Pennar 1–39, Peanut 1–12). Kolom = tanggal 01–$daysInMonth. Diekspor otomatis ke sheet Matriks_Harian.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatM3(value: Double): String {
    return if (value >= 1000.0) {
        String.format(Locale.US, "%,.0f", value)
    } else {
        String.format(Locale.US, "%,.1f", value)
    }
}

fun formatPct(value: Double): String = String.format(Locale.US, "%.1f", value)
