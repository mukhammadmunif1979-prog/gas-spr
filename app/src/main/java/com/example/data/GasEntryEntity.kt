package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gas_entries",
    indices = [
        Index(value = ["year", "month"]),
        Index(value = ["dateIso", "modelName", "binNumber", "shiftName"]),
    ],
)
data class GasEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateIso: String,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val modelName: String,
    val binNumber: Int,
    val shiftName: String,
    val targetGasM3: Double,
    val actualGasM3: Double,
    val alarmPercent: Double,
    val operatorName: String = "",
    val notes: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val sprModel: SprModel
        get() = SprModel.fromName(modelName)

    val binCode: String
        get() = sprModel.formatBinCode(binNumber)

    val realizationPercent: Double
        get() = if (targetGasM3 > 0.0) (actualGasM3 / targetGasM3) * 100.0 else 0.0

    val varianceM3: Double
        get() = actualGasM3 - targetGasM3

    val isWithinTarget: Boolean
        get() = actualGasM3 <= targetGasM3
}
