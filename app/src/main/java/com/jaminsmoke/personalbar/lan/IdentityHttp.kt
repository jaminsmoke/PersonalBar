package com.jaminsmoke.personalbar.lan

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Lee el body de una respuesta HTTP como texto UTF-8 explícito (independiente del charset de la plataforma). */
internal fun InputStream.readBodyUtf8(): String =
    reader(Charsets.UTF_8).use { it.readText() }

/**
 * Plumbing HTTP compartido por los dos clientes de Identity (negocio y camareros).
 * Aísla el detalle de `HttpURLConnection`, el charset UTF-8 y los timeouts.
 */
object IdentityHttp {

    /** GET binario. Devuelve (statusCode, bytes). -1 si falló la red. */
    fun requestBytes(
        baseUrl: String?,
        method: String,
        path: String,
        token: String? = null,
    ): Pair<Int, ByteArray> {
        val base = baseUrl ?: return -1 to ByteArray(0)
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$base$path")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = connection.responseCode
            val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.readBytes() ?: ByteArray(0)
            code to bytes
        } catch (_: Exception) {
            -1 to ByteArray(0)
        } finally {
            connection?.disconnect()
        }
    }

    /** Petición JSON. Devuelve (statusCode, body). -1 si falló la red. */
    fun request(
        baseUrl: String?,
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        auth: Boolean = true,
    ): Pair<Int, String> {
        val base = baseUrl ?: return -1 to ""
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$base$path")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                doOutput = body != null
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (auth && token != null) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.readBodyUtf8().orEmpty()
            code to text
        } catch (_: Exception) {
            -1 to ""
        } finally {
            connection?.disconnect()
        }
    }

    /** POST multipart (un único campo). Devuelve true si quedó en 2xx. */
    fun uploadMultipart(
        baseUrl: String?,
        path: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        mimetype: String,
        token: String? = null,
    ): Boolean {
        val base = baseUrl ?: return false
        val boundary = "----PersonalBar${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val dosGuiones = "--"
        val body = ByteArrayOutputStream().apply {
            write((dosGuiones + boundary + lineEnd).toByteArray(Charsets.UTF_8))
            write(("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"" + lineEnd).toByteArray(Charsets.UTF_8))
            write(("Content-Type: $mimetype" + lineEnd + lineEnd).toByteArray(Charsets.UTF_8))
            write(bytes)
            write((lineEnd + dosGuiones + boundary + dosGuiones + lineEnd).toByteArray(Charsets.UTF_8))
        }.toByteArray()

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$base$path")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 10000
            }
            connection.outputStream.use { it.write(body) }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
