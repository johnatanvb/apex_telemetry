package com.example.telemetry.udp

import android.util.Log
import com.example.telemetry.data.GameCatalog
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

object PacketAutoDetector {

    /**
     * Auto-detects the protocol from binary packet signature & payload length or port hint,
     * parses it into [world], and returns the identified [GameTitle] if valid.
     */
    fun detectAndParse(
        bytes: ByteArray,
        length: Int,
        world: F125PacketParser.UdpMutableWorld,
        selectedMode: GameTitle,
        sourcePortHint: Int = 0,
    ): GameTitle? {
        if (length <= 0) return null

        return when (selectedMode) {
            GameTitle.F1_25, GameTitle.F1_24, GameTitle.F1_23 -> {
                if (F125PacketParser.parsePacket(bytes, length, world)) world.detectedGame else null
            }

            GameTitle.GRAN_TURISMO_7 -> {
                if (Gt7Salsa20Decoder.parseEncryptedPacket(bytes, length, world)) GameTitle.GRAN_TURISMO_7 else null
            }

            GameTitle.FORZA_MOTORSPORT, GameTitle.FORZA_HORIZON_5 -> {
                if (ForzaPacketParser.parsePacket(bytes, length, world, selectedMode)) world.detectedGame else null
            }

            GameTitle.ASSETTO_CORSA -> {
                if (AssettoCorsaParsers.parseAcPacket(bytes, length, world)) GameTitle.ASSETTO_CORSA else null
            }

            GameTitle.ASSETTO_CORSA_COMPETIZIONE -> {
                if (AssettoCorsaParsers.parseAccPacket(bytes, length, world)) GameTitle.ASSETTO_CORSA_COMPETIZIONE else null
            }

            GameTitle.PROJECT_CARS_2, GameTitle.AUTOMOBILISTA_2 -> {
                if (SmsPcars2Parser.parsePacket(bytes, length, world, selectedMode)) world.detectedGame else null
            }

            GameTitle.AUTO -> {
                // 1) Try F1 23 / 24 / 25 signature
                if (F125PacketParser.looksLikeF1Packet(bytes, length) &&
                    F125PacketParser.parsePacket(bytes, length, world)
                ) {
                    return world.detectedGame
                }

                // 2) Try Forza exact lengths (232 / 311 / 323 / 324 / 331)
                if (ForzaPacketParser.looksLikeForzaPacket(length) &&
                    ForzaPacketParser.parsePacket(bytes, length, world, GameTitle.AUTO)
                ) {
                    return world.detectedGame
                }

                // 3) Try Gran Turismo 7 Salsa20 (296 bytes, decrypts to G7S0 magic)
                if (length == Gt7Salsa20Decoder.PACKET_A_SIZE &&
                    Gt7Salsa20Decoder.parseEncryptedPacket(bytes, length, world)
                ) {
                    return GameTitle.GRAN_TURISMO_7
                }

                // 4) Try Assetto Corsa (408 handshake or 328 RTCarInfo)
                if ((length == 408 || length == 328) &&
                    AssettoCorsaParsers.parseAcPacket(bytes, length, world)
                ) {
                    return GameTitle.ASSETTO_CORSA
                }

                // 5) Try SMS UDP v2 (Project CARS 2 / Automobilista 2)
                if (SmsPcars2Parser.looksLikeSmsV2Packet(bytes, length) &&
                    SmsPcars2Parser.parsePacket(bytes, length, world, GameTitle.AUTOMOBILISTA_2)
                ) {
                    return world.detectedGame
                }

                // 6) Try ACC Broadcasting on port 9000
                if (sourcePortHint == AssettoCorsaParsers.ACC_PORT &&
                    AssettoCorsaParsers.parseAccPacket(bytes, length, world)
                ) {
                    return GameTitle.ASSETTO_CORSA_COMPETIZIONE
                }

                null
            }
        }
    }
}

class UdpTelemetryReceiver {

