# AGENTS.md — Personal Bar

Familia **PersonalHostel** (Comander, Bar, Kitchen, TPV, servidor de identidad).

- Repo: `jaminsmoke/PersonalBar`
- Kanban: GitHub Project de este repo (no el de Commander)
- CLI: `bun run tools/kanban-cli/cli.ts`  (ver `tools/README.md`)
- Skills: `tools/agent-skills/jarvis-github-kanban` y `jarvis-github-agentuse`

Al crear, mover o cerrar ítems: leer esas skills. Ciclo Detectado → Debate → Roadmap → Ejecutando → Verificando → Changelog. En Debate, 4 opciones y **parar a preguntar**. Convertir draft→issue solo al entrar en Ejecutando. Labels: 1 `tipo:*` + 1 `area:*`.

## Qué es esto

App Android de **puesto de barra** y **nodo de sala LAN** de la familia Personal. Quien está en barra (poco se mueve) recibe las rondas de los Personal Commander, las prepara (comida y bebida **por separado**) y avisa cuando están listas para recoger.

- Carpeta local: `AndroidStudioProjects/PersonalBar` (hermana de `PersonalComander` y `PersonalHosteleriaServer`)
- Trabajo de **otro equipo / otro agente**. Commander no implementa el nodo aquí.
- Este scaffold es **mapa de intenciones**, no la app Gradle todavía. El equipo Bar crea el proyecto Android (Compose / Material 3, alineado a Commander) en este repo.

Si eres el agente que continúa aquí: lee este archivo entero antes de generar el módulo `app`. No implementes TPV, Kitchen, rankings ni recortes en Commander.

## Relación con el resto

```
[Identidad — PersonalHosteleriaServer]     QR permanente, login, foto
        ▲
        │ HTTPS (cuando exista registro)
        │
[Personal Bar  ← ESTE REPO]  ◄──LAN──►  [Personal Comander…]
  nodo de sala (fuente de verdad)         clientes: mapa + comandas
  lista blanca del local                  alta = QR pasado a Bar
  colas: bebida | comida
```

| Repo | Oficio | Rol |
|---|---|---|
| **Personal Bar** (este) | Expo barra + **host LAN** | Fuente de verdad de mesas, rondas, tickets |
| Personal Comander | Sala / terraza | Cliente LAN; un tablet solo sigue offline hasta que este nodo reciba |
| PersonalHosteleriaServer | Identidad de profesionales | Docker local `localhost:8080` (scaffold); no es el nodo de sala |
| Personal Kitchen (futuro) | Tickets de comida | Se lleva el destino COCINA; Bar se queda BARRA |
| Personal TPV (futuro) | Cobro / contabilidad | Puede **heredar el nodo**; Bar sigue siendo expo |

Kanban Commander (diferido): *Sala LAN: Personal Bar como nodo…* `PVTI_lAHOBM87Yc4BgJWOzg2ZsaU`. Se reabre cuando **este** repo pueda recibir una ronda. Hasta entonces Commander **no** quita `enviarACocina`.

## Producto (acordado)

1. **Oficio distinto a Commander.** No es un Commander «en modo barra». UI principal = colas de tickets, no el board de mesas como trabajo diario (el mapa puede existir como vista secundaria para ver la sala).
2. **Nodo:** los Commander de sala/terraza se conectan a **esta** instancia. Multi-dispositivo **exige** Bar. No hay Commander-host de respaldo en el diseño actual.
3. **Alta de camareros:** QR/clave **permanente** (identidad de org). El profesional se registra en Hostelería Server; pasa el QR a Bar; Bar lo mete en la lista blanca de **ese** local. Sin alta, el Wi‑Fi no basta.
4. **Rondas:** Commander envía una ronda (no «toda la mesa a cocina»). Bar la parte en tickets **BARRA** (bebida) y **COCINA** (comida) hasta que exista Kitchen; entonces se deja de mostrar comida aquí.
5. **Listo por destino** dentro de la ronda: las cañas pueden estar listas sin la pizza. Commander muestra «para recoger»; el camarero marca servido; el ticket sale de la cola y se acumula en servido.
6. **Mapa y comandas** se replican a todos los Commander dados de alta. Editar mesa de otro está permitido; la auditoría llega con el login (nombre), no bloquea el primer recorte.
7. **Catálogo y layout de mesas** canónicos en el nodo (no 16 mesas seed distintas por tablet).
8. El aparato de Bar conviene que se quede encendido (servicio en primer plano «Sala activa»). Si Bar se apaga, la sala se queda ciega.

