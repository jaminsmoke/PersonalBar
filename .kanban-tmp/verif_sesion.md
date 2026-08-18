## Verificación

Checklist ejecutado en rama `feature/sesion-offline`:

| Check | Resultado |
|---|---|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew test` | ✅ BUILD SUCCESSFUL (8 tests nuevos de `SesionEstado` + todos los existentes) |
| `./gradlew lint` | ✅ BUILD SUCCESSFUL sin errores (corregido `NonObservableLocale` en el header; sin `UnusedResources`) |
| `MigrationTest` v9→v10 | ✅ escrito (androidTest; se ejecuta en el CI del PR) |
| Schema 10 exportado | ✅ `app/schemas/.../10.json` con `validaHasta INTEGER` |
| Migración real en emulador (5558) | ✅ BD previa v9 → arranque sin crash: `user_version=10`, columna `validaHasta` presente, sin sesión → gate SIN_SESION |
| Revalidación (200/401/red) | ✅ lógica cubierta por `revalidarToken` (2xx/401/-1) + `sesionEstadoDe`; el flujo completo online no se puede ejercitar sin credenciales reales del VPS |
| Revisión visual del usuario | ⏳ pendiente en emulador (login → badge de validez en header; sin sesión → gate) |

### Validaciones adicionales del área (Datos + Sync)

- **Derivación de estado testeada**: borde `ahora == validaHasta` (VALIDA), `validaHasta` null/0/negativo (INVALIDA), pasada (CADUCADA), sin token (SIN_SESION).
- **Timer en el proceso** (`PersonalBarApp.startLocal`): revalidación inmediata al arrancar el nodo y cada 24 h; con el FGS activo el proceso sigue vivo en segundo plano → revalida también en background (decisión del dueño).
- **401 → inválida conservando datos**: `revalidar()` copia la sesión con `validaHasta = 0` y la persiste (los datos quedan para diagnóstico); `sesionEstado` → INVALIDA → gate cerrado.
- **Login offline dentro de 7 días**: `login`/`registro` fijan `validaHasta = now + 7d`; en el arranque, `restaurarSesion` restaura la sesión y el estado deriva VALIDA si `ahora <= validaHasta` — el puesto arranca sin red.
- **Sesiones v9 (null)**: INVALIDA hasta el primer contacto con el VPS (comportamiento seguro, no bloquea datos).
- Nodo LAN `:8787` y FGS intactos (no se tocó `BarLanService`; el timer se suma a los scopes de `startLocal`).
