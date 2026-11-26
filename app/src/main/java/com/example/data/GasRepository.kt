package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.roundToInt

data class DailyUsagePoint(
    val dayOfMonth: Int,
    val dateIso: String,
    val targetM3: Double,
    val actualM3: Double,
    val alarmPercent: Double,
    val entryCount: Int,
) {
    val realizationPercent: Double
        get() = if (targetM3 > 0.0) (actualM3 / targetM3) * 100.0 else 0.0
}

data class ModelRealization(
    val model: SprModel,
    val targetM3: Double,
    val actualM3: Double,
    val realizationPercent: Double,
    val unitsWithinTarget: Int,
    val activeUnits: Int,
)

data class BinMatrixRow(
    val model: SprModel,
    val binNumber: Int,
    val binCode: String,
    val dailyActuals: Map<Int, Double>,
    val dailyTargets: Map<Int, Double>,
    val totalActualM3: Double,
    val totalTargetM3: Double,
) {
    val realizationPercent: Double
        get() = if (totalTargetM3 > 0.0) (totalActualM3 / totalTargetM3) * 100.0 else 0.0
}

data class DashboardMetrics(
    val totalTargetM3: Double,
    val totalActualM3: Double,
    val realizationPercent: Double,
    val avgAlarmPercent: Double,
    val unitsWithinTarget: Int,
    val totalUnitsInScope: Int,
    val activeUnitsCount: Int,
    val dailyPoints: List<DailyUsagePoint>,
    val modelRealizations: List<ModelRealization>,
    val matrixRows: List<BinMatrixRow>,
    val maxDailyCellM3: Double,
    val filteredEntries: List<GasEntryEntity>,
)

class GasRepository(private val dao: GasEntryDao) {
    val allEntries: Flow<List<GasEntryEntity>> = dao.observeAllEntries()

    suspend fun getAllSnapshot(): List<GasEntryEntity> = dao.getAllEntriesSnapshot()

