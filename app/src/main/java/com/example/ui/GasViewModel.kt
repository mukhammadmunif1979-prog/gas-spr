package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DashboardMetrics
import com.example.data.ExcelWorkbookManager
import com.example.data.GasEntryEntity
import com.example.data.GasRepository
import com.example.data.MonthCatalog
import com.example.data.PreferencesRepository
import com.example.data.SprModel
import com.example.data.WorkShift
import com.example.data.WorkbookPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

enum class AppTab(val label: String) {
    DASHBOARD("Dashboard"),
    INPUT("Input"),
    SHEET("Sheet"),
    EXCEL("Excel"),
}

enum class SheetSortMode(val label: String) {
    NEWEST("Terbaru"),
    BIN_ASC("Nomor Bin"),
    OVER_TARGET("Over Target"),
}

data class FilterState(
    val selectedYear: Int = 2026,
    val selectedMonth: Int = 11,
    val selectedModel: SprModel? = null,
    val selectedShift: WorkShift? = null,
    val searchQuery: String = "",
    val sortMode: SheetSortMode = SheetSortMode.NEWEST,
)

data class GasUiState(
    val allEntries: List<GasEntryEntity> = emptyList(),
    val filter: FilterState = FilterState(),
    val metrics: DashboardMetrics = DashboardMetrics(
        totalTargetM3 = 0.0,
        totalActualM3 = 0.0,
        realizationPercent = 0.0,
        avgAlarmPercent = 0.0,
        unitsWithinTarget = 0,
        totalUnitsInScope = SprModel.TOTAL_UNITS,
        activeUnitsCount = 0,
        dailyPoints = emptyList(),
        modelRealizations = emptyList(),
        matrixRows = emptyList(),
        maxDailyCellM3 = 1.0,
        filteredEntries = emptyList(),
    ),
    val sheetEntries: List<GasEntryEntity> = emptyList(),
    val availableYears: List<Int> = listOf(2025, 2026, 2027),
    val preferences: WorkbookPreferences = WorkbookPreferences(),
    val isSyncingExcel: Boolean = false,
    val editingEntry: GasEntryEntity? = null,
    val prefillDateIso: String? = null,
    val prefillModel: SprModel? = null,
    val prefillBin: Int? = null,
)

sealed interface UiEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : UiEvent
    data class ShareExcelFile(val file: File) : UiEvent
    data object NavigateToInput : UiEvent
}

class GasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GasRepository(AppDatabase.getInstance(application).gasEntryDao())
    private val preferencesRepo = PreferencesRepository(application)
    private val excelManager = ExcelWorkbookManager(application)

    private val filterState = MutableStateFlow(defaultInitialFilter())
    private val isSyncingState = MutableStateFlow(false)
    private val editingEntryState = MutableStateFlow<GasEntryEntity?>(null)
    private val prefillState = MutableStateFlow<Triple<String?, SprModel?, Int?>>(Triple(null, null, null))

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val metaFlow = combine(
        preferencesRepo.preferencesFlow,
        isSyncingState,
        editingEntryState,
        prefillState,
    ) { prefs, syncing, editing, prefill ->
        MetaBundle(prefs, syncing, editing, prefill)
    }

    val uiState: StateFlow<GasUiState> = combine(
        repository.allEntries,
        filterState,
        metaFlow,
    ) { entries, filter, meta ->
        val metrics = repository.computeDashboard(
            allEntries = entries,
            year = filter.selectedYear,
            month = filter.selectedMonth,
            modelFilter = filter.selectedModel,
            shiftFilter = filter.selectedShift,
        )
        val query = filter.searchQuery.trim().lowercase()
        val sheetList = metrics.filteredEntries
            .filter { entry ->
                query.isEmpty() ||
                    entry.binCode.lowercase().contains(query) ||
                    entry.dateIso.contains(query) ||
                    entry.operatorName.lowercase().contains(query) ||
                    entry.notes.lowercase().contains(query) ||
                    entry.shiftName.lowercase().contains(query)
            }
            .let { list ->
                when (filter.sortMode) {
                    SheetSortMode.NEWEST -> list.sortedWith(
                        compareByDescending<GasEntryEntity> { it.dateIso }
                            .thenByDescending { it.updatedAtMillis },
                    )
                    SheetSortMode.BIN_ASC -> list.sortedWith(
                        compareBy<GasEntryEntity> { it.modelName }
                            .thenBy { it.binNumber }
                            .thenByDescending { it.dateIso },
                    )
                    SheetSortMode.OVER_TARGET -> list.sortedByDescending { it.realizationPercent }
                }
            }

        val years = (entries.map { it.year } + listOf(2025, 2026, 2027, filter.selectedYear))
            .distinct()
            .sorted()

        GasUiState(
            allEntries = entries,
            filter = filter,
            metrics = metrics,
            sheetEntries = sheetList,
            availableYears = years,
            preferences = meta.preferences,
            isSyncingExcel = meta.isSyncing,
            editingEntry = meta.editingEntry,
            prefillDateIso = meta.prefill.first,
            prefillModel = meta.prefill.second,
            prefillBin = meta.prefill.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = GasUiState(filter = filterState.value),
    )

    private data class MetaBundle(
        val preferences: WorkbookPreferences,
        val isSyncing: Boolean,
        val editingEntry: GasEntryEntity?,
        val prefill: Triple<String?, SprModel?, Int?>,
    )

    private fun defaultInitialFilter(): FilterState {
        val cal = Calendar.getInstance()
        return FilterState(
            selectedYear = cal.get(Calendar.YEAR),
            selectedMonth = cal.get(Calendar.MONTH) + 1,
        )
    }

    fun setYear(year: Int) = filterState.update { it.copy(selectedYear = year) }

    fun setMonth(month: Int) = filterState.update { it.copy(selectedMonth = month) }

    fun setModelFilter(model: SprModel?) = filterState.update { it.copy(selectedModel = model) }

    fun setShiftFilter(shift: WorkShift?) = filterState.update { it.copy(selectedShift = shift) }

    fun setSearchQuery(query: String) = filterState.update { it.copy(searchQuery = query) }

    fun setSortMode(mode: SheetSortMode) = filterState.update { it.copy(sortMode = mode) }

    fun startEditing(entry: GasEntryEntity) {
        editingEntryState.value = entry
        prefillState.value = Triple(null, null, null)
        _events.tryEmit(UiEvent.NavigateToInput)
    }

    fun startQuickInputForCell(dateIso: String, model: SprModel, binNumber: Int) {
        editingEntryState.value = null
        prefillState.value = Triple(dateIso, model, binNumber)
        _events.tryEmit(UiEvent.NavigateToInput)
    }

    fun clearEditAndPrefill() {
        editingEntryState.value = null
        prefillState.value = Triple(null, null, null)
    }

    fun saveEntry(entry: GasEntryEntity) {
        viewModelScope.launch {
            repository.upsertEntry(entry)
            preferencesRepo.updateLastOperator(entry.operatorName)
            editingEntryState.value = null
            prefillState.value = Triple(null, null, null)
            filterState.update { it.copy(selectedYear = entry.year, selectedMonth = entry.month) }

            val synced = autoSyncIfLinked(entry.year, entry.month)
            val msg = if (synced) {
                "${entry.binCode} (${entry.shiftName}) tersimpan & tertulis ke Excel .xlsx"
            } else {
                "${entry.binCode} (${entry.shiftName}) tersimpan. Hubungkan file .xlsx di tab Excel untuk auto-sync."
            }
            _events.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun saveBatchEntries(entries: List<GasEntryEntity>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repository.upsertBatch(entries)
            entries.firstOrNull()?.let { first ->
                preferencesRepo.updateLastOperator(first.operatorName)
                filterState.update { it.copy(selectedYear = first.year, selectedMonth = first.month) }
                val synced = autoSyncIfLinked(first.year, first.month)
                val msg = if (synced) {
                    "${entries.size} baris bin tersimpan & langsung tertulis ke sheet Excel .xlsx"
                } else {
                    "${entries.size} baris bin tersimpan ke daftar lokal."
                }
                _events.emit(UiEvent.ShowMessage(msg))
            }
        }
    }

    fun deleteEntry(entry: GasEntryEntity) {
        viewModelScope.launch {
            repository.deleteEntry(entry.id)
            if (editingEntryState.value?.id == entry.id) {
                editingEntryState.value = null
            }
            val synced = autoSyncIfLinked(filterState.value.selectedYear, filterState.value.selectedMonth)
            val suffix = if (synced) " & Excel diperbarui." else "."
            _events.emit(UiEvent.ShowMessage("Data ${entry.binCode} (${entry.dateIso}) dihapus$suffix"))
        }
    }

    fun loadSampleNov2026Data() {
        viewModelScope.launch {
            isSyncingState.value = true
            try {
                val count = repository.insertSampleNov2026Dataset()
                filterState.update {
                    it.copy(
                        selectedYear = 2026,
                        selectedMonth = 11,
                        selectedModel = null,
                        selectedShift = null,
                    )
                }
                val synced = autoSyncIfLinked(2026, 11)
                val extra = if (synced) " & ditulis ke file Excel terhubung." else "."
                _events.emit(UiEvent.ShowMessage("$count baris contoh Nov 2026 dimuat$extra"))
            } finally {
                isSyncingState.value = false
            }
        }
    }

    fun clearAllEntries() {
        viewModelScope.launch {
            repository.clearAll()
            editingEntryState.value = null
            autoSyncIfLinked(filterState.value.selectedYear, filterState.value.selectedMonth)
            _events.emit(UiEvent.ShowMessage("Semua baris data telah dikosongkan."))
        }
    }

    fun onCreateAndLinkExcelFile(uri: Uri) {
        viewModelScope.launch {
            isSyncingState.value = true
            try {
                persistUriPermissions(uri)
                val displayName = excelManager.resolveDisplayName(uri)
                preferencesRepo.setLinkedFile(uri.toString(), displayName)
                val state = uiState.value
                val snapshot = repository.getAllSnapshot()
                val metrics = repository.computeDashboard(
                    allEntries = snapshot,
                    year = state.filter.selectedYear,
                    month = state.filter.selectedMonth,
                    modelFilter = null,
                    shiftFilter = null,
                )
                val result = excelManager.writeWorkbookToUri(
                    uri = uri,
                    entries = snapshot,
                    summaryYear = state.filter.selectedYear,
                    summaryMonth = state.filter.selectedMonth,
                    metrics = metrics,
                )
                result.fold(
                    onSuccess = { rowCount ->
                        preferencesRepo.markSynced()
                        _events.emit(
                            UiEvent.ShowMessage(
                                "File \"$displayName\" terhubung ($rowCount baris tertulis ke 3 sheet).",
                            ),
                        )
                    },
                    onFailure = { err ->
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Gagal menulis ke Excel: ${err.message}",
                                isError = true,
                            ),
                        )
                    },
                )
            } finally {
                isSyncingState.value = false
            }
        }
    }

    fun onImportExcelFile(uri: Uri, linkForAutoSync: Boolean = true) {
        viewModelScope.launch {
            isSyncingState.value = true
            try {
                if (linkForAutoSync) {
                    persistUriPermissions(uri)
                }
                val displayName = excelManager.resolveDisplayName(uri)
                val result = excelManager.importFromUri(uri)
                result.fold(
                    onSuccess = { imported ->
                        if (imported.entries.isNotEmpty()) {
                            repository.upsertBatch(imported.entries)
                            val latest = imported.entries.maxByOrNull { it.dateIso }
                            if (latest != null) {
                                filterState.update {
                                    it.copy(
                                        selectedYear = latest.year,
                                        selectedMonth = latest.month,
                                    )
                                }
                            }
                        }
                        if (linkForAutoSync) {
                            preferencesRepo.setLinkedFile(uri.toString(), displayName)
                            preferencesRepo.markSynced()
                        }
                        val skipNote = if (imported.skippedRows > 0) " (${imported.skippedRows} baris dilewati)" else ""
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Berhasil impor ${imported.entries.size} baris dari \"$displayName\"$skipNote.",
                            ),
                        )
                    },
                    onFailure = { err ->
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Gagal membaca file .xlsx: ${err.message}",
                                isError = true,
                            ),
                        )
                    },
                )
            } finally {
                isSyncingState.value = false
            }
        }
    }

    fun syncLinkedExcelNow() {
        viewModelScope.launch {
            val prefs = uiState.value.preferences
            if (prefs.linkedFileUri.isBlank()) {
                _events.emit(
                    UiEvent.ShowMessage(
                        "Belum ada file .xlsx terhubung. Buat atau pilih file Excel terlebih dahulu.",
                        isError = true,
                    ),
                )
                return@launch
            }
            isSyncingState.value = true
            try {
                val filter = filterState.value
                val snapshot = repository.getAllSnapshot()
                val metrics = repository.computeDashboard(
                    allEntries = snapshot,
                    year = filter.selectedYear,
                    month = filter.selectedMonth,
                    modelFilter = null,
                    shiftFilter = null,
                )
                val result = excelManager.writeWorkbookToUri(
                    uri = Uri.parse(prefs.linkedFileUri),
                    entries = snapshot,
                    summaryYear = filter.selectedYear,
                    summaryMonth = filter.selectedMonth,
                    metrics = metrics,
                )
                result.fold(
                    onSuccess = { count ->
                        preferencesRepo.markSynced()
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Sinkronisasi sukses! $count baris tertulis ke \"${prefs.linkedFileName}\".",
                            ),
                        )
                    },
                    onFailure = { err ->
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Gagal sinkron ke file Excel: ${err.message}. Pilih ulang file di tab Excel.",
                                isError = true,
                            ),
                        )
                    },
                )
            } finally {
                isSyncingState.value = false
            }
        }
    }

    fun exportAndShareExcel() {
        viewModelScope.launch {
            isSyncingState.value = true
            try {
                val filter = filterState.value
                val snapshot = repository.getAllSnapshot()
                val metrics = repository.computeDashboard(
                    allEntries = snapshot,
                    year = filter.selectedYear,
                    month = filter.selectedMonth,
                    modelFilter = null,
                    shiftFilter = null,
                )
                val result = excelManager.exportToCacheFile(
                    entries = snapshot,
                    summaryYear = filter.selectedYear,
                    summaryMonth = filter.selectedMonth,
                    metrics = metrics,
                )
                result.fold(
                    onSuccess = { file ->
                        _events.emit(UiEvent.ShareExcelFile(file))
                    },
                    onFailure = { err ->
                        _events.emit(
                            UiEvent.ShowMessage("Gagal mengekspor .xlsx: ${err.message}", isError = true),
                        )
                    },
                )
            } finally {
                isSyncingState.value = false
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setAutoSync(enabled)
        }
    }

    fun unlinkExcelFile() {
        viewModelScope.launch {
            preferencesRepo.clearLinkedFile()
            _events.emit(UiEvent.ShowMessage("Tautan file Excel dilepas."))
        }
    }

    fun saveDefaultTargets(pennarTarget: Double, peanutTarget: Double, operatorName: String) {
        viewModelScope.launch {
            preferencesRepo.updateDefaults(
                pennarTargetM3 = pennarTarget.coerceAtLeast(0.1),
                peanutTargetM3 = peanutTarget.coerceAtLeast(0.1),
                operatorName = operatorName.trim(),
            )
            _events.emit(UiEvent.ShowMessage("Pengaturan target default tersimpan."))
        }
    }

    fun suggestedExcelFileName(): String {
        val f = filterState.value
        val monthShort = MonthCatalog.shortName(f.selectedMonth)
        return "GasUsage_SPR_${monthShort}_${f.selectedYear}.xlsx"
    }

    private suspend fun autoSyncIfLinked(year: Int, month: Int): Boolean {
        val prefs = uiState.value.preferences
        if (!prefs.autoSyncEnabled || prefs.linkedFileUri.isBlank()) return false
        return try {
            val snapshot = repository.getAllSnapshot()
            val metrics = repository.computeDashboard(
                allEntries = snapshot,
                year = year,
                month = month,
                modelFilter = null,
                shiftFilter = null,
            )
            val result = excelManager.writeWorkbookToUri(
                uri = Uri.parse(prefs.linkedFileUri),
                entries = snapshot,
                summaryYear = year,
                summaryMonth = month,
                metrics = metrics,
            )
            if (result.isSuccess) {
                preferencesRepo.markSynced()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun persistUriPermissions(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some SAF providers grant temporary write permission only; continue gracefully.
        }
    }
}
