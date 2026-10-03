# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`refrac` is a JavaFX 15 desktop application for **seismic refraction interpretation**. The user loads first-break picks from a seismic line, steps through shots one at a time, draws straight lines over the travel-time curve segments (direct wave + up to 3 refracted arrivals, on the left and right side of each shot), and the app derives layer velocities and thicknesses from the slopes/intercepts of those lines, plotting the resulting subsurface interfaces.

Comments in the codebase are a mix of English and Portuguese.

## Commands

```bash
mvn compile          # build
mvn javafx:run       # run the app (mainClass com.botoseis.App, configured in pom.xml)
mvn test             # no tests exist yet, despite junit-jupiter being on the test classpath
```

`maven.compiler.source/target` is 11; the app compiles and runs on newer JDKs. There is no `module-info.java`, so JavaFX runs off the classpath rather than the module path.

## Architecture

### Storage: Project → Line → four files

Everything on disk is directory-based. `Project` (`storage/Project.java`) is just a directory; each `Line` is a subdirectory of it containing exactly four files, all created together by `Line.create()` and reopened together by `Line.open()`:

| File | Class | Role |
|---|---|---|
| `picks_origin.dat` | `PicksTxt` | Verbatim copy of the user's picks file. Whitespace-delimited US-ASCII, 11 columns, one header line skipped. Full column spec is in the `PicksTxt` javadoc. Scanned once at open to derive shot count, per-shot metadata, and the station list. |
| `picks.bin` | `PicksBin` | Random-access mirror of the picks. Header = `shotAmount` (int) + `shotPositions` (long[]); each shot = 24-byte header + 32 bytes per pick. This is what the app actually reads/writes per shot; erased picks are persisted here. |
| `draw_points.bin` | `DrawPointsBin` | Header = `shotAmount` (int), then a fixed 14-point (`Point2D`, two doubles each) block per shot at a computed offset. |
| `interpretations.csv` | `InterpretationsCSV` | 21 columns per shot (thicknesses, velocities, intercepts, intersection x-values). Rewritten in full on every save — `saveInterpretation` does load-all → mutate one row → write-all. |

**`shotIndex` is the shared key.** Record *N* in `picks.bin`, block *N* in `draw_points.bin`, and row *N* in `interpretations.csv` all describe the same shot. Anything that changes shot ordering or count invalidates all three.

`Line` is a thin façade over the four storage classes; `MainController` never touches them directly.

### The eight line drawers

The dominant naming convention in the codebase is the ordered set:

```
head3L, head2L, head1L, directL, directR, head1R, head2R, head3R
```

(`head` = head wave / refracted arrival, `L`/`R` = left/right of the shot point). This same ordering recurs — and must stay consistent — across `PickChart` fields, `MainController.handleLineLoaded()`, the `InterpretationCalculator` constructor's 14 arguments, the `radioButton_*` ids in `main.fxml`, and the packing order in `PickChart.getDrawPoints()`/`setDrawPoints()`.

`getDrawPoints()` returns 14 points, not 16: the two `directL`/`directR` drawers are `OriginFixedLineDrawer`s pinned to (0,0), so only their second point is stored. That 14 is hardcoded as `DrawPointsBin.POINTS_AMOUNT` and determines the on-disk block size — changing the drawer set means changing both, and existing `draw_points.bin` files become unreadable (`DrawPointsBin.open` validates file size).

`LineDrawer` (abstract) owns point placement, dragging, the rubber-band preview line, and slope/intercept computation. Two subclasses: `StandardLineDrawer` (two free points, plus an `InterceptDrawer` marker at x=0) and `OriginFixedLineDrawer` (point 1 locked to the origin, with a mirrored preview line across the y-axis).

### Reactive wiring

JavaFX properties, not method calls, are the glue. `LineDrawer` exposes `slopeProperty()`, `interceptProperty()`, `drawnProperty()`, `enabledProperty()`, and everything downstream subscribes:

