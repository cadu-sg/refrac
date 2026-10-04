# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`refrac` is a PySide6 + PyQtGraph desktop application for **seismic refraction interpretation**. The user loads first-break picks from a seismic line and steps through the shots one at a time. Over each shot's travel-time curve they draw straight lines for the direct wave and up to 3 refracted arrivals, on both sides of the shot. The app derives layer velocities and thicknesses from the slopes and intercepts of those lines, and plots the resulting subsurface interfaces.

This branch (`python-port`) is a port of the original JavaFX application, which still lives on `main`.

## Commands

Python 3.14 comes from mise (`mise.toml`) and dependencies are managed with uv. With mise activated, entering the directory activates `.venv`.

```bash
mise install           # Python 3.14 and uv
uv sync                # create .venv with the dependencies
uv run refrac          # run the app (or: uv run python -m refrac)
uv run pytest          # all tests; GUI tests run offscreen (QT_QPA_PLATFORM=offscreen in tests/conftest.py)
uv run pytest tests/test_gui.py::test_shots_loaded_window   # a single test
```

`testdata/synthetic_picks.dat` (25 shots, 240 stations) is the sample picks file. `testdata/make_synthetic_picks.py` regenerates it.

## Architecture

All code is in `src/refrac/`.

### Storage (`storage.py`): Project → Line → four files

Everything on disk is directory-based. A `Project` is just a directory. Each `Line` is a subdirectory of it containing exactly four files, all created together by `Line.create()` and reopened together by `Line.open()`:

| File | Class | Role |
|---|---|---|
| `picks_origin.dat` | `PicksTxt` | Verbatim copy of the user's picks file. UTF-8 (a byte order mark is skipped), 11 columns delimited by whitespace or semicolons, with a decimal point or decimal comma detected per file, plus an optional `SOU_ELEV` (without it, a source takes the elevation of the receiver station with its station number). The first line is a header naming the columns, which may come in any order. The column spec is in the `PicksTxt` docstring. Parsed at open to derive the shots, their metadata and the station list. |
| `picks.bin` | `PicksBin` | Random-access mirror of the picks. Header = `shotAmount` (int) + `shotPositions` (long[]); each shot = 24-byte header + 32 bytes per pick. The app reads and writes shots here, and erased picks are persisted here. A shot is rewritten in place, so it can only shrink. |
| `draw_points.bin` | `DrawPointsBin` | Header = `shotAmount` (int), then a fixed block of 14 points (two doubles each) per shot. |
| `interpretations.csv` | `InterpretationsCSV` | 21 columns per shot (thicknesses, velocities, intercepts, intersection x values). Rewritten in full on every save, by loading all rows, replacing one and writing all. |

The binary files are **big-endian**, and the float fields of picks are float32. The CSV uses CRLF line endings and writes non-finite values as `NaN`, `Infinity` and `-Infinity`.

**`shotIndex` is the shared key.** Record *N* in `picks.bin`, block *N* in `draw_points.bin` and row *N* in `interpretations.csv` all describe the same shot. Anything that changes shot ordering or count invalidates all three.

### The eight line drawers

The dominant naming convention is the ordered tuple `DRAWER_NAMES` in `pick_chart.py`:

```
head3L, head2L, head1L, directL, directR, head1R, head2R, head3R
```

`head` is a head wave (refracted arrival), and `L`/`R` is the side of the shot point. `INTERSECTION_PAIRS`, `FIT_ORDER`, the fit wiring in `PickChart.__init__`, the `WAVES` rows in `main_window.py` and the draw-points packing all follow this ordering, and must stay consistent with it.

`PickChart.get_draw_points()` returns 14 points, not 16. The `directL`/`directR` drawers are `OriginFixedLineDrawer`s pinned to (0, 0), so only their second point is stored. That 14 is `DrawPointsBin.POINTS_AMOUNT` and determines the on-disk block size. Changing the drawer set means changing both, and existing `draw_points.bin` files then become unreadable (`DrawPointsBin.open` validates the file size). Incomplete lines (only p1 placed) are stored as undefined.

