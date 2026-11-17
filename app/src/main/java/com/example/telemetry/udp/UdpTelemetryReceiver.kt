package com.example.telemetry.udp

import android.util.Log
import com.example.telemetry.model.CarTelemetrySample
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.LiveTelemetryState
import com.example.telemetry.model.TelemetrySourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class UdpTelemetryReceiver {

    fun observeUdpStream(port: Int): Flow<LiveTelemetryState> = flow {
        val world = F125PacketParser.UdpMutableWorld()
        val traceHistory = ArrayDeque<CarTelemetrySample>(120)
        val localIp = resolveLocalIpv4Address()
        var packetCount = 0L
        var lastEmitMs = 0L

        emit(
            LiveTelemetryState(
                sourceMode = TelemetrySourceMode.UDP,
                connectionState = ConnectionState.WAITING_UDP,
                udpPort = port,
                localIpAddress = localIp,
                packetCount = 0L,
                sessionMeta = world.sessionMeta,
                currentSample = world.playerSample,
                carCondition = world.carCondition,
            )
        )

        val socket = try {
            DatagramSocket(port, InetAddress.getByName("0.0.0.0")).apply {
                soTimeout = 750
                reuseAddress = true
                receiveBufferSize = 65536
            }
        } catch (e: Exception) {
            Log.e("UdpTelemetryReceiver", "Falha ao abrir socket UDP na porta $port", e)
            emit(
                LiveTelemetryState(
                    sourceMode = TelemetrySourceMode.UDP,
                    connectionState = ConnectionState.DISCONNECTED,
                    udpPort = port,
                    localIpAddress = localIp,
                )
            )
            return@flow
        }

        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        try {
            while (currentCoroutineContext().isActive) {
                try {
                    socket.receive(packet)
                    val parsed = F125PacketParser.parsePacket(packet.data, packet.length, world)
                    if (parsed) {
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
                                    connectionState = ConnectionState.CONNECTED_UDP,
                                    udpPort = port,
                                    localIpAddress = localIp,
                                    packetCount = packetCount,
                                    sessionMeta = world.sessionMeta,
                                    currentSample = world.playerSample,
                                    traceBuffer = traceHistory.toList(),
                                    carCondition = world.carCondition,
                                    gridEntries = sortedGrid,
                                )
                            )
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    val now = System.currentTimeMillis()
                    if (packetCount == 0L && now - lastEmitMs >= 1500L) {
                        lastEmitMs = now
                        emit(
                            LiveTelemetryState(
                                sourceMode = TelemetrySourceMode.UDP,
                                connectionState = ConnectionState.WAITING_UDP,
                                udpPort = port,
                                localIpAddress = localIp,
                                packetCount = 0L,
                            )
                        )
                    }
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

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
