#!/usr/bin/env python3
"""Deriva los assets de marca públicos de Personal Bar desde la copa de barra.

Genera:
  - docs/assets/logo.png     (128x128, PNG lossless)
  - docs/assets/favicon.png  (32x32,  PNG lossless)
  - docs/assets/og-image.png (1200x630, social card con fondo sólido)

La marca canónica es `app/src/main/res/drawable/ic_launcher.xml` (vector: copa de
barra gold sobre navy). Este script replica sus paths con primitivas PIL a partir
de las coordenadas del propio vector, de forma determinista y sin dependencias
fuera de Pillow.

Uso:
    python scripts/generate_assets_bar.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs" / "assets"

# Colores de marca (ui/theme/Color.kt)
NAVY = (0x10, 0x14, 0x16)     # PbBackground
NAVY_DARK = (0x0B, 0x0F, 0x10)  # PbSurfaceContainerLowest
GOLD = (0xE9, 0xC3, 0x49)     # PbSecondary
LIGHT = (0xE0, 0xE3, 0xE5)    # PbOnSurface

FONT_CANDIDATES = [
    "C:/Windows/Fonts/seguisb.ttf",   # Segoe UI Semibold
    "C:/Windows/Fonts/segoeui.ttf",   # Segoe UI
    "C:/Windows/Fonts/arial.ttf",     # Arial
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def find_font(size: int):
    """Devuelve una ImageFont o None si no hay fuente disponible."""
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return None


def draw_copa(draw: ImageDraw.ImageDraw, cx: float, cy: float, scale: float) -> None:
    """Dibuja la copa de barra (paths del vector ic_launcher.xml) centrada en (cx, cy).

    El vector original vive en un viewport 108x108:
      - boca:  M34,28 h40 l-6,8 h-28 z          (trapezoide)
      - cuerpo: M40,40 h28 l-1,30 c0,3 -2,5 -5,5 h-16 c-3,0 -5,-2 -5,-5 z
      - hueco:  M44,44 h20 v20 h-20 z            (interior navy)
    """
    def pt(x: float, y: float):
        return (cx + (x - 54) * scale, cy + (y - 54) * scale)

    # boca (trapezoide)
    boca = [pt(34, 28), pt(74, 28), pt(68, 36), pt(40, 36)]
    draw.polygon(boca, fill=GOLD)

    # cuerpo (polígono aproximando la curva inferior de la copa)
    cuerpo = [pt(40, 40), pt(68, 40), pt(67, 66), pt(66, 73), pt(60, 75), pt(48, 75), pt(42, 73), pt(41, 66)]
    draw.polygon(cuerpo, fill=GOLD)

    # hueco interior de la copa (navy)
    hueco = [pt(44, 44), pt(64, 44), pt(64, 64), pt(44, 64)]
    draw.polygon(hueco, fill=NAVY)


def copa_square(size: int) -> Image.Image:
    """Renderiza la copa de barra centrada en un lienzo cuadrado navy."""
    img = Image.new("RGBA", (size, size), NAVY + (255,))
    draw = ImageDraw.Draw(img)
    draw_copa(draw, size / 2, size / 2, size / 108 * 0.92)
    return img


def generate_og_image() -> None:
    """Genera la social card 1200x630: navy + copa + wordmark + banda gold."""
    W, H = 1200, 630
    canvas = Image.new("RGBA", (W, H), NAVY_DARK + (255,))
    draw = ImageDraw.Draw(canvas)

    copa_size = 300
    copa = copa_square(copa_size)
    copa_x = 100
    copa_y = (H - copa_size) // 2
    canvas.paste(copa, (copa_x, copa_y), copa)

    wordmark = "Personal Bar"
    tagline = "El puesto de barra que gestiona la sala"

    text_x = copa_x + copa_size + 70

    title_font = find_font(76)
    tag_font = find_font(32)

    if title_font is not None:
        title_bbox = draw.textbbox((0, 0), wordmark, font=title_font)
        title_h = title_bbox[3] - title_bbox[1]
        tag_bbox = draw.textbbox((0, 0), tagline, font=tag_font) if tag_font else (0, 0, 0, 0)
        tag_h = tag_bbox[3] - tag_bbox[1]
        gap = 24
        block_h = title_h + gap + tag_h
        y = (H - block_h) // 2

        draw.text((text_x, y), wordmark, font=title_font, fill=GOLD + (255,))
        if tag_font is not None:
            draw.text((text_x, y + title_h + gap), tagline, font=tag_font, fill=LIGHT + (255,))

    # banda inferior gold sutil (patrón Commander)
    draw.rectangle([0, H - 8, W, H], fill=GOLD + (255,))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT_DIR / "og-image.png", "PNG", optimize=True)
    print(f"og-image.png -> {OUT_DIR.relative_to(ROOT)} (1200x630)")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    logo = copa_square(128)
    logo.save(OUT_DIR / "logo.png", "PNG")
    print(f"logo.png    -> {OUT_DIR.relative_to(ROOT)} (128x128)")

    favicon = copa_square(32)
    favicon.save(OUT_DIR / "favicon.png", "PNG")
    print(f"favicon.png -> {OUT_DIR.relative_to(ROOT)} (32x32)")

    generate_og_image()


if __name__ == "__main__":
    main()
