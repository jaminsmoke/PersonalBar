package com.jaminsmoke.personalbar.data

import java.net.URLDecoder
import java.util.UUID

/** Payload `phid1` parseado: identidad del camarero + credencial + firma. */
data class Phid1(
    val camareroId: String,
    val credencialId: String,
    val firma: String,
)

/**
 * Parser del QR permanente de Identity: `phid1:<camarero_id>:<credencial_id>:<firma>`.
 * Acepta también la URL pública de la ficha (`https://...?qr=phid1:...`) y extrae el
 * payload del query param `qr` (mismo criterio que `_extract_qr_payload` de Identity).
 * Valida solo la **forma** (prefijo + 4 partes + UUIDs + firma presente); la
 * verificación criptográfica Ed25519 llega cuando Identity exponga la clave pública.
 */
object QrParser {
    const val PREFIX = "phid1"

    /** @return [Phid1] si el payload tiene forma de QR `phid1` válido (directo o en URL); `null` si no. */
    fun parsear(payload: String): Phid1? {
        val phid = extraerPayload(payload) ?: return null
        val partes = phid.trim().split(':')
        if (partes.size != 4) return null
        if (partes[0] != PREFIX) return null
        val (_, camareroId, credencialId, firma) = partes
        if (!esUuid(camareroId) || !esUuid(credencialId)) return null
        if (firma.isBlank()) return null
        return Phid1(camareroId = camareroId, credencialId = credencialId, firma = firma)
    }

    /**
     * Acepta `phid1:...` o una URL `http(s)://...?qr=phid1:...` (como Identity):
     * extrae el query param `qr` y lo URL-decodifica. `null` si la URL no trae `qr`.
     */
    private fun extraerPayload(payload: String): String? {
        val p = payload.trim()
        if (!p.startsWith("http://") && !p.startsWith("https://")) return p
        return extraerQrDeUrl(p)
    }

    private fun extraerQrDeUrl(url: String): String? {
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        for (param in query.split('&')) {
            val eq = param.indexOf('=')
            if (eq <= 0) continue
            if (param.substring(0, eq).equals("qr", ignoreCase = true)) {
                val valor = param.substring(eq + 1)
                return runCatching { URLDecoder.decode(valor, "UTF-8") }.getOrNull() ?: valor
            }
        }
        return null
    }

    private fun esUuid(valor: String): Boolean = try {
        UUID.fromString(valor)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
