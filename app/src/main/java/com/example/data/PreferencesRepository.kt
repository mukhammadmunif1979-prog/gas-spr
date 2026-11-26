package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gasSheetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gassheet_preferences",
)

data class WorkbookPreferences(
    val linkedFileUri: String = "",
    val linkedFileName: String = "",
    val autoSyncEnabled: Boolean = true,
    val lastSyncedMillis: Long = 0L,
    val defaultPennarTargetM3: Double = SprModel.PENNAR.defaultTargetM3,
    val defaultPeanutTargetM3: Double = SprModel.PEANUT.defaultTargetM3,
    val lastOperatorName: String = "",
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LINKED_FILE_URI = stringPreferencesKey("linked_file_uri")
        val LINKED_FILE_NAME = stringPreferencesKey("linked_file_name")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNCED = longPreferencesKey("last_synced_millis")
        val DEFAULT_PENNAR_TARGET = doublePreferencesKey("default_pennar_target_m3")
        val DEFAULT_PEANUT_TARGET = doublePreferencesKey("default_peanut_target_m3")
        val LAST_OPERATOR = stringPreferencesKey("last_operator_name")
    }

    val preferencesFlow: Flow<WorkbookPreferences> = context.gasSheetDataStore.data.map { prefs ->
        WorkbookPreferences(
            linkedFileUri = prefs[Keys.LINKED_FILE_URI] ?: "",
            linkedFileName = prefs[Keys.LINKED_FILE_NAME] ?: "",
            autoSyncEnabled = prefs[Keys.AUTO_SYNC] ?: true,
            lastSyncedMillis = prefs[Keys.LAST_SYNCED] ?: 0L,
            defaultPennarTargetM3 = prefs[Keys.DEFAULT_PENNAR_TARGET] ?: SprModel.PENNAR.defaultTargetM3,
            defaultPeanutTargetM3 = prefs[Keys.DEFAULT_PEANUT_TARGET] ?: SprModel.PEANUT.defaultTargetM3,
            lastOperatorName = prefs[Keys.LAST_OPERATOR] ?: "",
        )
    }

    suspend fun setLinkedFile(uri: String, fileName: String) {
        context.gasSheetDataStore.edit { prefs ->
            prefs[Keys.LINKED_FILE_URI] = uri
            prefs[Keys.LINKED_FILE_NAME] = fileName
        }
    }

    suspend fun clearLinkedFile() {
        context.gasSheetDataStore.edit { prefs ->
            prefs.remove(Keys.LINKED_FILE_URI)
            prefs.remove(Keys.LINKED_FILE_NAME)
            prefs.remove(Keys.LAST_SYNCED)
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.gasSheetDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC] = enabled
        }
    }

    suspend fun markSynced(timestampMillis: Long = System.currentTimeMillis()) {
        context.gasSheetDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED] = timestampMillis
        }
    }

    suspend fun updateDefaults(
        pennarTargetM3: Double,
        peanutTargetM3: Double,
        operatorName: String,
    ) {
        context.gasSheetDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PENNAR_TARGET] = pennarTargetM3
            prefs[Keys.DEFAULT_PEANUT_TARGET] = peanutTargetM3
            prefs[Keys.LAST_OPERATOR] = operatorName
        }
    }

    suspend fun updateLastOperator(operatorName: String) {
        if (operatorName.isBlank()) return
        context.gasSheetDataStore.edit { prefs ->
            prefs[Keys.LAST_OPERATOR] = operatorName.trim()
        }
    }
}