- `InterpretationCalculator` holds the 8 slope + 6 intercept properties and reads them on demand in `computeAvailableInterpretations()`.
- `VelocitiesLegend` listens to the slopes and updates its overlay grid live.
- `IntersectionDetector` listens to two drawers' `drawn` properties and recomputes their intersection; `VerticalMarkerGenerator` listens to *that* and adds/removes a vertical marker on the chart.
- `LineFit*` classes run a least-squares fit (`math/LinearRegression`) over the picks between computed x-bounds and snap the drawer to it. `PickChart.enableLineFit()` wires them to the per-wave "Fit" buttons in the Interpretation grid (disabled until the drawer is `drawn`) and to "Fit all" (fits inside-out: direct → head1 → head2 → head3); the F key (`MainController.handleFitShortcut`) fits the line whose radio button is selected.
- `MainController` binds the toolbar toggles and interpretation radio buttons *bidirectionally* to the drawers' `enabled` properties; `PickChart.ensureSingleTrue()` enforces that at most one tool (zoom, eraser, or one drawer) is active.

### `UNDEFINED = 0`

Nearly every class redeclares `private static final double UNDEFINED = 0`. Zero is the sentinel for "not set": a slope/intercept of 0, a thickness of 0, and the point `(0, 0)` all mean absent. This is why code checks `x == 0 && y == 0` rather than nullity, and why `draw_points.bin` can be pre-allocated as zeroed bytes. `IntersectionDetector` is the exception — it signals "parallel, no intersection" with `Double.MAX_VALUE`.

### Physics conventions

Chart axes are offset in metres (x) and travel time in **milliseconds** (y), so drawer slopes are ms/m. Velocity is `|1000 / slope|` (m/s) and intercept times are divided by 1000 to reach seconds before entering the thickness math. `InterpretationCalculator.computeThicknessLayer1/2/3` implement the dipping-layer intercept-time method and are **chained**: layer 2 consumes layer 1's `{h, v, w}` result array, layer 3 consumes both. Left/right apparent velocities are averaged, and a missing side falls back to the other. An incoherent interpretation surfaces as `NaN` thicknesses, which `MainController.plotLayerThicknesses` catches.

### Scene lifecycle

`App` loads `main.fxml` and stashes the `Stage` in a public static field (`App.getStage()`), used by the directory choosers. `MainController` drives everything from two `BooleanProperty` flags, `projectLoaded` and `lineLoaded`, whose listeners enable/disable UI regions. `handleLineLoaded()` constructs a **fresh** `PickChart`, `LayerChart`, and `InterpretationCalculator` per line — these are never reused, and `handleLineUnloaded()` clears the chart containers and nulls them.

Shot navigation goes through `tryToChangeLoadedShot()`, which compares the current draw points against `lastSavedDrawPoints` and prompts before discarding. Loading a shot with no saved lines calls `loadFirstPreviousDrawPoints()`, which walks *backwards* from the current index to seed the drawers with the nearest previous interpretation.

`LayerChart` doubles as a navigation control: clicking it maps the x position to the nearest shot's `souX` and jumps there (`enableGoToShotFeature`, wired to `MainController::tryToChangeLoadedShot`).

### Chart plumbing gotchas

- `LineDrawer` must be constructed **after** its chart is added to the scene graph — it reads axis display positions and calls `series.getNode()`.
- Two coordinate spaces are in play. `mouseEventToDataValues()` converts scene coords → axis data values (for placing points); `dataValuesToChartPaneCoordinates()` goes the other way (for the preview `Line`, which lives in the `chartPane` overlay, not in the chart's data). Mixing them up is the usual source of misplaced lines.
- All styling is inline `setStyle()` strings built in `SeriesLayout` — there are no CSS files. Symbol shapes are single-char codes (`o`, `s`, `D`, `^`); an `h` prefix (`hs`, `hD`) means hollow.
- `LineChartWithMarkers` extends `LineChart` purely to override `layoutPlotChildren()` and draw marker lines in plot-children space.
