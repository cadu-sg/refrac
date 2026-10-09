# refrac

A desktop application for **seismic refraction interpretation**. Load the first-break picks of a seismic line, step through the shots, and draw straight lines over each travel-time curve: the direct wave and up to three refracted arrivals, on both sides of the shot. refrac derives the layer velocities and thicknesses from those lines and plots the resulting subsurface interfaces.

## Download

<p>
  <a href="https://github.com/cadu-sg/refrac/releases/latest/download/refrac-windows-setup.exe"><img src="docs/images/download-windows.svg" alt="Download for Windows" width="336" height="72"></a>
  &nbsp;
  <a href="https://github.com/cadu-sg/refrac/releases/latest/download/refrac-linux-x86_64.AppImage"><img src="docs/images/download-linux.svg" alt="Download for Linux" width="336" height="72"></a>
</p>

- **Windows:** [refrac-windows-setup.exe](https://github.com/cadu-sg/refrac/releases/latest/download/refrac-windows-setup.exe) (about 50 MB), for Windows 10 or 11, 64-bit.
- **Linux:** [refrac-linux-x86_64.AppImage](https://github.com/cadu-sg/refrac/releases/latest/download/refrac-linux-x86_64.AppImage) (about 60 MB), for 64-bit Ubuntu 22.04, Linux Mint 21, Debian 12, Fedora 36 or newer versions of them, or a distribution of similar age.

You don't need to install anything else: everything refrac needs comes inside the download. All versions are on the [Releases page](https://github.com/cadu-sg/refrac/releases).

## Installing on Windows

1. Click **Download for Windows** above.
2. Open the downloaded file, `refrac-windows-setup.exe`. It's usually in your **Downloads** folder, and your browser shows it at the end of the download.
3. Windows may warn you about the file, because refrac is new and doesn't come from a well-known publisher. The warnings look like this:
   - The browser says the file **"isn't commonly downloaded"**. In Microsoft Edge, click **⋯** next to the message, then **Keep**, then **Show more** and **Keep anyway**.
   - A blue window says **"Windows protected your PC"**. Click **More info**, then **Run anyway**.
4. Follow the installer: click **Next** and **Install**. It doesn't ask for an administrator password.
5. At the end, leave **Launch refrac** checked and click **Finish**.

From now on, start refrac from the **Start menu** or from the **refrac** icon on your desktop.

## Installing on Linux

1. Click **Download for Linux** above.
2. Move the downloaded file, `refrac-linux-x86_64.AppImage`, to a folder where you'll keep it, such as your home folder. The file *is* the program: there's nothing to install.
3. Allow the file to run as a program, once. Right-click it, choose **Properties**, and turn on the option called **Executable as Program** or **Allow executing file as program**, depending on your system. (If you prefer the terminal: `chmod +x refrac-linux-x86_64.AppImage`.)
4. Double-click the file to start refrac.

## Getting started

1. **Project → New Project...** creates a project folder. A project holds any number of seismic lines.
2. **Line → New Line...** asks for a title and a picks file. To try refrac out, use the sample file [synthetic_picks.dat](https://github.com/cadu-sg/refrac/blob/main/testdata/synthetic_picks.dat) (25 shots, 240 stations): open the link and click the **Download raw file** button at the top right of the file.
3. Step through the shots and draw the direct-wave and refracted-arrival lines over each travel-time curve. refrac computes velocities and thicknesses as you draw.

Your work is saved inside the project folder. Next time, use **Project → Open Project...** and **Line → Open Line...** to continue.

### Picks file format

A text file with one first-break pick per row. The first line is a header naming the columns, which may appear in any order:

| Column | Meaning |
|---|---|
| `FFID` | Shot sequential number |
| `SOU_SLOC` | Source station number |
| `SRF_SLOC` | Receiver station number |
| `FB_PICK` | Travel time (ms) |
| `SOU_X`, `SOU_Y` | Source coordinates |
| `REC_X`, `REC_Y` | Receiver coordinates |
| `REC_ELEV` | Receiver station elevation |
| `OFFSET` | Signed source–receiver offset (m) |
| `CDP` | Station nearest to the source–receiver midpoint |
| `SOU_ELEV` | Source elevation (optional) |

- Columns are separated by spaces, tabs or semicolons.
- Numbers may use a decimal point or a decimal comma, as long as the whole file uses the same one.
- Without a `SOU_ELEV` column, a source takes the elevation of the receiver station with its station number.
- A shot is a run of consecutive rows with the same `FFID`.

Open the sample file [synthetic_picks.dat](https://github.com/cadu-sg/refrac/blob/main/testdata/synthetic_picks.dat) for an example.

## Updating

Download refrac again with the buttons above.

- **Windows:** run the new installer. It replaces the old version.
- **Linux:** replace the old AppImage file with the new one, and allow it to run as in step 3 of [Installing on Linux](#installing-on-linux).

Your projects are kept in their own folders, so updating doesn't touch them.

## Uninstalling

- **Windows:** open **Settings → Apps → Installed apps**, find **refrac**, and choose **Uninstall**.
- **Linux:** delete the AppImage file.

Your projects stay where you saved them. Delete their folders too if you don't need them anymore.

## Troubleshooting

- **Windows: the antivirus removed or blocked the file.** Some antivirus programs distrust new programs that aren't from a well-known publisher. Restore the file from the antivirus's quarantine, or ask whoever manages your computer to allow it.
- **Linux: nothing happens when I double-click the file.** The file isn't allowed to run yet; see step 3 of [Installing on Linux](#installing-on-linux). If it still doesn't start, open a terminal in the file's folder and run `./refrac-linux-x86_64.AppImage` to see the error message.
- **Linux: the error mentions FUSE or `fusermount`.** Install FUSE (on Ubuntu or Debian: `sudo apt install fuse3`), or start refrac with `./refrac-linux-x86_64.AppImage --appimage-extract-and-run`.
- **Something else went wrong?** [Open an issue](https://github.com/cadu-sg/refrac/issues) describing what you did and what happened.

## Running from source

On macOS, or to run the latest development version, run refrac with your own Python 3.10 or newer (from [python.org](https://www.python.org/downloads/) or [Anaconda](https://www.anaconda.com/download)). In a terminal:

```
python3 -m venv refrac-env
source refrac-env/bin/activate          # Windows: refrac-env\Scripts\activate
pip install git+https://github.com/cadu-sg/refrac.git
refrac
```

This needs [Git](https://git-scm.com/downloads). Next time, activate the environment again before running `refrac`. On Linux, Qt also needs a system library to open windows: `sudo apt install libxcb-cursor0` on Ubuntu or Debian, `sudo dnf install xcb-util-cursor` on Fedora.

### Development

Developers can use [mise](https://mise.jdx.dev/) and [uv](https://docs.astral.sh/uv/):

```
git clone https://github.com/cadu-sg/refrac.git
cd refrac
mise install    # Python and uv
uv sync         # create .venv with the dependencies
uv run refrac   # run the app
uv run pytest   # run the tests
```

The Windows installer and the Linux AppImage are built by GitHub Actions; see `packaging/` and [CLAUDE.md](CLAUDE.md).
