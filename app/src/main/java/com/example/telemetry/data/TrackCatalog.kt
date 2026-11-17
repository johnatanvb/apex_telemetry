package com.example.telemetry.data

import com.example.telemetry.model.TrackPoint
import com.example.telemetry.model.TrackProfile
import kotlin.math.hypot

object TrackCatalog {

    val tracks: List<TrackProfile> = listOf(
        TrackProfile(
            trackId = 18,
            name = "Interlagos (São Paulo)",
            country = "Brasil",
            lengthMeters = 4309,
            cornersCount = 15,
            elevationChangeM = 43,
            downforceDemand = "Médio-Alto",
            straightRatioPct = 52,
            cornerProfileSummary = "S do Senna técnico, miolo de média/baixa e subida em aceleração plena",
            kerbAggression = "Alta nas zebras do S e Pinheirinho",
            tyreStressLevel = "Alto (traseiro esquerdo e dianteiro direito)",
            baseFrontWing = 28,
            baseRearWing = 26,
            pitLossSec = 21.5f,
            centerline = listOf(
                TrackPoint(0.22f, 0.85f),
                TrackPoint(0.45f, 0.82f),
                TrackPoint(0.72f, 0.78f),
                TrackPoint(0.84f, 0.72f),
                TrackPoint(0.76f, 0.64f),
                TrackPoint(0.86f, 0.56f),
                TrackPoint(0.82f, 0.34f),
                TrackPoint(0.74f, 0.18f),
                TrackPoint(0.58f, 0.16f),
                TrackPoint(0.42f, 0.28f),
                TrackPoint(0.52f, 0.42f),
                TrackPoint(0.36f, 0.50f),
                TrackPoint(0.46f, 0.62f),
                TrackPoint(0.26f, 0.66f),
                TrackPoint(0.14f, 0.52f),
                TrackPoint(0.12f, 0.70f),
            ),
        ),
        TrackProfile(
            trackId = 10,
            name = "Spa-Francorchamps",
            country = "Bélgica",
            lengthMeters = 7004,
            cornersCount = 19,
            elevationChangeM = 102,
            downforceDemand = "Médio-Baixo",
            straightRatioPct = 64,
            cornerProfileSummary = "Compressão Eau Rouge/Raidillon, retas longas e curvas rápidas no Setor 2",
            kerbAggression = "Média-Alta (Les Combes e Bus Stop)",
            tyreStressLevel = "Muito Alto (cargas laterais em Pouhon e Blanchimont)",
            baseFrontWing = 20,
            baseRearWing = 18,
            pitLossSec = 22.8f,
            centerline = listOf(
                TrackPoint(0.20f, 0.82f),
                TrackPoint(0.30f, 0.72f),
                TrackPoint(0.28f, 0.54f),
                TrackPoint(0.35f, 0.25f),
                TrackPoint(0.52f, 0.14f),
                TrackPoint(0.66f, 0.22f),
                TrackPoint(0.82f, 0.28f),
                TrackPoint(0.86f, 0.44f),
                TrackPoint(0.70f, 0.52f),
                TrackPoint(0.78f, 0.68f),
                TrackPoint(0.64f, 0.80f),
                TrackPoint(0.46f, 0.78f),
                TrackPoint(0.32f, 0.86f),
            ),
        ),
        TrackProfile(
            trackId = 6,
            name = "Silverstone",
            country = "Reino Unido",
            lengthMeters = 5891,
            cornersCount = 18,
            elevationChangeM = 18,
            downforceDemand = "Alto",
            straightRatioPct = 55,
            cornerProfileSummary = "Sequência de altíssima velocidade Copse-Maggotts-Becketts-Chapel",
            kerbAggression = "Média",
            tyreStressLevel = "Extremo (dianteiro esquerdo sob alta carga lateral)",
            baseFrontWing = 31,
            baseRearWing = 29,
            pitLossSec = 20.6f,
            centerline = listOf(
                TrackPoint(0.24f, 0.78f),
                TrackPoint(0.44f, 0.84f),
                TrackPoint(0.64f, 0.72f),
                TrackPoint(0.52f, 0.54f),
                TrackPoint(0.64f, 0.38f),
                TrackPoint(0.80f, 0.30f),
                TrackPoint(0.84f, 0.18f),
                TrackPoint(0.62f, 0.14f),
                TrackPoint(0.52f, 0.26f),
                TrackPoint(0.40f, 0.20f),
                TrackPoint(0.28f, 0.36f),
                TrackPoint(0.18f, 0.54f),
            ),
        ),
        TrackProfile(
            trackId = 11,
            name = "Monza",
            country = "Itália",
            lengthMeters = 5793,
            cornersCount = 11,
            elevationChangeM = 12,
            downforceDemand = "Mínimo",
            straightRatioPct = 76,
            cornerProfileSummary = "Templo da Velocidade: freadas pesadas de 340+ km/h e chicanes",
            kerbAggression = "Muito Alta nas chicanes (Rettifilo e Roggia)",
            tyreStressLevel = "Moderado (frenagem longitudinal intensa)",
            baseFrontWing = 10,
            baseRearWing = 8,
            pitLossSec = 23.5f,
            centerline = listOf(
                TrackPoint(0.18f, 0.78f),
                TrackPoint(0.76f, 0.78f),
                TrackPoint(0.82f, 0.64f),
                TrackPoint(0.72f, 0.44f),
                TrackPoint(0.82f, 0.26f),
                TrackPoint(0.62f, 0.16f),
                TrackPoint(0.44f, 0.28f),
                TrackPoint(0.32f, 0.42f),
                TrackPoint(0.16f, 0.54f),
            ),
        ),
        TrackProfile(
            trackId = 14,
            name = "Suzuka",
            country = "Japão",
            lengthMeters = 5807,
            cornersCount = 18,
            elevationChangeM = 40,
            downforceDemand = "Alto",
            straightRatioPct = 50,
            cornerProfileSummary = "Esses fluidos, Degners, Spoon e 130R de alta velocidade",
            kerbAggression = "Média",
            tyreStressLevel = "Alto (degradação térmica constante)",
            baseFrontWing = 32,
            baseRearWing = 30,
            pitLossSec = 22.0f,
            centerline = listOf(
                TrackPoint(0.28f, 0.74f),
                TrackPoint(0.56f, 0.78f),
                TrackPoint(0.74f, 0.70f),
                TrackPoint(0.66f, 0.56f),
                TrackPoint(0.78f, 0.48f),
                TrackPoint(0.62f, 0.34f),
                TrackPoint(0.52f, 0.44f),
                TrackPoint(0.38f, 0.28f),
                TrackPoint(0.16f, 0.22f),
                TrackPoint(0.14f, 0.42f),
                TrackPoint(0.32f, 0.52f),
                TrackPoint(0.22f, 0.64f),
            ),
        ),
    )

