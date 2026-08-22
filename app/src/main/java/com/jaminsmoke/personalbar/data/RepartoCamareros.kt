package com.jaminsmoke.personalbar.data

/**
 * Resuelve el camarero responsable de una mesa para los pedidos CFC.
 * Puro y determinista: no muta nada y no depende de Android.
 *
 * Precedencia (regla acordada v0.4):
 * 1. Asignación directa de mesa ([Mesa.camareroId]) si el camarero es válido.
 * 2. Camarero de zona ([Zona.camareroId]) — si la mesa cae en varias zonas con
 *    camarero, gana la de `id` menor (orden estable ante solapes).
 * 3. Camarero de servicio ([Camarero.deServicio]) con menor carga; tie-break
 *    por `id` (determinista).
 * 4. `null` → «Sin asignar».
 *
 * Un camarero es válido solo si existe en la lista, está `ACTIVA` y `deServicio`
 * (consistente con la UI, que solo permite poner de servicio a camareros activos).
 */
object RepartoCamareros {

    /**
     * @param mesa mesa a la que llega el pedido (ya resuelta por `mesaUuid`)
     * @param zonas zonas de todas las salas (se filtran por sala/geometría)
     * @param camareros camareros de la lista blanca
     * @param rondasActivas rondas con al menos un ticket en cola (carga)
     * @return id del camarero responsable, o null si ninguno es válido
     */
    fun resolverCamarero(
        mesa: Mesa,
        zonas: List<Zona>,
        camareros: List<Camarero>,
        rondasActivas: List<Ronda>,
    ): String? {
        val deServicio = camareros
            .filter { it.estado == CamareroEstado.ACTIVA && it.deServicio }
            .associateBy { it.id }

        // 1. Asignación directa de mesa
        mesa.camareroId?.let { directo ->
            if (deServicio.containsKey(directo)) return directo
        }

        // 2. Zonas de la mesa con camarero asignado, primera por id (estable)
        zonasDeMesa(zonas, mesa)
            .filter { it.camareroId != null && deServicio.containsKey(it.camareroId) }
            .minByOrNull { it.id }
            ?.camareroId
            ?.let { return it }

        // 3. Camareros de servicio por menor carga (tie-break por id)
        return deServicio.values
            .sortedWith(compareBy({ cargaDe(it.id, rondasActivas) }, { it.id }))
            .firstOrNull()?.id
    }

    /** Carga de un camarero: nº de rondas activas (con tickets en cola) que lleva. */
    fun cargaDe(camareroId: String, rondasActivas: List<Ronda>): Int =
        rondasActivas.count { it.camarero == camareroId }
}
