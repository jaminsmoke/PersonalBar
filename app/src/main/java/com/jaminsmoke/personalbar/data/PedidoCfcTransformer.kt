package com.jaminsmoke.personalbar.data

import com.jaminsmoke.personalbar.lan.PedidoCfcResponse
import java.time.Instant

/**
 * Transforma un pedido CFC del inbox de Identity en una [Ronda] de Bar.
 * Puro: no muta nada y no depende de Android. El flujo CFC es el espejo del
 * flujo LAN (Commander → POST /v1/rondas), pero aquí el pedido lo crea el
 * cliente (QR de la mesa) y [camarero] queda null.
 *
 * Devuelve null si la mesa del pedido no resuelve a una mesa local (borrada),
 * lo que el poller traduce en ACK rechazado.
 */
object PedidoCfcTransformer {

    /**
     * @param pedido pedido del inbox (PENDIENTE)
     * @param mesas mesas locales (se resuelve por `mesaUuid`)
     * @param salas salas locales (para derivar `idZona` de la mesa)
     * @param rondas rondas existentes (para el número de ronda incremental)
     * @param zonas zonas locales (reparto por zona)
     * @param camareros camareros de la lista blanca (reparto)
     * @param ticketsActivos tickets en cola (carga del reparto)
     * @return la ronda lista para `crearRonda`, o null si la mesa no existe
     */
    fun transformar(
        pedido: PedidoCfcResponse,
        mesas: List<Mesa>,
        salas: List<Sala>,
        rondas: List<Ronda>,
        zonas: List<Zona> = emptyList(),
        camareros: List<Camarero> = emptyList(),
        ticketsActivos: List<Ticket> = emptyList(),
    ): Ronda? {
        val mesa = mesas.firstOrNull { it.mesaUuid == pedido.mesaUuid } ?: return null
        val salaNombre = salas.firstOrNull { it.id == mesa.salaId }?.nombre ?: ""
        val mesaId = mesa.idZona(salaNombre)
        val numero = (rondas.filter { it.mesaId == mesaId }.maxOfOrNull { it.numero } ?: 0) + 1
        // Carga = rondas con al menos un ticket en cola (PENDIENTE+LISTO).
        val rondasActivas = rondas.filter { r -> ticketsActivos.any { it.rondaId == r.id } }
        return Ronda(
            id = pedido.id,
            mesaId = mesaId,
            numero = numero,
            camarero = RepartoCamareros.resolverCamarero(mesa, zonas, camareros, rondasActivas),
            creadoEn = epochDe(pedido.creadoEn),
            lineas = pedido.lineas.map {
                Linea(
                    productoId = it.productoId,
                    nombreProducto = it.nombre,
                    cantidad = it.cantidad,
                )
            },
        )
    }

    /** Epoch ms de un timestamp ISO-8601 de Identity; fallback al momento actual. */
    private fun epochDe(iso: String): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
}
