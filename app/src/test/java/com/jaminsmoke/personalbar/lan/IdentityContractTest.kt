package com.jaminsmoke.personalbar.lan

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Consume el **pack de contrato de Identity** (`docs/contracts/` del repo hermano
 * `PersonalHostelIdentity-Server`, `main`): por cada operación que Bar llama,
 * decodifica la fixture `response_200` con el DTO real, verifica que el DTO de
 * request cubre las claves documentadas, y que el `error` decodifica con
 * [IdentityError]. Es el espejo Kotlin del chequeo Python de método+path
 * (`scripts/check_family_contracts.py`) pero a nivel de **payload**.
 *
 * Si Identity renombra/elimina un campo o añade uno `required`, la fixture deja
 * de decodificar con el DTO de Bar y este test falla — el hueco que hoy se
 * colaría a runtime (`LanJson.ignoreUnknownKeys = true` solo tolera campos
 * extra, no campos desaparecidos).
 *
 * El pack vive en otro repo: en CI se baja con sparse checkout (`.family/identity`
 * en el job `unit-tests`); en local se resuelve desde el repo hermano. Si no se
 * encuentra, el test **falla con mensaje claro** (nada de skip silencioso).
 *
 * Limitación conocida: las fixtures `list[0]` del pack son arrays vacíos, así
 * que para las ops de lista solo se valida que la respuesta siga siendo un array
 * (el elemento se valida por el DTO solo cuando la fixture trae items). El peso
 * real está en los `Dato` (decode), los `request` y los `error`.
 */
class IdentityContractTest {

    // ── Resolución del pack (CI: .family/identity; local: repo hermano) ─────

    private fun resolver(relativos: List<String>): Path {
        val property = System.getProperty("identity.contractsDir")
        if (!property.isNullOrBlank()) return Paths.get(property)
        for (rel in relativos) {
            val p = Paths.get(rel)
            if (Files.exists(p)) return p
        }
        return Paths.get(relativos.first())
    }

    private val contractsDir: Path by lazy {
        resolver(
            listOf(
                "../.family/identity/docs/contracts",    // CI: cwd = app/
                ".family/identity/docs/contracts",       // CI: cwd = raíz
                "../../PersonalHostelIdentity-Server/docs/contracts", // local: cwd = app/
                "../PersonalHostelIdentity-Server/docs/contracts",    // local: cwd = raíz
            )
        )
    }

    private val negocioOps: JsonObject by lazy {
        val f = contractsDir.resolve("negocio.ops.json")
        if (!Files.exists(f)) {
            fail(
                "Pack de contrato de Identity no encontrado en ${contractsDir.toAbsolutePath()}. " +
                    "En CI se baja con sparse checkout (job unit-tests); en local clona " +
                    "PersonalHostelIdentity-Server junto a este repo o usa -Didentity.contractsDir=..."
            )
        }
        LanJson.decodeFromString<JsonObject>(Files.readString(f))
    }

    private val camarerosOps: JsonObject by lazy {
        val f = contractsDir.resolve("camareros.ops.json")
        if (!Files.exists(f)) {
            fail("Pack de contrato de Identity (camareros) no encontrado en ${contractsDir.toAbsolutePath()}")
        }
        LanJson.decodeFromString<JsonObject>(Files.readString(f))
    }

    // ── Normalización de rutas (misma regla canónica que check_family_contracts.py) ──

    private val PARAM_RE = Regex("""\{[^}]+\}|\$\{[^}]+\}|\$[A-Za-z_][A-Za-z0-9_]*""")
    private val QUERY_SUFFIX_VAR_RE = Regex("""(?<!/)\$[A-Za-z_][A-Za-z0-9_]*$""")

    /** `{param}`, `$var` y `${expr}` de segmento → `*`; el sufijo de query interpolada se elimina. */
    private fun normalize(path: String): String {
        var base = path.substringBefore('?').trim().trimEnd('/')
        base = QUERY_SUFFIX_VAR_RE.replace(base, "")
        return PARAM_RE.replace(base, "*")
    }

    private fun opKey(method: String, path: String): String = "$method ${normalize(path)}"

