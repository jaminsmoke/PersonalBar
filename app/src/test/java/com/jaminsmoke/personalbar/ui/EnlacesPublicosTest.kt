package com.jaminsmoke.personalbar.ui

import com.jaminsmoke.personalbar.lan.IdentityEnlacePublico
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnlacesPublicosTest {

    private fun enlace(tipo: String, estado: String = "activo") = IdentityEnlacePublico(
        id = "e-1",
        establecimientoId = "est-1",
        tipo = tipo,
        estado = estado,
        urlPublica = "https://web.negocio.siberia.solutions/negocios/demo",
    )

    @Test
    fun webActivoCubreTarjetaWeb() {
        assertTrue(enlace("web").cubreTipo(TipoEnlacePublico.WEB))
        assertFalse(enlace("web").cubreTipo(TipoEnlacePublico.CARTA))
    }

    @Test
    fun aliasFichaNegocioActivoCubreTarjetaWeb() {
        assertTrue(enlace("ficha_negocio").cubreTipo(TipoEnlacePublico.WEB))
        assertFalse(enlace("ficha_negocio").cubreTipo(TipoEnlacePublico.CARTA))
    }

    @Test
    fun cartaActivoSoloCubreTarjetaCarta() {
        assertTrue(enlace("carta").cubreTipo(TipoEnlacePublico.CARTA))
        assertFalse(enlace("carta").cubreTipo(TipoEnlacePublico.WEB))
    }

    @Test
    fun revocadoNoCubreNingunaTarjeta() {
        assertFalse(enlace("web", "revocado").cubreTipo(TipoEnlacePublico.WEB))
        assertFalse(enlace("ficha_negocio", "revocado").cubreTipo(TipoEnlacePublico.WEB))
        assertFalse(enlace("carta", "REVOCADO").cubreTipo(TipoEnlacePublico.CARTA))
    }
}