    fun observeUdpStream(
        selectedGame: GameTitle,
        customPort: Int,
        targetHostIp: String,
    ): Flow<LiveTelemetryState> = flow {
        val initialGame = if (selectedGame == GameTitle.AUTO) GameTitle.F1_25 else selectedGame
        val world = F125PacketParser.UdpMutableWorld(initialGame)
        val traceHistory = ArrayDeque<CarTelemetrySample>(120)
        val localIp = resolveLocalIpv4Address()
        var packetCount = 0L
        var lastEmitMs = 0L
        var activeDetectedGame = initialGame

        val effectivePort = if (selectedGame == GameTitle.AUTO) customPort else customPort.coerceIn(1024, 65535)

        emit(
            LiveTelemetryState(
                sourceMode = TelemetrySourceMode.UDP,
                selectedGameMode = selectedGame,
                detectedGame = activeDetectedGame,
                carCategory = activeDetectedGame.defaultCategory,
                capabilities = GameCatalog.capabilitiesFor(activeDetectedGame),
                connectionState = ConnectionState.WAITING_UDP,
                udpPort = effectivePort,
                targetHostIp = targetHostIp,
                localIpAddress = localIp,
                packetCount = 0L,
                sessionMeta = world.sessionMeta,
                currentSample = world.playerSample,
                carCondition = world.carCondition,
            )
        )

        val selector = try {
            Selector.open()
        } catch (e: Exception) {
            Log.e("UdpTelemetryReceiver", "Falha ao abrir Selector UDP", e)
            emit(
                LiveTelemetryState(
                    sourceMode = TelemetrySourceMode.UDP,
                    selectedGameMode = selectedGame,
                    detectedGame = activeDetectedGame,
                    carCategory = activeDetectedGame.defaultCategory,
                    capabilities = GameCatalog.capabilitiesFor(activeDetectedGame),
                    connectionState = ConnectionState.DISCONNECTED,
                    udpPort = effectivePort,
                    targetHostIp = targetHostIp,
                    localIpAddress = localIp,
                )
            )
            return@flow
        }

        val channelsByPort = linkedMapOf<Int, DatagramChannel>()
        val portsToBind = when (selectedGame) {
            GameTitle.AUTO -> linkedSetOf(
                effectivePort,
                20777,
                ForzaPacketParser.DEFAULT_PORT,
                SmsPcars2Parser.DEFAULT_PORT,
                Gt7Salsa20Decoder.GT7_RECEIVE_PORT,
            )

            GameTitle.GRAN_TURISMO_7 -> linkedSetOf(Gt7Salsa20Decoder.GT7_RECEIVE_PORT)
            GameTitle.ASSETTO_CORSA, GameTitle.ASSETTO_CORSA_COMPETIZIONE -> linkedSetOf(0) // ephemeral client socket
            else -> linkedSetOf(effectivePort)
        }

        try {
            portsToBind.forEach { port ->
                runCatching {
                    val channel = DatagramChannel.open().apply {
                        configureBlocking(false)
                        setOption(StandardSocketOptions.SO_REUSEADDR, true)
                        bind(InetSocketAddress("0.0.0.0", port))
                    }
                    channel.register(selector, SelectionKey.OP_READ, port)
                    channelsByPort[port] = channel
                }.onFailure { err ->
                    Log.w("UdpTelemetryReceiver", "Porta UDP $port indisponível: ${err.message}")
                }
            }

            if (channelsByPort.isEmpty()) {
                emit(
                    LiveTelemetryState(
                        sourceMode = TelemetrySourceMode.UDP,
                        selectedGameMode = selectedGame,
                        detectedGame = activeDetectedGame,
                        carCategory = activeDetectedGame.defaultCategory,
                        capabilities = GameCatalog.capabilitiesFor(activeDetectedGame),
                        connectionState = ConnectionState.DISCONNECTED,
                        udpPort = effectivePort,
                        targetHostIp = targetHostIp,
                        localIpAddress = localIp,
                    )
                )
                return@flow
            }

            val cleanHostIp = targetHostIp.trim()
            var lastHeartbeatMs = 0L
            var acSubscribed = false
            val readBuffer = ByteBuffer.allocate(2048)

            // Initial handshake if target IP is configured for AC or ACC
            if (cleanHostIp.isNotBlank()) {
                sendActiveHandshakes(
                    selectedGame = selectedGame,
                    targetHostIp = cleanHostIp,
                    effectivePort = effectivePort,
                    channelsByPort = channelsByPort,
                    acSubscribed = false,
                )
            }

            while (currentCoroutineContext().isActive) {
                val nowLoop = System.currentTimeMillis()
                if (cleanHostIp.isNotBlank() && nowLoop - lastHeartbeatMs >= 4500L) {
                    lastHeartbeatMs = nowLoop
                    sendActiveHandshakes(
                        selectedGame = selectedGame,
                        targetHostIp = cleanHostIp,
                        effectivePort = effectivePort,
                        channelsByPort = channelsByPort,
                        acSubscribed = acSubscribed,
                    )
                }

                val readyCount = selector.select(400L)
                if (readyCount > 0) {
                    val keyIter = selector.selectedKeys().iterator()
                    while (keyIter.hasNext()) {
                        val key = keyIter.next()
                        keyIter.remove()
                        if (!key.isValid || !key.isReadable) continue

                        val ch = key.channel() as? DatagramChannel ?: continue
                        val boundPort = (key.attachment() as? Int) ?: effectivePort
                        readBuffer.clear()
                        val sender = ch.receive(readBuffer)
                        if (sender != null) {
                            readBuffer.flip()
                            val len = readBuffer.remaining()
                            if (len > 0) {
                                val bytes = ByteArray(len)
                                readBuffer.get(bytes)

                                // If AC HandshakerResponse (408B) arrived, immediately send SUBSCRIBE_UPDATE
                                if (len == 408 && cleanHostIp.isNotBlank() && !acSubscribed) {
                                    acSubscribed = true
                                    runCatching {
                                        val acPort = if (selectedGame == GameTitle.ASSETTO_CORSA) effectivePort else AssettoCorsaParsers.AC_PORT
                                        ch.send(
                                            ByteBuffer.wrap(AssettoCorsaParsers.acSubscribeUpdate()),
                                            InetSocketAddress(cleanHostIp, acPort),
                                        )
                                    }
                                }

                                val detected = PacketAutoDetector.detectAndParse(
                                    bytes = bytes,
                                    length = len,
                                    world = world,
                                    selectedMode = selectedGame,
                                    sourcePortHint = boundPort,
                                )

                                if (detected != null) {
                                    activeDetectedGame = detected
                                    packetCount++
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitMs >= 50L) {
                                        lastEmitMs = now
                                        if (traceHistory.size >= 120) traceHistory.removeFirst()
                                        traceHistory.addLast(world.playerSample)

                                        val sortedGrid = world.drivers
                                            .take(world.activeCarsCount)
                                            .sortedBy { it.position }

                                        emit(
                                            LiveTelemetryState(
                                                sourceMode = TelemetrySourceMode.UDP,
                                                selectedGameMode = selectedGame,
                                                detectedGame = activeDetectedGame,
                                                carCategory = activeDetectedGame.defaultCategory,
                                                capabilities = GameCatalog.capabilitiesFor(activeDetectedGame),
                                                connectionState = ConnectionState.CONNECTED_UDP,
                                                udpPort = if (boundPort > 0) boundPort else effectivePort,
                                                targetHostIp = targetHostIp,
                                                localIpAddress = localIp,
                                                packetCount = packetCount,
                                                sessionMeta = world.sessionMeta,
                                                currentSample = world.playerSample,
                                                traceBuffer = traceHistory.toList(),
                                                carCondition = world.carCondition,
                                                gridEntries = sortedGrid,
                                                completedLaps = world.completedLaps.toList(),
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (packetCount == 0L && now - lastEmitMs >= 1500L) {
                        lastEmitMs = now
                        emit(
                            LiveTelemetryState(
                                sourceMode = TelemetrySourceMode.UDP,
                                selectedGameMode = selectedGame,
                                detectedGame = activeDetectedGame,
                                carCategory = activeDetectedGame.defaultCategory,
                                capabilities = GameCatalog.capabilitiesFor(activeDetectedGame),
                                connectionState = ConnectionState.WAITING_UDP,
                                udpPort = effectivePort,
                                targetHostIp = targetHostIp,
                                localIpAddress = localIp,
                                packetCount = 0L,
                            )
                        )
                    }
                }
            }
        } finally {
            if (targetHostIp.isNotBlank() &&
                (selectedGame == GameTitle.ASSETTO_CORSA || selectedGame == GameTitle.AUTO)
            ) {
                runCatching {
                    channelsByPort.values.firstOrNull()?.send(
                        ByteBuffer.wrap(AssettoCorsaParsers.acDismiss()),
                        InetSocketAddress(targetHostIp.trim(), AssettoCorsaParsers.AC_PORT),
                    )
                }
            }
            channelsByPort.values.forEach { ch -> runCatching { ch.close() } }
            runCatching { selector.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun sendActiveHandshakes(
        selectedGame: GameTitle,
        targetHostIp: String,
        effectivePort: Int,
        channelsByPort: Map<Int, DatagramChannel>,
        acSubscribed: Boolean,
    ) {
        val senderChannel = channelsByPort.values.firstOrNull() ?: return
        if (selectedGame == GameTitle.GRAN_TURISMO_7 || selectedGame == GameTitle.AUTO) {
            runCatching {
                val gtChannel = channelsByPort[Gt7Salsa20Decoder.GT7_RECEIVE_PORT] ?: senderChannel
                gtChannel.send(
                    ByteBuffer.wrap(Gt7Salsa20Decoder.heartbeatPayload()),
                    InetSocketAddress(targetHostIp, Gt7Salsa20Decoder.GT7_HEARTBEAT_PORT),
                )
            }
        }
        if ((selectedGame == GameTitle.ASSETTO_CORSA || selectedGame == GameTitle.AUTO) && !acSubscribed) {
            runCatching {
                val acPort = if (selectedGame == GameTitle.ASSETTO_CORSA) effectivePort else AssettoCorsaParsers.AC_PORT
                senderChannel.send(
                    ByteBuffer.wrap(AssettoCorsaParsers.acHandshakeStart()),
                    InetSocketAddress(targetHostIp, acPort),
                )
            }
        }
        if (selectedGame == GameTitle.ASSETTO_CORSA_COMPETIZIONE) {
            runCatching {
                senderChannel.send(
                    ByteBuffer.wrap(AssettoCorsaParsers.buildAccRegisterCommand()),
                    InetSocketAddress(targetHostIp, effectivePort),
                )
            }
        }
    }

    companion object {
        fun resolveLocalIpv4Address(): String {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "192.168.0.x"
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in intf.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress ?: "192.168.0.x"
                        }
                    }
                }
                "192.168.0.x"
            } catch (_: Exception) {
                "192.168.0.x"
            }
        }
    }
}