    // ── Validador por operación ───────────────────────────────────────────────

    private sealed interface Validador {
        /** DTO de request para el chequeo de claves documentadas (null = sin chequeo). */
        val requestDto: KSerializer<*>?

        data class Dato(val serializer: KSerializer<*>, override val requestDto: KSerializer<*>? = null) : Validador
        data class Lista(val serializer: KSerializer<*>) : Validador {
            override val requestDto: KSerializer<*>? = null
        }
        data class Estado(override val requestDto: KSerializer<*>? = null) : Validador
        data object Binario : Validador {
            override val requestDto: KSerializer<*>? = null
        }
    }

    /**
     * Mapa op→validador de **todo lo que Bar llama** contra Identity. Una entrada
     * `Dato` decodifica `response_200` con el DTO real; `Lista` exige array;
     * `Estado` exige objeto JSON (respuestas sin DTO, p. ej. las que Bar ignora y
     * devuelve Boolean); `Binario` exige string (bytes: logo/hero/galería).
     */
    private val MAPA: Map<String, Validador> = mapOf(
        // ── Servicio camareros (:8080) ───────────────────────────────────────
        "GET /v1/keys/qr" to Validador.Dato(IdentityQrPublicKey.serializer()),

        // ── Servicio negocio (:8082) — auth de cuenta ────────────────────────
        "POST /v1/auth/negocio/registro" to Validador.Dato(
            IdentityRegistroResponse.serializer(), RegistroNegocioRequest.serializer()
        ),
        "POST /v1/auth/negocio/login" to Validador.Dato(
            IdentityLoginResponse.serializer(), LoginRequest.serializer()
        ),
        "POST /v1/auth/negocio/refresh" to Validador.Dato(
            RefreshResponse.serializer(), RefreshRequest.serializer()
        ),
        "GET /v1/auth/negocio/me/sesiones" to Validador.Lista(SesionItem.serializer()),
        "POST /v1/auth/negocio/me/sesiones/revocar" to Validador.Estado(),
        "POST /v1/auth/negocio/me/sesiones/*/revocar" to Validador.Estado(),
        "POST /v1/auth/negocio/me/logo" to Validador.Estado(),
        "GET /v1/auth/negocio/me/logo" to Validador.Binario,
        "GET /v1/auth/negocio/me" to Validador.Dato(IdentityCuentaNegocio.serializer()),
        "POST /v1/auth/negocio/me/password" to Validador.Dato(
            CambioPasswordResponse.serializer(), CambioPasswordRequest.serializer()
        ),

        // ── Establecimiento ──────────────────────────────────────────────────
        "POST /v1/establecimientos" to Validador.Dato(IdentityEstablecimiento.serializer()),
        "GET /v1/establecimientos/mios" to Validador.Lista(IdentityEstablecimiento.serializer()),
        "GET /v1/establecimientos/*" to Validador.Dato(IdentityEstablecimiento.serializer()),
        "PATCH /v1/establecimientos/*" to Validador.Dato(
            IdentityEstablecimiento.serializer(), EstablecimientoUpdateRequest.serializer()
        ),

        // ── Camareros / directorio / membresías ──────────────────────────────
        "GET /v1/establecimientos/*/camareros/buscar" to Validador.Dato(IdentityCamarero.serializer()),
        "GET /v1/establecimientos/*/camareros/directorio" to Validador.Lista(IdentityCamareroDirectorio.serializer()),
        "GET /v1/establecimientos/*/miembros" to Validador.Lista(IdentityMembresia.serializer()),
        "POST /v1/establecimientos/*/miembros/qr" to Validador.Estado(),
        "DELETE /v1/establecimientos/*/miembros/*" to Validador.Estado(),

        // ── Invitaciones ─────────────────────────────────────────────────────
        "GET /v1/establecimientos/*/invitaciones" to Validador.Lista(IdentityInvitacion.serializer()),
        "POST /v1/establecimientos/*/invitaciones" to Validador.Dato(IdentityInvitacion.serializer()),
        "POST /v1/establecimientos/*/invitaciones/*/revocar" to Validador.Estado(),

        // ── Enlaces públicos ─────────────────────────────────────────────────
        "GET /v1/establecimientos/*/enlaces" to Validador.Lista(IdentityEnlacePublico.serializer()),
        "POST /v1/establecimientos/*/enlaces" to Validador.Dato(IdentityEnlacePublico.serializer()),
        "POST /v1/establecimientos/*/enlaces/*/revocar" to Validador.Estado(),
        "POST /v1/establecimientos/*/enlaces/*/rotar" to Validador.Dato(IdentityEnlacePublico.serializer()),

        // ── Layout del local ─────────────────────────────────────────────────
        "GET /v1/establecimientos/*/layout" to Validador.Dato(IdentityLayout.serializer()),
        "PUT /v1/establecimientos/*/layout" to Validador.Estado(LayoutUpdateRequest.serializer()),

        // ── Perfil web / logo / hero / galería ───────────────────────────────
        "GET /v1/establecimientos/*/perfil-web" to Validador.Dato(IdentityPerfilWeb.serializer()),
        "PATCH /v1/establecimientos/*/perfil-web" to Validador.Dato(
            IdentityPerfilWeb.serializer(), IdentityPerfilWebUpdate.serializer()
        ),
        "DELETE /v1/establecimientos/*/logo" to Validador.Estado(),
        "GET /v1/establecimientos/*/logo" to Validador.Binario,
        "POST /v1/establecimientos/*/logo" to Validador.Estado(),
        "DELETE /v1/establecimientos/*/hero" to Validador.Estado(),
        "GET /v1/establecimientos/*/hero" to Validador.Binario,
        "POST /v1/establecimientos/*/hero" to Validador.Dato(IdentityPerfilWeb.serializer()),
        "GET /v1/establecimientos/*/galeria" to Validador.Lista(IdentityImagenGaleria.serializer()),
        "POST /v1/establecimientos/*/galeria" to Validador.Estado(),
        "DELETE /v1/establecimientos/*/galeria/*" to Validador.Estado(),
        "GET /v1/establecimientos/*/galeria/*" to Validador.Binario,

        // ── Horario ──────────────────────────────────────────────────────────
        "GET /v1/establecimientos/*/horario" to Validador.Dato(IdentityHorarioResponse.serializer()),
        "PATCH /v1/establecimientos/*/horario" to Validador.Dato(
            IdentityHorarioResponse.serializer(), IdentityHorarioUpdate.serializer()
        ),

        // ── Fondos ───────────────────────────────────────────────────────────
        "GET /v1/establecimientos/*/fondos" to Validador.Dato(FondosAsignadosResponse.serializer()),
        "PUT /v1/establecimientos/*/fondos" to Validador.Dato(FondosAsignadosResponse.serializer()),
        "GET /v1/establecimientos/*/fondos/catalogo" to Validador.Lista(CatalogoFondoItem.serializer()),
        "DELETE /v1/establecimientos/*/fondos/*" to Validador.Dato(FondosAsignadosResponse.serializer()),
        "POST /v1/establecimientos/*/fondos/*" to Validador.Dato(FondosAsignadosResponse.serializer()),

        // ── Jornada CFC y pedidos ────────────────────────────────────────────
        "POST /v1/establecimientos/*/cfc/jornada/abrir" to Validador.Dato(JornadaCfcResponse.serializer()),
        "POST /v1/establecimientos/*/cfc/jornada/cerrar" to Validador.Estado(),
        "PUT /v1/establecimientos/*/cfc/heartbeat" to Validador.Dato(JornadaCfcResponse.serializer()),
        "GET /v1/establecimientos/*/cfc/pedidos" to Validador.Dato(PedidosCfcListaResponse.serializer()),
        "POST /v1/establecimientos/*/cfc/pedidos/*/ack" to Validador.Dato(PedidoCfcResponse.serializer()),
        "GET /v1/establecimientos/*/mesas-cfc" to Validador.Lista(MesaCfcResponse.serializer()),
        "PUT /v1/establecimientos/*/mesas-cfc" to Validador.Lista(MesaCfcResponse.serializer()),
        "POST /v1/establecimientos/*/mesas-cfc/*/rotar" to Validador.Dato(MesaCfcResponse.serializer()),

        // ── Catálogo / sync ──────────────────────────────────────────────────
        "GET /v1/establecimientos/*/catalogo" to Validador.Dato(CatalogoResponseDto.serializer()),
        "GET /v1/establecimientos/*/sync/cambios" to Validador.Dato(CambiosResponseDto.serializer()),
        "GET /v1/establecimientos/*/sync/conflictos" to Validador.Lista(ConflictoSyncDto.serializer()),
        "POST /v1/establecimientos/*/sync/conflictos/*/resolver" to Validador.Estado(ResolverConflictoRequest.serializer()),
        "POST /v1/establecimientos/*/sync/operaciones" to Validador.Dato(
            OperacionSyncResponse.serializer(), OperacionSyncRequest.serializer()
        ),

        // ── Notificaciones ───────────────────────────────────────────────────
        "GET /v1/establecimientos/*/notificaciones" to Validador.Lista(NotificacionNegocioDto.serializer()),
        "POST /v1/establecimientos/*/notificaciones/*/leer" to Validador.Estado(),

        // ── Libro de oficio ──────────────────────────────────────────────────
        "POST /v1/negocio/estadisticas/servicio" to Validador.Estado(ServicioRegistroRequest.serializer()),
    )

