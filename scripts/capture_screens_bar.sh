#!/usr/bin/env bash
# Captura las pantallas principales de Personal Bar desde el emulador de tablet
# (apaisado) y las guarda en docs/screenshots/{expo,mapa,gestion,carta,ajustes}.png
# normalizadas a 1400px de ancho.
#
# Requisitos:
#   - Emulador Pixel Tablet (apaisado) con la app instalada (o se instala aquí).
#   - adb del Android SDK accesible (busca en $ANDROID_HOME / $ANDROID_SDK_ROOT).
#
# Uso:
#   bash scripts/capture_screens_bar.sh
#   ADB_DEVICE=emulator-5554 bash scripts/capture_screens_bar.sh
#   INYECTAR_DEMO=1 ADB_DEVICE=emulator-5554 bash scripts/capture_screens_bar.sh
#
# Variables opcionales:
#   ADB_DEVICE     serial concreto (recomendable: tablet apaisado).
#   INYECTAR_DEMO=1  activa el nodo y POSTea 2 rondas demo para que la Expo
#                    muestre tickets reales en la captura. Al final SIEMPRE se
#                    limpia (pm clear) para no dejar datos de prueba.
#   SKIP_INSTALL=1   reutiliza una instalación existente.
#   PYTHON_BIN       intérprete con Pillow (python o python3 por defecto).
set -euo pipefail

export MSYS_NO_PATHCONV=1

PKG="com.jaminsmoke.personalbar"
ACTIVITY=".MainActivity"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
TMP="$ROOT/devartifacts"
WIDTH=1400
PYTHON_BIN="${PYTHON_BIN:-python}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  PYTHON_BIN=python3
fi

win() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  elif command -v wslpath >/dev/null 2>&1; then
    wslpath -w "$1" | tr -d '\r'
  else
    echo "$1"
  fi
}

# --- localizar adb -----------------------------------------------------------
find_adb() {
  local c
  for c in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb.exe" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb.exe" \
    "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe" \
    "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe" \
    "$HOME/Android/Sdk/platform-tools/adb"; do
    [[ -n "$c" && -f "$c" ]] && { echo "$c"; return 0; }
  done
  return 1
}

ADB="$(find_adb || true)"
if [[ -z "$ADB" ]]; then
  echo "ERROR: no se encontró adb. Define ANDROID_HOME." >&2
  exit 1
fi
echo "adb: $ADB"

if [[ -n "${ADB_DEVICE:-}" ]]; then
  DEVICE="$ADB_DEVICE"
else
  DEVICE="$($ADB devices | tr -d '\r' | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
if [[ -z "$DEVICE" ]]; then
  echo "ERROR: no hay emulador/dispositivo activo." >&2
  exit 1
fi
echo "dispositivo: $DEVICE"

adb() { "$ADB" -s "$DEVICE" "$@"; }

mkdir -p "$OUT" "$TMP"

# --- instalar y arrancar con seed limpio --------------------------------------
echo "==> Instalando app (installDebug)..."
if [[ "${SKIP_INSTALL:-0}" == "1" ]]; then
  echo "  instalación omitida (SKIP_INSTALL=1)"
else
  (cd "$ROOT" && ./gradlew installDebug >/dev/null)
fi

echo "==> Limpiando datos para seed y arrancando..."
adb shell pm clear "$PKG" >/dev/null || true
# Evita el diálogo de permisos de notificaciones en el primer arranque.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep 8

