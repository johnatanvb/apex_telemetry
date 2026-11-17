package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.FirestoreSessionRepository
import com.example.data.cloud.SavedLapDoc
import com.example.data.cloud.TelemetrySessionDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModel(
    private val repository: FirestoreSessionRepository,
    private val currentUserId: String,
) : ViewModel() {

    val sessionsState: StateFlow<UiState<List<TelemetrySessionDoc>>> =
        repository.observeSessions(currentUserId)
            .map<List<TelemetrySessionDoc>, UiState<List<TelemetrySessionDoc>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Erro ao observar sessões no Firestore", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Falha ao carregar histórico na nuvem."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading,
            )

    private val _selectedSessionAId = MutableStateFlow<String?>(null)
    val selectedSessionAId: StateFlow<String?> = _selectedSessionAId.asStateFlow()

    private val _selectedSessionBId = MutableStateFlow<String?>(null)
    val selectedSessionBId: StateFlow<String?> = _selectedSessionBId.asStateFlow()

    val lapsAState: StateFlow<List<SavedLapDoc>> = _selectedSessionAId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else repository.observeSessionLaps(currentUserId, id)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Erro ao observar voltas da sessão A", firestoreError ?: error)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val lapsBState: StateFlow<List<SavedLapDoc>> = _selectedSessionBId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else repository.observeSessionLaps(currentUserId, id)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Erro ao observar voltas da sessão B", firestoreError ?: error)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun selectSessionA(sessionId: String?) {
        _selectedSessionAId.value = sessionId
    }

    fun selectSessionB(sessionId: String?) {
        _selectedSessionBId.value = sessionId
    }

    fun updateSessionNotes(sessionId: String, notes: String) {
        viewModelScope.launch {
            val res = repository.updateSessionNotes(currentUserId, sessionId, notes)
            _actionMessage.value = res.fold(
                onSuccess = { "Anotações da corrida atualizadas." },
                onFailure = { it.localizedMessage ?: "Não foi possível atualizar anotações." }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_selectedSessionAId.value == sessionId) _selectedSessionAId.value = null
            if (_selectedSessionBId.value == sessionId) _selectedSessionBId.value = null
            val res = repository.deleteSession(currentUserId, sessionId)
            _actionMessage.value = res.fold(
                onSuccess = { "Sessão removida da nuvem." },
                onFailure = { it.localizedMessage ?: "Falha ao remover sessão." }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "SessionsVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