    // ── Tests ────────────────────────────────────────────────────────────────

    /** Las `response_200` del pack decodifican con los DTO de Bar (o tienen la forma esperada). */
    @Test
    fun respuestasDelPackDecodificanConLosDtoDeBar() {
        val ops = negocioOps.jsonObject + camarerosOps.jsonObject
        val fallos = mutableListOf<String>()
        for ((clave, op) in ops) {
            val validador = MAPA[clave] ?: continue // solo cubrimos lo que Bar llama
            val r200 = op.jsonObject["response_200"] ?: run {
                fallos += "$clave: el pack no declara response_200"
                continue
            }
            try {
                when (validador) {
                    is Validador.Dato -> LanJson.decodeFromString(validador.serializer as DeserializationStrategy<Any?>, r200.toString())
                    is Validador.Lista -> LanJson.decodeFromString(ListSerializer(validador.serializer), r200.toString())
                    is Validador.Estado -> LanJson.decodeFromString<JsonObject>(r200.toString())
                    is Validador.Binario -> LanJson.decodeFromString<String>(r200.toString())
                }
            } catch (e: Exception) {
                fallos += "$clave: response_200 no decodifica con el DTO de Bar (${e.message})"
            }
        }
        if (fallos.isNotEmpty()) fail("Desalineaciones DTO↔pack:\n" + fallos.joinToString("\n"))
    }

