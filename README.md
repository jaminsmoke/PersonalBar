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

Bar es el **host de sala**. Puerto fijo: **8787**.

| Endpoint | Método | Respuesta |
|---|---|---|
| `/health` | GET | `{"ok":true,"role":"bar","sala":"vacia","version":"0.1"}` |

```bash
# Desde el host hacia el emulador (tras adb reverse)
adb reverse tcp:8787 tcp:8787
curl http://127.0.0.1:8787/health
```

Cleartext solo en rangos LAN privados (`network_security_config`). Identidad = HTTPS a PersonalHostel-Identity, nunca este puerto.

Los Commanders descubren Bar escaneando el /24 contra el puerto 8787 (patrón `EscaneadorRed` de Commander). El FGS «Sala activa» y el registro de camareros son ítems aparte.

## Hermanos

- [PersonalComander](https://github.com/jaminsmoke/PersonalComander) — sala (cliente). Red LAN diferida hasta que Bar reciba rondas.
- [PersonalHostel-Identity](https://github.com/jaminsmoke/PersonalHostel-Identity) — identidad (Docker `localhost:8080`).
