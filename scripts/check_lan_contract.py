#!/usr/bin/env python3
"""Comprueba la coherencia del contrato LAN (`docs/contrato/openapi-lan.json`):

- Cada ruta que `BarLanModule.kt` registra (método + path) existe en el OpenAPI.
  `sse` de Ktor equivale a `get` con `text/event-stream`. Las rutas anidadas en
  `route("...")` (p. ej. `/v1/eventos` con `sse {}` interno) se resuelven.
- `info.version` del OpenAPI coincide con `BarLanConfig.VERSION`.
- Auth v0.2: el spec declara `securitySchemes.sesionLan` (bearer), `security`
  global, las rutas públicas (`/health`, `/v1/sesion`, `/v1/sesion/iniciar`)
  lo anulan con `security: []`, `/v1/sesion/cortar` acepta QR o Bearer, y toda
  ruta privada declara la respuesta `401`.
- `SesionIniciarResponse` expone el campo `token` y `/v1/eventos` exige el query
  param `token` (SSE no puede mandar cabeceras).
- Las rutas del OpenAPI que el código ya no registra se avisan (warning).

El contrato de payloads se verifica por fixtures doradas desde `LanContractTest`
(Kotlin/JVM): `docs/contrato/fixtures/*.json` deben decodificar con los modelos
actuales y cumplir los campos `required` de su schema. Este script es solo stdlib.

Uso:
    python scripts/check_lan_contract.py [--module path] [--config path] [--openapi path]
    python scripts/check_lan_contract.py --selftest
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DEFAULT_MODULE = ROOT / "app/src/main/java/com/jaminsmoke/personalbar/lan/BarLanModule.kt"
DEFAULT_CONFIG = ROOT / "app/src/main/java/com/jaminsmoke/personalbar/lan/BarLanConfig.kt"
DEFAULT_OPENAPI = ROOT / "docs/contrato/openapi-lan.json"

# Rutas Ktor de BarLanModule.kt: get/post/put/delete/patch/sse("...") con path literal.
KTOR_RUTA = re.compile(
    r"""\b(get|post|put|delete|patch|sse)\(\s*["']([^"']+)["']""",
    re.IGNORECASE,
)
# Contenedores: route("/v1/eventos") { ... }
KTOR_ROUTE = re.compile(
    r"""\broute\(\s*["']([^"']+)["']""",
    re.IGNORECASE,
)
# Verbos sin path dentro de un route (p. ej. `sse {`): heredan el path del contenedor.
KTOR_VERBO_SIN_PATH = re.compile(r"""\b(get|post|put|delete|patch|sse)\s*\{""", re.IGNORECASE)

CONST_VERSION_RE = re.compile(r'const\s+val\s+VERSION:\s*String\s*=\s*"([^"]*)"')

# sse() de Ktor se documenta en OpenAPI como GET text/event-stream.
VERBOS_LAN = ("get", "post", "put", "delete", "patch", "sse")
MÉTODO_LAN_A_OPENAPI = {"sse": "get"}

# Auth v0.2: rutas públicas (sin token) y la que acepta QR o Bearer.
PUBLICAS = {"/health", "/v1/sesion", "/v1/sesion/iniciar"}
CORTAR = "/v1/sesion/cortar"
SCHEMA_TOKEN = "SesionIniciarResponse"


def rutas_del_modulo(fuente: str) -> set[tuple[str, str]]:
    """Extrae (método, path) de las rutas registradas en BarLanModule.kt.

    Cubre tanto los verbos con path literal (`post("/v1/rondas")`) como los
    anidados en `route("/v1/eventos") { ... sse { ... } }`, donde el verbo sin
    path hereda el path del contenedor.
    """
    rutas = {(m.group(1).lower(), m.group(2)) for m in KTOR_RUTA.finditer(fuente)}

    for ruta_match in KTOR_ROUTE.finditer(fuente):
        container = ruta_match.group(1)
        # Ventana desde el final del route("...") hasta el cierre del bloque: usamos
        # el resto del archivo y recortamos por balance de llaves para no mezclar
        # verbos de rutas hermanas posteriores.
        inicio = ruta_match.end()
        resto = fuente[inicio:]
        profundidad = 0
        corte = 0
        for i, ch in enumerate(resto):
            if ch == "{":
                profundidad += 1
            elif ch == "}":
                profundidad -= 1
                if profundidad == 0:
                    corte = i
                    break
        cuerpo = resto[: corte + 1]
        for v in KTOR_VERBO_SIN_PATH.finditer(cuerpo):
            rutas.add((v.group(1).lower(), container))

    return rutas


def version_del_config(fuente: str) -> str | None:
    m = CONST_VERSION_RE.search(fuente)
    return m.group(1) if m else None


def openapi_rutas(path: Path) -> dict[str, set[str]]:
    spec = json.loads(path.read_text(encoding="utf-8"))
    paths = spec.get("paths")
    if not isinstance(paths, dict):
        raise ValueError(f"{path} no tiene objeto paths")
    out: dict[str, set[str]] = {}
    for raw, item in paths.items():
        if not isinstance(item, dict):
            continue
        out[raw] = {k.lower() for k in item if isinstance(item.get(k), dict)}
    return out


@dataclass
class Informe:
    markdown: str
    fallos: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def comprobar(
    module: Path = DEFAULT_MODULE,
    config: Path = DEFAULT_CONFIG,
    openapi: Path = DEFAULT_OPENAPI,
) -> Informe:
    src = module.read_text(encoding="utf-8")
    cfg = config.read_text(encoding="utf-8")

    rutas = rutas_del_modulo(src)
    version_codigo = version_del_config(cfg)
    spec_obj = json.loads(openapi.read_text(encoding="utf-8"))
    version_openapi = spec_obj.get("info", {}).get("version")
    spec = openapi_rutas(openapi)

    fallos: list[str] = []
    warnings: list[str] = []

    if version_codigo and version_openapi and version_codigo != version_openapi:
        fallos.append(
            f"Versión del contrato: BarLanConfig.VERSION={version_codigo!r} "
            f"≠ openapi-lan.json info.version={version_openapi!r}"
        )

    for metodo, ruta in sorted(rutas):
        verbos = spec.get(ruta)
        if verbos is None:
            fallos.append(f"BarLanModule registra {metodo.upper()} {ruta} que el OpenAPI no documenta")
            continue
        esperado = MÉTODO_LAN_A_OPENAPI.get(metodo, metodo)
        if esperado not in verbos:
            fallos.append(
                f"BarLanModule registra {metodo.upper()} {ruta} pero el OpenAPI solo declara "
                + ", ".join(sorted(v.upper() for v in verbos))
            )

    # --- Auth v0.2 (solo se exige si el contrato declara el esquema de sesión) ---
    schemes = spec_obj.get("components", {}).get("securitySchemes", {})
    rutas_codigo = {ruta for _, ruta in rutas}
    privadas = rutas_codigo - PUBLICAS - {CORTAR}

    if "sesionLan" not in schemes:
        fallos.append("El OpenAPI no declara components.securitySchemes.sesionLan (bearer v0.2)")
    else:
        scheme = schemes["sesionLan"]
        if scheme.get("type") != "http" or scheme.get("scheme") != "bearer":
            fallos.append(f"securitySchemes.sesionLan debe ser http/bearer: {scheme!r}")

        for ruta in sorted(PUBLICAS):
            op = _operacion(spec_obj, ruta)
            if op is None:
                continue  # ya lo avisa el chequeo de rutas
            if op.get("security") != []:
                fallos.append(f"{ruta} es pública (sin token) y debe declarar security: [] en el OpenAPI")

        op_cortar = _operacion(spec_obj, CORTAR)
        if op_cortar is not None:
            sec = op_cortar.get("security")
            if not isinstance(sec, list) or {} not in sec:
                fallos.append(
                    f"{CORTAR} acepta QR o Bearer: debe declarar security con una opción vacía "
                    f"{{}} (QR) además de {{sesionLan: []}}"
                )

        for ruta in sorted(privadas):
            op = _operacion(spec_obj, ruta)
            if op is None:
                continue
            if "401" not in op.get("responses", {}):
                fallos.append(f"{ruta} es privada (token exigido) y debe declarar la respuesta 401")

        # Token en SesionIniciarResponse.
        schemas = spec_obj.get("components", {}).get("schemas", {})
        iniciar = schemas.get(SCHEMA_TOKEN)
        if not isinstance(iniciar, dict) or "token" not in iniciar.get("properties", {}):
            fallos.append(f"{SCHEMA_TOKEN} debe exponer el campo `token` (aditivo, nullable) en el OpenAPI")

        # SSE: query param token obligatorio.
        op_eventos = _operacion(spec_obj, "/v1/eventos")
        if op_eventos is not None:
            params = op_eventos.get("parameters", [])
            tokens = [p for p in params if p.get("name") == "token"]
            if not tokens or not tokens[0].get("required"):
                fallos.append("/v1/eventos (SSE) debe exigir el query param `token` requerido (EventSource no manda cabeceras)")

        rutas_privadas_codigo = {r for r in rutas_codigo if r not in PUBLICAS}
        if "authenticate(" not in src and rutas_privadas_codigo:
            fallos.append("BarLanModule no usa authenticate() y hay rutas privadas que exigen token")

    for ruta in sorted(spec):
        if ruta not in rutas_codigo:
            warnings.append(f"OpenAPI documenta {ruta} que BarLanModule ya no registra")

    markdown = f"""# Contrato LAN — coherencia

Contraste de `BarLanModule.kt` contra `docs/contrato/openapi-lan.json` y
`BarLanConfig.VERSION`. Rojo si el código registra una ruta (o versión) que el
contrato no refleja, o si el contrato no documenta la auth de sesión v0.2
(securitySchemes bearer, 401 en privadas, token en iniciar, `?token=` en SSE).
Los payloads se verifican aparte con fixtures doradas (`LanContractTest`).

## Versión

- Código (`BarLanConfig.VERSION`): `{version_codigo or "-"}`
- Contrato (`info.version`): `{version_openapi or "-"}`

## Auth

- `securitySchemes.sesionLan`: `{"sí" if "sesionLan" in schemes else "NO"}`
- Públicas: {", ".join(sorted(PUBLICAS)) or "-"}
- Privadas (exigen 401): {", ".join(sorted(privadas)) or "-"}

## Rutas del módulo

{bullets(sorted(f"{m.upper()} {p}" for m, p in rutas))}

## Error

{bullets(fallos, "_Ninguno._")}
"""
    return Informe(markdown=markdown.strip() + "\n", fallos=fallos, warnings=warnings)


def _operacion(spec_obj: dict, ruta: str) -> dict | None:
    """Devuelve la primera operación (get/post) documentada para la ruta."""
    item = spec_obj.get("paths", {}).get(ruta)
    if not isinstance(item, dict):
        return None
    for op in item.values():
        if isinstance(op, dict) and "responses" in op:
            return op
    return None


def bullets(rutas: list[str], vacio: str = "_Ninguno._") -> str:
    if not rutas:
        return vacio
    return "\n".join(f"- `{r}`" for r in rutas)


def escribir_informe(informe: Informe) -> None:
    sys.stdout.write(informe.markdown)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write(informe.markdown)
    for w in informe.warnings:
        print(f"::warning::{w}")
    if informe.fallos:
        for f in informe.fallos:
            print(f"::error::{f}")
        sys.exit(1)


def _spec_base(version: str = "0.1", con_auth: bool = False) -> dict:
    """Spec mínimo para los selftests de rutas."""
    paths = {
        "/health": {"get": {"responses": {"200": {}}}},
        "/v1/sesion": {"post": {"responses": {"200": {}}}},
        "/v1/rondas": {"post": {"responses": {"200": {}}}},
        "/v1/tickets/{id}/preparado": {"post": {"responses": {"200": {}}}},
        "/v1/eventos": {"get": {"responses": {"200": {}}}},
    }
    spec = {"openapi": "3.1.0", "info": {"title": "x", "version": version}, "paths": paths}
    if con_auth:
        spec["security"] = [{"sesionLan": []}]
        spec["components"] = {
            "securitySchemes": {"sesionLan": {"type": "http", "scheme": "bearer"}},
            "schemas": {
                "SesionIniciarResponse": {"type": "object", "properties": {"token": {"type": "string"}}},
            },
        }
        spec["paths"]["/health"]["get"]["security"] = []
        spec["paths"]["/v1/sesion"]["post"]["security"] = []
        spec["paths"]["/v1/rondas"]["post"]["responses"]["401"] = {}
        spec["paths"]["/v1/tickets/{id}/preparado"]["post"]["responses"]["401"] = {}
        spec["paths"]["/v1/eventos"]["get"]["responses"]["401"] = {}
        spec["paths"]["/v1/eventos"]["get"]["parameters"] = [
            {"name": "token", "in": "query", "required": True}
        ]
    return spec


def selftest() -> None:
    src = '''
        get("/health")
        post("/v1/sesion")
        post("/v1/rondas")
        post("/v1/tickets/{id}/preparado")
        route("/v1/eventos") {
            intercept(ApplicationCallPipeline.Call) {
                call.respond(HttpStatusCode.Unauthorized)
            }
            sse {
                repository.eventos.collect { evento ->
                    emit(ServerSentEvent(...))
                }
            }
        }
    '''
    rutas = rutas_del_modulo(src)
    assert ("get", "/health") in rutas, rutas
    assert ("post", "/v1/sesion") in rutas, rutas
    assert ("post", "/v1/rondas") in rutas, rutas
    assert ("post", "/v1/tickets/{id}/preparado") in rutas, rutas
    # El `sse {` anidado en route() hereda el path del contenedor como GET.
    assert ("sse", "/v1/eventos") in rutas, rutas
    assert version_del_config('const val VERSION: String = "0.1"') == "0.1"

    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        openapi = td / "openapi.json"
        config = td / "BarLanConfig.kt"
        module = td / "BarLanModule.kt"

        # Auth v0.2: spec con auth completo + módulo con authenticate → verde.
        src_auth = '''
            get("/health")
            post("/v1/sesion")
            post("/v1/sesion/iniciar")
            post("/v1/sesion/cortar")
            authenticate("sesion-lan") {
                post("/v1/rondas")
                post("/v1/tickets/{id}/preparado")
            }
            route("/v1/eventos") {
                sse { }
            }
        '''
        spec_auth = _spec_base("0.2", con_auth=True)
        spec_auth["paths"]["/v1/sesion/iniciar"] = {"post": {"security": [], "responses": {"200": {}}}}
        spec_auth["paths"]["/v1/sesion/cortar"] = {
            "post": {"security": [{}, {"sesionLan": []}], "responses": {"200": {}, "401": {}}}
        }
        openapi.write_text(json.dumps(spec_auth), encoding="utf-8")
        config.write_text('const val VERSION: String = "0.2"', encoding="utf-8")
        module.write_text(src_auth, encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert inf.fallos == [], inf.fallos

        # Spec sin sesionLan (contrato 0.1 legacy) → ROJO: la auth es obligatoria en v0.2.
        openapi.write_text(json.dumps(_spec_base("0.1")), encoding="utf-8")
        config.write_text('const val VERSION: String = "0.1"', encoding="utf-8")
        module.write_text(src, encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("sesionLan" in f for f in inf.fallos), inf.fallos

        # Ruta que el código registra y el OpenAPI no → ROJO (spec auth de nuevo).
        openapi.write_text(json.dumps(spec_auth), encoding="utf-8")
        config.write_text('const val VERSION: String = "0.2"', encoding="utf-8")
        module.write_text(src_auth + '\n        get("/v1/nueva")\n', encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("GET /v1/nueva" in f for f in inf.fallos), inf.fallos

        # Versión desincronizada → ROJO.
        module.write_text(src_auth, encoding="utf-8")
        config.write_text('const val VERSION: String = "0.3"', encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("Versión" in f for f in inf.fallos), inf.fallos

        # Quitar el 401 de una ruta privada → ROJO.
        spec_rota = json.loads(openapi.read_text(encoding="utf-8"))
        del spec_rota["paths"]["/v1/rondas"]["post"]["responses"]["401"]
        openapi.write_text(json.dumps(spec_rota), encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("401" in f and "/v1/rondas" in f for f in inf.fallos), inf.fallos

        # Quitar el token de SesionIniciarResponse → ROJO.
        spec_rota = json.loads(openapi.read_text(encoding="utf-8"))
        del spec_rota["components"]["schemas"]["SesionIniciarResponse"]["properties"]["token"]
        openapi.write_text(json.dumps(spec_rota), encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("token" in f for f in inf.fallos), inf.fallos

        # Quitar securitySchemes → ROJO.
        spec_rota = json.loads(openapi.read_text(encoding="utf-8"))
        del spec_rota["components"]["securitySchemes"]
        openapi.write_text(json.dumps(spec_rota), encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("sesionLan" in f for f in inf.fallos), inf.fallos

        # Módulo sin authenticate con privadas y spec con auth → ROJO.
        spec_ok = json.loads(openapi.read_text(encoding="utf-8"))
        spec_ok["components"]["securitySchemes"] = {"sesionLan": {"type": "http", "scheme": "bearer"}}
        openapi.write_text(json.dumps(spec_ok), encoding="utf-8")
        module.write_text(src_auth.replace('authenticate("sesion-lan") {', "authenticateX() {"), encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("authenticate" in f for f in inf.fallos), inf.fallos

    print("selftest OK")


def main() -> None:
    parser = argparse.ArgumentParser(description="Coherencia del contrato LAN")
    parser.add_argument("--module", type=Path, default=DEFAULT_MODULE)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--openapi", type=Path, default=DEFAULT_OPENAPI)
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        selftest()
        return
    informe = comprobar(args.module, args.config, args.openapi)
    escribir_informe(informe)


if __name__ == "__main__":
    main()
