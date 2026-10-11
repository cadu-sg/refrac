"""Layer chart: the surface and the interpreted interfaces along the line."""

import numpy as np
import pyqtgraph as pg
from PySide6.QtCore import QPointF, Signal
from PySide6.QtGui import QFont
from PySide6.QtWidgets import QVBoxLayout, QWidget

from refrac.structs import UNDEFINED, Point, Shot, ShotMetadata, Station

SURFACE_COLOR = "#F3622D"
INTERFACE_COLORS = ("#FBA71B", "#57B757", "#41A9C9")
SOURCE_COLOR = "#FF9800"
HOVER_FONT_SIZE = 8  # pt


class LayerChart(QWidget):
    """Elevation profile of the line: the surface through every station, the receivers and source
    of the main shot, and the interfaces interpreted below each shot.

    Clicking the chart emits shotClicked with the index of the shot nearest to the click.

    Hovering the chart shows the velocity of a layer below one shot: the shot nearest to the
    mouse along x, and the layer the mouse is in (the deepest interface stands for the layers
    below it). A thin segment spans the layer, from its interface point up to the point above.
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
        plot_item.plot([s.x for s in surface], [s.elev for s in surface],
                       pen=pg.mkPen(SURFACE_COLOR, width=2), name="Surface")
        self._interfaces = [
            plot_item.plot([], [], pen=pg.mkPen(color, width=2), symbol="o", symbolSize=6,
                           symbolPen=pg.mkPen(color, width=1.5), symbolBrush="w",
                           name=f"Interface {number}")
            for number, color in enumerate(INTERFACE_COLORS, start=1)]
        self._receivers = pg.ScatterPlotItem(symbol="o", size=4, pen=None, brush="k")
        self._source = pg.ScatterPlotItem(symbol="d", size=15, pen=None, brush=SOURCE_COLOR)
        plot_item.addItem(self._receivers)
        plot_item.addItem(self._source)

        self._hover_segment = pg.PlotDataItem([], [])
        self._hover_text = pg.TextItem()
        font = QFont()
        font.setPointSize(HOVER_FONT_SIZE)
        self._hover_text.setFont(font)
        for item in (self._hover_segment, self._hover_text):
            item.hide()
            plot_item.addItem(item, ignoreBounds=True)
        self._hover_pos: Point | None = None

        # (x, interface 1 elevation, interface 2 elevation, interface 3 elevation) by station
        self.interface_points: dict[int, tuple[float, float, float, float]] = {}
        # (station elevation, (h1, h2, h3), (v1, v2, v3)) by station
        self._layers: dict[int, tuple[float, tuple[float, ...], tuple[float, ...]]] = {}

        self._shots_sou_x = np.array([shot.sou_x for shot in shots_metadata], dtype=float)
        self._plot_widget.scene().sigMouseClicked.connect(self._on_click)
        self._plot_widget.scene().sigMouseMoved.connect(self._on_mouse_moved)

    def plot_layer_thickness(self, thicknesses: list[float], velocities: list[float],
                             station: Station) -> None:
        """Plots the interfaces below the station from the thicknesses of the three layers, or
        removes them if the first thickness is UNDEFINED. An interface whose layer thickness is
        UNDEFINED is left out, so its line joins the stations around. The velocities of the three
        layers are shown on hover."""
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
            interface.setData(*self._interface_line(number))
        if self._hover_pos is not None:
            self.show_velocity_at(*self._hover_pos)

    def plot_shot_in_surface(self, shot: Shot) -> None:
        """Marks the receivers and the source of the shot on the surface."""
        receivers = [self._stations[num] for num in {pick.rec_stat for pick in shot.picks}
                     if num in self._stations]
        self._receivers.setData(x=[s.x for s in receivers], y=[s.elev for s in receivers])
        self._source.setData(x=[shot.sou_x], y=[shot.sou_elev])

    def velocity_target(self, x: float, y: float) -> tuple[int, int] | None:
        """The station and the interface (1 to 3) whose velocity is shown for the mouse at (x, y),
        or None if no interface is plotted.

        The interface is the first one below y, or the deepest one if y is below them all. The
        station is the one nearest to x among those with a layer above that interface, falling
        back to the interface above if there are none.
        """
        if not self._layers:
            return None
        above = 0
        for interface in (1, 2, 3):
            xs, elevations = self._interface_line(interface)
            if xs.size and np.interp(x, xs, elevations) > y:
                above += 1
        for interface in range(min(above + 1, 3), 0, -1):
            candidates = self._interface_stations(interface)
            if candidates:
                station = min(candidates, key=lambda num: abs(self.interface_points[num][0] - x))
                return station, interface
        return None

    def show_velocity_at(self, x: float, y: float) -> None:
        """Shows the velocity for the mouse at (x, y), as described in velocity_target."""
        target = self.velocity_target(x, y)
        if target is None:
            self._hide_velocity()
            return
        station, interface = target
        station_x, *elevations = self.interface_points[station]
        surface_elevation, _, velocities = self._layers[station]
        bottom = elevations[interface - 1]
        top = surface_elevation if interface == 1 else elevations[interface - 2]
        color = INTERFACE_COLORS[interface - 1]

        self._hover_segment.setData([station_x, station_x], [top, bottom])
        self._hover_segment.setPen(pg.mkPen(color, width=1))
        (x_min, x_max), _ = self._plot_widget.getPlotItem().getViewBox().viewRange()
        text = f"{velocities[interface - 1]:.0f} m/s"
        if station_x > (x_min + x_max) / 2:
            self._hover_text.setText(f"{text} ", color=color)
            self._hover_text.setAnchor((1, 0.5))
        else:
            self._hover_text.setText(f" {text}", color=color)
            self._hover_text.setAnchor((0, 0.5))
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