    /** Las claves documentadas del `request` están cubiertas por el DTO de request de Bar. */
    @Test
    fun requestDocumentadoCubiertoPorElDtoDeBar() {
        val ops = negocioOps.jsonObject + camarerosOps.jsonObject
        val fallos = mutableListOf<String>()
        for ((clave, op) in ops) {
            val validador = MAPA[clave] ?: continue
            val requestDto = validador.requestDto ?: continue
            val request = op.jsonObject["request"] ?: continue
            if (request !is JsonObject) continue
            val nombres = requestDto.descriptor.elementNames.toSet()
            for (campo in request.keys) {
                if (campo !in nombres) {
                    fallos += "$clave: el pack documenta el campo de request `$campo` que el DTO ${requestDto.descriptor.serialName} no tiene"
                }
            }
        }
        if (fallos.isNotEmpty()) fail("Requests desalineados:\n" + fallos.joinToString("\n"))
    }

    /** El `error` documentado decodifica con [IdentityError] y su code es estable. */
    @Test
    fun erroresDelPackDecodificanConIdentityError() {
        val ops = negocioOps.jsonObject + camarerosOps.jsonObject
        val fallos = mutableListOf<String>()
        for ((clave, op) in ops) {
            if (clave !in MAPA) continue
            val error = op.jsonObject["error"] ?: continue
            if (error !is JsonObject) continue
            val desconocidas = error.keys - setOf("code", "detail")
            if (desconocidas.isNotEmpty()) {
                fallos += "$clave: error con campos no canónicos: $desconocidas"
            }
            val code = error["code"]?.jsonPrimitive?.content.orEmpty()
            if (code.isNotBlank() && !IDENTITY_CODE_RE.matches(code)) {
                fallos += "$clave: code de error no canónico: `$code`"
            }
        }
        if (fallos.isNotEmpty()) fail("Errores desalineados:\n" + fallos.joinToString("\n"))
    }

