"""Render packaging/icon/refrac.svg to the PNG and ICO the bundles use.

Run from the repository root after editing the SVG:

    uv run --with pillow python packaging/make_icons.py
"""

import io
from pathlib import Path

from PIL import Image
from PySide6.QtCore import QBuffer, QByteArray, QIODevice, Qt
from PySide6.QtGui import QGuiApplication, QImage, QPainter
from PySide6.QtSvg import QSvgRenderer

ICON_DIR = Path(__file__).parent / "icon"
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)


def render(renderer: QSvgRenderer, size: int) -> Image.Image:
    image = QImage(size, size, QImage.Format.Format_ARGB32)
    image.fill(Qt.GlobalColor.transparent)
    painter = QPainter(image)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    renderer.render(painter)
    painter.end()
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(buffer, "PNG")
    return Image.open(io.BytesIO(data.data())).convert("RGBA")


def main() -> None:
    QGuiApplication([])
    renderer = QSvgRenderer(str(ICON_DIR / "refrac.svg"))
    images = {size: render(renderer, size) for size in ICO_SIZES}
    images[256].save(ICON_DIR / "refrac.png")
    images[256].save(ICON_DIR / "refrac.ico", sizes=[(s, s) for s in ICO_SIZES],
                     append_images=[images[s] for s in ICO_SIZES[:-1]])


if __name__ == "__main__":
    main()
