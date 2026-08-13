package com.jaminsmoke.personalbar.lan

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * Servidor HTTP embebido del nodo de sala.
 * Bind `0.0.0.0` en [BarLanConfig.PORT]. Solo `GET /health` por ahora.
 */
class BarLanServer(
    private val port: Int = BarLanConfig.PORT,
) : NanoHTTPD(port) {

    @Volatile
    var isRunning: Boolean = false
        private set

    fun startServer(): Boolean {
        if (isRunning) return true
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            isRunning = true
            Log.i(TAG, "Bar LAN listening on 0.0.0.0:$port")
            true
        } catch (e: IOException) {
            isRunning = false
            Log.e(TAG, "Failed to bind port $port", e)
            false
        }
    }

    fun stopServer() {
        if (!isRunning) return
        stop()
        isRunning = false
        Log.i(TAG, "Bar LAN stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method
        if (method == Method.GET && (uri == "/health" || uri == "health")) {
            val body = HealthPayload.json()
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                body,
            )
        }
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "not found",
        )
    }

    companion object {
        private const val TAG = "BarLanServer"
    }
}
