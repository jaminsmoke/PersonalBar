# Política de seguridad — Personal Bar

## Reportar una vulnerabilidad

**No abras un issue público para vulnerabilidades de seguridad.**

Usa el **reporte privado de vulnerabilidades** de GitHub (Private Vulnerability Reporting) directamente en [security/advisories/new](https://github.com/jaminsmoke/PersonalBar/security/advisories/new), o contacta con el mantenedor por correo a través del perfil de GitHub.

Proceso:

1. Describe la vulnerabilidad con detalle: pasos para reproducir, impacto y versión afectada.
2. El mantenedor acusará recibo en **48-72 horas** y evaluará la severidad.
3. Trabajaremos en una corrección y coordinaremos la divulgación contigo antes de hacerla pública.

## Versiones soportadas

| Versión | Soportada |
|---|---|
| Última release (`latest`) | ✅ Se corrigen activamente |
| Rama `main` | ✅ Se corrigen activamente |
| Versiones anteriores | ❌ No reciben parches |

Se recomienda usar siempre la última versión publicada en [Releases](https://github.com/jaminsmoke/PersonalBar/releases).

## Áreas sensibles

Personal Bar es un puesto Android que actúa como **host de sala LAN**. Las áreas con implicaciones de seguridad incluyen:

- **Nodo LAN (puerto 8787)**: Bar es el servidor de la sala; el tráfico es cleartext **solo en rangos LAN privados** (`network_security_config`). No exponer el puerto a internet. La lista blanca de camareros controla quién accede; sin alta no hay acceso aunque estén en el Wi‑Fi.
- **Firma y keystore**: la firma de release usa `keystore.properties` (local, nunca versionado). No compartir keystores ni contraseñas.
- **Identidad**: el login y la creación de cuenta de establecimiento van por HTTPS a PersonalHostel-Identity; nunca por el puerto LAN.
- **Datos locales**: establecimiento, salas, rondas y tickets viven en una base de datos Room local; protégete el acceso físico al puesto.

## Buena práctica para investigadores

Si haces pruebas de seguridad, ten en cuenta:

- Realiza las pruebas en tu propia instalación o con el permiso del propietario.
- No ejecutes escaneos de red agresivos contra la LAN de terceros.
- Reporta hallazgos a través del proceso privado; espera el visto bueno del mantenedor antes de divulgarlos públicamente.
