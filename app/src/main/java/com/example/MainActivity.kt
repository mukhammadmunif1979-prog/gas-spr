package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.GasViewModel
import com.example.ui.UiEvent
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExcelSyncScreen
import com.example.ui.screens.InputScreen
import com.example.ui.screens.SheetScreen
import com.example.ui.theme.GasSheetTheme
import java.io.File

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GasSheetTheme {
                GasSheetApp()
            }
        }
    }
}

@Composable
fun GasSheetApp(viewModel: GasViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }

    // Return to Dashboard on system back press from secondary tabs
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        if (currentTab == AppTab.INPUT) {
            viewModel.clearEditAndPrefill()
        }
        currentTab = AppTab.DASHBOARD
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(XLSX_MIME),
    ) { uri ->
        if (uri != null) {
            viewModel.onCreateAndLinkExcelFile(uri)
        }
    }

    val openExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onImportExcelFile(uri, linkForAutoSync = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.ShareExcelFile -> {
                    shareExcelFile(context, event.file)
                }
                UiEvent.NavigateToInput -> {
                    currentTab = AppTab.INPUT
                }
            }
        }
    }

    val triggerCreateExcel = {
        createExcelLauncher.launch(viewModel.suggestedExcelFileName())
    }
    val triggerImportExcel = {
        openExcelLauncher.launch(
            arrayOf(
                XLSX_MIME,
                "application/vnd.ms-excel",
                "application/octet-stream",
            ),
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            state = state,
                            onYearChange = viewModel::setYear,
                            onMonthChange = viewModel::setMonth,
                            onModelChange = viewModel::setModelFilter,
                            onShiftChange = viewModel::setShiftFilter,
                            onNavigateToInput = { currentTab = AppTab.INPUT },
                            onNavigateToExcel = { currentTab = AppTab.EXCEL },
                            onImportExcelClick = triggerImportExcel,
                            onShareExcelClick = viewModel::exportAndShareExcel,
                            onLoadSampleClick = viewModel::loadSampleNov2026Data,
                            onQuickInputCell = { dateIso, model, bin ->
                                viewModel.startQuickInputForCell(dateIso, model, bin)
                            },
                        )

                        AppTab.INPUT -> InputScreen(
                            state = state,
                            onSaveSingle = viewModel::saveEntry,
                            onSaveBatch = viewModel::saveBatchEntries,
                            onCancelEdit = viewModel::clearEditAndPrefill,
                            onOpenExcelTab = { currentTab = AppTab.EXCEL },
                        )

                        AppTab.SHEET -> SheetScreen(
                            state = state,
                            onSearchChange = viewModel::setSearchQuery,
                            onSortChange = viewModel::setSortMode,
                            onEditEntry = viewModel::startEditing,
                            onDeleteEntry = viewModel::deleteEntry,
                            onAddClick = { currentTab = AppTab.INPUT },
                            onShareExcelClick = viewModel::exportAndShareExcel,
                        )

                        AppTab.EXCEL -> ExcelSyncScreen(
                            state = state,
                            onCreateExcelClick = triggerCreateExcel,
                            onImportExcelClick = triggerImportExcel,
                            onSyncNowClick = viewModel::syncLinkedExcelNow,
                            onShareExcelClick = viewModel::exportAndShareExcel,
                            onAutoSyncChange = viewModel::setAutoSync,
                            onUnlinkExcelClick = viewModel::unlinkExcelFile,
                            onSaveDefaults = viewModel::saveDefaultTargets,
                            onLoadSampleClick = viewModel::loadSampleNov2026Data,
                            onClearAllClick = viewModel::clearAllEntries,
                        )
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: AppTab, selected: Boolean): ImageVector = when (tab) {
    AppTab.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    AppTab.INPUT -> if (selected) Icons.Filled.AddCircle else Icons.Outlined.AddCircleOutline
    AppTab.SHEET -> if (selected) Icons.Filled.TableView else Icons.Outlined.TableView
    AppTab.EXCEL -> if (selected) Icons.Filled.Description else Icons.Outlined.Description
}

private fun shareExcelFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = XLSX_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan / Buka di Excel"))
}
