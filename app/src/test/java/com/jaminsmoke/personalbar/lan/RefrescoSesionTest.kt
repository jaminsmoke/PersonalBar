package com.jaminsmoke.personalbar.lan

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Prueba la lógica de sesiones revocables (v0.4) contra un `HttpServer` local:
 * - `revalidarToken()` usa `POST /refresh` con el refresh opaco (no `GET /me`).
 * - 401 del refresh → `REVOCADA`; 200 → `OK` y rota token+refresh.
 * - Sin refresh (sesión legacy) cae al `GET /me`.
 * - `IdentityHttp` reintenta una vez tras el 401 si el hook refresca (401-interception).
 *
 * `IdentityNegocioClient` es un singleton: cada test resetea su estado en [instalar].
 */
class RefrescoSesionTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    /** Comportamiento del servidor simulado por contexto. */
    private var refreshStatus: Int = 200
    private var meStatusPorToken: Map<String, Int> = emptyMap()
    private var refreshDevuelve: String =
        """{"token":"nuevo","refresh_token":"refresco-nuevo","expires_in":43200,"sesion_id":"s1"}"""
    private var refreshBodyRecibido: String? = null

    @Before
    fun instalar() {
        IdentityNegocioClient.desconectar()
        refreshStatus = 200
        meStatusPorToken = emptyMap()
        refreshDevuelve =
            """{"token":"nuevo","refresh_token":"refresco-nuevo","expires_in":43200,"sesion_id":"s1"}"""
        refreshBodyRecibido = null

        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/auth/negocio/refresh") { ex ->
            refreshBodyRecibido = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            responder(ex, refreshStatus, refreshDevuelve)
        }
        server.createContext("/v1/auth/negocio/me") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization") ?: ""
            val status = meStatusPorToken.entries.firstOrNull { auth.contains(it.key) }?.value
            if (status != null) {
                responder(ex, status, """{"id":"c1","email":"x","nombre_mostrar":"x"}""")
            } else {
                responder(ex, 401, """{"code":"identity.no_autorizado","detail":"x"}""")
            }
        }
        server.start()
        baseUrl = "http://localhost:${server.address.port}"
        IdentityNegocioClient.configurar(baseUrl)
    }

    @After
    fun desmontar() {
        server.stop(0)
        IdentityNegocioClient.desconectar()
    }

    private fun responder(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    @Test
    fun revalidarConRefreshRotaTokenYRefresh() = runBlocking {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = "refresco-viejo"

        val resultado = IdentityNegocioClient.revalidarToken()

        assertEquals(IdentityNegocioClient.RevalidacionResultado.OK, resultado)
        assertEquals("nuevo", IdentityNegocioClient.negocioToken)
        assertEquals("refresco-nuevo", IdentityNegocioClient.refreshToken)
        // El refresh envió el refresh_token opaco.
        assertTrue(refreshBodyRecibido.orEmpty().contains("refresco-viejo"))
    }

    @Test
    fun refresh401MarcaRevocada() = runBlocking {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = "refresco-vencido"
        refreshStatus = 401

        val resultado = IdentityNegocioClient.revalidarToken()

        assertEquals(IdentityNegocioClient.RevalidacionResultado.REVOCADA, resultado)
    }

    @Test
    fun sinRefreshUsaGetMeLegacy() = runBlocking {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = null
        meStatusPorToken = mapOf("viejo" to 200)

        val resultado = IdentityNegocioClient.revalidarToken()

        assertEquals(IdentityNegocioClient.RevalidacionResultado.OK, resultado)
        // Sin refresh no se rota nada.
        assertEquals("viejo", IdentityNegocioClient.negocioToken)
        assertNull(IdentityNegocioClient.refreshToken)
    }

    @Test
    fun sinRefreshYMe401MarcaRevocada() = runBlocking {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = null
        meStatusPorToken = emptyMap() // /me siempre 401

        val resultado = IdentityNegocioClient.revalidarToken()

        assertEquals(IdentityNegocioClient.RevalidacionResultado.REVOCADA, resultado)
    }

    @Test
    fun refrescarSesionBloqueanteSinRefreshDevuelveSinRedSinLlamar() {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = null

        // Acceso a la función privada vía el hook registrado por el init.
        val hook = IdentityHttp.onUnauthorized
        assertNotNull("El init de IdentityNegocioClient debe registrar el hook 401", hook)

        // Sin refresh, el hook no puede refrescar → null (no llama al server).
        val tokenNuevo = hook!!.invoke()
        assertNull(tokenNuevo)
    }

    @Test
    fun interception401RefrescaYReintentaConTokenNuevo() {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = "refresco-viejo"
        // /me: 401 con "viejo", 200 con "nuevo" (el token que da el refresh).
        meStatusPorToken = mapOf("nuevo" to 200)

        val (code, _) = IdentityHttp.request(
            IdentityNegocioClient.baseUrl, "GET", "/v1/auth/negocio/me", token = "viejo"
        )

        assertEquals(200, code)
        // El hook actualizó el token del cliente y el reintento usó el nuevo.
        assertEquals("nuevo", IdentityNegocioClient.negocioToken)
    }

    @Test
    fun interception401SinRefreshDevuelve401Original() {
        IdentityNegocioClient.negocioToken = "viejo"
        IdentityNegocioClient.refreshToken = null
        meStatusPorToken = emptyMap() // /me siempre 401

        val (code, _) = IdentityHttp.request(
            IdentityNegocioClient.baseUrl, "GET", "/v1/auth/negocio/me", token = "viejo"
        )

        // Sin refresh el hook no puede renovar → se devuelve el 401 original.
        assertEquals(401, code)
    }
}
