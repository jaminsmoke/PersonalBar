package com.jaminsmoke.personalbar.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Emite el beacon de presencia mientras el nodo HTTP está arriba.
 * El adiós se manda en [stop] antes de bajar el server.
 */
class PresenciaEmisor(
    context: Context,
    private val nombreEstablecimiento: () -> String,
) {
    private val appContext = context.applicationContext
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop(enviarAdios = false)
        job = scope.launch(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (isActive) {
                    enviar(socket, activo = true)
                    delay(PresenciaLan.HEARTBEAT_MS)
                }
            }
        }
    }

    fun stop(enviarAdios: Boolean = true) {
        val anterior = job
        job = null
        anterior?.cancel()
        if (enviarAdios) {
            runBlocking(Dispatchers.IO) {
                try {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        repeat(PresenciaLan.ADIOS_VECES) {
                            enviar(socket, activo = false)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo enviar el adiós de presencia: $e")
                }
            }
        }
    }

    private fun enviar(socket: DatagramSocket, activo: Boolean) {
        val cuerpo = PresenciaLan.encode(
            PresenciaLan.Anuncio(
                establecimiento = nombreEstablecimiento(),
                activo = activo,
            ),
        ).toByteArray(Charsets.UTF_8)
        val destino = destinoBroadcast()
        socket.send(DatagramPacket(cuerpo, cuerpo.size, destino, PresenciaLan.PUERTO))
    }

    // `dhcpInfo` está deprecado en API 33+ pero sigue funcionando en todas las APIs y
    // es la vía más simple para la dirección de broadcast; la alternativa (LinkProperties
    // de ConnectivityManager) añade parsing de prefijos sin beneficio para un beacon
    // best-effort con fallback a 255.255.255.255.
    @Suppress("DEPRECATION")
    @Suppress("DEPRECATION")
    private fun destinoBroadcast(): InetAddress {
        return try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wm.dhcpInfo ?: return InetAddress.getByName(BROADCAST_TODOS)
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val bytes = ByteArray(4)
            for (i in 0..3) bytes[i] = (broadcast shr (8 * i)).toByte()
            InetAddress.getByAddress(bytes)
        } catch (_: Exception) {
            InetAddress.getByName(BROADCAST_TODOS)
        }
    }

    companion object {
        private const val TAG = "PresenciaEmisor"
        private const val BROADCAST_TODOS = "255.255.255.255"
    }
}