    fun byId(trackId: Int): TrackProfile =
        tracks.firstOrNull { it.trackId == trackId } ?: tracks.first()

    fun byName(name: String): TrackProfile =
        tracks.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: tracks.first()

    /**
     * Interpolates a smooth (x, z) point in [0..1] normalized track coordinates for a given
     * lap progress in [0f..1f).
     */
    fun samplePosition(profile: TrackProfile, progress: Float): TrackPoint {
        val pts = profile.centerline
        if (pts.size < 2) return TrackPoint(0.5f, 0.5f)
        val wrapped = ((progress % 1f) + 1f) % 1f
        val segmentLengths = FloatArray(pts.size)
        var total = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val len = hypot((b.x - a.x).toDouble(), (b.z - a.z).toDouble()).toFloat()
            segmentLengths[i] = len
            total += len
        }
        if (total <= 0f) return pts.first()
        var target = wrapped * total
        for (i in pts.indices) {
            val segLen = segmentLengths[i]
            if (target <= segLen || i == pts.lastIndex) {
                val t = if (segLen > 0f) (target / segLen).coerceIn(0f, 1f) else 0f
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                return TrackPoint(
                    x = a.x + (b.x - a.x) * t,
                    z = a.z + (b.z - a.z) * t,
                )
            }
            target -= segLen
        }
        return pts.first()
    }
}
