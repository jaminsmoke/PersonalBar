package com.jaminsmoke.personalbar.data

import java.util.UUID

/** Payload `phid1` parseado: identidad del camarero + credencial + firma. */
data class Phid1(
    val camareroId: String,
    val credencialId: String,
    val firma: String,
)

/**
 * Parser del QR permanente de Identity: `phid1:<camarero_id>:<credencial_id>:<firma>`.
 * Valida solo la **forma** (prefijo + 4 partes + UUIDs + firma presente); la
 * verificación criptográfica Ed25519 llega cuando Identity exponga la clave pública.
 */
object QrParser {
    const val PREFIX = "phid1"

    /** @return [Phid1] si el payload tiene forma de QR `phid1` válido; `null` si no. */
    fun parsear(payload: String): Phid1? {
        val partes = payload.trim().split(':')
        if (partes.size != 4) return null
        if (partes[0] != PREFIX) return null
        val (_, camareroId, credencialId, firma) = partes
        if (!esUuid(camareroId) || !esUuid(credencialId)) return null
        if (firma.isBlank()) return null
        return Phid1(camareroId = camareroId, credencialId = credencialId, firma = firma)
    }

    private fun esUuid(valor: String): Boolean = try {
        UUID.fromString(valor)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
