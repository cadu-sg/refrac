# PyInstaller spec for the refrac bundles. Build from the repository root,
# with refrac and its dependencies installed in the active environment:
#
#     pyinstaller --noconfirm packaging/refrac.spec
#
# The output is the one-folder bundle dist/refrac/.

import sys
from pathlib import Path

from PyInstaller.depend import bindepend

PACKAGING = Path(SPECPATH)
LINUX = sys.platform.startswith("linux")

# Linux: Qt's xcb platform plugin needs libxcb-cursor, which many desktop
# installations lack. Bundle it from the build machine. The bootloader puts
# the bundle on LD_LIBRARY_PATH, so the plugin finds it there.
binaries = []
if LINUX:
    xcb_cursor = Path("/usr/lib/x86_64-linux-gnu/libxcb-cursor.so.0")
    if not xcb_cursor.exists():
        raise SystemExit(f"{xcb_cursor} is missing: install libxcb-cursor0")
    binaries.append((str(xcb_cursor), "."))

a = Analysis(
    [str(PACKAGING / "launcher.py")],
    binaries=binaries,
    excludes=[
        # refrac draws with pyqtgraph's 2D items only
        "pyqtgraph.opengl",
        "pyqtgraph.examples",
        "pyqtgraph.jupyter",
        "OpenGL",
        # other Qt bindings pyqtgraph would otherwise consider
        "PyQt5",
        "PyQt6",
        "PySide2",
        "tkinter",
    ],
)

# Qt plugins a desktop app doesn't need. Some drag in large or fragile
# dependencies: the GTK theme bundles GTK, the virtual keyboard and the PDF
# image format bundle Qt Quick.
UNUSED_PLUGINS = (
    "egldeviceintegrations/",
    "generic/",
    "networkinformation/",
    "tls/",
    "platforms/libqeglfs.so",
    "platforms/libqlinuxfb.so",
    "platforms/libqminimalegl.so",
    "platforms/libqvkkhrdisplay.so",
    "platforms/libqvnc.so",
    "platformthemes/libqgtk3.so",
    "platforminputcontexts/libqtvirtualkeyboardplugin.so",
    "imageformats/libqpdf.so",
)

# Libraries every Linux desktop has, and that must come from the system to
# match its graphics drivers and fonts: the AppImage community excludelist,
# https://github.com/AppImageCommunity/pkg2appimage/blob/master/excludelist
# libstdc++ and libgcc_s are on it too: Mesa's drivers need the system's newer
# ones, and the build machine has the oldest supported distribution.
SYSTEM_LIBRARIES = {
    "ld-linux.so.2", "ld-linux-x86-64.so.2", "libanl.so.1", "libBrokenLocale.so.1",
    "libcidn.so.1", "libc.so.6", "libdl.so.2", "libm.so.6", "libmvec.so.1",
    "libnss_compat.so.2", "libnss_dns.so.2", "libnss_files.so.2", "libnss_hesiod.so.2",
    "libnss_nisplus.so.2", "libnss_nis.so.2", "libpthread.so.0", "libresolv.so.2",
    "librt.so.1", "libthread_db.so.1", "libutil.so.1", "libstdc++.so.6", "libGL.so.1",
    "libEGL.so.1", "libGLdispatch.so.0", "libGLX.so.0", "libOpenGL.so.0", "libdrm.so.2",
    "libglapi.so.0", "libgbm.so.1", "libxcb.so.1", "libX11.so.6", "libX11-xcb.so.1",
    "libwayland-client.so.0", "libasound.so.2", "libfontconfig.so.1", "libfreetype.so.6",
    "libharfbuzz.so.0", "libcom_err.so.2", "libexpat.so.1", "libgcc_s.so.1",
    "libgpg-error.so.0", "libICE.so.6", "libSM.so.6", "libusb-1.0.so.0", "libuuid.so.1",
    "libz.so.1", "libjack.so.0", "libpipewire-0.3.so.0", "libxcb-dri3.so.0",
    "libxcb-dri2.so.0", "libfribidi.so.0", "libgmp.so.10",
}


def prune_linux_binaries(toc):
    """Drop unused plugins and system libraries, then every library that only
    they needed. Python extensions, Qt plugins and libpython are kept; any
    other shared library stays only if one of those needs it."""
    toc = [entry for entry in toc
           if not any(pattern in entry[0] for pattern in UNUSED_PLUGINS)
           and Path(entry[0]).name not in SYSTEM_LIBRARIES]
    by_name = {Path(dest).name: (dest, src) for dest, src, _ in toc}
    pending = [dest for dest, _, typecode in toc
               if typecode == "EXTENSION" or "/plugins/" in dest
               or dest.startswith("libpython")]
    sources = {dest: src for dest, src in by_name.values()}
    kept = set()
    while pending:
        dest = pending.pop()
        if dest in kept:
            continue
        kept.add(dest)
        # ldd lists the transitive dependencies too
        for name, _ in bindepend.get_imports(sources[dest]):
            if Path(name).name in by_name:
                pending.append(by_name[Path(name).name][0])
    return [entry for entry in toc if entry[0] in kept]


if LINUX:
    a.binaries = prune_linux_binaries(a.binaries)
    bundled = {entry[0] for entry in a.binaries}
    if "libxcb-cursor.so.0" not in bundled:
        raise SystemExit("libxcb-cursor.so.0 was pruned from the bundle")
    # PyInstaller links libraries it moved under PySide6/ into the bundle
    # root; drop the links to pruned ones
    a.datas = [entry for entry in a.datas
               if entry[2] != "SYMLINK" or entry[1] in bundled]

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    exclude_binaries=True,
    name="refrac",
    console=False,
    icon=str(PACKAGING / "icon" / "refrac.ico"),
)

coll = COLLECT(exe, a.binaries, a.datas, name="refrac")
