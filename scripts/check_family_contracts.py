#!/usr/bin/env python3
"""Comprueba que Bar no llama rutas de Identity que ya no existen y deja un
informe de aprovechamiento de la superficie LAN que Bar expone.

Uso:
    python scripts/check_family_contracts.py \\
        --camareros-openapi path/openapi-camareros.json \\
        --negocio-openapi path/openapi-negocio.json

    python scripts/check_family_contracts.py --selftest

Los clientes y el módulo LAN se resuelven por defecto desde la raíz del repo
(se pueden sobreescribir con --camareros-client / --negocio-client / --bar-module).

Rojo solo si Bar llama una ruta Identity que ya no está en el OpenAPI. Las rutas
LAN son solo informe (Bar usa las suyas; `preparado`/`recogido` son internas).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DEFAULT_CAMAREROS_CLIENT = ROOT / "app/src/main/java/com/jaminsmoke/personalbar/lan/IdentityCamareroClient.kt"
DEFAULT_NEGOCIO_CLIENT = ROOT / "app/src/main/java/com/jaminsmoke/personalbar/lan/IdentityNegocioClient.kt"
DEFAULT_BAR_MODULE = ROOT / "app/src/main/java/com/jaminsmoke/personalbar/lan/BarLanModule.kt"

KTOR_RUTA = re.compile(
    r"""(?:get|post|put|delete|patch|sse)\(\s*["']([^"']+)["']""",
    re.IGNORECASE,
)

# IdentityHttp.request / requestBytes: método + path (literal o constante).
REQUEST_RE = re.compile(
    r"""IdentityHttp\.(?:request|requestBytes)\(\s*baseUrl\s*,\s*"([A-Z]+)"\s*,\s*(?:"([^"]*)"|([A-Z_][A-Z0-9_]*))"""
)

# uploadMultipart: siempre POST; el path es el 2º argumento.
UPLOAD_RE = re.compile(
    r"""IdentityHttp\.uploadMultipart\(\s*baseUrl\s*,\s*(?:"([^"]*)"|([A-Z_][A-Z0-9_]*))"""
)

CONST_RE = re.compile("const\\s+val\\s+([A-Z_][A-Z0-9_]*)\\s*:\\s*String\\s*=\\s*\"([^\"]*)\"")

# Rutas LAN que Commander consume (espejo de su bar-contract-paths.txt). Informativas.
COMMANDER_ROUTES = {"/health", "/v1/rondas", "/v1/estado", "/v1/eventos", "/v1/carta"}


def load_constants(fuente: str) -> dict[str, str]:
    return {name: value for name, value in CONST_RE.findall(fuente)}


def normalize(ruta: str) -> str:
    """Convierte la interpolación Kotlin en placeholders OpenAPI y quita la query."""
    ruta = ruta.replace("$id", "{establecimiento_id}")
    ruta = ruta.replace("$camareroId", "{camarero_id}")
    ruta = ruta.replace("$invitacionId", "{invitacion_id}")
    ruta = ruta.replace("$enlaceId", "{enlace_id}")
    ruta = ruta.replace("$conflictoId", "{conflicto_id}")
    # Query interpolada (p. ej. ".../invitaciones$q"): el sufijo `$var` al final no es path.
    ruta = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*$", "", ruta)
    return ruta.split("?", 1)[0]


def client_paths(fuente: str) -> set[tuple[str, str]]:
    """Extrae (método, ruta) de un cliente Identity, resolviendo constantes y normalizando."""
    consts = load_constants(fuente)
    rutas: set[tuple[str, str]] = set()
    for m in REQUEST_RE.finditer(fuente):
        method = m.group(1)
        path = m.group(2) if m.group(2) is not None else consts.get(m.group(3) or "", "")
        if path:
            rutas.add((method, normalize(path)))
    for m in UPLOAD_RE.finditer(fuente):
        path = m.group(1) if m.group(1) is not None else consts.get(m.group(2) or "", "")
        if path:
            rutas.add(("POST", normalize(path)))
    return rutas


def openapi_paths(path: Path) -> set[str]:
    spec = json.loads(path.read_text(encoding="utf-8"))
    paths = spec.get("paths")
    if not isinstance(paths, dict):
        raise ValueError(f"{path} no tiene objeto paths")
    return set(paths)


def ktor_paths(fuente: str) -> set[str]:
    return set(KTOR_RUTA.findall(fuente))


def bullets(rutas: list[str], vacio: str = "_Ninguna._") -> str:
    if not rutas:
        return vacio
    return "\n".join(f"- `{r}`" for r in rutas)


@dataclass
class Informe:
    markdown: str
    fallos: list[str] = field(default_factory=list)


