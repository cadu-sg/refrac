# refrac

A desktop application for **seismic refraction interpretation**. Load the first-break picks of a seismic line, step through the shots, and draw straight lines over each travel-time curve: the direct wave and up to three refracted arrivals, on both sides of the shot. refrac derives the layer velocities and thicknesses from those lines and plots the resulting subsurface interfaces.

It runs on Windows, macOS and Linux.

## Requirements

You need two things installed before you start:

- **Python 3.10 or newer.** Any installation works:
  - [Anaconda](https://www.anaconda.com/download) or [Miniconda](https://docs.anaconda.com/miniconda/)
  - Python from [python.org](https://www.python.org/downloads/)
  - On Linux, the Python that comes with the system. Ubuntu 22.04 and newer are recent enough. Older releases, such as Ubuntu 20.04 (Python 3.8), are not.

  On macOS, the `python3` that comes with the system is 3.9, which is too old. Use Anaconda or python.org instead.
- **Git**, to download the code. Get it from [git-scm.com](https://git-scm.com/downloads). If you use Anaconda, you can also install it later with `conda install git`. On Linux, use the package manager (see [Linux](#linux)).

You also need an internet connection during installation, to download the libraries refrac uses.

## Installation

Pick the section that matches how you installed Python. You only do this once.

### With Anaconda or Miniconda

The steps are the same on every operating system.

1. Open a terminal:
   - **Windows:** open **Anaconda Prompt** from the Start menu.
   - **macOS:** open **Terminal** (in Applications → Utilities).
   - **Linux:** open your terminal.

2. Create a separate environment for refrac and activate it. Keeping refrac in its own environment stops it from clashing with the packages in Anaconda's `base` environment.

   ```
   conda create -n refrac python=3.12 -y
   conda activate refrac
   ```

   The start of the prompt changes from `(base)` to `(refrac)`.

3. Download refrac and install it:

   ```
   git clone https://github.com/cadu-sg/refrac.git
   cd refrac
   pip install .
   ```

   This creates a `refrac` folder inside the folder the terminal was in (usually your home folder). Keep it: you'll need it for updates, and it contains sample data.

4. Start refrac:

   ```
   refrac
   ```

### With pip (Python from python.org, or the system Python on Linux)

Here the commands differ between operating systems.

#### Windows

1. Open **Command Prompt** (search for `cmd` in the Start menu).

2. Download refrac:

   ```
   git clone https://github.com/cadu-sg/refrac.git
   cd refrac
   ```

3. Create a virtual environment inside the `refrac` folder, activate it and install refrac:

   ```
   py -m venv .venv
   .venv\Scripts\activate
   pip install .
   ```

   The prompt now starts with `(.venv)`.

4. Start refrac:

   ```
   refrac
   ```

#### macOS

1. Open **Terminal** (in Applications → Utilities).

2. Download refrac:

   ```
   git clone https://github.com/cadu-sg/refrac.git
   cd refrac
   ```

3. Create a virtual environment inside the `refrac` folder, activate it and install refrac:

   ```
   python3 -m venv .venv
   source .venv/bin/activate
   pip install .
   ```

   The prompt now starts with `(.venv)`.

4. Start refrac:

   ```
   refrac
   ```

#### Linux

These steps use the Python that comes with your distribution. Recent Ubuntu and Debian releases refuse to `pip install` into the system Python, so refrac goes in a virtual environment of its own.

1. Open your terminal.

2. Install git, Python's virtual environment module, and a system library Qt needs to open windows. On Ubuntu or Debian:

   ```
   sudo apt install git python3-venv libxcb-cursor0
   ```

   On Fedora:

   ```
   sudo dnf install git python3 xcb-util-cursor
   ```

   Check that your Python is 3.10 or newer with `python3 --version`.

3. Download refrac:

   ```
   git clone https://github.com/cadu-sg/refrac.git
   cd refrac
   ```

4. Create a virtual environment inside the `refrac` folder, activate it and install refrac:

   ```
   python3 -m venv .venv
   source .venv/bin/activate
   pip install .
   ```

   The prompt now starts with `(.venv)`.

5. Start refrac:

   ```
   refrac
   ```

## Starting refrac again later

Every time you open a new terminal, activate the environment first, then run `refrac`.

| Installed with | Commands |
|---|---|
| Anaconda | `conda activate refrac`<br>`refrac` |
| pip, Windows | `cd refrac`<br>`.venv\Scripts\activate`<br>`refrac` |
| pip, macOS / Linux | `cd refrac`<br>`source .venv/bin/activate`<br>`refrac` |

The `cd refrac` step assumes the terminal starts in the folder where you cloned refrac, which is usually your home folder.

## Getting started

1. **Project → New Project...** creates a project folder. A project holds any number of seismic lines.
2. **Line → New Line...** asks for a title and a picks file. To try refrac out, use the sample file `testdata/synthetic_picks.dat` inside the `refrac` folder (25 shots, 240 stations).
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

Open `testdata/synthetic_picks.dat` in a text editor for an example.

## Updating

Activate the environment as in [Starting refrac again later](#starting-refrac-again-later), then run, from inside the `refrac` folder:

```
git pull
pip install .
```

## Uninstalling

- **Anaconda:** run `conda env remove -n refrac`, then delete the `refrac` folder.
- **pip:** delete the `refrac` folder. The virtual environment is inside it.

## Troubleshooting

- **`refrac` is not recognized / command not found.** The environment isn't active. Activate it as in [Starting refrac again later](#starting-refrac-again-later).
- **`pip install` fails with `externally-managed-environment`.** You're installing into the system Python on Linux. Activate the virtual environment first (`source .venv/bin/activate` inside the `refrac` folder), or create it if you skipped that step.
- **`python3 -m venv` fails on Ubuntu or Debian.** Install the venv module with `sudo apt install python3-venv`.
- **On Linux, refrac fails with `Could not load the Qt platform plugin "xcb"`.** Install the missing system library. On Ubuntu or Debian: `sudo apt install libxcb-cursor0`. On Fedora: `sudo dnf install xcb-util-cursor`. This can happen with Anaconda too, since Qt uses the system's libraries to open windows.
- **On Windows, `py` is not recognized.** Python from python.org wasn't installed with the "py launcher" option. Try `python` instead of `py`, or reinstall Python and keep that option checked.
- **`pip install .` says your Python version is not supported.** refrac needs Python 3.10 or newer. Check yours with `python --version`.

## Development

Developers can use [mise](https://mise.jdx.dev/) and [uv](https://docs.astral.sh/uv/) instead of the steps above:

```
mise install    # Python and uv
uv sync         # create .venv with the dependencies
uv run refrac   # run the app
uv run pytest   # run the tests
```
