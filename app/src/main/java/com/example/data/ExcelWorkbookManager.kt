package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExcelImportResult(
    val entries: List<GasEntryEntity>,
    val skippedRows: Int,
)

class ExcelWorkbookManager(private val context: Context) {

    fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "GasUsage_SPR.xlsx"
    }

    suspend fun writeWorkbookToUri(
        uri: Uri,
        entries: List<GasEntryEntity>,
        summaryYear: Int,
        summaryMonth: Int,
        metrics: DashboardMetrics,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Tidak dapat membuka file Excel untuk ditulis.")
            out.use { stream ->
                writeXlsxStream(stream, entries, summaryYear, summaryMonth, metrics)
            }
            entries.size
        }
    }

    suspend fun exportToCacheFile(
        entries: List<GasEntryEntity>,
        summaryYear: Int,
        summaryMonth: Int,
        metrics: DashboardMetrics,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val monthTag = MonthCatalog.shortName(summaryMonth)
            val file = File(dir, "GasUsage_SPR_${monthTag}_${summaryYear}.xlsx")
            FileOutputStream(file).use { stream ->
                writeXlsxStream(stream, entries, summaryYear, summaryMonth, metrics)
            }
            file
        }
    }

    suspend fun importFromUri(uri: Uri): Result<ExcelImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Tidak dapat membaca file Excel yang dipilih.")
            input.use { stream ->
                parseXlsxStream(stream)
            }
        }
    }

    private fun writeXlsxStream(
        outputStream: OutputStream,
        entries: List<GasEntryEntity>,
        summaryYear: Int,
        summaryMonth: Int,
        metrics: DashboardMetrics,
    ) {
        val sortedEntries = entries.sortedWith(
            compareBy<GasEntryEntity> { it.dateIso }
                .thenBy { it.sprModel.ordinal }
                .thenBy { it.binNumber }
                .thenBy { it.shiftName },
        )

        ZipOutputStream(outputStream).use { zip ->
            addZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())
            addZipEntry(zip, "_rels/.rels", buildRootRelsXml())
            addZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            addZipEntry(zip, "xl/styles.xml", buildStylesXml())
            addZipEntry(zip, "xl/worksheets/sheet1.xml", buildInputDataSheetXml(sortedEntries))
            addZipEntry(
                zip,
                "xl/worksheets/sheet2.xml",
                buildDashboardSummarySheetXml(summaryYear, summaryMonth, metrics),
            )
            addZipEntry(
                zip,
                "xl/worksheets/sheet3.xml",
                buildMatrixSheetXml(summaryYear, summaryMonth, metrics),
            )
        }
    }

    private fun addZipEntry(zip: ZipOutputStream, path: String, xml: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(xml.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun buildRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Input_Data" sheetId="1" r:id="rId1"/>
    <sheet name="Ringkasan_Dashboard" sheetId="2" r:id="rId2"/>
    <sheet name="Matriks_Harian" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

    private fun buildWorkbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /**
     * Style indices:
     * 0 = Default body
     * 1 = Navy header (white bold on #1B3A6B)
     * 2 = Teal KPI header (white bold on #1AA08A)
     * 3 = Number 1 decimal (#,##0.0)
     * 4 = Percent number (0.0"%")
     * 5 = Status OK (dark teal on #D1F4EC)
     * 6 = Status Over (crimson on #FDEAEA)
     * 7 = Title Banner (13pt white bold on #1B3A6B)
     * 8 = Summary bold (#EBF0F7 fill)
     * 9 = Integer count (#,##0)
     */
    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="3">
    <numFmt numFmtId="164" formatCode="#,##0.0"/>
    <numFmt numFmtId="165" formatCode="0.0&quot;%&quot;"/>
    <numFmt numFmtId="166" formatCode="#,##0"/>
  </numFmts>
  <fonts count="5">
    <font><sz val="10"/><color rgb="FF131D2E"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FF04382F"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FFC00000"/><name val="Calibri"/></font>
    <font><b/><sz val="13"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="7">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1B3A6B"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1AA08A"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD1F4EC"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFDEAEA"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFEBF0F7"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD7DFEC"/></left>
      <right style="thin"><color rgb="FFD7DFEC"/></right>
      <top style="thin"><color rgb="FFD7DFEC"/></top>
      <bottom style="thin"><color rgb="FFD7DFEC"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="10">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="right"/></xf>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="right"/></xf>
    <xf numFmtId="0" fontId="2" fillId="4" borderId="1" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="5" borderId="1" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="0" fontId="4" fillId="2" borderId="0" applyFont="1" applyFill="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="164" fontId="2" fillId="6" borderId="1" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="right"/></xf>
    <xf numFmtId="166" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center"/></xf>
  </cellXfs>
</styleSheet>"""

    private fun buildInputDataSheetXml(entries: List<GasEntryEntity>): String {
        val sb = StringBuilder(4096 + entries.size * 260)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        val widths = listOf(13, 8, 8, 13, 8, 12, 10, 15, 15, 13, 14, 12, 14, 18, 26)
        widths.forEachIndexed { idx, w ->
            sb.append("""<col min="${idx + 1}" max="${idx + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")

        val headers = listOf(
            "Tanggal", "Tahun", "Bulan", "Model", "No_Bin", "Kode_Bin", "Shift",
            "Target_Gas_m3", "Aktual_Gas_m3", "Selisih_m3", "Realisasi_Persen",
            "Alarm_Persen", "Status_Target", "Operator", "Catatan",
        )
        sb.append("""<row r="1" ht="24" customHeight="1">""")
        headers.forEachIndexed { colIdx, title ->
            sb.append(stringCell(colIdx, 1, title, style = 1))
        }
        sb.append("</row>")

        entries.forEachIndexed { idx, e ->
            val r = idx + 2
            val statusText = if (e.isWithinTarget) "<= Target" else "Over Target"
            val statusStyle = if (e.isWithinTarget) 5 else 6
            sb.append("""<row r="$r">""")
            sb.append(stringCell(0, r, e.dateIso, style = 0))
            sb.append(numCell(1, r, e.year.toDouble(), style = 9))
            sb.append(stringCell(2, r, MonthCatalog.shortName(e.month), style = 0))
            sb.append(stringCell(3, r, e.modelName, style = 0))
            sb.append(numCell(4, r, e.binNumber.toDouble(), style = 9))
            sb.append(stringCell(5, r, e.binCode, style = 0))
            sb.append(stringCell(6, r, e.shiftName, style = 0))
            sb.append(numCell(7, r, e.targetGasM3, style = 3))
            sb.append(numCell(8, r, e.actualGasM3, style = 3))
            sb.append(formulaNumCell(9, r, "I$r-H$r", e.varianceM3, style = 3))
            sb.append(formulaNumCell(10, r, "IF(H$r>0,(I$r/H$r)*100,0)", e.realizationPercent, style = 4))
            sb.append(numCell(11, r, e.alarmPercent, style = 4))
            sb.append(stringCell(12, r, statusText, style = statusStyle))
            sb.append(stringCell(13, r, e.operatorName, style = 0))
            sb.append(stringCell(14, r, e.notes, style = 0))
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildDashboardSummarySheetXml(
        year: Int,
        month: Int,
        metrics: DashboardMetrics,
    ): String {
        val sb = StringBuilder(4096)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        listOf(22, 18, 18, 18, 16, 16).forEachIndexed { idx, w ->
            sb.append("""<col min="${idx + 1}" max="${idx + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")

        // Row 1: Banner
        sb.append("""<row r="1" ht="28" customHeight="1">""")
        sb.append(stringCell(0, 1, "Gas Usage Tracker | SPR Pennar & SPR Peanut | Pemakaian vs Target", style = 7))
        for (c in 1..5) sb.append(stringCell(c, 1, "", style = 7))
        sb.append("</row>")

        // Row 2: Period
        sb.append("""<row r="2">""")
        sb.append(stringCell(0, 2, "Periode Laporan", style = 8))
        sb.append(stringCell(1, 2, "${MonthCatalog.fullName(month)} $year", style = 0))
        sb.append(stringCell(2, 2, "Total Baris Data", style = 8))
        sb.append(numCell(3, 2, metrics.filteredEntries.size.toDouble(), style = 9))
        sb.append("</row>")

        // Row 4-5: 5 KPI Cards matching dashboard layout
        sb.append("""<row r="4" ht="22" customHeight="1">""")
        listOf(
            "TARGET GAS (m3)",
            "AKTUAL GAS (m3)",
            "REALISASI vs TARGET",
            "ALARM %",
            "UNIT <= TARGET",
        ).forEachIndexed { idx, label ->
            sb.append(stringCell(idx, 4, label, style = 2))
        }
        sb.append("</row>")

        sb.append("""<row r="5" ht="22" customHeight="1">""")
        sb.append(numCell(0, 5, metrics.totalTargetM3, style = 8))
        sb.append(numCell(1, 5, metrics.totalActualM3, style = 8))
        sb.append(numCell(2, 5, metrics.realizationPercent, style = 4))
        sb.append(numCell(3, 5, metrics.avgAlarmPercent, style = 4))
        sb.append(stringCell(4, 5, "${metrics.unitsWithinTarget} / ${metrics.totalUnitsInScope}", style = 5))
        sb.append("</row>")

        // Row 7: Model Breakdown
        sb.append("""<row r="7" ht="22" customHeight="1">""")
        listOf("Model", "Target (m3)", "Aktual (m3)", "Realisasi (%)", "Unit <= Target", "Total Unit")
            .forEachIndexed { idx, label -> sb.append(stringCell(idx, 7, label, style = 1)) }
        sb.append("</row>")

        metrics.modelRealizations.forEachIndexed { idx, item ->
            val r = 8 + idx
            sb.append("""<row r="$r">""")
            sb.append(stringCell(0, r, item.model.displayName, style = 0))
            sb.append(numCell(1, r, item.targetM3, style = 3))
            sb.append(numCell(2, r, item.actualM3, style = 3))
            sb.append(numCell(3, r, item.realizationPercent, style = 4))
            sb.append(numCell(4, r, item.unitsWithinTarget.toDouble(), style = 9))
            sb.append(numCell(5, r, item.model.binCount.toDouble(), style = 9))
            sb.append("</row>")
        }

        // Row 11: Daily Breakdown
        sb.append("""<row r="11" ht="22" customHeight="1">""")
        listOf("Tanggal", "Target Harian (m3)", "Aktual Harian (m3)", "Realisasi (%)", "Alarm (%)", "Jumlah Input")
            .forEachIndexed { idx, label -> sb.append(stringCell(idx, 11, label, style = 1)) }
        sb.append("</row>")

        metrics.dailyPoints.forEachIndexed { idx, day ->
            val r = 12 + idx
            sb.append("""<row r="$r">""")
            sb.append(stringCell(0, r, day.dateIso, style = 0))
            sb.append(numCell(1, r, day.targetM3, style = 3))
            sb.append(numCell(2, r, day.actualM3, style = 3))
            sb.append(numCell(3, r, day.realizationPercent, style = 4))
            sb.append(numCell(4, r, day.alarmPercent, style = 4))
            sb.append(numCell(5, r, day.entryCount.toDouble(), style = 9))
            sb.append("</row>")
        }

        sb.append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:F1\"/></mergeCells></worksheet>")
        return sb.toString()
    }

    private fun buildMatrixSheetXml(
        year: Int,
        month: Int,
        metrics: DashboardMetrics,
    ): String {
        val daysInMonth = MonthCatalog.daysInMonth(year, month)
        val sb = StringBuilder(8192)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="13" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="12" customWidth="1"/>""")
        for (d in 1..daysInMonth) {
            sb.append("""<col min="${d + 2}" max="${d + 2}" width="7" customWidth="1"/>""")
        }
        sb.append("""<col min="${daysInMonth + 3}" max="${daysInMonth + 5}" width="13" customWidth="1"/>""")
        sb.append("</cols><sheetData>")

        sb.append("""<row r="1" ht="24" customHeight="1">""")
        sb.append(stringCell(0, 1, "Model", style = 1))
        sb.append(stringCell(1, 1, "Kode_Bin", style = 1))
        for (d in 1..daysInMonth) {
            sb.append(stringCell(d + 1, 1, d.toString().padStart(2, '0'), style = 2))
        }
        sb.append(stringCell(daysInMonth + 2, 1, "Total_Aktual", style = 1))
        sb.append(stringCell(daysInMonth + 3, 1, "Total_Target", style = 1))
        sb.append(stringCell(daysInMonth + 4, 1, "Realisasi_%", style = 1))
        sb.append("</row>")

        metrics.matrixRows.forEachIndexed { idx, row ->
            val r = idx + 2
            sb.append("""<row r="$r">""")
            sb.append(stringCell(0, r, row.model.displayName, style = 0))
            sb.append(stringCell(1, r, row.binCode, style = 0))
            for (d in 1..daysInMonth) {
                val value = row.dailyActuals[d] ?: 0.0
                sb.append(numCell(d + 1, r, value, style = 3))
            }
            sb.append(numCell(daysInMonth + 2, r, row.totalActualM3, style = 8))
            sb.append(numCell(daysInMonth + 3, r, row.totalTargetM3, style = 3))
            sb.append(numCell(daysInMonth + 4, r, row.realizationPercent, style = 4))
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun parseXlsxStream(inputStream: InputStream): ExcelImportResult {
        var sharedStringsBytes: ByteArray? = null
        var sheet1Bytes: ByteArray? = null
        var fallbackSheetBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase(Locale.US)
                when {
                    name == "xl/sharedstrings.xml" -> sharedStringsBytes = readAllBytes(zip)
                    name == "xl/worksheets/sheet1.xml" -> sheet1Bytes = readAllBytes(zip)
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") && fallbackSheetBytes == null -> {
                        fallbackSheetBytes = readAllBytes(zip)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val targetSheet = sheet1Bytes ?: fallbackSheetBytes
            ?: error("File .xlsx tidak memiliki lembar kerja (worksheet) yang valid.")

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it) } ?: emptyList()
        val rows = parseWorksheetRows(targetSheet, sharedStrings)
        if (rows.isEmpty()) return ExcelImportResult(emptyList(), 0)

        // Identify column indices from header row (flexible matching)
        val headerRow = rows.first()
        val colMap = mutableMapOf<String, Int>()
        headerRow.forEach { (colIdx, raw) ->
            val key = raw.trim().lowercase(Locale.US)
            when {
                key.contains("tanggal") || key == "date" -> colMap["date"] = colIdx
                key == "model" || key.contains("spr") -> colMap["model"] = colIdx
                key.contains("no_bin") || key == "bin" || key.contains("nomor") -> colMap["bin"] = colIdx
                key.contains("kode_bin") || key.contains("kode") -> colMap["binCode"] = colIdx
                key.contains("shift") -> colMap["shift"] = colIdx
                key.contains("target") && !key.contains("status") -> colMap["target"] = colIdx
                key.contains("aktual") || key.contains("actual") -> colMap["actual"] = colIdx
                key.contains("alarm") -> colMap["alarm"] = colIdx
                key.contains("operator") -> colMap["operator"] = colIdx
                key.contains("catatan") || key.contains("note") -> colMap["notes"] = colIdx
            }
        }

        // Fall back to standard GasSheet column order if headers don't match explicitly
        val dateCol = colMap["date"] ?: 0
        val modelCol = colMap["model"] ?: 3
        val binCol = colMap["bin"] ?: 4
        val binCodeCol = colMap["binCode"] ?: 5
        val shiftCol = colMap["shift"] ?: 6
        val targetCol = colMap["target"] ?: 7
        val actualCol = colMap["actual"] ?: 8
        val alarmCol = colMap["alarm"] ?: 11
        val operatorCol = colMap["operator"] ?: 13
        val notesCol = colMap["notes"] ?: 14

        val imported = mutableListOf<GasEntryEntity>()
        var skipped = 0

        for (i in 1 until rows.size) {
            val row = rows[i]
            val parsedDate = parseFlexibleDate(row[dateCol].orEmpty())
            val actualValue = parseFlexibleDouble(row[actualCol])
            val targetValue = parseFlexibleDouble(row[targetCol])

            if (parsedDate == null || (actualValue == null && targetValue == null)) {
                skipped++
                continue
            }

            val rawModel = row[modelCol].orEmpty().ifBlank { row[binCodeCol].orEmpty() }
            val model = SprModel.fromName(rawModel)

            val binNum = parseFlexibleInt(row[binCol])
                ?: extractTrailingDigits(row[binCodeCol].orEmpty())
                ?: 1
            val safeBin = binNum.coerceIn(1, model.binCount)
            val shift = WorkShift.fromLabel(row[shiftCol].orEmpty())
            val finalTarget = (targetValue ?: model.defaultTargetM3).coerceAtLeast(0.0)
            val finalActual = (actualValue ?: 0.0).coerceAtLeast(0.0)
            val finalAlarm = (parseFlexibleDouble(row[alarmCol]) ?: 0.0).coerceIn(0.0, 100.0)

            imported.add(
                GasEntryEntity(
                    dateIso = parsedDate.first,
                    year = parsedDate.second,
                    month = parsedDate.third,
                    dayOfMonth = parsedDate.fourth,
                    modelName = model.displayName,
                    binNumber = safeBin,
                    shiftName = shift.label,
                    targetGasM3 = finalTarget,
                    actualGasM3 = finalActual,
                    alarmPercent = finalAlarm,
                    operatorName = row[operatorCol].orEmpty().trim(),
                    notes = row[notesCol].orEmpty().trim(),
                ),
            )
        }

        return ExcelImportResult(entries = imported, skippedRows = skipped)
    }

    private fun readAllBytes(stream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(4096)
        var n: Int
        while (stream.read(data).also { n = it } != -1) {
            buffer.write(data, 0, n)
        }
        return buffer.toByteArray()
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var inSi = false
        var current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        current = StringBuilder()
                    } else if (inSi && parser.name == "t") {
                        current.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si" && inSi) {
                        strings.add(current.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorksheetRows(
        xmlBytes: ByteArray,
        sharedStrings: List<String>,
    ): List<Map<Int, String>> {
        val rows = mutableListOf<Map<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentRow: MutableMap<Int, String>? = null
        var currentColIdx = 0
        var currentCellType: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            currentColIdx = if (!ref.isNullOrBlank()) {
                                columnLettersToIndex(ref.takeWhile { it.isLetter() })
                            } else {
                                currentRow?.size ?: 0
                            }
                            currentCellType = parser.getAttributeValue(null, "t")
                        }
                        "v" -> {
                            val raw = parser.nextText()
                            val value = if (currentCellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            } else {
                                raw
                            }
                            currentRow?.put(currentColIdx, value)
                        }
                        "t" -> {
                            if (currentCellType == "inlineStr") {
                                currentRow?.put(currentColIdx, parser.nextText())
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "row" && currentRow != null && currentRow.isNotEmpty()) {
                        rows.add(currentRow)
                        currentRow = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private data class ParsedDateQuad(
        val first: String,
        val second: Int,
        val third: Int,
        val fourth: Int,
    )

    private fun parseFlexibleDate(raw: String): ParsedDateQuad? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        // Handle ISO YYYY-MM-DD
        val isoRegex = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
        isoRegex.find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: return null
            val d = m.groupValues[3].toIntOrNull()?.coerceIn(1, 31) ?: return null
            return ParsedDateQuad("%04d-%02d-%02d".format(y, mo, d), y, mo, d)
        }

        // Handle DD/MM/YYYY or DD-MM-YYYY
        val dmyRegex = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})""")
        dmyRegex.find(text)?.let { m ->
            val d = m.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: return null
            val mo = m.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: return null
            val y = m.groupValues[3].toIntOrNull() ?: return null
            return ParsedDateQuad("%04d-%02d-%02d".format(y, mo, d), y, mo, d)
        }

        // Handle Excel serial date numbers (e.g., 46351 -> 2026-11-25)
        val serial = text.toDoubleOrNull()?.toInt()
        if (serial != null && serial in 30000..70000) {
            val cal = Calendar.getInstance().apply {
                clear()
                set(1899, Calendar.DECEMBER, 30)
                add(Calendar.DAY_OF_YEAR, serial)
            }
            val y = cal.get(Calendar.YEAR)
            val mo = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            return ParsedDateQuad("%04d-%02d-%02d".format(y, mo, d), y, mo, d)
        }

        return null
    }

    private fun parseFlexibleDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace("%", "").replace(",", ".")
        return cleaned.toDoubleOrNull()
    }

    private fun parseFlexibleInt(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().toDoubleOrNull()?.toInt()
    }

    private fun extractTrailingDigits(raw: String): Int? =
        Regex("""(\d+)$""").find(raw.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun stringCell(colIdx: Int, row: Int, value: String, style: Int): String {
        val ref = "${indexToColumnLetters(colIdx)}$row"
        return """<c r="$ref" t="inlineStr" s="$style"><is><t>${escapeXml(value)}</t></is></c>"""
    }

    private fun numCell(colIdx: Int, row: Int, value: Double, style: Int): String {
        val ref = "${indexToColumnLetters(colIdx)}$row"
        val formatted = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        return """<c r="$ref" s="$style"><v>$formatted</v></c>"""
    }

    private fun formulaNumCell(
        colIdx: Int,
        row: Int,
        formula: String,
        cachedValue: Double,
        style: Int,
    ): String {
        val ref = "${indexToColumnLetters(colIdx)}$row"
        val formatted = String.format(Locale.US, "%.2f", cachedValue).trimEnd('0').trimEnd('.')
        return """<c r="$ref" s="$style"><f>${escapeXml(formula)}</f><v>$formatted</v></c>"""
    }

    private fun indexToColumnLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun columnLettersToIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase(Locale.US)) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    companion object {
        fun formatTimestamp(millis: Long): String {
            if (millis <= 0L) return "Belum pernah disinkronkan"
            val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            return fmt.format(Date(millis))
        }
    }
}
