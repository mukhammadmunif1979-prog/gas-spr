package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.DashboardMetrics
import com.example.data.ExcelWorkbookManager
import com.example.data.GasEntryEntity
import com.example.data.SprModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GasSheet SPR", appName)
    }

    @Test
    fun `export and import xlsx workbook round trip succeeds with three sheets`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ExcelWorkbookManager(context)
        val entries = listOf(
            GasEntryEntity(
                dateIso = "2026-11-25",
                year = 2026,
                month = 11,
                dayOfMonth = 25,
                modelName = SprModel.PENNAR.displayName,
                binNumber = 5,
                shiftName = "Shift 1",
                targetGasM3 = 13.5,
                actualGasM3 = 11.2,
                alarmPercent = 2.4,
                operatorName = "Budi",
                notes = "Normal",
            ),
            GasEntryEntity(
                dateIso = "2026-11-25",
                year = 2026,
                month = 11,
                dayOfMonth = 25,
                modelName = SprModel.PEANUT.displayName,
                binNumber = 3,
                shiftName = "Shift 2",
                targetGasM3 = 12.8,
                actualGasM3 = 13.9,
                alarmPercent = 4.1,
                operatorName = "Rina",
                notes = "Over target",
            ),
        )
        val metrics = DashboardMetrics(
            totalTargetM3 = 26.3,
            totalActualM3 = 25.1,
            realizationPercent = 95.4,
            avgAlarmPercent = 3.25,
            unitsWithinTarget = 1,
            totalUnitsInScope = 51,
            activeUnitsCount = 2,
            dailyPoints = emptyList(),
            modelRealizations = emptyList(),
            matrixRows = emptyList(),
            maxDailyCellM3 = 13.9,
            filteredEntries = entries,
        )

        val file = manager.exportToCacheFile(entries, 2026, 11, metrics).getOrThrow()
        assertTrue(file.exists() && file.length() > 500L)

        // Verify all 3 worksheets exist in the OOXML archive
        ZipFile(file).use { zip ->
            assertTrue(zip.getEntry("xl/worksheets/sheet1.xml") != null)
            assertTrue(zip.getEntry("xl/worksheets/sheet2.xml") != null)
            assertTrue(zip.getEntry("xl/worksheets/sheet3.xml") != null)
        }

        // Verify re-importing the generated .xlsx recovers both rows accurately
        val imported = manager.importFromUri(Uri.fromFile(file)).getOrThrow()
        assertEquals(2, imported.entries.size)
        assertEquals("Pennar-05", imported.entries[0].binCode)
        assertEquals(11.2, imported.entries[0].actualGasM3, 0.01)
        assertEquals("Peanut-03", imported.entries[1].binCode)
        assertEquals(13.9, imported.entries[1].actualGasM3, 0.01)
    }
}
