<div align="center">

<img src="docs/assets/logo.png" alt="Personal Bar" width="140">

# 🍸 Personal Bar

**El puesto de barra que gestiona la sala.** Colas de Bebida y Comida separadas, nodo LAN de la familia PersonalHostel: los Personal Commander de sala y terraza se conectan aquí, mandan rondas y la barra las prepara y entrega **por destino**.

[🌐 Site](https://jaminsmoke.github.io/PersonalBar/) · [📖 Wiki](https://github.com/jaminsmoke/PersonalBar/wiki) · [Personal Comander](https://github.com/jaminsmoke/PersonalComander) · [PersonalHostel-Server](https://github.com/jaminsmoke/PersonalHostel-Server)

[![Build](https://img.shields.io/github/actions/workflow/status/jaminsmoke/PersonalBar/ci.yml?label=build&color=%23E9C349)](https://github.com/jaminsmoke/PersonalBar/actions)
[![License](https://img.shields.io/github/license/jaminsmoke/PersonalBar?color=%23E9C349)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

**v0.3** · Android 7.0+ (API 24) · Tablet apaisado · [Nodo LAN en el puerto 8787](#nodo-lan)

</div>

---

## El ciclo de una ronda

Una ronda llega del Commander → Bar la parte en **BARRA** (bebida) y **COCINA** (comida) → cada cola avisa a su preparador → **listo por destino** → el camarero la **recoge** y sale de la cola. Las cañas no esperan a la pizza.

## ✨ Features

- 🥤 **Expo de colas por destino** — dos columnas fijas (Bebida y Comida) en la tablet apaisado. Ticket: mesa, ronda, camarero y líneas.
- 📡 **Nodo de sala LAN** — servidor Ktor integrado (puerto **8787**). Los Commander se conectan, envían rondas y reciben el estado en tiempo real (SSE).
- 🔒 **Lista blanca del local** — los camareros se dan de alta por su **QR de identidad** (PersonalHostel-Server). Sin alta no hay acceso, aunque estén en el Wi‑Fi.
- 🗺️ **Mapa de la sala** — salas y mesas canónicas del establecimiento (barra, interior, terraza…), replicadas a los Commander.
- 🎛️ **Gestión** — camareros (lista blanca) y carta del bar, desde el hub de gestión.
- 🛎️ **Preparado con nombre** — cada ticket registra quién lo preparó, simétrico a quién lo pidió.
- 🌙 **Marca dark premium** — design system navy & gold de la familia PersonalHostel.

## 📱 Capturas

| Expo de colas | Mapa de la sala |
|:---:|:---:|
| <img src="docs/screenshots/expo.png" alt="Expo de colas" width="360"> | <img src="docs/screenshots/mapa.png" alt="Mapa de la sala" width="360"> |

| Gestión | Carta |
|:---:|:---:|
| <img src="docs/screenshots/gestion.png" alt="Gestión" width="360"> | <img src="docs/screenshots/carta.png" alt="Carta" width="360"> |

| Ajustes |
|:---:|
| <img src="docs/screenshots/ajustes.png" alt="Ajustes" width="360"> |

## 🚀 Puesta en marcha

### Requisitos

- JDK 17+
- Android SDK con `platforms;android-37`
- Emulador o dispositivo Android 7.0+ (API 24+); **tablet apaisado** recomendada (AVD `Tablet-PixelTablet`)

Lanzar el tablet con la UI en español y hora de Madrid (Windows): `emulador.bat` en la raíz (oculta la consola de `emulator.exe`, puerto **5558** para no chocar con el móvil). Equivalente manual:

```bash
emulator -avd Tablet-PixelTablet -port 5558 -timezone Europe/Madrid -change-locale es-ES
```

### Build y ejecución

```bash
./gradlew installDebug   # instala en el dispositivo/emulador
./gradlew test           # tests unitarios
./gradlew lint           # lint
```

El primer arranque siembra las salas generales (Barra, Interior, Terraza con 4 mesas cada una) y una carta mínima; las colas parten vacías — la primera ronda real llega de un Commander.

## 🧑‍🍳 Uso diario

1. **Colas** — el día a día: Bebida y Comida en paralelo; `preparado` marca quién lo preparó y `recogido` saca el ticket de la cola.
2. **Nodo activo** — el chip «Local» del header enciende/apaga el servidor; el servicio en primer plano mantiene la sala viva con la pantalla apagada.
3. **Mapa** — la sala del establecimiento: salas y mesas canónicas (la fuente de verdad del layout para los Commander).
4. **Gestión** — camareros (pega el QR de identidad para dar de alta) y carta del bar.
5. **Ajustes** — el establecimiento (cuenta y local).

## 🏗️ Arquitectura

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navegación | Secciones del rail + estado local (sin `navigation-compose`) |
| Estado | ViewModel + StateFlow |
| Datos | Room (SQLite) con KSP |
| Nodo LAN | Ktor (CIO), puerto 8787, eventos SSE |
| Min SDK / Target | 24 / 36 |

```
app/src/main/java/com/jaminsmoke/personalbar/
├── data/     # Modelo, DAOs, repos (fuente de verdad del nodo), exportación de layout
├── lan/      # Servidor Ktor, servicios del nodo
└── ui/       # Expo de colas, mapa, gestión, carta, ajustes, componentes Pb*
```

## Nodo LAN

Bar es el **host de sala** de la familia. Puerto fijo: **8787** (solo LAN privada; identidad = HTTPS a PersonalHostel-Server).

| Endpoint | Método | Descripción |
|---|---|---|
| `/health` | GET | Estado del nodo (`establecimiento_id` = UUID Identity cuando está vinculado) |
| `/v1/sesion` | POST | Consulta de lista blanca para el candado UX de Commander |
| `/v1/rondas` | POST | Recibe una ronda (idempotente por `id`) → tickets BARRA/COCINA |
| `/v1/tickets/{id}/preparado` | POST | Marca preparado (con `preparado_por`) |
| `/v1/tickets/{id}/recogido` | POST | Marca recogido; sale de la cola |
| `/v1/estado` | GET | Estado completo (establecimiento, salas, colas, servidos, mesas) |
| `/v1/eventos` | SSE | Push de eventos `ticket.preparado` / `ticket.recogido` |
| UDP **8788** | Beacon | Presencia al activar/cortar Local activo (`phbar1`; Commander confirma con `/health`) |

El **ciclo del ticket** en Bar: `PENDIENTE → PREPARADO → RECOGIDO`. «Preparado» registra quién lo preparó; «Recogido» lo saca de la cola. El cierre del ciclo (SERVIDO, ronda finalizada en mesa) vive en Commander.

Los **payloads completos** (ronda, sesión, evento SSE, contrato del mapa con `posX/posY` y la conversión al canvas de Commander) están en la [wiki — Contrato LAN](https://github.com/jaminsmoke/PersonalBar/wiki/Contrato-LAN).

## 🖼️ Regenerar capturas y assets de marca

Los activos públicos (logo, favicon, social card y capturas de pantalla) se generan con scripts versionados:

```bash
# Logo, favicon y og-image (desde la copa de barra de la marca)
python scripts/generate_assets_bar.py

# Capturas de pantalla (requiere AVD Tablet-PixelTablet activo; apaisado)
ADB_DEVICE=emulator-5554 bash scripts/capture_screens_bar.sh

# Con rondas demo para que la Expo muestre tickets reales (limpia al final)
ADB_DEVICE=emulator-5554 INYECTAR_DEMO=1 bash scripts/capture_screens_bar.sh
```

Ejecútalos después de cualquier cambio de marca o rediseño de UI para mantener coherentes el README y la wiki.

## 🗺️ Roadmap

- **v0.1** — ciclo interno de la ronda (nodo LAN, colas, lista blanca); no hubo GitHub Release.
- **v0.2** — primer corte público: APK/AAB, Identity, carta, mapa, gestión y Local activo.
- **v0.3** — insets del puesto (nav de 3 botones en tablets); el login y el shell no se recortan.
- Después — premium (ver modelo de licencia).

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Consulta la [guía de contribución](CONTRIBUTING.md) y abre issues para bugs o ideas. Para vulnerabilidades, la [política de seguridad](SECURITY.md). El trabajo se planifica en el [kanban del proyecto](https://github.com/users/jaminsmoke/projects/11).

## 📄 Licencia

Publicado bajo la [Licencia MIT](LICENSE).
