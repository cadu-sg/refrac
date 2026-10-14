"""Layer chart: the surface and the interpreted interfaces along the line."""

import numpy as np
import pyqtgraph as pg
from PySide6.QtCore import QPointF, Qt, Signal
from PySide6.QtGui import QBrush, QColor, QFont, QLinearGradient, QPen, QPolygonF
from PySide6.QtWidgets import QGraphicsPolygonItem, QVBoxLayout, QWidget

from refrac.structs import UNDEFINED, Point, Shot, ShotMetadata, Station

SURFACE_COLOR = "#F3622D"
SOURCE_COLOR = "#FF9800"
SOURCE_LINE_COLOR = "#9E9E9E"
# Colors of layers 1 to 4, from the slowest to the fastest velocity of each layer
LAYER_COLORMAPS = tuple(pg.ColorMap(None, [pg.mkColor(color) for color in colors]) for colors in (
    ("#FDD0A2", "#FD8D3C", "#A63603"),
    ("#C7E9C0", "#74C476", "#006D2C"),
    ("#C6DBEF", "#6BAED6", "#08519C"),
    ("#DADAEB", "#9E9AC8", "#54278F"),
))
# A layer's colormap spans at least this fraction of its mean velocity, so that small velocity
# differences don't stretch over the whole colormap
MIN_RELATIVE_SPAN = 0.1
FILL_ALPHA = 170
HOVER_FONT_SIZE = 8  # pt
DEFAULT_DEPTH = 1000  # m: default bottom of the chart below the lowest station
TOP_MARGIN = 0.05  # fraction of the elevation range left above the highest point


