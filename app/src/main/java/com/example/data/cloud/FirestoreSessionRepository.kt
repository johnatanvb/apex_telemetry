package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.telemetry.model.CompletedLapSummary
import com.example.telemetry.model.LiveTelemetryState
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class FirestoreSessionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário precisa estar autenticado com Google.")

    fun observeSessions(userId: String): Flow<List<TelemetrySessionDoc>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TelemetrySessionDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSessionLaps(userId: String, sessionId: String): Flow<List<SavedLapDoc>> = flow {
        val path = "users/$userId/sessions/$sessionId/laps"
        emitAll(
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("laps")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedLapDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.lapNumber }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveLiveSession(
        state: LiveTelemetryState,
        notes: String = "",
    ): Result<String> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/sessions"
        return try {
            val uid = requireUserId()
            val laps = state.completedLaps
            val sessionRef = db.collection("users").document(uid).collection("sessions").document()
            val sessionId = sessionRef.id
            path = sessionRef.path

            val validLaps = laps.filter { it.lapTimeMs >= 1000L }
            val bestLapMs = validLaps.minOfOrNull { it.lapTimeMs }
                ?: state.sessionMeta.playerBestLapMs.coerceIn(0L, 3_600_000L)
            val avgLapMs = if (validLaps.isNotEmpty()) {
                validLaps.map { it.lapTimeMs }.average().toLong().coerceIn(0L, 3_600_000L)
            } else {
                bestLapMs
            }
            val topSpeed = maxOf(
                validLaps.maxOfOrNull { it.topSpeedKmh } ?: 0,
                state.traceBuffer.maxOfOrNull { it.speedKmh } ?: 0,
                state.currentSample.speedKmh,
            ).toLong().coerceIn(0L, 450L)

            val consistencyPct = computeConsistencyScore(validLaps)
            val playerEntry = state.gridEntries.firstOrNull { it.isPlayer }
            val finishPos = (playerEntry?.position ?: 1).toLong().coerceIn(1L, 22L)
            val pitStops = (playerEntry?.pitStops ?: 0).toLong().coerceIn(0L, 20L)
            val compound = (playerEntry?.tyreCompound?.code ?: "M").takeIf {
                it in setOf("S", "M", "H", "I", "W")
            } ?: "M"

            val sanitizedTrack = state.sessionMeta.trackName.ifBlank { "Interlagos" }.take(80)
            val sanitizedType = state.sessionMeta.sessionType.ifBlank { "Corrida" }.take(40)
            val sanitizedWeather = state.sessionMeta.weather.ifBlank { "Seco" }.take(40)
            val sanitizedNotes = notes.trim().take(300)
            val sourceName = if (state.sourceMode.name == "UDP") "UDP" else "SIMULATOR"

            val sessionPayload = mapOf(
                "userId" to uid,
                "trackName" to sanitizedTrack,
                "sessionType" to sanitizedType,
                "weather" to sanitizedWeather,
                "sourceMode" to sourceName,
                "bestLapMs" to bestLapMs,
                "avgLapMs" to avgLapMs,
                "topSpeedKmh" to topSpeed,
                "consistencyPct" to consistencyPct,
                "totalLaps" to validLaps.size.coerceIn(0, 200).toLong(),
                "finishPosition" to finishPos,
                "pitStops" to pitStops,
                "primaryCompound" to compound,
                "trackTempC" to state.sessionMeta.trackTempC.toLong().coerceIn(-20L, 90L),
                "notes" to sanitizedNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            val batch = db.batch()
            batch.set(sessionRef, sessionPayload)

            validLaps.take(50).forEach { lap ->
                val lapRef = sessionRef.collection("laps").document("lap_${lap.lapNumber}")
                val traceList = lap.speedTrace.take(60).map { it.toLong().coerceIn(0L, 450L) }
                val lapPayload = mapOf(
                    "userId" to uid,
                    "sessionId" to sessionId,
                    "lapNumber" to lap.lapNumber.toLong().coerceIn(1L, 200L),
                    "lapTimeMs" to lap.lapTimeMs.coerceIn(1000L, 3_600_000L),
                    "sector1Ms" to lap.sector1Ms.coerceIn(0L, 1_200_000L),
                    "sector2Ms" to lap.sector2Ms.coerceIn(0L, 1_200_000L),
                    "sector3Ms" to lap.sector3Ms.coerceIn(0L, 1_200_000L),
                    "tyreCompound" to lap.tyreCompound.code,
                    "topSpeedKmh" to lap.topSpeedKmh.toLong().coerceIn(0L, 450L),
                    "avgThrottlePct" to lap.avgThrottlePct.toLong().coerceIn(0L, 100L),
                    "maxTyreWearPct" to lap.maxTyreWearPct.toLong().coerceIn(0L, 100L),
                    "speedTrace" to traceList,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                batch.set(lapRef, lapPayload)
            }

            batch.commit().await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionNotes(
        userId: String,
        sessionId: String,
        notes: String,
    ): Result<Unit> {
        val path = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Usuário autenticado divergente." }
            require(sessionId.isNotBlank()) { "Session ID obrigatório." }
            db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .update(
                    mapOf(
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(
        userId: String,
        sessionId: String,
    ): Result<Unit> {
        val path = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Usuário autenticado divergente." }
            require(sessionId.isNotBlank()) { "Session ID obrigatório." }
            val sessionRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
            val lapsSnap = sessionRef.collection("laps").get().await()
            val batch = db.batch()
            lapsSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(sessionRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun computeConsistencyScore(laps: List<CompletedLapSummary>): Double {
        if (laps.size < 2) return 98.0
        val times = laps.map { it.lapTimeMs.toDouble() }
        val mean = times.average()
        if (mean <= 0.0) return 95.0
        val variance = times.sumOf { (it - mean).pow(2) } / times.size
        val stdDev = sqrt(variance)
        val cvPct = (stdDev / mean) * 100.0
        val rawScore = (100.0 - cvPct * 4.5).coerceIn(50.0, 99.9)
        return (rawScore * 10.0).roundToInt() / 10.0
    }
}
