#!/usr/bin/env python3
"""Comprueba la coherencia del contrato LAN (`docs/contrato/openapi-lan.json`):

- Cada ruta que `BarLanModule.kt` registra (método + path) existe en el OpenAPI.
  `sse` de Ktor equivale a `get` con `text/event-stream`.
- `info.version` del OpenAPI coincide con `BarLanConfig.VERSION`.
- Las rutas del OpenAPI que el código ya no registra se avisan (warning).

El contrato de payloads se verifica por fixtures doradas desde `LanContractTest`
(Kotlin/JVM): `docs/contrato/fixtures/*.json` deben decodificar con los modelos
actuales. Este script es solo stdlib.

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

# Rutas Ktor de BarLanModule.kt: get/post/put/delete/patch/sse("...").
KTOR_RUTA = re.compile(
    r"""(get|post|put|delete|patch|sse)\(\s*["']([^"']+)["']""",
    re.IGNORECASE,
)

CONST_VERSION_RE = re.compile(r'const\s+val\s+VERSION:\s*String\s*=\s*"([^"]*)"')

# sse() de Ktor se documenta en OpenAPI como GET text/event-stream.
VERBOS_LAN = ("get", "post", "put", "delete", "patch", "sse")
MÉTODO_LAN_A_OPENAPI = {"sse": "get"}


def rutas_del_modulo(fuente: str) -> set[tuple[str, str]]:
    """Extrae (método, path) de las rutas registradas en BarLanModule.kt."""
    return {(m.group(1).lower(), m.group(2)) for m in KTOR_RUTA.finditer(fuente)}


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
    spec = openapi_rutas(openapi)
    spec_obj = json.loads(openapi.read_text(encoding="utf-8"))
    version_openapi = spec_obj.get("info", {}).get("version")

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

    rutas_codigo = {ruta for _, ruta in rutas}
    for ruta in sorted(spec):
        if ruta not in rutas_codigo:
            warnings.append(f"OpenAPI documenta {ruta} que BarLanModule ya no registra")

    markdown = f"""# Contrato LAN — coherencia

Contraste de `BarLanModule.kt` contra `docs/contrato/openapi-lan.json` y
`BarLanConfig.VERSION`. Rojo si el código registra una ruta (o versión) que el
contrato no refleja. Los payloads se verifican aparte con fixtures doradas
(`LanContractTest`).

## Versión

- Código (`BarLanConfig.VERSION`): `{version_codigo or "-"}`
- Contrato (`info.version`): `{version_openapi or "-"}`

## Rutas del módulo

{bullets(sorted(f"{m.upper()} {p}" for m, p in rutas))}

## Error

{bullets(fallos, "_Ninguno._")}
"""
    return Informe(markdown=markdown.strip() + "\n", fallos=fallos, warnings=warnings)


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


def selftest() -> None:
    src = '''
        get("/health")
        post("/v1/sesion")
        post("/v1/rondas")
        post("/v1/tickets/{id}/preparado")
        sse("/v1/eventos")
    '''
    assert rutas_del_modulo(src) == {
        ("get", "/health"),
        ("post", "/v1/sesion"),
        ("post", "/v1/rondas"),
        ("post", "/v1/tickets/{id}/preparado"),
        ("sse", "/v1/eventos"),
    }, rutas_del_modulo(src)
    assert version_del_config('const val VERSION: String = "0.1"') == "0.1"

    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        openapi = td / "openapi.json"
        openapi.write_text(json.dumps({
            "openapi": "3.1.0",
            "info": {"title": "x", "version": "0.1"},
            "paths": {
                "/health": {"get": {}},
                "/v1/sesion": {"post": {}},
                "/v1/rondas": {"post": {}},
                "/v1/tickets/{id}/preparado": {"post": {}},
                "/v1/eventos": {"get": {}},
            },
        }), encoding="utf-8")
        config = td / "BarLanConfig.kt"
        config.write_text('const val VERSION: String = "0.1"', encoding="utf-8")
        module = td / "BarLanModule.kt"
        module.write_text(src, encoding="utf-8")

        inf = comprobar(module, config, openapi)
        assert inf.fallos == [], inf.fallos

        # Ruta que el código registra y el OpenAPI no → ROJO.
        module.write_text(src + '\n        get("/v1/nueva")\n', encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("GET /v1/nueva" in f for f in inf.fallos), inf.fallos

        # Versión desincronizada → ROJO.
        module.write_text(src, encoding="utf-8")
        config.write_text('const val VERSION: String = "0.2"', encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert any("Versión" in f for f in inf.fallos), inf.fallos

        # sse documentado como get (no post) → no debe fallar.
        module.write_text(src, encoding="utf-8")
        config.write_text('const val VERSION: String = "0.1"', encoding="utf-8")
        inf = comprobar(module, config, openapi)
        assert inf.fallos == [], inf.fallos

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
