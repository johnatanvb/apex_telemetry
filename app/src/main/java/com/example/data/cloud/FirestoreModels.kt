package com.example.data.cloud

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class TelemetrySessionDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackName: String = "",
    val sessionType: String = "",
    val weather: String = "",
    val sourceMode: String = "SIMULATOR",
    val bestLapMs: Long = 0L,
    val avgLapMs: Long = 0L,
    val topSpeedKmh: Long = 0L,
    val consistencyPct: Double = 0.0,
    val totalLaps: Long = 0L,
    val finishPosition: Long = 1L,
    val pitStops: Long = 0L,
    val primaryCompound: String = "M",
    val trackTempC: Long = 32L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SavedLapDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val lapNumber: Long = 1L,
    val lapTimeMs: Long = 0L,
    val sector1Ms: Long = 0L,
    val sector2Ms: Long = 0L,
    val sector3Ms: Long = 0L,
    val tyreCompound: String = "M",
    val topSpeedKmh: Long = 0L,
    val avgThrottlePct: Long = 0L,
    val maxTyreWearPct: Long = 0L,
    val speedTrace: List<Long> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)
