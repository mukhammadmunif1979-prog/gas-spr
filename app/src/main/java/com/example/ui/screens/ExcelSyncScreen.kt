package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ExcelWorkbookManager
import com.example.ui.GasUiState
import com.example.ui.theme.GasTeal
import com.example.ui.theme.GasTealContainer
import com.example.ui.theme.IndustrialNavy
import com.example.ui.theme.OnGasTealContainer
import java.util.Locale

@Composable
fun ExcelSyncScreen(
    state: GasUiState,
    onCreateExcelClick: () -> Unit,
    onImportExcelClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onShareExcelClick: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onUnlinkExcelClick: () -> Unit,
    onSaveDefaults: (Double, Double, String) -> Unit,
    onLoadSampleClick: () -> Unit,
    onClearAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val isLinked = prefs.linkedFileUri.isNotBlank()

    var pennarTargetText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", prefs.defaultPennarTargetM3))
    }
    var peanutTargetText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", prefs.defaultPeanutTargetM3))
    }
    var operatorText by remember { mutableStateOf(prefs.lastOperatorName) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(prefs.defaultPennarTargetM3, prefs.defaultPeanutTargetM3, prefs.lastOperatorName) {
        pennarTargetText = String.format(Locale.US, "%.1f", prefs.defaultPennarTargetM3)
        peanutTargetText = String.format(Locale.US, "%.1f", prefs.defaultPeanutTargetM3)
        if (operatorText.isBlank()) operatorText = prefs.lastOperatorName
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Kosongkan Semua Data?") },
            text = {
                Text(
                    "Seluruh ${state.allEntries.size} baris data gas akan dihapus. Pastikan Anda sudah menyimpan cadangan .xlsx bila diperlukan.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllClick()
                        showClearConfirm = false
                    },
                ) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Batal") }
            },
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "Sinkronisasi Sheet Excel (.xlsx)",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Hubungkan file Excel asli di penyimpanan HP / Google Drive untuk tulis & baca otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Workbook Status Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isLinked) GasTeal else IndustrialNavy),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isLinked) Icons.Default.CloudDone else Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLinked) prefs.linkedFileName else "Belum Ada File Excel Terhubung",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = if (isLinked) {
                                        "Sinkron terakhir: ${ExcelWorkbookManager.formatTimestamp(prefs.lastSyncedMillis)}"
                                    } else {
                                        "Buat file .xlsx baru atau buka file .xlsx yang sudah ada"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.isSyncingExcel) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                )
                            }
                        }

                        // 3 Sheet Badges inside generated .xlsx
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("Input_Data", "Ringkasan_Dashboard", "Matriks_Harian").forEach { sheet ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GasTealContainer,
                                ) {
                                    Text(
                                        text = sheet,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = OnGasTealContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // Primary File Actions
                        Button(
                            onClick = onCreateExcelClick,
                            enabled = !state.isSyncingExcel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_excel_button"),
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buat & Hubungkan File .xlsx Baru")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onImportExcelClick,
                                enabled = !state.isSyncingExcel,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("import_excel_button"),
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Buka / Impor .xlsx")
                            }
                            FilledTonalButton(
                                onClick = onShareExcelClick,
                                enabled = !state.isSyncingExcel,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_excel_button"),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Bagikan .xlsx")
                            }
                        }

                        if (isLinked) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onSyncNowClick,
                                    enabled = !state.isSyncingExcel,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("sync_now_excel_button"),
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tulis Ulang .xlsx")
                                }
                                OutlinedButton(
                                    onClick = onUnlinkExcelClick,
                                    modifier = Modifier.height(46.dp),
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = "Putuskan file Excel", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Auto-Sync Toggle
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Tulis ke File .xlsx",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Otomatis perbarui file Excel setiap kali simpan atau ubah data input.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Switch(
                                    checked = prefs.autoSyncEnabled,
                                    onCheckedChange = onAutoSyncChange,
                                    modifier = Modifier.testTag("auto_sync_switch"),
                                )
                            }
                        }
                    }
                }
            }

            // Default Target & Operator Settings Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Pengaturan Target Default Harian",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = pennarTargetText,
                                onValueChange = { pennarTargetText = it },
                                label = { Text("SPR Pennar (m³)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = peanutTargetText,
                                onValueChange = { peanutTargetText = it },
                                label = { Text("SPR Peanut (m³)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = operatorText,
                            onValueChange = { operatorText = it },
                            label = { Text("Nama Operator Default") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilledTonalButton(
                            onClick = {
                                val p1 = pennarTargetText.replace(",", ".").toDoubleOrNull() ?: 13.5
                                val p2 = peanutTargetText.replace(",", ".").toDoubleOrNull() ?: 12.8
                                onSaveDefaults(p1, p2, operatorText)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_defaults_button"),
                        ) {
                            Text("Simpan Pengaturan Target")
                        }
                    }
                }
            }

            // Dataset Utilities Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Utilitas Data (${state.allEntries.size} total baris tersimpan)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedButton(
                            onClick = onLoadSampleClick,
                            enabled = !state.isSyncingExcel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("excel_load_sample_btn"),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Muat Data Contoh Nov 2026 (51 Bin × 30 Hari)")
                        }
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            enabled = state.allEntries.isNotEmpty() && !state.isSyncingExcel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("excel_clear_all_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Kosongkan Semua Data", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
