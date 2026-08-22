package com.jaminsmoke.personalbar.lan

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sesión LAN firmada (auth v0.2, ítem «Exigir autenticación de sesión en las
 * operaciones LAN privadas»).
 *
 * El token es un sello HMAC-SHA256 verificable **sin estado**:
 * `phbar1.<payloadBase64Url>.<firmaBase64Url>` donde el payload es
 * `camareroId|expiraciónEpochMs`. El secreto del nodo se persiste cifrado
 * (`NodoSecreto` en Room, cifrado con [TokenCifrador]) para que los tokens
 * sobrevivan reinicios; la caducidad de jornada (`sesionActiva` en Room)
 * sigue mandando además del TTL.
 */
object NodoSesion {

    const val PREFIX = "phbar1"
    /** TTL de un token emitido (ms). El fin de jornada corta antes si aplica. */
    const val TTL_MS: Long = 24 * 60 * 60 * 1000L
    private const val HMAC_ALGO = "HmacSHA256"
    private const val TAMANO_SECRETO = 32

    /**
     * Genera un secreto HMAC nuevo (32 bytes aleatorios, Base64). No persiste:
     * quien lo genera (p. ej. el arranque del nodo) decide cómo guardarlo.
     */
    fun generarSecreto(): String {
        val bytes = ByteArray(TAMANO_SECRETO)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Devuelve el secreto del nodo, generándolo, cifrándolo con [TokenCifrador]
     * (AndroidKeyStore) y persistiéndolo en [dao] si no existe. `null` si el
     * Keystore falla: el nodo queda sin auth (todas las privadas devuelven 401).
     */
    suspend fun obtenerSecreto(dao: com.jaminsmoke.personalbar.data.BarDao): String? {
        val existente = dao.getNodoSecreto()
        if (existente != null && existente.secretoCifrado.isNotEmpty()) {
            return TokenCifrador.descifrar(existente.secretoCifrado)
        }
        val secreto = generarSecreto()
        val cifrado = runCatching { TokenCifrador.cifrar(secreto) }.getOrNull() ?: return null
        dao.upsertNodoSecreto(
            com.jaminsmoke.personalbar.data.NodoSecreto(secretoCifrado = cifrado)
        )
        return secreto
    }

    /**
     * Emite un token firmado para [camareroId] con el [secreto] del nodo.
     * `ahoraMs` y `ttlMs` son inyectables para los tests.
     */
    fun emitir(
        camareroId: String,
        secreto: String,
        ahoraMs: Long = System.currentTimeMillis(),
        ttlMs: Long = TTL_MS,
    ): String {
        // Sin secreto (Keystore roto) el nodo no puede firmar: no se emite credencial.
        if (secreto.isBlank()) return ""
        val payload = "$camareroId|${ahoraMs + ttlMs}"
        val firma = hmac(payload, secreto)
        return "$PREFIX.${b64url(payload.toByteArray())}.$firma"
    }

    /**
     * Verifica un token y devuelve el `camareroId` si la firma es válida y no
     * ha expirado. `null` si el formato, la firma o la expiración fallan.
     */
    fun verificar(
        token: String,
        secreto: String,
        ahoraMs: Long = System.currentTimeMillis(),
    ): String? {
        // Fail-closed: sin secreto nunca hay sesión válida (todas las privadas → 401).
        if (secreto.isBlank()) return null
        val partes = token.split('.')
        if (partes.size != 3) return null
        if (partes[0] != PREFIX) return null
        val payload = runCatching { String(b64urlDecode(partes[1]), Charsets.UTF_8) }.getOrNull()
            ?: return null
        val firma = partes[2]
        if (!mensajesIguales(hmac(payload, secreto), firma)) return null
        val sep = payload.lastIndexOf('|')
        if (sep <= 0) return null
        val camareroId = payload.substring(0, sep)
        val expiracion = payload.substring(sep + 1).toLongOrNull() ?: return null
        if (ahoraMs > expiracion) return null
        return camareroId
    }

    /** HMAC-SHA256 de [mensaje] con [secreto], en Base64URL (sin padding). */
    private fun hmac(mensaje: String, secreto: String): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(secreto.toByteArray(Charsets.UTF_8), HMAC_ALGO))
        return b64url(mac.doFinal(mensaje.toByteArray(Charsets.UTF_8)))
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64urlDecode(s: String): ByteArray =
        Base64.getUrlDecoder().decode(s)

    /** Comparación en tiempo constante para no filtrar la firma por timing. */
    private fun mensajesIguales(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
