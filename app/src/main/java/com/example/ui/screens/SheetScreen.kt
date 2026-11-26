package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GasEntryEntity
import com.example.data.MonthCatalog
import com.example.ui.GasUiState
import com.example.ui.SheetSortMode
import com.example.ui.theme.AlarmCrimson
import com.example.ui.theme.AlarmCrimsonSoft
import com.example.ui.theme.GasTeal
import com.example.ui.theme.GasTealContainer
import com.example.ui.theme.OnGasTealContainer
import com.example.ui.theme.SheetCellMonoStyle

@Composable
fun SheetScreen(
    state: GasUiState,
    onSearchChange: (String) -> Unit,
    onSortChange: (SheetSortMode) -> Unit,
    onEditEntry: (GasEntryEntity) -> Unit,
    onDeleteEntry: (GasEntryEntity) -> Unit,
    onAddClick: () -> Unit,
    onShareExcelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<GasEntryEntity?>(null) }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus Baris Sheet?") },
            text = {
                Text(
                    "Baris ${target.binCode} tanggal ${target.dateIso} (${target.shiftName}) akan dihapus dari daftar & file Excel.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(target)
                        pendingDelete = null
                    },
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sheet Input_Data",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${MonthCatalog.fullName(state.filter.selectedMonth)} ${state.filter.selectedYear} • ${state.sheetEntries.size} baris tampil",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onShareExcelClick,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("sheet_export_xlsx_btn"),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Ekspor Excel", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(".xlsx")
            }
        }

        // Search box
        OutlinedTextField(
            value = state.filter.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari kode bin, tanggal, operator...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_search_input"),
        )

        // Sort filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetSortMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.filter.sortMode == mode,
                    onClick = { onSortChange(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        if (state.sheetEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.TableRows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "Belum ada baris data di sheet ini",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = onAddClick,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sheet_add_row_btn"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Baris Input")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.sheetEntries,
                    key = { it.id },
                ) { entry ->
                    SheetRowCard(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetRowCard(
    entry: GasEntryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val within = entry.isWithinTarget
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheet_row_${entry.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = entry.binCode,
                            style = SheetCellMonoStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "${entry.dateIso} • ${entry.shiftName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (within) GasTealContainer else AlarmCrimsonSoft,
                ) {
                    Text(
                        text = if (within) "≤ Target" else "Over",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (within) OnGasTealContainer else AlarmCrimson,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Tabular numeric strip mimicking spreadsheet columns
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SheetMetricColumn(label = "Target", value = "${formatM3(entry.targetGasM3)} m³")
                    SheetMetricColumn(
                        label = "Aktual",
                        value = "${formatM3(entry.actualGasM3)} m³",
                        highlight = if (within) GasTeal else AlarmCrimson,
                    )
                    SheetMetricColumn(label = "Realisasi", value = "${formatPct(entry.realizationPercent)}%")
                    SheetMetricColumn(
                        label = "Alarm",
                        value = "${formatPct(entry.alarmPercent)}%",
                        highlight = if (entry.alarmPercent > 4.0) AlarmCrimson else null,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (entry.operatorName.isNotBlank()) {
                        Text(
                            text = "Operator: ${entry.operatorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (entry.notes.isNotBlank()) {
                        Text(
                            text = entry.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit baris ${entry.binCode}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus baris ${entry.binCode}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetMetricColumn(
    label: String,
    value: String,
    highlight: androidx.compose.ui.graphics.Color? = null,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = SheetCellMonoStyle.copy(fontWeight = FontWeight.Bold),
            color = highlight ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}