    /** El mapa cubre TODO lo que Bar llama contra Identity (y no hay entradas obsoletas). */
    @Test
    fun mapaCubreLasRutasQueBarLlama() {
        val llamadas = rutasDelCliente("IdentityNegocioClient.kt") + rutasDelCliente("IdentityCamareroClient.kt")
        val sinMapear = llamadas.filter { it !in MAPA }
        if (sinMapear.isNotEmpty()) {
            fail(
                "El cliente de Bar llama rutas que IdentityContractTest no mapea: " +
                    sinMapear.joinToString(", ") +
                    ". Añade una entrada al mapa (y actualiza el fixture del pack si procede)."
            )
        }
        val ops = (negocioOps.jsonObject.keys + camarerosOps.jsonObject.keys)
            .map { clave -> clave.split(" ", limit = 2).let { opKey(it[0], it.getOrElse(1) { "" }) } }
            .toSet()
        val obsoletas = MAPA.keys.filter { it !in ops }
        if (obsoletas.isNotEmpty()) {
            fail("Entradas obsoletas en el mapa (ya no existen en el pack): " + obsoletas.joinToString(", "))
        }
    }

    // ── Extracción de rutas del cliente (espejo de check_family_contracts.py) ──

    private val REQUEST_RE = Regex(
        """IdentityHttp\.(?:request|requestBytes)\(\s*baseUrl\s*,\s*"([A-Z]+)"\s*,\s*(?:"([^"]*)"|([A-Z_][A-Z0-9_]*))"""
    )
    private val UPLOAD_RE = Regex(
        """IdentityHttp\.uploadMultipart\(\s*baseUrl\s*,\s*(?:"([^"]*)"|([A-Z_][A-Z0-9_]*))"""
    )
    private val CONST_RE = Regex("""const\s+val\s+([A-Z_][A-Z0-9_]*)\s*:\s*String\s*=\s*"([^"]*)${'"'}""")

    private fun rutasDelCliente(nombre: String): Set<String> {
        val fuente = leerFuenteCliente(nombre)
        val consts = CONST_RE.findAll(fuente).associate { it.groupValues[1] to it.groupValues[2] }
        val rutas = mutableSetOf<String>()
        for (m in REQUEST_RE.findAll(fuente)) {
            val method = m.groupValues[1]
            val path = m.groupValues[2].ifBlank { consts[m.groupValues[3]] ?: "" }
            if (path.isNotEmpty()) rutas += opKey(method, path)
        }
        for (m in UPLOAD_RE.findAll(fuente)) {
            val path = m.groupValues[1].ifBlank { consts[m.groupValues[2]] ?: "" }
            if (path.isNotEmpty()) rutas += opKey("POST", path)
        }
        return rutas
    }

    private fun leerFuenteCliente(nombre: String): String {
        val candidatas = listOf(
            Paths.get("src/main/java/com/jaminsmoke/personalbar/lan", nombre), // cwd = app/
            Paths.get("app/src/main/java/com/jaminsmoke/personalbar/lan", nombre), // cwd = raíz
            Paths.get("../app/src/main/java/com/jaminsmoke/personalbar/lan", nombre),
        )
        val fichero = candidatas.firstOrNull { Files.exists(it) }
            ?: error("Fuente del cliente no encontrada: $nombre (cwd=${Path.of(".").toAbsolutePath()})")
        return Files.readString(fichero)
    }

    private companion object {
        val IDENTITY_CODE_RE = Regex("""^identity\.[a-z0-9_]+$""")
    }
}
