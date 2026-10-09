#!/bin/bash
# Build refrac-linux-x86_64.AppImage inside an Ubuntu 22.04 container, as root.
# Ubuntu 22.04 is the oldest supported distribution: the AppImage runs on
# systems whose glibc is as new as the build machine's or newer.
#
#     packaging/linux/build-appimage.sh APP_DIR OUT_DIR [VERSION]
#
# APP_DIR is a checkout of the refrac version to build, OUT_DIR receives the
# AppImage. Locally, from the repository root:
#
#     docker run --rm -v "$PWD:/src" -w /src -e OWNER="$(id -u):$(id -g)" \
#         ubuntu:22.04 packaging/linux/build-appimage.sh . dist
set -euo pipefail

APP_DIR="$(readlink -f "$1")"
OUT_DIR="$(mkdir -p "$2" && readlink -f "$2")"
VERSION="${3:-dev}"
PACKAGING="$(dirname "$(readlink -f "$0")")/.."
WORK="$(mktemp -d)"

APPIMAGETOOL_URL=https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage
APPIMAGETOOL_SHA256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
RUNTIME_URL=https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64
RUNTIME_SHA256=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d

# Python, and the X11 libraries Qt's xcb plugin links against, so that
# PyInstaller finds and bundles them. libxcb-cursor0 is the one most
# desktop installations lack.
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q --no-install-recommends \
    python3.10 python3.10-venv python3.10-dev binutils ca-certificates curl file libglib2.0-0 \
    libxcb-cursor0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 libxcb-randr0 \
    libxcb-render-util0 libxcb-shape0 libxcb-xfixes0 libxcb-xkb1 libxkbcommon-x11-0 \
    libfontconfig1 libdbus-1-3 libegl1 libgl1 libwayland-client0 libwayland-cursor0 \
    libwayland-egl1

# refrac's dependencies, exactly as locked in its uv.lock, and PyInstaller
python3.10 -m venv "$WORK/venv"
source "$WORK/venv/bin/activate"
pip install -q --upgrade pip uv
uv export --project "$APP_DIR" --frozen --no-dev --no-emit-project -q -o "$WORK/requirements.txt"
pip install -q --require-hashes -r "$WORK/requirements.txt"
pip install -q --no-deps "$APP_DIR"
pip install -q -r "$PACKAGING/build-requirements.txt"

pyinstaller --noconfirm --distpath "$WORK/dist" --workpath "$WORK/build" "$PACKAGING/refrac.spec"

# AppDir: the bundle in usr/lib/refrac, plus the entry point, desktop entry and icon
APPDIR="$WORK/AppDir"
mkdir -p "$APPDIR/usr/lib" "$APPDIR/usr/share/applications" \
    "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp -a "$WORK/dist/refrac" "$APPDIR/usr/lib/refrac"
install -m 755 "$PACKAGING/linux/AppRun" "$APPDIR/AppRun"
install -m 644 "$PACKAGING/linux/refrac.desktop" "$APPDIR/refrac.desktop"
install -m 644 "$PACKAGING/linux/refrac.desktop" "$APPDIR/usr/share/applications/refrac.desktop"
install -m 644 "$PACKAGING/icon/refrac.png" "$APPDIR/refrac.png"
install -m 644 "$PACKAGING/icon/refrac.png" "$APPDIR/usr/share/icons/hicolor/256x256/apps/refrac.png"
ln -s refrac.png "$APPDIR/.DirIcon"

test -f "$APPDIR/usr/lib/refrac/_internal/libxcb-cursor.so.0"

download() {  # URL SHA256 FILE
    curl -fsSL -o "$3" "$1"
    echo "$2  $3" | sha256sum -c -
}
download "$APPIMAGETOOL_URL" "$APPIMAGETOOL_SHA256" "$WORK/appimagetool"
download "$RUNTIME_URL" "$RUNTIME_SHA256" "$WORK/runtime"
chmod +x "$WORK/appimagetool"

# Containers have no FUSE, so appimagetool runs extracted
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 VERSION="$VERSION" \
    "$WORK/appimagetool" --no-appstream --runtime-file "$WORK/runtime" \
    "$APPDIR" "$OUT_DIR/refrac-linux-x86_64.AppImage"

if [ -n "${OWNER:-}" ]; then
    chown "$OWNER" "$OUT_DIR" "$OUT_DIR/refrac-linux-x86_64.AppImage"
fi
rm -rf "$WORK"
