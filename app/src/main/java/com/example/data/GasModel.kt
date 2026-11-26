package com.example.data

enum class SprModel(
    val displayName: String,
    val shortPrefix: String,
    val binCount: Int,
    val defaultTargetM3: Double,
) {
    PENNAR(
        displayName = "SPR Pennar",
        shortPrefix = "Pennar",
        binCount = 39,
        defaultTargetM3 = 13.5,
    ),
    PEANUT(
        displayName = "SPR Peanut",
        shortPrefix = "Peanut",
        binCount = 12,
        defaultTargetM3 = 12.8,
    );

    fun formatBinCode(binNumber: Int): String =
        "$shortPrefix-${binNumber.toString().padStart(2, '0')}"

    companion object {
        const val TOTAL_UNITS: Int = 51

        fun fromName(value: String): SprModel {
            val normalized = value.trim().lowercase()
            return if (normalized.contains("peanut")) PEANUT else PENNAR
        }
    }
}

enum class WorkShift(val label: String, val shortLabel: String) {
    SHIFT_1("Shift 1", "S1"),
    SHIFT_2("Shift 2", "S2"),
    SHIFT_3("Shift 3", "S3");

    companion object {
        fun fromLabel(value: String): WorkShift {
            val normalized = value.trim().lowercase()
            return when {
                normalized.contains("2") -> SHIFT_2
                normalized.contains("3") -> SHIFT_3
                else -> SHIFT_1
            }
        }
    }
}

object MonthCatalog {
    val shortNames: List<String> = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
    )

    val fullNames: List<String> = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    fun shortName(month: Int): String = shortNames.getOrElse(month - 1) { "Nov" }

    fun fullName(month: Int): String = fullNames.getOrElse(month - 1) { "November" }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 30
    }
}
