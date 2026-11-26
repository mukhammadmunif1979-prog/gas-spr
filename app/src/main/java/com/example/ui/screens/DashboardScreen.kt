package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MonthCatalog
import com.example.data.SprModel
import com.example.data.WorkShift
import com.example.ui.GasUiState
import com.example.ui.theme.AlarmCrimson
import com.example.ui.theme.ExcelPillAmber
import com.example.ui.theme.ExcelPillAmberBorder
import com.example.ui.theme.ExcelPillInk
import com.example.ui.theme.GasTeal
import com.example.ui.theme.IndustrialNavy
import com.example.ui.theme.IndustrialNavyDark
import com.example.ui.theme.MetricNumberStyle

@Composable
fun DashboardScreen(
    state: GasUiState,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onModelChange: (SprModel?) -> Unit,
    onShiftChange: (WorkShift?) -> Unit,
    onNavigateToInput: () -> Unit,
    onNavigateToExcel: () -> Unit,
    onImportExcelClick: () -> Unit,
    onShareExcelClick: () -> Unit,
    onLoadSampleClick: () -> Unit,
    onQuickInputCell: (dateIso: String, model: SprModel, binNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = state.metrics
    val hasData = metrics.filteredEntries.isNotEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Signature Navy Industrial Banner matching the Gas Usage Tracker header
            item {
                DashboardHeaderBanner(
                    linkedFileName = state.preferences.linkedFileName,
                    totalRows = state.allEntries.size,
                    onExcelBadgeClick = onNavigateToExcel,
                    onShareClick = onShareExcelClick,
                )
            }

            // Excel-inspired Dropdown Filter Strip (Tahun | Bulan | Model | Shift)
            item {
                FilterDropdownStrip(
                    year = state.filter.selectedYear,
                    month = state.filter.selectedMonth,
                    model = state.filter.selectedModel,
                    shift = state.filter.selectedShift,
                    availableYears = state.availableYears,
                    onYearChange = onYearChange,
                    onMonthChange = onMonthChange,
                    onModelChange = onModelChange,
                    onShiftChange = onShiftChange,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }

            // 5 KPI Cards (TARGET GAS, AKTUAL GAS, REALISASI vs TARGET, ALARM %, UNIT <= TARGET)
            item {
                KpiCardsGrid(
                    targetM3 = metrics.totalTargetM3,
                    actualM3 = metrics.totalActualM3,
                    realizationPct = metrics.realizationPercent,
                    alarmPct = metrics.avgAlarmPercent,
                    unitsWithin = metrics.unitsWithinTarget,
                    totalUnits = metrics.totalUnitsInScope,
                    isWide = isWide,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }

            if (!hasData) {
                item {
                    DashboardEmptyPrompt(
                        periodLabel = "${MonthCatalog.fullName(state.filter.selectedMonth)} ${state.filter.selectedYear}",
                        onNavigateToInput = onNavigateToInput,
                        onImportExcelClick = onImportExcelClick,
                        onLoadSampleClick = onLoadSampleClick,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            } else {
                // Chart 1: Daily Gas Usage Target vs Actual
                item {
                    DailyTargetVsActualChart(
                        dailyPoints = metrics.dailyPoints,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }

                // Chart 2 & 3: Alarm % Harian + Realisasi per Model (side-by-side on wide, stacked on phone)
                item {
                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DailyAlarmLineChart(
                                dailyPoints = metrics.dailyPoints,
                                modifier = Modifier.weight(1.25f),
                            )
                            ModelRealizationCard(
                                models = metrics.modelRealizations,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DailyAlarmLineChart(dailyPoints = metrics.dailyPoints)
                            ModelRealizationCard(models = metrics.modelRealizations)
                        }
                    }
                }

                // Chart 4: Matriks Harian Semua Bin
                item {
                    BinDailyMatrixCard(
                        year = state.filter.selectedYear,
                        month = state.filter.selectedMonth,
                        matrixRows = metrics.matrixRows,
                        maxCellM3 = metrics.maxDailyCellM3,
                        onQuickInputCell = onQuickInputCell,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeaderBanner(
    linkedFileName: String,
    totalRows: Int,
    onExcelBadgeClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(IndustrialNavyDark, IndustrialNavy),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GasTeal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Gas Usage Tracker",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gas Usage Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = "SPR Pennar & SPR Peanut • Pemakaian vs Target",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("dashboard_share_excel_btn"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Bagikan file Excel .xlsx",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Linked Excel file status strip
            Surface(
                onClick = onExcelBadgeClick,
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_excel_status_chip"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (linkedFileName.isNotBlank()) GasTeal else ExcelPillAmber,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (linkedFileName.isNotBlank()) {
                                Icons.Default.CloudDone
                            } else {
                                Icons.Default.TableChart
                            },
                            contentDescription = "Status Sheet Excel",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (linkedFileName.isNotBlank()) {
                                "Excel aktif: $linkedFileName"
                            } else {
                                "Belum terhubung ke file .xlsx — ketuk untuk hubungkan"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$totalRows baris",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcelPillAmber,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterDropdownStrip(
    year: Int,
    month: Int,
    model: SprModel?,
    shift: WorkShift?,
    availableYears: List<Int>,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onModelChange: (SprModel?) -> Unit,
    onShiftChange: (WorkShift?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExcelDropdownPill(
            label = "Tahun",
            value = year.toString(),
            options = availableYears.map { it.toString() to it },
            onSelect = onYearChange,
            tag = "filter_year",
        )
        ExcelDropdownPill(
            label = "Bulan",
            value = MonthCatalog.shortName(month),
            options = (1..12).map { MonthCatalog.shortName(it) to it },
            onSelect = onMonthChange,
            tag = "filter_month",
        )
        ExcelDropdownPill(
            label = "Model",
            value = model?.displayName ?: "Semua",
            options = listOf("Semua" to null) + SprModel.entries.map { it.displayName to it },
            onSelect = onModelChange,
            tag = "filter_model",
        )
        ExcelDropdownPill(
            label = "Shift",
            value = shift?.label ?: "Semua",
            options = listOf("Semua" to null) + WorkShift.entries.map { it.label to it },
            onSelect = onShiftChange,
            tag = "filter_shift",
        )
    }
}

@Composable
private fun <T> ExcelDropdownPill(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    tag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp)
                .testTag(tag),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ExcelPillAmber,
                border = BorderStroke(1.dp, ExcelPillAmberBorder),
            ) {
                Row(
                    modifier = Modifier.padding(start = 9.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        color = ExcelPillInk,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Pilih $label",
                        tint = ExcelPillInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelect(optionValue)
                    },
                )
            }
        }
    }
}

@Composable
private fun KpiCardsGrid(
    targetM3: Double,
    actualM3: Double,
    realizationPct: Double,
    alarmPct: Double,
    unitsWithin: Int,
    totalUnits: Int,
    isWide: Boolean,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        Triple("TARGET GAS (m³)", formatM3(targetM3), MaterialTheme.colorScheme.onSurface),
        Triple("AKTUAL GAS (m³)", formatM3(actualM3), MaterialTheme.colorScheme.onSurface),
        Triple(
            "REALISASI vs TARGET",
            "${formatPct(realizationPct)}%",
            if (realizationPct <= 100.0) GasTeal else AlarmCrimson,
        ),
        Triple(
            "ALARM %",
            "${formatPct(alarmPct)}%",
            if (alarmPct > 4.0) AlarmCrimson else MaterialTheme.colorScheme.onSurface,
        ),
        Triple("UNIT ≤ TARGET", "$unitsWithin / $totalUnits", IndustrialNavy),
    )

    if (isWide) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.forEach { (label, value, accent) ->
                KpiMetricCard(
                    label = label,
                    value = value,
                    valueColor = accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiMetricCard(
                    label = cards[0].first,
                    value = cards[0].second,
                    valueColor = cards[0].third,
                    modifier = Modifier.weight(1f),
                )
                KpiMetricCard(
                    label = cards[1].first,
                    value = cards[1].second,
                    valueColor = cards[1].third,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiMetricCard(
                    label = cards[2].first,
                    value = cards[2].second,
                    valueColor = cards[2].third,
                    modifier = Modifier.weight(1f),
                )
                KpiMetricCard(
                    label = cards[3].first,
                    value = cards[3].second,
                    valueColor = cards[3].third,
                    modifier = Modifier.weight(1f),
                )
                KpiMetricCard(
                    label = cards[4].first,
                    value = cards[4].second,
                    valueColor = cards[4].third,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GasTeal)
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    style = MetricNumberStyle.copy(fontSize = 17.sp),
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DashboardEmptyPrompt(
    periodLabel: String,
    onNavigateToInput: () -> Unit,
    onImportExcelClick: () -> Unit,
    onLoadSampleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = "Belum ada data Excel",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = "Belum ada data gas untuk $periodLabel",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Mulai catat pemakaian gas bin SPR Pennar (1–39) & SPR Peanut (1–12) langsung ke sheet Excel (.xlsx), atau impor file Excel yang sudah ada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onNavigateToInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_input_first_btn"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Input Data Gas Pertama")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onImportExcelClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("empty_import_excel_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Impor .xlsx", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onLoadSampleClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("empty_load_sample_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Contoh Nov '26", maxLines = 1)
                }
            }
        }
    }
}
