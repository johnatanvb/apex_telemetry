package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.GameTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.telemetryDataStore by preferencesDataStore(name = "apex_telemetry_prefs")

class TelemetryPreferencesRepository(private val context: Context) {

    private object Keys {
        val UDP_PORT = intPreferencesKey("udp_port")
        val SELECTED_GAME = stringPreferencesKey("selected_game")
        val TARGET_HOST_IP = stringPreferencesKey("target_host_ip")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val ENGINE_WARN_C = intPreferencesKey("engine_warn_c")
        val ENGINE_CRIT_C = intPreferencesKey("engine_crit_c")
        val TYRE_WEAR_WARN_PCT = intPreferencesKey("tyre_wear_warn_pct")
        val TYRE_WEAR_CRIT_PCT = intPreferencesKey("tyre_wear_crit_pct")
        val TYRE_TEMP_WARN_C = intPreferencesKey("tyre_temp_warn_c")
        val BRAKE_TEMP_WARN_C = intPreferencesKey("brake_temp_warn_c")
        val FUEL_WARN_LAPS = floatPreferencesKey("fuel_warn_laps")
        val PIT_LOSS_SEC = floatPreferencesKey("pit_loss_sec")
        val STRATEGY_ALERTS = booleanPreferencesKey("strategy_alerts")
    }

    val udpPortFlow: Flow<Int> = context.telemetryDataStore.data.map { prefs ->
        prefs[Keys.UDP_PORT] ?: 20777
    }

    val selectedGameFlow: Flow<GameTitle> = context.telemetryDataStore.data.map { prefs ->
        val raw = prefs[Keys.SELECTED_GAME]
        if (raw == null) GameTitle.AUTO else GameTitle.entries.firstOrNull { it.name == raw } ?: GameTitle.AUTO
    }

    val targetHostIpFlow: Flow<String> = context.telemetryDataStore.data.map { prefs ->
        prefs[Keys.TARGET_HOST_IP] ?: ""
    }

    val keepScreenOnFlow: Flow<Boolean> = context.telemetryDataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    val thresholdsFlow: Flow<AlertThresholds> = context.telemetryDataStore.data.map { prefs ->
        AlertThresholds(
            engineTempWarnC = prefs[Keys.ENGINE_WARN_C] ?: 110,
            engineTempCritC = prefs[Keys.ENGINE_CRIT_C] ?: 117,
            tyreWearWarnPct = prefs[Keys.TYRE_WEAR_WARN_PCT] ?: 55,
            tyreWearCritPct = prefs[Keys.TYRE_WEAR_CRIT_PCT] ?: 75,
            tyreTempWarnC = prefs[Keys.TYRE_TEMP_WARN_C] ?: 104,
            brakeTempWarnC = prefs[Keys.BRAKE_TEMP_WARN_C] ?: 920,
            fuelWarnLaps = prefs[Keys.FUEL_WARN_LAPS] ?: 1.6f,
            pitLossSeconds = prefs[Keys.PIT_LOSS_SEC] ?: 22.0f,
            enableStrategyAlerts = prefs[Keys.STRATEGY_ALERTS] ?: true,
        )
    }

    suspend fun saveUdpPort(port: Int) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.UDP_PORT] = port.coerceIn(1024, 65535)
        }
    }

    suspend fun saveSelectedGame(game: GameTitle, updateDefaultPort: Boolean = true) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.SELECTED_GAME] = game.name
            if (updateDefaultPort) {
                prefs[Keys.UDP_PORT] = game.defaultPort
            }
        }
    }

    suspend fun saveTargetHostIp(hostIp: String) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.TARGET_HOST_IP] = hostIp.trim().take(64)
        }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun saveThresholds(thresholds: AlertThresholds) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.ENGINE_WARN_C] = thresholds.engineTempWarnC
            prefs[Keys.ENGINE_CRIT_C] = thresholds.engineTempCritC
            prefs[Keys.TYRE_WEAR_WARN_PCT] = thresholds.tyreWearWarnPct
            prefs[Keys.TYRE_WEAR_CRIT_PCT] = thresholds.tyreWearCritPct
            prefs[Keys.TYRE_TEMP_WARN_C] = thresholds.tyreTempWarnC
            prefs[Keys.BRAKE_TEMP_WARN_C] = thresholds.brakeTempWarnC
            prefs[Keys.FUEL_WARN_LAPS] = thresholds.fuelWarnLaps
            prefs[Keys.PIT_LOSS_SEC] = thresholds.pitLossSeconds
            prefs[Keys.STRATEGY_ALERTS] = thresholds.enableStrategyAlerts
        }
    }
}
