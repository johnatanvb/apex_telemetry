package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.FirestoreSessionRepository
import com.example.data.prefs.TelemetryPreferencesRepository
import com.example.telemetry.analysis.AlertEngine
import com.example.telemetry.analysis.SetupAdvisorEngine
import com.example.telemetry.model.AlertThresholds
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import com.example.telemetry.sim.RaceSimulatorEngine
import com.example.telemetry.udp.UdpTelemetryReceiver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SaveRaceUiState {
    data object Idle : SaveRaceUiState
    data object Saving : SaveRaceUiState
    data class Saved(val sessionId: String) : SaveRaceUiState
    data class Error(val message: String) : SaveRaceUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryViewModel(
    private val sessionRepository: FirestoreSessionRepository,
    private val prefsRepository: TelemetryPreferencesRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val udpReceiver = UdpTelemetryReceiver()
    private val simulatorEngine = RaceSimulatorEngine()
    private val alertEngine = AlertEngine()
    private val setupAdvisor = SetupAdvisorEngine()

    private val _sourceMode = MutableStateFlow(TelemetrySourceMode.SIMULATOR)
    private val _simTrackId = MutableStateFlow(18)
    private val _stressTest = MutableStateFlow(false)
    private val _acknowledgedAlerts = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedGridCarIndex = MutableStateFlow<Int?>(null)

    private val _saveState = MutableStateFlow<SaveRaceUiState>(SaveRaceUiState.Idle)
    val saveState: StateFlow<SaveRaceUiState> = _saveState.asStateFlow()

    val udpPort: StateFlow<Int> = prefsRepository.udpPortFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 20777)

    val selectedGameMode: StateFlow<GameTitle> = prefsRepository.selectedGameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), GameTitle.AUTO)

    val targetHostIp: StateFlow<String> = prefsRepository.targetHostIpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val keepScreenOn: StateFlow<Boolean> = prefsRepository.keepScreenOnFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val thresholds: StateFlow<AlertThresholds> = prefsRepository.thresholdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AlertThresholds())

    val stressTestEnabled: StateFlow<Boolean> = _stressTest.asStateFlow()

    private data class StreamKey(
        val mode: TelemetrySourceMode,
        val game: GameTitle,
        val port: Int,
        val targetHostIp: String,
        val trackId: Int,
        val stressTest: Boolean,
    )

    private val rawStream = combine(
        _sourceMode,
        selectedGameMode,
        udpPort,
        targetHostIp,
        _simTrackId,
        _stressTest,
    ) { arr ->
        StreamKey(
            mode = arr[0] as TelemetrySourceMode,
            game = arr[1] as GameTitle,
            port = arr[2] as Int,
            targetHostIp = arr[3] as String,
            trackId = arr[4] as Int,
            stressTest = arr[5] as Boolean,
        )
    }.flatMapLatest { key ->
        val localIp = UdpTelemetryReceiver.resolveLocalIpv4Address()
        when (key.mode) {
            TelemetrySourceMode.UDP -> udpReceiver.observeUdpStream(
                selectedGame = key.game,
                customPort = key.port,
                targetHostIp = key.targetHostIp,
            )

            TelemetrySourceMode.SIMULATOR -> simulatorEngine.observeSimulation(
                trackId = key.trackId,
                udpPort = key.port,
                localIp = localIp,
                stressTestMode = key.stressTest,
                simulatedGame = key.game,
            )
        }
    }

    val telemetryState: StateFlow<LiveTelemetryState> = combine(
        rawStream,
        thresholds,
        _acknowledgedAlerts,
        _selectedGridCarIndex,
        keepScreenOn,
    ) { raw, thresh, acks, selectedCar, keepOn ->
        val alertResult = alertEngine.evaluate(raw, thresh, acks)
        val setupRec = setupAdvisor.analyzeAndRecommend(raw)
        raw.copy(
            activeAlerts = alertResult.alerts,
            pitWindow = alertResult.pitWindow,
            setupRecommendation = setupRec,
            selectedGridCarIndex = selectedCar,
            keepScreenOn = keepOn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LiveTelemetryState(),
    )

    fun switchSourceMode(mode: TelemetrySourceMode) {
        _sourceMode.value = mode
    }

    fun selectGameMode(game: GameTitle, updateDefaultPort: Boolean = true) {
        _acknowledgedAlerts.value = emptySet()
        viewModelScope.launch {
            prefsRepository.saveSelectedGame(game, updateDefaultPort = updateDefaultPort)
        }
    }

    fun selectSimulatorTrack(trackId: Int) {
        _simTrackId.value = trackId
    }

    fun toggleStressTest(enabled: Boolean) {
        _stressTest.value = enabled
        if (!enabled) _acknowledgedAlerts.value = emptySet()
    }

    fun acknowledgeAlert(alertId: String) {
        _acknowledgedAlerts.value = _acknowledgedAlerts.value + alertId
    }

    fun selectGridCar(carIndex: Int?) {
        _selectedGridCarIndex.value = if (_selectedGridCarIndex.value == carIndex) null else carIndex
    }

    fun updateUdpSettings(port: Int, hostIp: String) {
        viewModelScope.launch {
            prefsRepository.saveUdpPort(port)
            prefsRepository.saveTargetHostIp(hostIp)
        }
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.saveKeepScreenOn(enabled)
        }
    }

    fun updateAlertThresholds(updated: AlertThresholds) {
        viewModelScope.launch {
            prefsRepository.saveThresholds(updated)
        }
    }

    fun saveCurrentRaceToCloud(notes: String) {
        if (currentUserId.isBlank()) return
        val snapshot = telemetryState.value
        _saveState.value = SaveRaceUiState.Saving
        viewModelScope.launch {
            val result = sessionRepository.saveLiveSession(snapshot, notes)
            _saveState.value = result.fold(
                onSuccess = { id -> SaveRaceUiState.Saved(id) },
                onFailure = { err ->
                    SaveRaceUiState.Error(err.localizedMessage ?: "Falha ao salvar corrida na nuvem.")
                }
            )
        }
    }

    fun clearSaveState() {
        _saveState.value = SaveRaceUiState.Idle
    }
}