    suspend fun upsertEntry(entry: GasEntryEntity): Long {
        if (entry.id != 0L) {
            dao.updateEntry(entry.copy(updatedAtMillis = System.currentTimeMillis()))
            return entry.id
        }
        val existing = dao.findExistingSlot(
            dateIso = entry.dateIso,
            modelName = entry.modelName,
            binNumber = entry.binNumber,
            shiftName = entry.shiftName,
        )
        return if (existing != null) {
            dao.updateEntry(
                entry.copy(
                    id = existing.id,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            existing.id
        } else {
            dao.insertEntry(entry.copy(updatedAtMillis = System.currentTimeMillis()))
        }
    }

    suspend fun upsertBatch(entries: List<GasEntryEntity>) {
        entries.forEach { upsertEntry(it) }
    }

    suspend fun deleteEntry(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.deleteAll()

    suspend fun insertSampleNov2026Dataset(): Int {
        val generated = buildNov2026SampleDataset()
        dao.insertAll(generated)
        return generated.size
    }

    fun computeDashboard(
        allEntries: List<GasEntryEntity>,
        year: Int,
        month: Int,
        modelFilter: SprModel?,
        shiftFilter: WorkShift?,
    ): DashboardMetrics {
        val daysInMonth = MonthCatalog.daysInMonth(year, month)
        val filtered = allEntries.filter { entry ->
            entry.year == year &&
                entry.month == month &&
                (modelFilter == null || entry.sprModel == modelFilter) &&
                (shiftFilter == null || WorkShift.fromLabel(entry.shiftName) == shiftFilter)
        }

        val totalTarget = filtered.sumOf { it.targetGasM3 }
        val totalActual = filtered.sumOf { it.actualGasM3 }
        val realization = if (totalTarget > 0.0) (totalActual / totalTarget) * 100.0 else 0.0
        val avgAlarm = if (filtered.isNotEmpty()) filtered.map { it.alarmPercent }.average() else 0.0

        val unitScope = when (modelFilter) {
            SprModel.PENNAR -> SprModel.PENNAR.binCount
            SprModel.PEANUT -> SprModel.PEANUT.binCount
            null -> SprModel.TOTAL_UNITS
        }

        val byBinKey = filtered.groupBy { "${it.sprModel.name}_${it.binNumber}" }
        val unitsWithin = byBinKey.values.count { binEntries ->
            binEntries.sumOf { it.actualGasM3 } <= binEntries.sumOf { it.targetGasM3 } + 0.001
        }

        val byDay = filtered.groupBy { it.dayOfMonth }
        val dailyPoints = (1..daysInMonth).map { day ->
            val dayList = byDay[day].orEmpty()
            val iso = "%04d-%02d-%02d".format(year, month, day)
            DailyUsagePoint(
                dayOfMonth = day,
                dateIso = iso,
                targetM3 = dayList.sumOf { it.targetGasM3 },
                actualM3 = dayList.sumOf { it.actualGasM3 },
                alarmPercent = if (dayList.isNotEmpty()) dayList.map { it.alarmPercent }.average() else 0.0,
                entryCount = dayList.size,
            )
        }

        val modelRealizations = SprModel.entries.map { model ->
            val modelEntries = allEntries.filter { entry ->
                entry.year == year &&
                    entry.month == month &&
                    entry.sprModel == model &&
                    (shiftFilter == null || WorkShift.fromLabel(entry.shiftName) == shiftFilter)
            }
            val mTarget = modelEntries.sumOf { it.targetGasM3 }
            val mActual = modelEntries.sumOf { it.actualGasM3 }
            val mReal = if (mTarget > 0.0) (mActual / mTarget) * 100.0 else 0.0
            val mBins = modelEntries.groupBy { it.binNumber }
            val mWithin = mBins.values.count { list ->
                list.sumOf { it.actualGasM3 } <= list.sumOf { it.targetGasM3 } + 0.001
            }
            ModelRealization(
                model = model,
                targetM3 = mTarget,
                actualM3 = mActual,
                realizationPercent = mReal,
                unitsWithinTarget = mWithin,
                activeUnits = mBins.size,
            )
        }

        val modelsForMatrix = if (modelFilter != null) listOf(modelFilter) else SprModel.entries
        var maxCell = 0.0
        val matrixRows = buildList {
            modelsForMatrix.forEach { model ->
                for (bin in 1..model.binCount) {
                    val binEntries = byBinKey["${model.name}_$bin"].orEmpty()
                    val dailyAct = binEntries.groupBy { it.dayOfMonth }
                        .mapValues { (_, list) -> list.sumOf { it.actualGasM3 } }
                    val dailyTgt = binEntries.groupBy { it.dayOfMonth }
                        .mapValues { (_, list) -> list.sumOf { it.targetGasM3 } }
                    dailyAct.values.forEach { value ->
                        if (value > maxCell) maxCell = value
                    }
                    add(
                        BinMatrixRow(
                            model = model,
                            binNumber = bin,
                            binCode = model.formatBinCode(bin),
                            dailyActuals = dailyAct,
                            dailyTargets = dailyTgt,
                            totalActualM3 = binEntries.sumOf { it.actualGasM3 },
                            totalTargetM3 = binEntries.sumOf { it.targetGasM3 },
                        ),
                    )
                }
            }
        }

        return DashboardMetrics(
            totalTargetM3 = totalTarget,
            totalActualM3 = totalActual,
            realizationPercent = realization,
            avgAlarmPercent = avgAlarm,
            unitsWithinTarget = unitsWithin,
            totalUnitsInScope = unitScope,
            activeUnitsCount = byBinKey.size,
            dailyPoints = dailyPoints,
            modelRealizations = modelRealizations,
            matrixRows = matrixRows,
            maxDailyCellM3 = maxCell.coerceAtLeast(1.0),
            filteredEntries = filtered,
        )
    }

    private fun buildNov2026SampleDataset(): List<GasEntryEntity> {
        val entries = mutableListOf<GasEntryEntity>()
        val year = 2026
        val month = 11
        val operators = listOf("Budi Santoso", "Agus Pratama", "Rina Kusuma")
        val shifts = WorkShift.entries
        // Deterministic realistic dataset aligned with SPR Pennar (39 bins) & SPR Peanut (12 bins)
        // Daily target per bin ~13.33 m3 -> 51 bins * 30 days = ~20,400 m3 target, ~81% realization, ~2.9% alarm
        val dailyAlarmBase = doubleArrayOf(
            2.5, 3.0, 2.0, 3.5, 4.4, 3.0, 2.2, 3.8, 4.6, 3.2,
            2.6, 3.9, 2.8, 2.1, 3.4, 4.1, 3.0, 2.4, 3.6, 2.9,
            2.3, 2.8, 3.1, 2.5, 2.7, 3.3, 2.6, 2.2, 2.9, 2.4,
        )

        val overTargetBins = setOf("PENNAR_7", "PENNAR_18", "PENNAR_29", "PENNAR_34", "PEANUT_5")

        for (day in 1..30) {
            val dateIso = "%04d-%02d-%02d".format(year, month, day)
            val dayFactor = 0.74 + ((day * 7) % 19) * 0.011
            val shift = shifts[(day - 1) % shifts.size]
            val operator = operators[(day - 1) % operators.size]

            for (model in SprModel.entries) {
                for (bin in 1..model.binCount) {
                    val target = if (model == SprModel.PENNAR) 13.5 else 12.8
                    val isOver = "${model.name}_$bin" in overTargetBins
                    val binSeed = abs((bin * 31 + day * 17) % 23) / 100.0
                    val ratio = if (isOver) {
                        1.03 + (binSeed * 0.35)
                    } else {
                        (dayFactor - 0.05 + binSeed * 0.65).coerceIn(0.62, 0.96)
                    }
                    val actual = ((target * ratio) * 10.0).roundToInt() / 10.0
                    val alarm = ((dailyAlarmBase[day - 1] + (bin % 5 - 2) * 0.18).coerceAtLeast(0.5) * 10.0)
                        .roundToInt() / 10.0

                    entries.add(
                        GasEntryEntity(
                            dateIso = dateIso,
                            year = year,
                            month = month,
                            dayOfMonth = day,
                            modelName = model.displayName,
                            binNumber = bin,
                            shiftName = shift.label,
                            targetGasM3 = target,
                            actualGasM3 = actual,
                            alarmPercent = alarm,
                            operatorName = operator,
                            notes = if (isOver) "Periksa regulator tekanan bin" else "",
                        ),
                    )
                }
            }
        }
        return entries
    }
}
