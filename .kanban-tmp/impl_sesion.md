## Implementación

Rama `feature/sesion-offline` · commit `HEAD` (local, pendiente PR)

### Qué se hizo realmente (vs plan aprobado)

1. **Room v10** (`Entities.kt` + `AppDatabase.kt`): `SesionNegocio.validaHasta: Long? = null` (epoch ms; `null` = sin validez, `0` = inválida por 401) + `MIGRATION_9_10` (`ALTER TABLE sesion_negocio ADD COLUMN validaHasta INTEGER`). Schema 10 exportado. Sin cambios en DAOs (upsert completo de la entidad).
2. **`SesionEstado`** (nuevo `data/SesionEstado.kt`): enum `SIN_SESION/VALIDA/CADUCADA/INVALIDA` + función pura `sesionEstadoDe(sesion, ahora)` (testeable sin Room ni red).
3. **Holder de sesión en `PersonalBarApp`** (proceso, no ViewModel): `sesion`/`logoBytes`/`sesionEstado` como StateFlows; `restaurarSesion()` (onCreate), `setSesion(sesion, recordar)`, `cerrarSesion()`, `revalidar()`, `startRevalidacionSesion()` (timer 24 h arrancado en `startLocal`, igual que el timeout de sesiones y el proyector de oficio → revalida también con la app en segundo plano vía FGS). `hidratarIdentity` y `sincronizarDesdeIdentity` se movieron del ViewModel a la app.
4. **`IdentityNegocioClient.revalidarToken()`**: `GET /v1/auth/negocio/me` con el token guardado → `RevalidacionResultado { OK (2xx), REVOCADA (401), SIN_RED (fallo de red/-1) }`. Endpoint existente del server, sin ítem cross.
5. **`SesionViewModel`** (fachada de UI): `sesion`/`logoBytes`/`sesionEstado` delegados a la app; `login`/`registro` fijan `validaHasta = now + 7d` (`PersonalBarApp.SESION_VALIDEZ_MS`); `logout` → `app.cerrarSesion()`. Se eliminaron `persistirSesion`, `cargarLogo`, `sincronizarDesdeIdentity`, `hidratarIdentity` (ahora en la app) — el feedback `trabajando`/`mensaje` sigue en el ViewModel.
6. **Gate #92 actualizado** (`ExpoScreen`): pasa de `sesion == null` a `sesionEstado != SesionEstado.VALIDA` — ahora caducada/inválida también bloquea.
7. **UX header** (`SesionHeader`): badge `PbSesionValidez` — «Sesión válida hasta el <fecha>» (formato del dispositivo vía `LocalConfiguration.current.locales`, respeta la regla de lint `NonObservableLocale`); si quedan ≤24 h muestra «Caduca en menos de 24 h» en color de warning (`colorScheme.tertiary`). Strings nuevos en `values` + `values-en` (1:1).
8. **Tests**: `SesionEstadoTest` (8 casos: SIN_SESION, borde `ahora == validaHasta`, CADUCADA, INVALIDA por null/0/negativo) + `MigrationTest.migracion_v9_a_v10_anade_validaHasta` (conserva token/email/nombre/uuid, `validaHasta` NULL).

### Diferencias con el plan aprobado

- **Revalidación al arrancar**: el timer (`startRevalidacionSesion`) hace la primera revalidación **inmediatamente** al arrancar el nodo (no tras esperar 24 h) y luego cada 24 h — cubre el caso «arranque + timer diario» del plan.
- **Sesiones v9 existentes** (`validaHasta = null` tras migrar): estado `INVALIDA` hasta el primer contacto con el VPS (login o revalidación OK) — comportamiento seguro según el plan.
- Sin otros cambios: nodo LAN/FGS intactos, sin ítem cross.

### Archivos

- `app/src/main/java/com/jaminsmoke/personalbar/data/Entities.kt` (+`validaHasta`)
- `app/src/main/java/com/jaminsmoke/personalbar/data/AppDatabase.kt` (+v10, +`MIGRATION_9_10`)
- `app/src/main/java/com/jaminsmoke/personalbar/data/SesionEstado.kt` (nuevo)
- `app/src/main/java/com/jaminsmoke/personalbar/lan/IdentityNegocioClient.kt` (+`revalidarToken`)
- `app/src/main/java/com/jaminsmoke/personalbar/PersonalBarApp.kt` (holder de sesión + timer)
- `app/src/main/java/com/jaminsmoke/personalbar/ui/SesionViewModel.kt` (fachada; -107 líneas)
- `app/src/main/java/com/jaminsmoke/personalbar/ui/ExpoScreen.kt` (gate por estado)
- `app/src/main/java/com/jaminsmoke/personalbar/ui/sesion/SesionHeader.kt` (+badge validez)
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` (+2 claves 1:1)
- `app/src/test/java/.../SesionEstadoTest.kt` (nuevo) · `app/src/androidTest/.../MigrationTest.kt` (+v9→v10)
- `app/schemas/.../10.json` (nuevo)
