# AGENTS.md — Personal Bar

## Project

**Personal Bar** is the bar station and **LAN room node** of the **PersonalHostel** family (Comander, Bar, Kitchen, TPV, identity server). Staff at the bar receive rounds from Personal Commander, prepare food and drink **separately**, and mark tickets ready for pickup.

- Repo: `jaminsmoke/PersonalBar`
- Local folder: `AndroidStudioProjects/PersonalBar` (sibling of `PersonalComander` and `PersonalHosteleriaServer`)
- Package (suggested): `com.jaminsmoke.personalbar`
- Min SDK (align with Commander): 24
- Version target: **v0.1** (no releases yet — `gh release list`; next version must be > latest)
- This scaffold is an **intent map**, not a Gradle app yet. This team creates the Android project here.

If you are the agent continuing here: read this **entire** file before generating `app/`. Do not implement TPV, Kitchen, rankings, or cut Commander (`enviarACocina`) from this repo.

## Familia PersonalHostel

Familia de producto de hostelería. Owner GitHub: [`jaminsmoke`](https://github.com/jaminsmoke) (cuenta personal). **No** es [SiberIA-Solutions](https://github.com/SiberIA-Solutions) (empresa de desarrollo). No hay organización GitHub de producto: los repos se agrupan con **esta tabla**.

**Al nacer un miembro nuevo** (Kitchen, TPV, …) se añade una fila aquí **en todos** los `AGENTS.md` de la familia.

| App | Repo | Oficio | Kanban |
|---|---|---|---|
| **Personal Comander** | [`jaminsmoke/PersonalComander`](https://github.com/jaminsmoke/PersonalComander) | App del camarero (móvil vertical): mesas, comanda, cuenta profesional | [Project 9](https://github.com/users/jaminsmoke/projects/9) |
| **Personal Bar** (este) | [`jaminsmoke/PersonalBar`](https://github.com/jaminsmoke/PersonalBar) | Puesto del negocio (tablet apaisada): nodo LAN `:8787`, colas, lista blanca, mapa | [Project 11](https://github.com/users/jaminsmoke/projects/11) |
| **PersonalHostel Identity** | [`jaminsmoke/PersonalHostel-Identity`](https://github.com/jaminsmoke/PersonalHostel-Identity) | Registro canónico (Docker/VPS): camareros `:8080`, negocio `:8082` | [Project 10](https://github.com/users/jaminsmoke/projects/10) |

Kanban: cada app tiene el suyo. Cambio que necesite al otro lado → Detectado en **su** Project. Commander no llama a `:8082`.

## Stack (provisional)

Align with Commander unless there is a strong reason not to.

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | `androidx.navigation.compose` |
| State | `ViewModel` + `StateFlow` |
| DB | Room (SQLite) with KSP — or in-memory until Debate decides |
| Build | Gradle KTS + Version Catalog |
| LAN server | Chosen in Debate (Ktor, NanoHTTPD, …). Document the port in README. |
| Desugaring | `desugar_jdk_libs` if Java 11 APIs are needed on API 24 |

There is no `./gradlew` until the bootstrap item lands.

## Relación con el resto

```
[Identidad — PersonalHostel-Identity]     QR permanente, login, foto
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
| PersonalHostel-Identity | Identidad de profesionales | Docker local `localhost:8080` (scaffold); no es el nodo de sala |
| Personal Kitchen (futuro) | Tickets de comida | Se lleva el destino COCINA; Bar se queda BARRA |
| Personal TPV (futuro) | Cobro / contabilidad | Puede **heredar el nodo**; Bar sigue siendo expo |

Kanban Commander (diferido): *Sala LAN: Personal Bar como nodo…* `PVTI_lAHOBM87Yc4BgJWOzg2ZsaU`. Se reabre cuando **este** repo pueda recibir una ronda. Hasta entonces Commander **no** quita `enviarACocina`.

## Producto (acordado)

1. **Oficio distinto a Commander.** No es un Commander «en modo barra». UI principal = colas de tickets, no el board de mesas como trabajo diario (el mapa puede existir como vista secundaria para ver la sala).
2. **Nodo:** los Commander de sala/terraza se conectan a **esta** instancia. Multi-dispositivo **exige** Bar. No hay Commander-host de respaldo en el diseño actual.
3. **Alta de camareros:** QR/clave **permanente** (identidad de org). El profesional se registra en Identity; pasa el QR a Bar; Bar lo mete en la lista blanca de **ese** local. Sin alta, el Wi‑Fi no basta.
4. **Rondas:** Commander envía una ronda (no «toda la mesa a cocina»). Bar la parte en tickets **BARRA** (bebida) y **COCINA** (comida) hasta que exista Kitchen; entonces se deja de mostrar comida aquí.
5. **Listo por destino** dentro de la ronda: las cañas pueden estar listas sin la pizza. Commander muestra «para recoger»; el camarero marca servido; el ticket sale de la cola y se acumula en servido.
6. **Mapa y comandas** se replican a todos los Commander dados de alta. Editar mesa de otro está permitido; la auditoría llega con el login (nombre), no bloquea el primer recorte.
7. **Catálogo y layout de mesas** canónicos en el nodo (no 16 mesas seed distintas por tablet).
8. El aparato de Bar conviene que se quede encendido (servicio en primer plano «Sala activa»). Si Bar se apaga, la sala se queda ciega.

## Qué hay ahora (scaffold)

```
PersonalBar/
├── AGENTS.md                 # este archivo (léelo primero)
├── README.md
├── .gitignore
├── .kanbanrc.json.template   # IDs del Project (versionado)
├── .kanbanrc.json            # copia local (gitignored)
└── tools/
    ├── README.md
    ├── kanban-cli/           # bun install aquí
    └── agent-skills/         # jarvis-github-kanban + jarvis-github-agentuse
```

No hay `app/` ni Gradle.

## Contrato LAN (intención, no implementado)

El detalle vive aquí hasta que Commander retome el ítem diferido. Propuesta de superficie:

| Idea | Notas |
|---|---|
| Descubrimiento | Reutilizar la idea de escaneo TCP de Commander (`TpvCliente` /24) o mDNS `_personalbar._tcp`. Puerto fijo documentado. |
| Auth de sala | Commander presenta el QR/id de identidad; Bar acepta o rechaza (lista blanca). |
| Estado | Mesas (identidad estable `zona`+`indiceZona` o UUID, **no** `Mesa.id` local), pedidos, rondas, líneas, destinos. |
| Eventos | ronda enviada → ticket en cola; ticket listo → aviso al Commander; servido → sale de expo. |
| Cleartext | Solo LAN privada, igual que Commander (`network_security_config`). |

Cleartext en internet para identidad: no. Identidad = HTTPS al servidor Identity.

## UI a diseñar

- **Target de dispositivo v0.1: tablet apaisado (landscape) solo.** Bar es un puesto estático (nodo de sala), a diferencia de Commander (móvil vertical). No se adapta a móvil en v0.1. Pruebas en emulador Pixel Tablet.
- Cola **Bebida** y cola **Comida** (separadas, mismo dispositivo; dos columnas fijas aprovechando el ancho de la tablet, no tabs).
- Ticket: mesa, ronda, camarero (cuando haya nombre), líneas, acciones listo.
- Alta de camarero (pegar/escanear QR).
- Estado de sala (host activo / tablets conectados).
- Vista mapa opcional, no el flujo principal.

Strings en español (`res/values`). Marca dark premium / gold alineada a Commander si se comparte tema; no copiar el APK entero.

## Oficios (división acordada 14-08-2026)

- **Commander** es la app **específica de camareros**: crea y gestiona la cuenta (incluido el nick visible en los establecimientos, ítem `PVTI_lAHOBM87Yc4BgJWOzg2gWTY`).
- **Bar** es el puesto de **gestión del negocio**: asigna camareros al establecimiento (lista blanca) y **recoge** la info de las cuentas desde Identity, pero **no crea ni edita** datos de camareros (no hay editor local de nombre).
- En el puesto pueden estar **varios camareros de servicio a la vez** (`Camarero.deServicio`); el «en mano» (último chip pulsado) es quien prepara.

## Qué no hacer

- No copiar Personal Comander y «cambiar el título».
- No sync P2P entre Commanders como fuente de verdad.
- No implementar el servidor de identidad aquí (repo hermano Docker).
- No pedir a Commander que borre `enviarACocina` hasta que `POST`/`evento` de ronda exista de verdad.
- No rankings ni marketplace.
- No Kitchen/TPV.
- No saltarse Debate ni convertir draft→issue antes de Ejecutando.

## 🎯 Kanban workflow — GitHub Project

El trabajo se rastrea **solo** en el Project de este repo: [github.com/users/jaminsmoke/projects/11](https://github.com/users/jaminsmoke/projects/11) (`PVT_kwHOBM87Yc4BgQqa`). **No** uses el kanban de Commander ni el de Identity.

Antes de crear, mover o cerrar ítems, lee:

- `tools/agent-skills/jarvis-github-kanban/SKILL.md`
- `tools/agent-skills/jarvis-github-agentuse/SKILL.md`

### Lifecycle

```
Detectado → Debate → Roadmap → Ejecutando → Verificando → Changelog
  Draft      Draft     Draft     Issue OPEN    Issue OPEN    Issue CLOSED
```

**Drafts** until `Ejecutando` — NEVER convert to issue before that.

**No skipping**: every item advances in order. Exception: `Cancelado` → Changelog.

**Version always > latest release**: consult `gh release list`, pick the next one (currently **v0.1**).

Bodies in UTF-8. On Windows do **not** pipe PowerShell `Get-Content` into the CLI (mojibake). Prefer Python or `--body-file` / `gh issue edit --body-file`.

#### 1. Detectado — Describir el problema a fondo

El body debe contener una descripción **muy completa** del item y del problema detectado. No perder contexto: cuanta más información se documente aquí, más fácil será retomarlo en el futuro.

- Rellenar TODAS las secciones de la plantilla con contenido específico, no placeholders.
- Incluir: archivos exactos, líneas de código, trazas, versiones, métricas, capturas si aplica.
- Describir el impacto real en el usuario/producto, no solo el síntoma técnico.

#### 2. Debate — Preguntar al usuario, NO decidir solo

**Regla de oro**: NUNCA pasar de Debate a Roadmap sin preguntar al usuario y recibir su aprobación explícita.

**Investigación previa (obligatoria al entrar en Debate, antes de listar opciones)**:

Al pasar a Debate — y **antes** de redactar `Alternativas` — investigar a fondo la causa y el espacio de soluciones. Documentar en el body la sección `Investigación previa`:

- Archivos, flujos y dependencias leídos (con rutas concretas).
- Hipótesis de causa(s): los ítems suelen ser **multicausales**; no quedarse en el síntoma superficial.
- Patrones del proyecto / ecosistema relevantes (Compose, Room, Material 3, HTTP embebido, LAN).
- Restricciones reales (API, datos, UX en barra, alcance de versión, contrato con Commander/Identity).
- Qué se descartó y por qué (aunque sea breve).
- **Estrategia de rama / integración**: ¿el cambio justifica rama dedicada (`feature/...`) vs trabajo en `main`? Anotar propuesta (nombre de rama, merge a `main` al Changelog, PRs si aplica). En cambios grandes (proyecto Android inicial, servidor LAN, migraciones, features transversales) la rama dedicada es la opción por defecto a contemplar.

Sin esta sección no se presentan las opciones. La investigación vive en Debate (no hincha Detectado); Detectado aporta el problema, Debate aporta el mapa de soluciones.

**Formato fijo de alternativas** — siempre presentar exactamente estas **4** opciones (en este orden):

1. **Solución raíz** 🌳 — va al origen del problema (modelo, arquitectura, navegación, contrato de datos, identidad de producto…). No se limita a “hacerlo bien dentro de lo que hay”; puede proponer rediseño o cambio de enfoque. Exige basarse en la `Investigación previa`. Si el problema es genuinamente superficial y no hay causa estructural, indicar **"no aplica"** con una frase de justificación (casi siempre sí conviene explorarla: un bug “simple” puede esconder una solución más robusta).
2. **Opción sólida** 🏗️ — la más correcta y robusta **dentro del diseño actual** (o con cambios acotados). Mejor arquitectura/mantenibilidad/escalabilidad sin replantear el sistema entero.
3. **Opción rápida** ⚡ — la más rápida de implementar. Puede coincidir o no con la sólida/raíz. Prioriza velocidad sobre perfección.
4. **Opción intermedia** ⚖️ — equilibrio entre profundidad y velocidad. Solo cuando exista un punto medio real; si no hay, indicar "no aplica".

Cada opción debe llevar:
- Descripción clara de la solución
- Número estimado de líneas/cambios
- Pros (✅) y contras (⚠️)

**Recomendación situacional (revisar por ítem)**: al final, recomendar una opción **según el contexto concreto de ese ítem**, no por regla mecánica. Orientaciones de partida (siempre contrastarlas con lo hallado en la investigación):

- Bug crítico en producción → suele favorecer la **rápida** (mitigar ya), sin ocultar si la raíz merece un follow-up.
- Mejora sin urgencia → suele favorecer la **sólida** o la **raíz**, según si el diseño actual basta o hay que replantear.
- Deuda técnica acumulada → suele favorecer la **intermedia** o la **raíz** si la deuda es estructural.
- Rediseño / identidad de producto / problema multicausal profundo → valorar explícitamente la **raíz**.

La recomendación debe citar **por qué** encaja este ítem (1–3 frases), no solo etiquetar el tipo.

**Proceso**:
- Añadir secciones `Investigación previa`, `Análisis`, `Alternativas` (con las 4 opciones) y `Recomendación` al body.
- **Parar y preguntar** al usuario. Solo cuando él decida, marcar `Decision: Aprobado` y mover a Roadmap.
- Si `Decision: Cancelado` → documentar motivo, convertir a issue, cerrar, mover a Changelog.
- Si `Decision: Diferido` → documentar motivo y condición, devolver a Detectado.

#### 3. Roadmap — Planificar en profundidad antes de tocar código

Con la decisión ya tomada y acordada en la fase anterior, detallar **mucho más** el plan de implementación.

- Investigar a fondo: leer archivos relacionados, imports necesarios, dependencias, posibles efectos colaterales (incluido el contrato con Commander).
- Revisar si el plan acordado en Debate se queda corto — añadir lo que falte.
- Documentar: `Decisión acordada`, `Plan aprobado` (paso a paso), `Criterios de aceptación`, `Plan de verificación`, `Riesgos y recuperación`.
- Solo cuando el plan sea sólido y completo, mover a Ejecutando.

#### 4. Ejecutando — Implementar el plan

- Al entrar: convertir draft → issue, añadir labels (1 Tipo + 1 Área). **Aquí empieza el código.**
- Implementar siguiendo el plan detallado de Roadmap.
- Si algo difiere del plan original, **documentarlo** en el body (sección `Implementación`) explicando el porqué del cambio.
- Hacer commits locales con mensajes descriptivos.

#### 5. Verificando — Tests, lint y comprobaciones exhaustivas

**No es solo compilar.** Es verificar que el cambio funciona, no rompe nada y cumple estándares de calidad.

**Checklist obligatorio** (cuando exista `./gradlew`, ejecutar TODO):

1. **Typecheck**: `./gradlew assembleDebug` — debe ser BUILD SUCCESSFUL
2. **Tests unitarios**: `./gradlew test` — todos deben pasar
3. **Lint**: `./gradlew lint` — debe pasar sin errores
   - Si hay **errores que introdujimos**, corregirlos obligatoriamente
   - Si hay **warnings preexistentes** relacionados con nuestro cambio, corregirlos si es posible
   - Si hay **warnings no relacionados**, documentarlos pero no es bloqueante
4. **Tests nuevos**: crear tests unitarios para la lógica nueva si no existen
   - ViewModel: test de funciones principales
   - Parser / contrato LAN: test de payloads si se modificó
   - Funciones puras: test de utilidades nuevas
   - NO crear tests de UI (Compose) salvo que sea crítico
5. **Revisión visual**: si hay cambios UI, verificar en emulador que se ve correcto

Hasta que no exista Gradle: documentar en `Verificación` qué se comprobó (p. ej. README, `config validate`) y no fingir `assembleDebug`.

**Validaciones adicionales según el área**:

- UI/UX → `assembleDebug`, revisión visual (expo de colas, no un board de mesas como home).
- Datos → tests de Room, migraciones, integridad (mesa canónica, ronda, ticket).
- Sync → `GET /health`, descubrimiento LAN, ronda de prueba si el ítem lo pide.
- Android → ciclo de vida, FGS «Sala activa», permisos.
- Docs → el README y este archivo siguen siendo verdad.

**Reglas de lint** (cuando apliquen, igual que Commander):

- `LocalContextGetResourceValueCall`: usar `stringResource()` en composables, no `context.getString()`
- `EmptySuperCall`: eliminar `super.onCleared()` si el método está vacío
- `UnusedResources`: eliminar strings no usados
- `OldTargetApi` / `NotShrinkingResources`: no corregir sin autorización

**Antes de pasar a Changelog**:
- Documentar TODO en el body: sección `Verificación` con checklist de lo ejecutado y resultados
- Si se encontraron y corrigieron errores preexistentes, documentarlos
- Hacer commit con los fixes de verificación
- Solo cuando todo esté verificado, pasar a Changelog

#### 6. Changelog — Cerrar, fechar y publicar

1. **Commit final** con mensaje descriptivo (si no se hizo ya en Verificando).
2. Anotar el **SHA del commit** en el body (sección `Commit`).
3. Mover status a `Changelog`.
4. Setear `Completado` (fecha) y `Completado exacto` (ISO-8601).
5. Añadir ✅ al título del issue.
6. Cerrar el issue (`gh issue close -r completed`).
7. **Push** a la rama de trabajo (normalmente `main`).

### CLI (all commands from project root)

```bash
KANBAN="bun run tools/kanban-cli/cli.ts"

# Primera vez en la máquina
cd tools/kanban-cli && bun install && cd ../..
copy .kanbanrc.json.template .kanbanrc.json   # Windows
# cp .kanbanrc.json.template .kanbanrc.json
$KANBAN config validate

# Create item
$KANBAN create --title "..." --tipo Feature --area Android --priority Alta --version "v0.1"

# List
$KANBAN list

# Show item
$KANBAN show <itemId>

# Read/set body
$KANBAN body <itemId>              # read
$KANBAN body <itemId> --set "..."  # replace
$KANBAN body <itemId> --append "Investigación previa" --content "..."

# Change status (use set-field, NOT move)
$KANBAN set-field <itemId> --field "Status" --option "Debate"

# Convert draft → issue (only at Ejecutando)
$KANBAN convert-draft <itemId>
gh issue edit <N> --add-label "tipo:feature,area:android"

# Verificando (cuando exista Gradle)
./gradlew assembleDebug
./gradlew test
./gradlew lint

# Changelog: commit con SHA referenciable, cerrar, push
git add <files> && git commit -m "..."
$KANBAN body <itemId> --append "Commit" --content "SHA: \`$(git rev-parse --short HEAD)\`"
$KANBAN set-field <itemId> --field "Status" --option "Changelog"
$KANBAN set-field <itemId> --field "Completado" --date "YYYY-MM-DD"
$KANBAN set-field <itemId> --field "Completado exacto" --text "YYYY-MM-DDTHH:MM:SSZ"
gh issue edit <N> --title "✅ ..."
gh issue close <N> -r completed
git push

# Delete (IRREVERSIBLE, requires --yes)
$KANBAN delete <itemId> --yes
```

Áreas válidas en `--area` de **este** Project: `UI/UX`, `Datos`, `Sync`, `Android`, `Build/CI`, `Docs`.

### Body sections by phase

Each item's body evolves through the lifecycle. The CLI generates a template at creation — **always fill it with specific content**, never leave the placeholders.

| Phase | Body sections | Reglas |
|---|---|---|
| **Detectado** | Contexto, Hallazgo y evidencia, Impacto, Alcance a debatir, Preguntas para Debate, Criterio para avanzar, Clasificación preliminar | Descripción MUY completa. No perder contexto. |
| **Debate** | + Investigación previa, Análisis, Alternativas (4: raíz / sólida / rápida / intermedia), Recomendación | Investigar antes de opciones. **PARAR y preguntar.** No avanzar sin aprobación explícita. |
| **Roadmap** | + Decisión acordada, Plan aprobado, Criterios de aceptación, Plan de verificación, Riesgos y recuperación | Investigar a fondo. Añadir lo que falte al plan. |
| **Ejecutando** | + Implementación (qué se hizo realmente, diferencias con el plan si las hay) | Convertir draft→issue al ENTRAR. Documentar cambios sobre el plan. |
| **Verificando** | + Verificación (checklist de tests, typecheck, lint, comprobaciones específicas) | Ejecutar TODO lo aplicable. Arreglar errores preexistentes si se encuentran. |
| **Changelog** | + Commit (SHA). Setear `Completado`, `Completado exacto`. ✅ en título. | Commit → SHA al body → cerrar issue → push a main. |

### Fields reference

| Field | Type | Purpose |
|---|---|---|
| Status | SingleSelect | Detectado → ... → Changelog |
| Prioridad | SingleSelect | Alta, Media, Baja |
| Tipo | SingleSelect | Bug, Feature, Mejora, Tarea |
| Área principal | SingleSelect | UI/UX, Datos, Sync, Android, Build/CI, Docs |
| Versión | SingleSelect | Sin asignar, v0.1, … |
| Decision | SingleSelect | Pendiente, Aprobado, Diferido, Cancelado |
| HighLighted | SingleSelect | Yes, No (for changelog highlights) |
| Inicio exacto | Text | ISO-8601 UTC timestamp |
| Inicio | Date | YYYY-MM-DD |
| Completado exacto | Text | ISO-8601 UTC (set on Changelog) |
| Completado | Date | YYYY-MM-DD (set on Changelog) |

### Labels canónicas

Cada Issue debe tener exactamente **1 label de Tipo + 1 label de Área**. Status,
Prioridad y Versión viven exclusivamente en campos del Project y no se duplican
como labels.

| Campo Tipo | Label | Uso |
|---|---|---|
| Bug | `tipo:bug` | Comportamiento incorrecto o regresión verificable |
| Feature | `tipo:feature` | Capacidad nueva observable para usuario o producto |
| Mejora | `tipo:mejora` | Calidad, UX, rendimiento o mantenibilidad |
| Tarea | `tipo:tarea` | Trabajo operativo o técnico acotado |

| Área principal | Label | Incluye |
|---|---|---|
| UI/UX | `area:ui-ux` | Compose, interacción, accesibilidad, diseño y navegación |
| Datos | `area:datos` | Room, DAOs, migraciones, integridad (mesa, ronda, ticket) |
| Sync | `area:sync` | Nodo LAN, Commander, rondas, lista blanca, health |
| Android | `area:android` | SDK, ciclo de vida, FGS, permisos, dispositivos |
| Build/CI | `area:build-ci` | Gradle, CI, firma, releases |
| Docs | `area:docs` | Documentación y contratos para agentes |

Labels auxiliares permitidas cuando correspondan: `security`, `dependencies`,
`duplicate`, `invalid`, `wontfix`, `question`, `good first issue` y `help wanted`.
No usar los aliases antiguos `bug`, `enhancement` o `documentation`.

### Configuración local del Kanban

`.kanbanrc.json` contiene IDs específicos del Project y permanece gitignored.
`.kanbanrc.json.template` se versiona como referencia reproducible.

Tras crear, borrar o modificar opciones de un campo SingleSelect, todos sus IDs
pueden cambiar. Regenerar y validar inmediatamente:

```bash
$KANBAN config generate --project PVT_kwHOBM87Yc4BgQqa
# El generador deja estos valores como REPLACE_ME; restaurarlos antes de continuar:
# repoId: R_kgDOT3ZYFw
# repo: jaminsmoke/PersonalBar
$KANBAN config validate
```

Después, comprobar que ningún ítem perdió el valor del campo modificado, reponerlo
por nombre si fuera necesario y actualizar `.kanbanrc.json.template` con los IDs
nuevos. Nunca ejecutar `convert-draft` mientras `repoId` sea `REPLACE_ME`.

## Backlog Detectado (deriva de arranque)

Ítems ya creados en el Project #11. Están en **Detectado** (drafts). El equipo los mueve a Debate, investiga, presenta las 4 alternativas y **para a preguntar**. No empieces código en un ítem que siga en Detectado.

Deriva sugerida: bootstrap → expo de colas → health LAN → modelo → recibir ronda. QR y FGS en paralelo más tarde. Mapa al final (no es el home).

| Pri | Área | Título | Item ID |
|---|---|---|---|
| Alta | Android | Bootstrap del proyecto Android (Compose, Gradle, package) | `PVTI_lAHOBM87Yc4BgQqazg2aLzQ` |
| Alta | UI/UX | Expo de barra: colas Bebida y Comida + estado Sala activa | `PVTI_lAHOBM87Yc4BgQqazg2aLw4` |
| Alta | Sync | Nodo LAN: GET /health y descubrimiento para Commander | `PVTI_lAHOBM87Yc4BgQqazg2aLus` |
| Alta | Datos | Modelo de datos: mesa canónica, ronda y ticket por destino | `PVTI_lAHOBM87Yc4BgQqazg2aLp0` |
| Alta | Sync | Recibir ronda y listo por destino (colas reales) | `PVTI_lAHOBM87Yc4BgQqazg2aLsY` |
| Media | Sync | Lista blanca del local: alta de camarero por QR pegado o escaneado | `PVTI_lAHOBM87Yc4BgQqazg2aLn0` |
| Media | Android | Servicio en primer plano «Sala activa» mientras Bar es el nodo | `PVTI_lAHOBM87Yc4BgQqazg2aLlY` |
| Baja | UI/UX | Vista mapa secundaria (no es el flujo principal de Bar) | `PVTI_lAHOBM87Yc4BgQqazg2aLig` |

`$KANBAN show <itemId>` y `$KANBAN body <itemId>` para el texto completo (preguntas de Debate ya van en cada body).

El ítem de **recibir ronda** es la condición para reabrir el Detectado Diferido de sala LAN en Commander. No lo des por hecho en un comentario: tiene que existir el POST/evento de verdad.

## Code conventions

Cuando exista `app/`, alinear con Commander:

- **Language**: Spanish for UI strings & comments, English for code symbols
- **Compose**: `@Composable` functions use `PascalCase`; modifiers as first parameter where possible
- **State**: `StateFlow` in ViewModels, `collectAsState()` in UI
- **Colors**: prefer `MaterialTheme.colorScheme.*` over hardcoded `Color(0xFF...)`. **Excepción espacial**: el plano del mapa (`PbBoardCanvas`, grid) y los fills de mesa (`MesaColors`) son tokens de espacio físico, no del tema dark. El viewport alrededor del plano sí usa `colorScheme`.
- **Icons**: `Icons.Default.*` or `Icons.AutoMirrored.Filled.*`; always set `contentDescription` (never `null` for interactive icons)
- **Strings**: all user-facing text in `res/values/strings.xml`
- **Room**: operations touching 2+ tables MUST use `@Transaction` or `db.withTransaction {}`
- **Migrations**: schema exported to `app/schemas/`; migration tests in `androidTest`
- **Test data**: toda entidad de prueba creada en tests (camareros, salas, productos, negocios) lleva sufijo `Test` en el nombre (p. ej. `carmenTest`, `cocacolaTest`, `salaTest`) para que sea identificable y borrable de un vistazo; nunca usar nombres que parezcan datos reales.

## Keys & security

- `keystore.properties` / `local.properties` gitignored
- Cleartext solo LAN (`network_security_config`); identidad = HTTPS a Identity
- GraphQL token for kanban CLI: `GH_TOKEN` / `GITHUB_TOKEN` from `gh auth`

## License & business model

Same family as Commander: public MIT. Do not put paid premium code in this public repo (would live in a private `:pro` module if that model is adopted).

## Dev tools

```
tools/kanban-cli/          # bun install; CLI = bun run tools/kanban-cli/cli.ts
tools/agent-skills/        # jarvis-github-kanban + jarvis-github-agentuse
.kanbanrc.json             # local Project IDs (gitignored)
.kanbanrc.json.template    # versioned reproducible reference
```