# Espera a que la UI real de la app esté visible (no el launcher ni un diálogo).
# El primer arranque tras pm clear puede tardar; el diálogo de permisos aparece
# encima de la app y debe cerrarse ANTES de navegar/capturar.
wait_ui() {
  local xml="$TMP/ui.xml"
  local i
  for i in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml "$(win "$xml")" >/dev/null 2>&1
    if grep -q '"Colas"\|"Local inactivo"' "$xml" 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if wait_ui; then
  # Cierra el diálogo de permisos si sigue encima (p. ej. 'Allow').
  if grep -q '"Allow"' "$TMP/ui.xml" 2>/dev/null; then
    tap_text_any "Allow" "Don" || true
    sleep 2
    wait_ui || true
  fi
  echo "  UI de la app visible"
else
  echo "  ! la UI de la app no apareció; se captura igualmente" >&2
fi

# --- helpers ------------------------------------------------------------------
# Vuelca la jerarquía UI y devuelve el centro (x y) del primer nodo cuyo text
# contiene needle. Devuelve éxito si lo encuentra.
find_tap() {
  local needle="$1"
  local xml="$TMP/ui.xml"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$(win "$xml")" >/dev/null 2>&1
  "$PYTHON_BIN" - "$(win "$xml")" "$needle" <<'PY'
import re, sys
xml_path, needle = sys.argv[1], sys.argv[2]
xml = open(xml_path, encoding='utf-8').read()
nodes = []
for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    nodes.append((m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)))
# 1) coincidencia exacta (evita ambigüedad "Gestión" vs "Gestión del menú")
for text, x1, y1, x2, y2 in nodes:
    if text.strip() == needle:
        print((int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
        sys.exit(0)
# 2) substring como fallback
for text, x1, y1, x2, y2 in nodes:
    if needle in text:
        print((int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
        sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local needle="$1"
  local bounds
  if bounds="$(find_tap "$needle")"; then
    adb shell input tap $bounds >/dev/null
    sleep 2
    return 0
  fi
  echo "  ! no se encontró el nodo '$needle'; se omite" >&2
  return 1
}

tap_text_any() {
  local needle
  for needle in "$@"; do
    if tap_text "$needle"; then
      return 0
    fi
  done
  return 1
}

capture() {
  local name="$1"
  local dev="/sdcard/raw_$name.png"
  adb shell screencap -p "$dev" >/dev/null || { echo "  ! error capturando $name" >&2; return 1; }
  adb pull "$dev" "$(win "$TMP/raw_$name.png")" >/dev/null 2>&1 || { echo "  ! error descargando $name" >&2; return 1; }
  "$PYTHON_BIN" - "$(win "$TMP/raw_$name.png")" "$(win "$OUT/$name.png")" "$WIDTH" <<'PY'
import sys
from PIL import Image
src, dst, width = sys.argv[1], sys.argv[2], int(sys.argv[3])
im = Image.open(src).convert("RGBA")
if im.width != width:
    h = round(im.height * width / im.width)
    im = im.resize((width, h), Image.LANCZOS)
im.save(dst, "PNG")
print(f"  -> {im.width}x{im.height} {dst}")
PY
  rm -f "$TMP/raw_$name.png"
}

# --- inyección de rondas demo (opcional) ---------------------------------------
inyectar_demo() {
  echo "==> Inyectando rondas demo (INYECTAR_DEMO=1)..."
  # Activa el nodo tocando el chip del header.
  tap_text_any "Local inactivo" || { echo "  ! chip 'Local inactivo' no encontrado" >&2; return 1; }
  sleep 3
  adb forward tcp:18787 tcp:8787 >/dev/null 2>&1 || true
  local ronda1='{"id":"r-demo-1","mesaId":"B1","numero":1,"camarero":"Lucía","creadoEn":1755100000000,"lineas":[{"productoId":"cana","nombreProducto":"Caña","cantidad":2},{"productoId":"croquetas","nombreProducto":"Croquetas","cantidad":1}]}'
  local ronda2='{"id":"r-demo-2","mesaId":"T2","numero":2,"camarero":"Ana","creadoEn":1755100100000,"lineas":[{"productoId":"tinto-verano","nombreProducto":"Tinto de verano","cantidad":2},{"productoId":"tostada","nombreProducto":"Tostada con tomate","cantidad":1}]}'
  curl -s -m 5 -X POST -H "Content-Type: application/json" -d "$ronda1" http://127.0.0.1:18787/v1/rondas >/dev/null && echo "  ✓ ronda r-demo-1 (B1)"
  curl -s -m 5 -X POST -H "Content-Type: application/json" -d "$ronda2" http://127.0.0.1:18787/v1/rondas >/dev/null && echo "  ✓ ronda r-demo-2 (T2)"
  sleep 2
}

if [[ "${INYECTAR_DEMO:-0}" == "1" ]]; then
  inyectar_demo || echo "  (la inyección falló; se captura sin rondas demo)"
fi

# --- capturas ------------------------------------------------------------------
echo "==> Capturando pantallas..."
capture expo

echo "  navegando a Mapa..."
tap_text_any "Mapa" && capture mapa

echo "  navegando a Gestión (hub)..."
tap_text_any "Gestión" && capture gestion

echo "  navegando a Carta..."
tap_text_any "Carta" && capture carta

echo "  navegando a Ajustes (rail, sin back: el back puede cerrar la app)..."
# El rail del sidebar está siempre visible (también dentro de sub-pantallas de
# Gestión); usar back puede cerrar la actividad y capturar el launcher.
tap_text_any "Ajustes" "Settings" && capture ajustes

# --- limpieza (nunca dejar datos de prueba) -------------------------------------
echo "==> Limpiando (pm clear) y relanzando con seed por defecto..."
adb forward --remove tcp:18787 >/dev/null 2>&1 || true
adb shell pm clear "$PKG" >/dev/null || true
adb shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep 3

echo "==> Listo. Capturas en $OUT/"