class LayerChart(QWidget):
    """Elevation profile of the line: the surface through every station, the receivers and source
    of the main shot, and the interfaces interpreted below each shot.

    Clicking the chart emits shotClicked with the index of the shot nearest to the click.

    Below a shot, layer n spans from its interface n point up to the point above it, and the
    deepest layer, below the shot's last interface, reaches down to the bottom of the chart. Each
    layer is colored from its velocity: each layer has its own hue, from light for the slowest
    velocity of the layer along the line to dark for the fastest. Each interface point takes the
    color of the layer above it, and the layers are filled with a gradient between the shots.

    Hovering the chart shows the velocity of a layer below one shot: the shot nearest to the
    mouse along x, and the layer the mouse is in. A thin segment spans the layer, from its
    interface point (or the bottom of the chart) up to the point above.

    The y axis spans from the bottom elevation, DEFAULT_DEPTH below the lowest station (floored to the metre) unless
    set_bottom changes it, up to the highest station or source. The x axis autoranges.
    """

    shotClicked = Signal(int)

    def __init__(self, stations: list[Station], shots_metadata: list[ShotMetadata],
                 parent: QWidget | None = None):
        super().__init__(parent)
        self._plot_widget = pg.PlotWidget()
        plot_item = self._plot_widget.getPlotItem()
        plot_item.showAxis("top")
        plot_item.hideAxis("bottom")
        plot_item.showGrid(x=True, y=True, alpha=0.15)
        plot_item.setMenuEnabled(False)
        plot_item.hideButtons()
        plot_item.getViewBox().setMouseEnabled(False, False)
        plot_item.addLegend(offset=(10, -10))
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self._plot_widget)

        self._stations = {station.num: station for station in stations}
        surface = sorted(stations, key=lambda station: station.x)
        self._surface_x = np.array([s.x for s in surface], dtype=float)
        self._surface_elev = np.array([s.elev for s in surface], dtype=float)
        plot_item.plot(self._surface_x, self._surface_elev,
                       pen=pg.mkPen(SURFACE_COLOR, width=2), name="Surface")
        self._interfaces = []
        for number, colormap in enumerate(LAYER_COLORMAPS[:3], start=1):
            dark, middle = colormap.map(1.0, mode="qcolor"), colormap.map(0.5, mode="qcolor")
            interface = plot_item.plot([], [], pen=pg.mkPen(dark, width=2), symbol="o",
                                       symbolSize=7, symbolPen=pg.mkPen(dark, width=1),
                                       symbolBrush=middle, name=f"Interface {number}")
            # The legend draws the scatter's default brush, which the points' brushes leave alone
            interface.scatter.setBrush(middle)
            self._interfaces.append(interface)
        self._fills: list[QGraphicsPolygonItem] = []
        self._receivers = pg.ScatterPlotItem(symbol="o", size=4, pen=None, brush="k")
        self._source = pg.ScatterPlotItem(symbol="d", size=15, pen=None, brush=SOURCE_COLOR)
        plot_item.addItem(self._receivers)
        plot_item.addItem(self._source)
        # Vertical line through the source, hidden until a shot is plotted
        self._source_line = pg.InfiniteLine(
            angle=90, pen=pg.mkPen(SOURCE_LINE_COLOR, width=1, style=Qt.PenStyle.DashLine))
        self._source_line.hide()
        plot_item.addItem(self._source_line, ignoreBounds=True)

        foreground = pg.getConfigOption("foreground")
        label_fill = pg.mkColor(pg.getConfigOption("background"))
        label_fill.setAlpha(200)
        self._hover_segment = pg.PlotDataItem([], [], pen=pg.mkPen(foreground, width=1))
        self._hover_text = pg.TextItem(color=foreground, fill=label_fill)
        font = QFont()
        font.setPointSize(HOVER_FONT_SIZE)
        self._hover_text.setFont(font)
        for item in (self._hover_segment, self._hover_text):
            item.hide()
            plot_item.addItem(item, ignoreBounds=True)
        self._hover_pos: Point | None = None

        # (x, interface 1 elevation, interface 2 elevation, interface 3 elevation) by station
        self.interface_points: dict[int, tuple[float, float, float, float]] = {}
        # (station elevation, (h1, h2, h3), (v1, v2, v3, v4)) by station
        self._layers: dict[int, tuple[float, tuple[float, ...], tuple[float, ...]]] = {}

        self._shots_sou_x = np.array([shot.sou_x for shot in shots_metadata], dtype=float)
        self._plot_widget.scene().sigMouseClicked.connect(self._on_click)
        self._plot_widget.scene().sigMouseMoved.connect(self._on_mouse_moved)

        # Elevation of the highest point on the chart: the interfaces are below the surface
        self.top = max([*self._surface_elev, *(shot.sou_elev for shot in shots_metadata)])
        # A whole metre, as the bottom is set in whole metres
        self.set_bottom(float(np.floor(self._surface_elev.min())) - DEFAULT_DEPTH)

    def set_bottom(self, elevation: float) -> None:
        """Sets the elevation at the bottom of the chart, which should be below self.top."""
        self.bottom = elevation
        margin = TOP_MARGIN * (self.top - elevation)
        self._plot_widget.getPlotItem().getViewBox().setYRange(elevation, self.top + margin,
                                                               padding=0)
        # The deepest layers reach down to the bottom
        self._refresh_fills()
        if self._hover_pos is not None:
            self.show_velocity_at(*self._hover_pos)

    def plot_layer_thickness(self, thicknesses: list[float], velocities: list[float],
                             station: Station) -> None:
        """Plots the interfaces below the station from the thicknesses of the three layers, or
        removes them if the first thickness is UNDEFINED. An interface whose layer thickness is
        UNDEFINED is left out, so its line joins the stations around. The velocities of the four
        layers color them and are shown on hover."""
        h1, h2, h3 = thicknesses
        if h1 == UNDEFINED:
            self.interface_points.pop(station.num, None)
            self._layers.pop(station.num, None)
        else:
            elevation1 = station.elev - h1
            elevation2 = elevation1 - h2
            elevation3 = elevation2 - h3
            self.interface_points[station.num] = (station.x, elevation1, elevation2, elevation3)
            self._layers[station.num] = (station.elev, (h1, h2, h3), tuple(velocities))
        for number, interface in enumerate(self._interfaces, start=1):
            colors = self._layer_colors(number)
            middle = LAYER_COLORMAPS[number - 1].map(0.5, mode="qcolor")
            interface.setData(*self._interface_line(number),
                              symbolBrush=[colors.get(num, middle)
                                           for num in self._interface_stations(number)])
        self._refresh_fills()
        if self._hover_pos is not None:
            self.show_velocity_at(*self._hover_pos)

    def plot_shot_in_surface(self, shot: Shot) -> None:
        """Marks the receivers and the source of the shot on the surface, and the x of the source
        with a vertical line."""
        receivers = [self._stations[num] for num in {pick.rec_stat for pick in shot.picks}
                     if num in self._stations]
        self._receivers.setData(x=[s.x for s in receivers], y=[s.elev for s in receivers])
        self._source.setData(x=[shot.sou_x], y=[shot.sou_elev])
        self._source_line.setValue(shot.sou_x)
        self._source_line.show()

    def velocity_target(self, x: float, y: float) -> tuple[int, int] | None:
        """The station and the layer (1 to 4) whose velocity is shown for the mouse at (x, y),
        or None if no interface is plotted.

        The layer is the one below the interface lines above y, those drawn at x (or at the
        nearest end of interface 1 if x is beyond it). The station is the one nearest to x among
        those with that layer, falling back to the layer above if there are none.
        """
        if not self._layers:
            return None
        xs, _ = self._interface_line(1)
        x_drawn = min(max(x, xs[0]), xs[-1])
        above = 0
        for interface in (1, 2, 3):
            xs, elevations = self._interface_line(interface)
            if xs.size and xs[0] <= x_drawn <= xs[-1] and np.interp(x_drawn, xs, elevations) > y:
                above += 1
        for layer in range(above + 1, 0, -1):
            candidates = self._layer_stations(layer)
            if candidates:
                station = min(candidates, key=lambda num: abs(self.interface_points[num][0] - x))
                return station, layer
        return None

    def show_velocity_at(self, x: float, y: float) -> None:
        """Shows the velocity for the mouse at (x, y), as described in velocity_target."""
        target = self.velocity_target(x, y)
        if target is None:
            self._hide_velocity()
            return
        station, layer = target
        station_x = self.interface_points[station][0]
        top = self._layer_top(station, layer)
        bottom = (self.interface_points[station][layer]
                  if layer <= 3 and self._layers[station][1][layer - 1] != UNDEFINED
                  else self.bottom)
        velocity = self._layers[station][2][layer - 1]

        self._hover_segment.setData([station_x, station_x], [top, bottom])
        (x_min, x_max), _ = self._plot_widget.getPlotItem().getViewBox().viewRange()
        self._hover_text.setText(f"{velocity:.0f} m/s")
        # Beside the segment, a little apart, on the side with more room
        self._hover_text.setAnchor((1.1, 0.5) if station_x > (x_min + x_max) / 2 else (-0.1, 0.5))
        self._hover_text.setPos(station_x, (top + bottom) / 2)
        self._hover_segment.show()
        self._hover_text.show()

    def _interface_stations(self, interface: int) -> list[int]:
        """The stations with a layer above the interface (1 to 3), sorted by x."""
        stations = [num for num, (_, thicknesses, _) in self._layers.items()
                    if thicknesses[interface - 1] != UNDEFINED]
        return sorted(stations, key=lambda num: self.interface_points[num][0])

    def _interface_line(self, interface: int) -> tuple[np.ndarray, np.ndarray]:
        """x and elevation of the points of the interface (1 to 3), sorted by x."""
        points = [self.interface_points[num] for num in self._interface_stations(interface)]
        return (np.array([point[0] for point in points], dtype=float),
                np.array([point[interface] for point in points], dtype=float))

    def _layer_stations(self, layer: int) -> list[int]:
        """The stations below which the layer (1 to 4) is, with a velocity, sorted by x."""
        stations = (sorted(self._layers, key=lambda num: self.interface_points[num][0])
                    if layer == 1 else self._interface_stations(layer - 1))
        return [num for num in stations
                if self._layers[num][2][layer - 1] != UNDEFINED
                and np.isfinite(self._layers[num][2][layer - 1])]

    def _layer_top(self, station: int, layer: int) -> float:
        """Elevation of the top of the layer (1 to 4) below the station."""
        return self._layers[station][0] if layer == 1 else self.interface_points[station][layer - 1]

    def _layer_colors(self, layer: int) -> dict[int, QColor]:
        """Color of the layer (1 to 4) below each station with that layer, sorted by x."""
        stations = self._layer_stations(layer)
        if not stations:
            return {}
        velocities = np.array([self._layers[num][2][layer - 1] for num in stations], dtype=float)
        v_min, v_max = velocities.min(), velocities.max()
        span = max(v_max - v_min, MIN_RELATIVE_SPAN * velocities.mean())
        positions = (np.clip((velocities - (v_min + v_max) / 2) / span + 0.5, 0, 1) if span > 0
                     else np.full(len(stations), 0.5))
        return dict(zip(stations, LAYER_COLORMAPS[layer - 1].map(positions, mode="qcolor")))

    def _refresh_fills(self) -> None:
        """Fills the layers between each pair of neighbouring stations with that layer, with a
        gradient from the color of the layer below one station to the other's. A layer between
        two interfaces is filled between the stations with its interface, and the deepest layer
        from the last interface down to the bottom of the chart."""
        plot_item = self._plot_widget.getPlotItem()
        for fill in self._fills:
            plot_item.removeItem(fill)
        self._fills = []
        for layer in (1, 2, 3):
            colors = self._layer_colors(layer)
            stations = [num for num in self._interface_stations(layer) if num in colors]
            top_xs, top_elevations = ((self._surface_x, self._surface_elev) if layer == 1
                                      else self._interface_line(layer - 1))
            for a, b in zip(stations, stations[1:]):
                x_a, x_b = self.interface_points[a][0], self.interface_points[b][0]
                between = (top_xs > x_a) & (top_xs < x_b)
                self._add_fill([(x_a, self._layer_top(a, layer)),
                                *zip(top_xs[between], top_elevations[between]),
                                (x_b, self._layer_top(b, layer)),
                                (x_b, self.interface_points[b][layer]),
                                (x_a, self.interface_points[a][layer])],
                               x_a, x_b, colors[a], colors[b])
        for interface in (1, 2, 3):
            colors = self._layer_colors(interface + 1)
            stations = self._interface_stations(interface)
            below = set(self._interface_stations(interface + 1)) if interface < 3 else set()
            for a, b in zip(stations, stations[1:]):
                # Unless the next interface spans the pair, the layer below reaches the bottom
                if {a, b} <= below or a not in colors or b not in colors:
                    continue
                x_a, x_b = self.interface_points[a][0], self.interface_points[b][0]
                self._add_fill([(x_a, self.interface_points[a][interface]),
                                (x_b, self.interface_points[b][interface]),
                                (x_b, self.bottom), (x_a, self.bottom)],
                               x_a, x_b, colors[a], colors[b])

    def _add_fill(self, outline: list[Point], x_a: float, x_b: float, color_a: QColor,
                  color_b: QColor) -> None:
        """Fills the polygon with a gradient along x, from color_a at x_a to color_b at x_b."""
        gradient = QLinearGradient(x_a, 0, x_b, 0)
        for position, color in ((0, color_a), (1, color_b)):
            color = QColor(color)
            color.setAlpha(FILL_ALPHA)
            gradient.setColorAt(position, color)
        fill = QGraphicsPolygonItem(QPolygonF([QPointF(x, y) for x, y in outline]))
        fill.setBrush(QBrush(gradient))
        fill.setPen(QPen(Qt.PenStyle.NoPen))
        self._plot_widget.getPlotItem().addItem(fill, ignoreBounds=True)
        fill.setZValue(-1)
        self._fills.append(fill)

    def _hide_velocity(self) -> None:
        self._hover_segment.hide()
        self._hover_text.hide()

    def leaveEvent(self, event) -> None:
        self._hover_pos = None
        self._hide_velocity()
        super().leaveEvent(event)

    def _on_mouse_moved(self, scene_pos: QPointF) -> None:
        view_box = self._plot_widget.getPlotItem().getViewBox()
        if not view_box.sceneBoundingRect().contains(scene_pos):
            self._hover_pos = None
            self._hide_velocity()
            return
        pos = view_box.mapSceneToView(scene_pos)
        self._hover_pos = pos.x(), pos.y()
        self.show_velocity_at(*self._hover_pos)

    def _on_click(self, ev) -> None:
        if ev.double() or self._shots_sou_x.size == 0:
            return
        x = self._plot_widget.getPlotItem().getViewBox().mapSceneToView(ev.scenePos()).x()
        self.shotClicked.emit(int(np.argmin(np.abs(self._shots_sou_x - x))))
