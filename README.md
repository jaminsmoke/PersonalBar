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
| `/v1/tickets/{id}/listo` | POST | Marca listo un ticket (por destino) |
| `/v1/tickets/{id}/servido` | POST | Marca servido; el ticket sale de cola → servidos |
| `/v1/estado` | GET | Estado completo (establecimiento, salas, colas, servidos, mesas, versión) |
| `/v1/eventos` | SSE | Push de eventos `ticket.listo` / `ticket.servido` |

### Glosario

| Término | Significado | Dueño |
|---|---|---|
| **Establecimiento** (negocio/local) | cuenta del bar, fuente de verdad | Bar (este repo) |
| **Sala** | zona del mapa (barra, interior, terraza…) | mapa del establecimiento, en Bar |
| **Camarero** | identidad + QR | Identity |

Un nodo Bar = un establecimiento en v0.1. Las mesas cuelgan de una sala (`salaId`); el ID de red es `idZona` (p. ej. `T3` = Terraza 3).

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

Bar parte la ronda en tickets **BARRA** (bebida) y **COCINA** (comida); el destino se deriva de la categoría del producto. Listo/servido es **por destino** (cañas ≠ pizza).

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

Los Commanders descubren Bar escaneando el /24 contra el puerto 8787 (patrón `EscaneadorRed` de Commander). El FGS «Local activo» y el registro de camareros son ítems aparte.

## Hermanos

- [PersonalComander](https://github.com/jaminsmoke/PersonalComander) — sala (cliente). Red LAN diferida hasta que Bar reciba rondas.
- [PersonalHostel-Identity](https://github.com/jaminsmoke/PersonalHostel-Identity) — identidad (Docker `localhost:8080`).
