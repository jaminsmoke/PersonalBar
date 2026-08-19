package com.jaminsmoke.personalbar.lan

import android.util.Log
import com.jaminsmoke.personalbar.data.BarRepository
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Servidor HTTP embebido del nodo de sala (Ktor, engine CIO).
 * Bind `0.0.0.0` en [BarLanConfig.PORT]. Rutas definidas en [barModule].
 */
class BarLanServer(
    private val repository: BarRepository,
    private val port: Int = BarLanConfig.PORT,
) {

    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun startServer(): Boolean {
        if (isRunning) return true
        return try {
            // Scope propio del engine: el engine CIO hace el bind del puerto de forma
            // asíncrona dentro de su `acceptJob`. Sin un CoroutineExceptionHandler en su
            // contexto raíz, un fallo de bind se propaga al handler por defecto del thread
            // y tumba el proceso (FATAL EXCEPTION) aunque `start()` también lo lance.
            // Con este scope, la excepción se entrega aquí (log) y la única vía de fallo
            // es `start()` → el try/catch de abajo degrada limpio sin crash.
            val engineScope = CoroutineScope(
                SupervisorJob() + CoroutineExceptionHandler { _, cause ->
                    Log.e(TAG, "Corrutina del nodo LAN falló", cause)
                }
            )
            val s = engineScope.embeddedServer(CIO, port = port, host = "0.0.0.0") {
                barModule(repository)
            }
            s.start(wait = false)
            server = s
            isRunning = true
            Log.i(TAG, "Bar LAN listening on 0.0.0.0:$port")
            true
        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "Failed to bind port $port", e)
            false
        }
    }

    fun stopServer() {
        if (!isRunning) return
        server?.stop(1000, 2000)
        server = null
        isRunning = false
        Log.i(TAG, "Bar LAN stopped")
    }

    companion object {
        private const val TAG = "BarLanServer"
    }
}
