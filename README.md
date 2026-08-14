# Personal Bar

Puesto de **barra** y **nodo de sala** de la familia **PersonalHostel**.  
Repo: https://github.com/jaminsmoke/PersonalBar

Los Personal Commander se conectan aquí. La identidad de los camareros es [PersonalHostel-Identity](https://github.com/jaminsmoke/PersonalHostel-Identity).

Mapa de producto y **flujo kanban completo** (Detectado → Changelog, Debate, CLI): [`AGENTS.md`](AGENTS.md). Setup corto de la CLI: [`tools/README.md`](tools/README.md).

## Estado

Proyecto Android **v0.1 en construcción** (`:app`, Compose + Material 3, Room/KSP, Gradle KTS + Version Catalog). Stack alineado con Personal Commander (AGP 9.3.1, Kotlin 2.4.10, Compose BOM 2026.06.01, minSdk 24 / target 36).

### Abrir en Android Studio

1. `File → Open` y seleccionar esta carpeta (`AndroidStudioProjects/PersonalBar`).
2. Esperar a que Gradle sincronice.
3. Añadir `local.properties` si no existe con `sdk.dir=` apuntando a tu SDK (está gitignored).
4. Run sobre un dispositivo/emulador (requiere API 24+; se desarrolla contra API 37).

Build por CLI:

```bash
./gradlew assembleDebug
```

## Nodo LAN

Bar es el **host de sala**. Servidor Ktor (CIO). Puerto fijo: **8787**.

| Endpoint | Método | Descripción |
|---|---|---|
| `/health` | GET | `{"ok":true,"role":"bar","establecimiento":"La Terraza","sala":"La Terraza","version":"0.1"}` (`sala` = alias deprecado) |
| `/v1/rondas` | POST | Recibe una ronda (idempotente por `id`) → 201 con los tickets BARRA/COCINA |
| `/v1/tickets/{id}/preparado` | POST | Marca **preparado** un ticket (por destino); body `{"preparado_por":"Ana"}` |
| `/v1/tickets/{id}/recogido` | POST | Marca **recogido**; el ticket sale de cola → servidos |
| `/v1/estado` | GET | Estado completo (establecimiento, salas, colas, servidos, mesas, versión) |
| `/v1/eventos` | SSE | Push de eventos `ticket.preparado` / `ticket.recogido` (evento autodescriptivo v1: mesa, camarero, resumen, ticket completo) |

**Ciclo del ticket** (decisión de producto 14-08-2026): en Bar el ticket va **PENDIENTE → PREPARADO → RECOGIDO**.
«Preparado» registra **quién lo preparó** (`preparado_por`, cuenta de camarero de la lista blanca), simétrico a
«quien lo pidió» (`Ronda.camarero`); «Recogido» lo saca de la cola. El cierre del ciclo — **SERVIDO** y ronda
**finalizada**, cuando la ronda llega a la mesa del cliente — vive en **Commander** (ítem diferido de sala LAN
`PVTI_lAHOBM87Yc4BgJWOzg2ZsaU`).

### Glosario

| Término | Significado | Dueño |
|---|---|---|
| **Establecimiento** (negocio/local) | cuenta del bar, fuente de verdad | Bar (este repo) |
| **Sala** | zona del mapa (barra, interior, terraza…) | mapa del establecimiento, en Bar |
| **Camarero** | identidad + QR | Identity |

Un nodo Bar = un establecimiento en v0.1. Las mesas cuelgan de una sala (`salaId`); el ID de red es `idZona` (p. ej. `T3` = Terraza 3).

### Contrato del mapa (`/v1/estado`)

Bar es la **fuente de verdad del layout**; Commander lo replica en solo-lectura cuando está admitido. La identidad de red de una mesa es `idZona` (prefijo de sala + `indiceZona`), nunca el `id` local.

| Campo de mesa (layout) | Tipo | Notas |
|---|---|---|
| `id` | string | id local (no viaja como identidad) |
| `salaId` | string | referencia a `Sala.id` (se reconcilia por nombre/orden) |
| `indiceZona` | int | índice dentro de la sala (B1, T2…) |
| `numero` | int | número global |
| `alias` | string? | nombre visible opcional |
| `forma` | enum | `REDONDA` / `CUADRADA` / `RECTANGULAR` / `RECTANGULAR_XL` |
| `capacidad` | int | plazas |
| `posX`/`posY` | float | posición en el grid (40dp) |
| `girada` | bool | girar rectangulares |
| `bloqueada` | bool | hold comercial |
| `reservaActivaId` | string? | reserva activa (tabla `reservas`) |

El estado operativo (LIBRE/OCUPADA/EN_COCINA) se deriva de las rondas/tickets en Bar y del ciclo de comanda en Commander; no viaja como campo de layout. Los enums serializan por nombre (compatibles con Gson y kotlinx.serialization).

### Payload de ronda (`POST /v1/rondas`)

```json
{
  "id": "r-123",
  "mesaId": "T3",
  "numero": 1,
  "camarero": "Lucía",
  "creadoEn": 1730000000000,
  "lineas": [
    { "productoId": "cana", "nombreProducto": "Caña", "cantidad": 2 },
    { "productoId": "croquetas", "nombreProducto": "Croquetas", "cantidad": 1 }
  ]
}
```

Bar parte la ronda en tickets **BARRA** (bebida) y **COCINA** (comida); el destino se deriva de la categoría del producto. Preparado/recogido es **por destino** (cañas ≠ pizza), y `preparado_por` viaja en el ticket y en el evento SSE.

### Payload del evento SSE (`/v1/eventos`)

Cada evento es un JSON autodescriptivo (v1): `event:` = `tipo`, `data:` = payload.

```json
{
  "version": 1,
  "tipo": "ticket.preparado",
  "ticketId": "r-123-barra",
  "preparadoPor": "Ana",
  "mesaId": "T3",
  "camarero": "Lucía",
  "resumen": "2× Caña, 1× Croquetas",
  "ticket": {
    "id": "r-123-barra",
    "rondaId": "r-123",
    "destino": "BARRA",
    "estado": "PREPARADO",
    "preparadoPor": "Ana",
    "numeroCola": 1,
    "lineas": [
      { "productoId": "cana", "nombreProducto": "Caña", "cantidad": 2 }
    ]
  }
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `version` | int | `1` (contrato del evento) |
| `tipo` | string | `ticket.preparado` / `ticket.recogido` (también el `event:` SSE) |
| `ticketId` | string | id del ticket (por destino: `{rondaId}-barra` / `{rondaId}-cocina`) |
| `preparadoPor` | string? | quién lo elaboró (cuenta de camarero) |
| `mesaId` | string? | idZona de red de la mesa (`T3`), desde `Ronda.mesaId` |
| `camarero` | string? | quién pidió la ronda (`Ronda.camarero`) |
| `resumen` | string | líneas legibles («2× Caña, 1× Croquetas») |
| `ticket` | Ticket? | el ticket completo (ronda, destino, estado, `numeroCola`, líneas) |

`destino` serializa como enum `BARRA`/`COCINA` (Commander mapea a Bebida/Comida). Los campos nuevos son opcionales (default) para backward-compatibilidad: un Commander antiguo ignora los desconocidos y uno nuevo decodifica eventos antiguos con `mesaId`/`ticket` en `null`.

### Probar desde el host

```bash
adb forward tcp:18787 tcp:8787

# Health
curl http://127.0.0.1:18787/health

# Enviar una ronda
curl -X POST http://127.0.0.1:18787/v1/rondas \
  -H 'Content-Type: application/json' \
  -d '{"id":"r-1","mesaId":"T3","numero":1,"camarero":"Lucía","lineas":[{"productoId":"cana","nombreProducto":"Caña","cantidad":2}]}'

# Estado
curl http://127.0.0.1:18787/v1/estado

# Eventos (SSE)
curl -N http://127.0.0.1:18787/v1/eventos
```

Cleartext solo en rangos LAN privados (`network_security_config`). Identidad = HTTPS a PersonalHostel-Identity, nunca este puerto.

Los Commanders descubren Bar escaneando el /24 contra el puerto 8787 (patrón `EscaneadorRed` de Commander).

### Servicio en primer plano «Local activo»

El nodo vive en un foreground service (`BarLanService`) para sobrevivir con la **pantalla bloqueada**:

- Notificación persistente «Local activo» (canal `local_activo`).
- `foregroundServiceType="connectedDevice"` (los Commander son dispositivos externos en LAN).
- partial `WakeLock` + `WifiLock` para que la CPU y el Wi‑Fi no se duerman en doze.
- El indicador «Local activo» de la barra superior es un **toggle**: arranca/para el service y el server.
- Sin arranque al boot en v0.1 (se activa a mano).

Permisos: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` (runtime en API 33+), `WAKE_LOCK`.

### Lista blanca de camareros (Identity)

Bar guarda la **lista blanca del establecimiento** (a quién acepta en la LAN). La identidad canónica vive en Identity, que emite el QR permanente `phid1:<camarero_id>:<credencial_id>:<firma-ed25519>`.

**Conexión** (Ajustes → Identity): URL de Identity + email/contraseña de la **cuenta de negocio** → `POST /v1/auth/negocio/login`; Bar crea/encuentra su establecimiento y guarda el UUID. La config se **persiste en Room** (sobrevive a reinicios). Desde el emulador, Identity corre en el host en `http://10.0.2.2:8080`.

Dos canales de alta:
- **QR** (verificado): al pegar el `phid1`, Bar llama `POST /v1/establecimientos/{id}/miembros/qr`; el server verifica la firma Ed25519 y la credencial activa. Si la rechaza, no da de alta. Sin Identity conectado → alta local sin verificar (fallback v0.1).
- **Email**: sección «Invitar por email» → `GET /camareros/buscar?email=` valida que el email existe → `POST /invitaciones` → Identity **envía el correo con el magic-link** (TTL 72 h). La aceptación ocurre en **Identity Web** (fuera de Bar); Bar muestra el estado (pendiente/revocada) y puede revocar. «Sincronizar desde Identity» trae los miembros ACTIVA al espejo local.

La lista local (persistida en Room) sigue siendo la fuente para la LAN; Identity es el espejo (alta/revocación reflejada). La validación del login de red del Commander contra esta lista se completa cuando Commander envíe su QR.

**División de oficios**: la **cuenta de camarero** (y su nick visible) se crea y gestiona en **Commander** — la app específica de camareros (ítem `PVTI_lAHOBM87Yc4BgJWOzg2gWTY`). **Bar** es el puesto de **gestión del negocio**: asigna camareros al establecimiento (lista blanca) y **recoge** la info de la cuenta desde Identity, pero **no crea ni edita** datos de camareros. En el puesto, varios camareros pueden estar **de servicio a la vez** (chips «Quién soy»); el que prepara («en mano») es el último chip pulsado.

### Varios preparadores en el puesto

- `Camarero.deServicio` (migración Room v3): lista de camareros **de servicio** en el turno, persistida (sobrevive a reinicios).
- La barra «Quién soy» muestra **chips múltiples**: tap = añadir/quitar de servicio; el texto «de servicio» fija el chip como **«en mano»** (el que prepara al tocar «Marcar preparado»).
- `nombre`/`email` se rellenan desde Identity cuando el dato existe (invitación por email / sync); **sin editor local** — Bar no edita la cuenta.

## Persistencia (Room)

El nodo **persiste todo en Room** (`personalbar.db`, esquema v1 exportado a `app/schemas/`): establecimiento, salas/mesas (layout), catálogo, rondas, tickets (colas + servidos), reservas, camareros (lista blanca), invitaciones y la config de Identity.

- **Arquitectura**: `RoomBarRepository` envuelve el `InMemoryBarRepository` (cerebro: lógica, colas, idempotencia, secuencias). Cada mutación actualiza el estado en memoria y **persiste por dominio** en un scope serializado; si una escritura falla, el estado en memoria sigue mandando y se re-persiste en la siguiente mutación.
- **Arranque**: carga todo de Room; si la BD está vacía (primera instalación) siembra el seed demo (establecimiento «La Terraza», 3 salas, catálogo, 4 mesas, 2 rondas).
- **No se persisten** los eventos SSE (`SalaEvent`): al reconectar, Commander re-sincroniza por `/v1/estado`.
- **Migraciones**: schema exportado desde v1 para versionar cambios futuros igual que Commander. v2 añade `tickets.numeroCola` (id de cola visible/hablable) con `MIGRATION_1_2` + backfill por destino en `RoomBarRepository` al cargar.
- Los eventos `ticket.preparado`/`ticket.recogido` y el `preparadoPor` sobreviven al reinicio (los tickets se persisten con su estado).

### Id visible de cola («Cola N Bebida/Comida»)

Cada ticket en cola tiene un **id de orden estable y hablable** por destino (`numeroCola`, migración Room v2): «Cola 1 Bebida», «Cola 2 Comida»…

- **Monótono en el turno, no compacta**: si se recoge Cola 1 Bebida, Cola 2 Bebida sigue siendo 2 (ancla estable para voz y tacto; una ronda = 2 tickets, mesa+ronda sería ambiguo).
- **Color por estado**: tarjeta **amarilla** PENDIENTE (post-it) y **verde** PREPARADO (listo), fuera del scheme como los tokens del mapa. Las recogidas salen de expo.
- Se muestra en la tarjeta de la expo y en el sheet de comanda del mapa (misma `PbTicketCard`).

## Sesión de la cuenta de establecimiento

Bar se identifica con la **cuenta de negocio/establecimiento** (no con camareros). En el **header** hay un icono de cuenta que abre un modal con **dos flujos separados**:

- **Crear cuenta** (registro): nombre, email, contraseña, **tipo** (bar/restaurante/cafetería/pub/bar de copas) y **logo opcional** (picker de imagen). Llama a `POST /v1/auth/negocio/registro` (envía el tipo), luego vincula el establecimiento (`/v1/establecimientos`) y sube el logo (`POST /v1/auth/negocio/me/logo`, multipart).
- **Iniciar sesión** (login): email + contraseña, con **«Recuérdame»**: si se marca, la sesión (token + perfil) se **persiste en Room** (`sesion_negocio`, migración v5) y se restaura al arrancar; si no, solo en memoria.

Una vez logueado, el header muestra el **nombre del establecimiento** y su **logo real** (descargado de Identity; si no hay logo o falla, solo el nombre); **nada de camareros** (los camareros se gestionan en Camareros, dentro de Gestión). El usuario **no ve la URL del server Identity** (config de entorno; en dev `http://10.0.2.2:8080`, en producción un VPS).

**Tipo y logo se sincronizan contra Identity** (fuente canónica): el registro envía `tipo_establecimiento`, el login recupera `tipo_establecimiento` + `logo_url` del perfil y Bar persiste el `logoUrl` en la sesión (Room v5 sustituye el antiguo placeholder `logoClave`).

## Voz en colas

La sección Colas tiene una **barra de escucha por voz** (botón grande con micrófono bajo «Quién soy») para cambiar el estado de una orden **ya identificada** por su id de cola. Reutiliza el `SpeechRecognizer` de Commander (timeouts RMS, `es-ES`, silencio permisivo en barra ruidosa) con otra gramática:

- **Preparado**: `<nombre del preparador> Cola N <Bebida|Comida> preparado` — p. ej. «Lucía, Cola 1 Bebida preparado». El nombre debe estar en la lista blanca ACTIVA; si no se reconoce, se rechaza (no se escribe texto crudo). Sin nombre, se atribuye al camarero «en mano».
- **Recogido**: `[nombre] Cola N <Bebida|Comida> recogido` — el nombre es opcional y se ignora al casar.

El parser acepta números en letra («cola uno», «treinta y cinco»), relleno («de», «la») y sinónimos de acción. El destino hablado es **Bebida/Comida** (nunca «Cocina», que es el nombre interno del enum). Permiso `RECORD_AUDIO` (runtime). Sin motor de voz en el dispositivo, el botón informa del error y no entra en escucha.

## Hermanos

- [PersonalComander](https://github.com/jaminsmoke/PersonalComander) — sala (cliente). Red LAN diferida hasta que Bar reciba rondas.
- [PersonalHostel-Identity](https://github.com/jaminsmoke/PersonalHostel-Identity) — identidad (Docker `localhost:8080`).
