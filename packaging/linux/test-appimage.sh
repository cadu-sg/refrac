#!/bin/bash
# Smoke-test the AppImage inside a clean Ubuntu container, as root: the main
# window must appear on a virtual X display. Only libraries a desktop always
# has are installed, and libxcb-cursor0 is not, to prove the AppImage
# brings its own.
#
#     packaging/linux/test-appimage.sh APPIMAGE
#
# Locally, from the repository root:
#
#     docker run --rm -v "$PWD:/src" -w /src ubuntu:24.04 \
#         packaging/linux/test-appimage.sh dist/refrac-linux-x86_64.AppImage
set -euo pipefail

APPIMAGE="$(readlink -f "$1")"
WORK="$(mktemp -d)"

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q --no-install-recommends \
    xvfb x11-utils libfontconfig1 libfreetype6 libharfbuzz0b libegl1 libgl1 \
    libwayland-client0 libwayland-cursor0 libwayland-egl1 libx11-6 libx11-xcb1 libxcb1

if dpkg -s libxcb-cursor0 >/dev/null 2>&1; then
    echo "libxcb-cursor0 is installed, so the test would prove nothing" >&2
    exit 1
fi

# Every library the Qt platform plugins need must resolve
cd "$WORK"
"$APPIMAGE" --appimage-extract >/dev/null
BUNDLE="$WORK/squashfs-root/usr/lib/refrac/_internal"
for plugin in "$BUNDLE"/PySide6/Qt/plugins/platforms/lib{qxcb,qwayland}.so; do
    if LD_LIBRARY_PATH="$BUNDLE" ldd "$plugin" | grep "not found"; then
        echo "$(basename "$plugin") has unresolved libraries" >&2
        exit 1
    fi
done

Xvfb :99 -screen 0 1280x1024x24 &
export DISPLAY=:99
sleep 2
QT_QPA_PLATFORM=xcb "$WORK/squashfs-root/AppRun" > "$WORK/refrac.log" 2>&1 &
PID=$!
for _ in $(seq 60); do
    if xwininfo -root -tree | grep -q '"refrac"'; then
        echo "The refrac window appeared"
        kill "$PID"
        exit 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
        break
    fi
    sleep 1
done
echo "The refrac window did not appear. Its output:" >&2
cat "$WORK/refrac.log" >&2
exit 1
