package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.BarRepository

/**
 * Respaldo best-effort del layout (salas + mesas) en Identity, que es la fuente de
 * verdad de la plataforma. Se invoca tras mutar el layout en Bar; sin conexión o sin
 * cuenta vinculada no hace nada (SQLite sigue como estado local).
 */
object LayoutBackup {

    suspend fun respaldar(repository: BarRepository) {
        if (!IdentityNegocioClient.conectado) return
        IdentityNegocioClient.guardarLayout(repository.salas.value, repository.mesas.value, repository.zonas.value)
    }
}
