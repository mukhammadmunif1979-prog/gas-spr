package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.sp
import com.example.data.GasEntryEntity
import com.example.data.SprModel
import com.example.data.WorkShift
import com.example.ui.GasUiState
import com.example.ui.theme.AlarmCrimson
import com.example.ui.theme.AlarmCrimsonSoft
import com.example.ui.theme.GasTeal
import com.example.ui.theme.GasTealContainer
import com.example.ui.theme.MetricNumberStyle
import com.example.ui.theme.OnGasTealContainer
import com.example.ui.theme.SheetCellMonoStyle
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private enum class InputMode(val label: String) {
    SINGLE("Input Satuan"),
    BATCH("Input Batch Shift"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InputScreen(
    state: GasUiState,
    onSaveSingle: (GasEntryEntity) -> Unit,
    onSaveBatch: (List<GasEntryEntity>) -> Unit,
    onCancelEdit: () -> Unit,
    onOpenExcelTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.editingEntry
    val prefs = state.preferences

    var inputMode by remember { mutableStateOf(InputMode.SINGLE) }
    var dateIso by remember { mutableStateOf(todayIsoDate()) }
    var selectedModel by remember { mutableStateOf(SprModel.PENNAR) }
    var selectedBin by remember { mutableIntStateOf(1) }
    var selectedShift by remember { mutableStateOf(WorkShift.SHIFT_1) }
    var targetText by remember { mutableStateOf(formatInputNumber(prefs.defaultPennarTargetM3)) }
    var actualText by remember { mutableStateOf("") }
    var alarmText by remember { mutableStateOf("2.0") }
    var operatorText by remember { mutableStateOf(prefs.lastOperatorName) }
    var notesText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showBinGrid by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Batch input map: binNumber -> actual gas text
    val batchActualInputs = remember { mutableStateMapOf<Int, String>() }

    // React to edit target
    LaunchedEffect(editing?.id) {
        if (editing != null) {
            inputMode = InputMode.SINGLE
            dateIso = editing.dateIso
            selectedModel = editing.sprModel
            selectedBin = editing.binNumber
            selectedShift = WorkShift.fromLabel(editing.shiftName)
            targetText = formatInputNumber(editing.targetGasM3)
            actualText = formatInputNumber(editing.actualGasM3)
            alarmText = formatInputNumber(editing.alarmPercent)
            operatorText = editing.operatorName
            notesText = editing.notes
            validationError = null
        }
    }

    // React to matrix cell prefill
    LaunchedEffect(state.prefillDateIso, state.prefillModel, state.prefillBin) {
        val pDate = state.prefillDateIso
        val pModel = state.prefillModel
        val pBin = state.prefillBin
        if (pDate != null && pModel != null && pBin != null && editing == null) {
            inputMode = InputMode.SINGLE
            dateIso = pDate
            selectedModel = pModel
            selectedBin = pBin.coerceIn(1, pModel.binCount)
            val defTarget = if (pModel == SprModel.PENNAR) prefs.defaultPennarTargetM3 else prefs.defaultPeanutTargetM3
            targetText = formatInputNumber(defTarget)
            actualText = ""
            validationError = null
        }
    }

    // Sync last operator default if user hasn't typed one
    LaunchedEffect(prefs.lastOperatorName) {
        if (operatorText.isBlank() && prefs.lastOperatorName.isNotBlank()) {
            operatorText = prefs.lastOperatorName
        }
    }

    // Existing bins already logged on this date + model + shift
    val filledBins = remember(state.allEntries, dateIso, selectedModel, selectedShift) {
        state.allEntries
            .filter {
                it.dateIso == dateIso &&
                    it.sprModel == selectedModel &&
                    WorkShift.fromLabel(it.shiftName) == selectedShift
            }
            .map { it.binNumber }
            .toSet()
    }

    if (showDatePicker) {
        val initialMillis = parseIsoToUtcMillis(dateIso)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dateIso = utcMillisToIsoDate(millis)
                        }
                        showDatePicker = false
                    },
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = pickerState)
        }
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
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editing != null) "Edit Data Gas (${editing.binCode})" else "Input Data ke Sheet Excel",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = if (prefs.linkedFileName.isNotBlank() && prefs.autoSyncEnabled) {
                                "Auto-sync aktif ke ${prefs.linkedFileName}"
                            } else {
                                "Data tersimpan ke sheet Input_Data & Matriks_Harian"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (editing != null) {
                        IconButton(onClick = {
                            onCancelEdit()
                            actualText = ""
                            notesText = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Batal edit")
                        }
                    }
                }
            }

            // Linked Excel quick hint banner if not yet connected
            if (prefs.linkedFileName.isBlank()) {
                item {
                    Surface(
                        onClick = onOpenExcelTab,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Hubungkan file .xlsx di tab Excel agar tiap input langsung menulis ke Excel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Hubungkan",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Mode toggle (only when not editing existing row)
            if (editing == null) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        InputMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = inputMode == mode,
                                onClick = {
                                    inputMode = mode
                                    validationError = null
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = InputMode.entries.size,
                                ),
                                modifier = Modifier.testTag("input_mode_${mode.name.lowercase()}"),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // Common Metadata Card: Date, Model, Shift
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Date row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("input_date_button"),
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih tanggal", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = dateIso,
                                    style = SheetCellMonoStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                )
                            }
                            FilterChip(
                                selected = dateIso == todayIsoDate(),
                                onClick = { dateIso = todayIsoDate() },
                                label = { Text("Hari Ini") },
                                modifier = Modifier.height(48.dp),
                            )
                        }

                        // Model selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Model Mesin SPR",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SprModel.entries.forEachIndexed { idx, model ->
                                    SegmentedButton(
                                        selected = selectedModel == model,
                                        onClick = {
                                            selectedModel = model
                                            selectedBin = selectedBin.coerceIn(1, model.binCount)
                                            if (editing == null) {
                                                val defTarget = if (model == SprModel.PENNAR) {
                                                    prefs.defaultPennarTargetM3
                                                } else {
                                                    prefs.defaultPeanutTargetM3
                                                }
                                                targetText = formatInputNumber(defTarget)
                                            }
                                            batchActualInputs.clear()
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = SprModel.entries.size,
                                        ),
                                        modifier = Modifier.testTag("model_btn_${model.name.lowercase()}"),
                                    ) {
                                        Text("${model.displayName} (1–${model.binCount})")
                                    }
                                }
                            }
                        }

                        // Shift selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Shift Kerja",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WorkShift.entries.forEach { shift ->
                                    FilterChip(
                                        selected = selectedShift == shift,
                                        onClick = { selectedShift = shift },
                                        label = { Text(shift.label) },
                                        leadingIcon = if (selectedShift == shift) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("shift_chip_${shift.shortLabel.lowercase()}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (inputMode == InputMode.SINGLE) {
                // Single Bin Selector & Gas Measurement Card
                item {
                    val targetNum = parseNum(targetText) ?: 0.0
                    val actualNum = parseNum(actualText)
                    val previewRealization = if (actualNum != null && targetNum > 0.0) {
                        (actualNum / targetNum) * 100.0
                    } else {
                        null
                    }
                    val previewVariance = if (actualNum != null) actualNum - targetNum else null

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Bin picker header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "Nomor Bin Aktif: ${selectedModel.formatBinCode(selectedBin)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "${filledBins.size}/${selectedModel.binCount} bin sudah terisi pada tanggal & shift ini",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showBinGrid = !showBinGrid },
                                    modifier = Modifier.testTag("toggle_bin_grid_btn"),
                                ) {
                                    Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (showBinGrid) "Tutup" else "Pilih Bin")
                                }
                            }

                            // Quick Prev / Next Bin buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedBin = if (selectedBin > 1) selectedBin - 1 else selectedModel.binCount
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                ) { Text("← Bin Sebelumnya") }
                                OutlinedButton(
                                    onClick = {
                                        selectedBin = if (selectedBin < selectedModel.binCount) selectedBin + 1 else 1
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                ) { Text("Bin Berikutnya →") }
                            }

                            // Expandable Bin Grid
                            if (showBinGrid) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    for (bin in 1..selectedModel.binCount) {
                                        val isSelected = bin == selectedBin
                                        val isFilled = bin in filledBins
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isFilled -> GasTealContainer
                                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                                    },
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                                .clickable {
                                                    selectedBin = bin
                                                    showBinGrid = false
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = bin.toString().padStart(2, '0'),
                                                style = SheetCellMonoStyle.copy(fontWeight = FontWeight.Bold),
                                                color = when {
                                                    isSelected -> Color.White
                                                    isFilled -> OnGasTealContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            if (isFilled && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(3.dp)
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(GasTeal),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Target & Actual Gas fields
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = targetText,
                                    onValueChange = {
                                        targetText = it
                                        validationError = null
                                    },
                                    label = { Text("Target Gas (m³)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_target_gas"),
                                )
                                OutlinedTextField(
                                    value = actualText,
                                    onValueChange = {
                                        actualText = it
                                        validationError = null
                                    },
                                    label = { Text("Aktual Gas (m³)") },
                                    placeholder = { Text("mis. 11.2") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_actual_gas"),
                                )
                            }

                            // Live realization calculation preview
                            if (previewRealization != null && previewVariance != null) {
                                val withinTarget = previewVariance <= 0.001
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (withinTarget) GasTealContainer else AlarmCrimsonSoft,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (withinTarget) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (withinTarget) GasTeal else AlarmCrimson,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = if (withinTarget) "Hemat (≤ Target)" else "Melebihi Target!",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (withinTarget) OnGasTealContainer else AlarmCrimson,
                                                )
                                                val sign = if (previewVariance > 0) "+" else ""
                                                Text(
                                                    text = "Selisih $sign${formatM3(previewVariance)} m³",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (withinTarget) OnGasTealContainer else AlarmCrimson,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${formatPct(previewRealization)}%",
                                            style = MetricNumberStyle,
                                            color = if (withinTarget) GasTeal else AlarmCrimson,
                                        )
                                    }
                                }
                            }

                            // Alarm % field + quick presets
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = alarmText,
                                    onValueChange = { alarmText = it },
                                    label = { Text("Alarm (%)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_alarm_percent"),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("0.0", "1.5", "2.5", "4.0").forEach { preset ->
                                        FilterChip(
                                            selected = alarmText == preset,
                                            onClick = { alarmText = preset },
                                            label = { Text("$preset%") },
                                        )
                                    }
                                }
                            }

                            // Operator & Notes
                            OutlinedTextField(
                                value = operatorText,
                                onValueChange = { operatorText = it },
                                label = { Text("Nama Operator (opsional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_operator"),
                            )
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Catatan / Keterangan (opsional)") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_notes"),
                            )

                            if (validationError != null) {
                                Text(
                                    text = validationError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            // Primary Save to Excel button
                            Button(
                                onClick = {
                                    val t = parseNum(targetText)
                                    val a = parseNum(actualText)
                                    val al = parseNum(alarmText) ?: 0.0
                                    if (t == null || t <= 0.0) {
                                        validationError = "Target gas (m³) harus lebih besar dari 0."
                                        return@Button
                                    }
                                    if (a == null || a < 0.0) {
                                        validationError = "Masukkan angka aktual gas (m³) yang valid."
                                        return@Button
                                    }
                                    val (y, m, d) = splitIsoDate(dateIso)
                                    val entity = GasEntryEntity(
                                        id = editing?.id ?: 0L,
                                        dateIso = dateIso,
                                        year = y,
                                        month = m,
                                        dayOfMonth = d,
                                        modelName = selectedModel.displayName,
                                        binNumber = selectedBin,
                                        shiftName = selectedShift.label,
                                        targetGasM3 = t,
                                        actualGasM3 = a,
                                        alarmPercent = al.coerceIn(0.0, 100.0),
                                        operatorName = operatorText.trim(),
                                        notes = notesText.trim(),
                                    )
                                    onSaveSingle(entity)
                                    if (editing == null) {
                                        actualText = ""
                                        notesText = ""
                                        selectedBin = if (selectedBin < selectedModel.binCount) selectedBin + 1 else 1
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("save_to_excel_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(
                                    imageVector = if (prefs.linkedFileName.isNotBlank() && prefs.autoSyncEnabled) {
                                        Icons.Default.CloudDone
                                    } else {
                                        Icons.Default.Save
                                    },
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (editing != null) {
                                        "Perbarui & Tulis ke Excel"
                                    } else {
                                        "Simpan & Tulis ke Excel"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            } else {
                // Batch Input Mode: rapid multi-bin spreadsheet entry
                item {
                    val targetNum = parseNum(targetText) ?: selectedModel.defaultTargetM3
                    val alarmNum = (parseNum(alarmText) ?: 2.0).coerceIn(0.0, 100.0)
                    val filledBatchCount = batchActualInputs.values.count { parseNum(it) != null }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Input Cepat Multi-Bin (${selectedModel.displayName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Isi kolom Aktual (m³) hanya pada bin yang ingin dicatat. Semua baris langsung masuk ke sheet Excel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = targetText,
                                    onValueChange = { targetText = it },
                                    label = { Text("Target / Bin (m³)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = alarmText,
                                    onValueChange = { alarmText = it },
                                    label = { Text("Alarm (%)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = operatorText,
                                onValueChange = { operatorText = it },
                                label = { Text("Operator") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(4.dp))

                            // Compact 2-column spreadsheet rows for all bins of selected model
                            val totalBins = selectedModel.binCount
                            for (rowStart in 1..totalBins step 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    for (bin in rowStart..(rowStart + 1).coerceAtMost(totalBins)) {
                                        val alreadyLogged = bin in filledBins
                                        OutlinedTextField(
                                            value = batchActualInputs[bin].orEmpty(),
                                            onValueChange = { batchActualInputs[bin] = it },
                                            label = {
                                                Text(
                                                    text = selectedModel.formatBinCode(bin) +
                                                        if (alreadyLogged) " ✓" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            placeholder = { Text("m³") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (rowStart + 1 > totalBins) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val (y, m, d) = splitIsoDate(dateIso)
                                    val list = batchActualInputs.entries.mapNotNull { (bin, raw) ->
                                        val act = parseNum(raw) ?: return@mapNotNull null
                                        if (act < 0.0) return@mapNotNull null
                                        GasEntryEntity(
                                            dateIso = dateIso,
                                            year = y,
                                            month = m,
                                            dayOfMonth = d,
                                            modelName = selectedModel.displayName,
                                            binNumber = bin,
                                            shiftName = selectedShift.label,
                                            targetGasM3 = targetNum.coerceAtLeast(0.1),
                                            actualGasM3 = act,
                                            alarmPercent = alarmNum,
                                            operatorName = operatorText.trim(),
                                            notes = "",
                                        )
                                    }
                                    if (list.isNotEmpty()) {
                                        onSaveBatch(list)
                                        batchActualInputs.clear()
                                    }
                                },
                                enabled = filledBatchCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("save_batch_to_excel_button"),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Simpan $filledBatchCount Bin ke Excel")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseNum(raw: String): Double? = raw.trim().replace(",", ".").toDoubleOrNull()

private fun formatInputNumber(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun todayIsoDate(): String {
    val cal = Calendar.getInstance()
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}

private fun splitIsoDate(iso: String): Triple<Int, Int, Int> {
    val parts = iso.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 11
    val d = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: 1
    return Triple(y, m, d)
}

private fun parseIsoToUtcMillis(iso: String): Long {
    val (y, m, d) = splitIsoDate(iso)
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(y, m - 1, d)
    }
    return cal.timeInMillis
}

private fun utcMillisToIsoDate(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}