`drawers.py` has the `LineDrawer` base, which holds `p1`, `p2`, `drawn` and `enabled` plus the pyqtgraph items, and two subclasses:
- `StandardLineDrawer`: two free points, plus a filled intercept marker at x = 0.
- `OriginFixedLineDrawer`: p1 locked to the origin, with a preview mirrored across the y axis.

`slope` and `intercept` are computed properties, UNDEFINED unless `drawn`. `drawn` is false while an end point is being dragged.

### State and signals

The Java version chained JavaFX property listeners. This port recomputes from state instead:
- Every drawer change calls back into `PickChart._on_drawers_changed`. That refreshes the six vertical intersection markers and emits `drawingsChanged`.
- `MainWindow` listens to `drawingsChanged` and refreshes the velocities table and the enabled state of the Fit / Fit all buttons.
- `Intersection.point()` is computed on demand from the two drawers. It is None unless both are drawn and not parallel (determinant within ±10).
- `line_fit.py` holds the three fit strategies (`LineFitEdgy`, `LineFitTwoIntersections`, `LineFitOriginFixed`). They differ in how they bound the picks used by the least-squares fit, using the neighbouring intersections.

**Active tool.** `PickChart.active_tool` is the single source of truth (`None`, `ZOOM`, `ERASER` or a drawer name), so at most one tool is active. `MainWindow` maps its ten checkable buttons onto it and re-syncs them, with signals blocked, on `activeToolChanged`. Loading draw points clears the drawers and deselects the active drawer.

### Mouse handling in the pick chart

`_ToolViewBox` turns off pyqtgraph's pan, zoom and context menu and forwards clicks and drags to `PickChart`:
- A drag that starts within `GRAB_RADIUS` px of a complete line's end point moves that point, whatever tool is active.
- Otherwise the event goes to the active tool: drawer clicks, or the zoom / eraser rubber band.
- Hover (`scene().sigMouseMoved`) drives the preview lines and the cursor.

Everything is in data coordinates (offset m, time ms). Two gotchas:
- Scatter items must be `PassiveScatter` (from `drawers.py`). A plain `ScatterPlotItem` accepts left clicks on its points, so a click on a pick would never reach the ViewBox.
- pyqtgraph drops mouse moves less than 10 ms apart (`mouseRateLimit`). Tests that synthesise mouse events need `QTest.qWait` between moves.

The pick chart manages its range explicitly; it doesn't autorange. `reset_view()` fits the alive picks plus the origin. A zoom persists across shots until Esc calls `reset_zoom()`.

### `UNDEFINED = 0`

`structs.UNDEFINED` is 0 and is the sentinel for "not set": a slope or intercept of 0, a thickness of 0 and the point (0, 0) all mean absent. This is why code checks `is_point_defined(point)` rather than for None in stored data, and why `draw_points.bin` can be pre-allocated as zeroed bytes.

### Physics conventions (`interpretation.py`)

Chart axes are offset in metres (x) and travel time in **milliseconds** (y), so drawer slopes are in ms/m. Velocity is `|1000 / slope|` (m/s), and intercept times are divided by 1000 to reach seconds before entering the thickness math. `_layer1/2/3` implement the dipping-layer intercept-time method and are **chained**: layer 2 consumes layer 1's `h, v, w`, and layer 3 consumes both. Left/right apparent velocities are combined, and a missing side falls back to the other. The math runs on numpy float64 scalars with errors ignored, so an incoherent interpretation surfaces as NaN thicknesses instead of raising. `MainWindow.save_plot` reports those NaNs.

### Main window lifecycle (`main_window.py`)

`set_project()` / `set_line()` load and unload. Each line load builds a **fresh** `PickChart` and `LayerChart`; they are never reused, and `_unload_line()` deletes them.

Shot changes all go through `try_change_shot()`. It compares the current draw points against `last_saved_draw_points` and asks Save / Don't save / Cancel before discarding. `_update_plot()` seeds the drawers with the nearest interpretation saved at or before the main shot (`_first_previous_draw_points`). If there is none, the drawers keep whatever they had.

`LayerChart` doubles as a navigation control: clicking it emits `shotClicked` with the shot whose `sou_x` is nearest.