def comprobar(
    camareros_openapi: Path,
    negocio_openapi: Path,
    camareros_client: Path = DEFAULT_CAMAREROS_CLIENT,
    negocio_client: Path = DEFAULT_NEGOCIO_CLIENT,
    bar_module: Path = DEFAULT_BAR_MODULE,
) -> Informe:
    cam_want = client_paths(camareros_client.read_text(encoding="utf-8"))
    neg_want = client_paths(negocio_client.read_text(encoding="utf-8"))

    cam_have = openapi_paths(camareros_openapi)
    neg_have = openapi_paths(negocio_openapi)

    missing_cam = sorted(p for _, p in cam_want if p not in cam_have)
    missing_neg = sorted(p for _, p in neg_want if p not in neg_have)

    bar_src = bar_module.read_text(encoding="utf-8")
    bar_routes = sorted(ktor_paths(bar_src))
    publicas = [r for r in bar_routes if r in COMMANDER_ROUTES]
    expo = [r for r in bar_routes if r.rstrip("/").endswith("/preparado") or r.rstrip("/").endswith("/recogido")]
    otras = [r for r in bar_routes if r not in COMMANDER_ROUTES and r not in expo]

    fallos: list[str] = []
    if missing_cam:
        fallos.append("Identity camareros no tiene rutas que Bar llama: " + ", ".join(missing_cam))
    if missing_neg:
        fallos.append("Identity negocio no tiene rutas que Bar llama: " + ", ".join(missing_neg))

    markdown = f"""# Family contracts — Bar

Rojo solo si Bar llama una ruta de Identity que ya no existe. Las rutas LAN
son informativas (Bar usa las suyas; `preparado`/`recogido` son internas).

## Identity camareros (`:8080`)

{bullets(sorted(f"{m} {p}" for m, p in cam_want))}

### Error

{bullets(missing_cam)}

## Identity negocio (`:8082`)

{bullets(sorted(f"{m} {p}" for m, p in neg_want))}

### Error

{bullets(missing_neg)}

## Bar LAN (`:8787`) — superficie expuesta

### Públicas para Commander

{bullets(publicas)}

### Solo expo Bar (internas)

{bullets(expo)}

### Otras (solo Bar)

{bullets(otras)}
"""
    return Informe(markdown=markdown.strip() + "\n", fallos=fallos)


def escribir_informe(informe: Informe) -> None:
    sys.stdout.write(informe.markdown)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write(informe.markdown)
    if informe.fallos:
        for f in informe.fallos:
            print(f"::error::{f}")
        sys.exit(1)


def selftest() -> None:
    # Extracción + normalización de interpolación y constantes.
    src = '''
        const val LOGO_PATH: String = "/v1/auth/negocio/me/logo"
        IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/miembros", token = t)
        IdentityHttp.requestBytes(baseUrl, "GET", LOGO_PATH, t)
        IdentityHttp.uploadMultipart(baseUrl, LOGO_PATH, "logo", "f", b, "mime", t)
        IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/enlaces/$enlaceId/revocar", token = t)
        IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/sync/conflictos/$conflictoId/resolver", token = t)
        IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/invitaciones$q", token = t)
    '''
    rutas = client_paths(src)
    assert ("GET", "/v1/establecimientos/{establecimiento_id}/miembros") in rutas, rutas
    assert ("GET", "/v1/auth/negocio/me/logo") in rutas, rutas
    assert ("POST", "/v1/auth/negocio/me/logo") in rutas, rutas
    assert ("POST", "/v1/establecimientos/{establecimiento_id}/enlaces/{enlace_id}/revocar") in rutas, rutas
    assert (
        "POST",
        "/v1/establecimientos/{establecimiento_id}/sync/conflictos/{conflicto_id}/resolver",
    ) in rutas, rutas
    assert ("GET", "/v1/establecimientos/{establecimiento_id}/invitaciones") in rutas, rutas

    # Comparación: falta una ruta → fallo.
    import tempfile

    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        cam = td / "cam.json"
        neg = td / "neg.json"
        cam.write_text(json.dumps({"paths": {"/v1/keys/qr": {}}}), encoding="utf-8")
        neg.write_text(
            json.dumps({"paths": {"/v1/establecimientos/{establecimiento_id}/miembros": {}}}),
            encoding="utf-8",
        )
        cam_client = td / "cam.kt"
        cam_client.write_text('IdentityHttp.request(baseUrl, "GET", "/v1/keys/qr", auth = false)', encoding="utf-8")
        neg_client = td / "neg.kt"
        neg_client.write_text(
            'IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/miembros", token = t)',
            encoding="utf-8",
        )
        # Ambos presentes → sin fallo
        inf = comprobar(cam, neg, camareros_client=cam_client, negocio_client=neg_client, bar_module=DEFAULT_BAR_MODULE)
        assert inf.fallos == [], inf.fallos
        # neg.json sin la ruta de negocio → fallo
        neg.write_text(json.dumps({"paths": {"/v1/otra": {}}}), encoding="utf-8")
        inf = comprobar(cam, neg, camareros_client=cam_client, negocio_client=neg_client, bar_module=DEFAULT_BAR_MODULE)
        assert inf.fallos, inf.fallos

    print("selftest OK")


def main() -> None:
    parser = argparse.ArgumentParser(description="Family contracts de Bar")
    parser.add_argument("--camareros-openapi", type=Path)
    parser.add_argument("--negocio-openapi", type=Path)
    parser.add_argument("--camareros-client", type=Path, default=DEFAULT_CAMAREROS_CLIENT)
    parser.add_argument("--negocio-client", type=Path, default=DEFAULT_NEGOCIO_CLIENT)
    parser.add_argument("--bar-module", type=Path, default=DEFAULT_BAR_MODULE)
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        selftest()
        return
    if not args.camareros_openapi or not args.negocio_openapi:
        parser.error("--camareros-openapi y --negocio-openapi son obligatorios (o usa --selftest)")

    informe = comprobar(
        camareros_openapi=args.camareros_openapi,
        negocio_openapi=args.negocio_openapi,
        camareros_client=args.camareros_client,
        negocio_client=args.negocio_client,
        bar_module=args.bar_module,
    )
    escribir_informe(informe)


if __name__ == "__main__":
    main()
