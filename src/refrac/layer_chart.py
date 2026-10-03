"""Layer chart: the surface and the interpreted interfaces along the line."""

import numpy as np
import pyqtgraph as pg
from PySide6.QtCore import Signal
from PySide6.QtWidgets import QVBoxLayout, QWidget

from refrac.structs import UNDEFINED, Shot, ShotMetadata, Station

SURFACE_COLOR = "#F3622D"
INTERFACE_COLORS = ("#FBA71B", "#57B757", "#41A9C9")
SOURCE_COLOR = "#FF9800"


class LayerChart(QWidget):
    """Elevation profile of the line: the surface through every station, the receivers and source
    of the main shot, and the interfaces interpreted below each shot.

    Clicking the chart emits shotClicked with the index of the shot nearest to the click.
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

        # (x, interface 1 elevation, interface 2 elevation, interface 3 elevation) by station
        self.interface_points: dict[int, tuple[float, float, float, float]] = {}

        self._shots_sou_x = np.array([shot.sou_x for shot in shots_metadata], dtype=float)
        self._plot_widget.scene().sigMouseClicked.connect(self._on_click)

    def plot_layer_thickness(self, thicknesses: list[float], station: Station) -> None:
        """Plots the interfaces below the station from the thicknesses of the three layers, or
        removes them if the first thickness is UNDEFINED."""
        h1, h2, h3 = thicknesses
        if h1 == UNDEFINED:
            self.interface_points.pop(station.num, None)
        else:
            elevation1 = station.elev - h1
            elevation2 = elevation1 - h2
            elevation3 = elevation2 - h3
            self.interface_points[station.num] = (station.x, elevation1, elevation2, elevation3)
        rows = np.array(sorted(self.interface_points.values()), dtype=float).reshape(-1, 4)
        for column, interface in enumerate(self._interfaces, start=1):
            interface.setData(rows[:, 0], rows[:, column])

    def plot_shot_in_surface(self, shot: Shot) -> None:
        """Marks the receivers and the source of the shot on the surface."""
        receivers = [self._stations[num] for num in {pick.rec_stat for pick in shot.picks}
                     if num in self._stations]
        self._receivers.setData(x=[s.x for s in receivers], y=[s.elev for s in receivers])
        source = self._stations.get(shot.sou_stat)
        self._source.setData(x=[source.x] if source else [], y=[source.elev] if source else [])

    def _on_click(self, ev) -> None:
        if ev.double() or self._shots_sou_x.size == 0:
            return
        x = self._plot_widget.getPlotItem().getViewBox().mapSceneToView(ev.scenePos()).x()
        self.shotClicked.emit(int(np.argmin(np.abs(self._shots_sou_x - x))))
