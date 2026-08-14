package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.QrKey
import com.jaminsmoke.personalbar.data.QrParser
import com.jaminsmoke.personalbar.data.QrVerificador
import kotlinx.serialization.Serializable

/** Cuerpo de `POST /v1/sesion`: QR permanente `phid1` del camarero. */
@Serializable
data class SesionRequest(
    val qr: String = "",
)

/** Respuesta de consulta a la lista blanca. No es un alta. */
@Serializable
data class SesionResponse(
    val admitido: Boolean,
    val camareroId: String? = null,
    val nombre: String? = null,
)

/**
 * Consulta LAN de lista blanca para el candado UX de Commander.
 * No da de alta ni pone de servicio.
 */
object SesionConsulta {

    sealed class Resultado {
        data class Ok(val respuesta: SesionResponse) : Resultado()
        data object QrInvalido : Resultado()
    }

    fun evaluar(
        qr: String,
        camareros: List<Camarero>,
        qrKey: QrKey?,
    ): Resultado {
        val phid = QrParser.parsear(qr) ?: return Resultado.QrInvalido
        val clave = qrKey?.publicKey?.trim().orEmpty()
        if (clave.isNotEmpty() && !QrVerificador.verificar(qr, clave)) {
            return Resultado.Ok(
                SesionResponse(admitido = false, camareroId = phid.camareroId),
            )
        }
        val camarero = camareros.firstOrNull { it.id == phid.camareroId }
        return Resultado.Ok(
            SesionResponse(
                admitido = camarero?.estado == CamareroEstado.ACTIVA,
                camareroId = phid.camareroId,
                nombre = camarero?.nombre,
            ),
        )
    }
}