## Qué hay ahora (scaffold)

```
PersonalBar/
├── AGENTS.md              # este mapa (léelo primero)
├── README.md
└── .gitignore             # listo para cuando exista el módulo Android
```

No hay `app/` ni Gradle. El primer entregable de **este** equipo es crear el proyecto Android en esta carpeta (mismo nivel de calidad de UI que Commander) y un servidor LAN mínimo (health + «sala vacía»).

## Contrato LAN (intención, no implementado)

El detalle vive aquí hasta que Commander retome el ítem diferido. Propuesta de superficie:

| Idea | Notas |
|---|---|
| Descubrimiento | Reutilizar la idea de escaneo TCP de Commander (`TpvCliente` /24) o mDNS `_personalbar._tcp`. Puerto fijo documentado. |
| Auth de sala | Commander presenta el QR/id de identidad; Bar acepta o rechaza (lista blanca). |
| Estado | Mesas (identidad estable `zona`+`indiceZona` o UUID, **no** `Mesa.id` local), pedidos, rondas, líneas, destinos. |
| Eventos | ronda enviada → ticket en cola; ticket listo → aviso al Commander; servido → sale de expo. |
| Cleartext | Solo LAN privada, igual que Commander (`network_security_config`). |

Cleartext en internet para identidad: no. Identidad = HTTPS al Hostelería Server.

## UI a diseñar (equipo Bar)

- Cola **Bebida** y cola **Comida** (separadas, mismo dispositivo).
- Ticket: mesa, ronda, camarero (cuando haya nombre), líneas, acciones listo.
- Alta de camarero (pegar/escanear QR).
- Estado de sala (host activo / tablets conectados).
- Vista mapa opcional, no el flujo principal.

Strings en español (`res/values`). Marca dark premium / gold alineada a Commander si se comparte tema; no copiar el APK entero.

## Qué no hacer

- No copiar Personal Comander y «cambiar el título».
- No sync P2P entre Commanders como fuente de verdad.
- No implementar el servidor de identidad aquí (repo hermano Docker).
- No pedir a Commander que borre `enviarACocina` hasta que `POST`/`evento` de ronda exista de verdad.
- No rankings ni marketplace.
- No Kitchen/TPV.

## Primeras tareas (para el agente de este repo)

1. Proyecto Android (package sugerido `com.jaminsmoke.personalbar`), min SDK 24, Compose + Material 3.
2. Pantalla cola stub (dos columnas comida/bebida vacías) + «Sala activa».
3. Servidor HTTP/LAN local de health (`GET /health`) para que un Commander de prueba pueda descubrir el host.
4. Modelo mental Room o en memoria: mesa canónica, ronda, ticket destino.
5. Flujo alta QR (aunque el servidor de identidad aún sea scaffold: aceptar un código pegado).
6. Cuando haya remoto GitHub: equipo propio; Commander solo visibilidad.

## Stack de arranque (provisional)

Alinear con Commander salvo razón fuerte: Kotlin, Compose, Material 3, Gradle KTS. El servidor LAN embebido (Ktor, NanoHTTPD, etc.) lo elige este equipo; documentar el puerto en el README.

## Cómo seguir

Ver `README.md`. No hay `./gradlew` todavía.
