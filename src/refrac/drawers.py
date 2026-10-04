"""Line drawers: the straight lines the user draws over the travel-time curve, one per branch.

All positions are pick-chart data values (offset m, travel time ms). The drawers keep their own
state and pyqtgraph items; PickChart decides which drawer receives the mouse events.
"""

from collections.abc import Callable

import numpy as np
import pyqtgraph as pg
from PySide6.QtCore import Qt

from refrac.geometry import line_intersection
from refrac.structs import UNDEFINED, Point

ORIGIN: Point = (0.0, 0.0)
SYMBOL_SIZE = 10


class PassiveScatter(pg.ScatterPlotItem):
    """Scatter whose points don't swallow clicks, so they reach the ViewBox tools below."""

    def mouseClickEvent(self, ev):
        ev.ignore()


class LineDrawer:
    """A line through two points placed with the mouse while the drawer is enabled.

    The line is `drawn` once both points are placed; dragging an end point clears it until the
    point is released. Slope and intercept are UNDEFINED while the line is not drawn.
    """

    def __init__(self, plot_item: pg.PlotItem, color: str, symbol: str,
                 on_change: Callable[[], None]):
        self.color = color
        self.symbol = symbol
        self.p1: Point | None = None
        self.p2: Point | None = None
        self.drawn = False
        self.enabled = False
        self._on_change = on_change

        self._line = pg.PlotCurveItem(pen=pg.mkPen(color, width=2))
        # Hollow end point symbols
        self._end_points = PassiveScatter(
            symbol=symbol, size=SYMBOL_SIZE, pen=pg.mkPen(color, width=1.5), brush=pg.mkBrush("w"))
        plot_item.addItem(self._line)
        plot_item.addItem(self._end_points)
        self._end_points.setZValue(5)

        # Line from the last placed point to the mouse cursor
        self._preview = self._new_preview(plot_item)
        self._preview_start = ORIGIN

    @property
    def slope(self) -> float:
        return self._coefficients()[0]

    @property
    def intercept(self) -> float:
        return self._coefficients()[1]

    @property
    def complete(self) -> bool:
        return self.p1 is not None and self.p2 is not None

    def _coefficients(self) -> tuple[float, float]:
        if not self.drawn:
            return UNDEFINED, UNDEFINED
        (x1, y1), (x2, y2) = self.p1, self.p2
        # A vertical line gives an infinite slope
        with np.errstate(all="ignore"):
            slope = np.float64(y2 - y1) / np.float64(x2 - x1)
            return float(slope), float(y1 - slope * x1)

    def set_points(self, p1: Point, p2: Point) -> None:
        """Places both points at once, completing the line."""
        self.p1, self.p2 = p1, p2
        self.drawn = True
        self._hide_previews()
        self._refresh()

    def clear(self) -> None:
        self.enabled = False
        self.p1 = self.p2 = None
        self.drawn = False
        self._hide_previews()
        self._refresh()

    def set_enabled(self, enabled: bool) -> None:
        raise NotImplementedError

    def click(self, pos: Point, button: Qt.MouseButton) -> None:
        """Handles a click on the chart while this drawer is enabled."""
        raise NotImplementedError

    def hover(self, pos: Point) -> None:
        if self._preview.isVisible():
            _set_segment(self._preview, self._preview_start, pos)

    def draggable_points(self) -> list[tuple[int, Point]]:
        """End points that can be dragged, as (index, point) with index 0 for p1 and 1 for p2."""
        raise NotImplementedError

    def start_drag(self, index: int) -> None:
        raise NotImplementedError

    def drag_to(self, pos: Point) -> None:
        self.p2 = pos
        self._refresh()

    def finish_drag(self) -> None:
        self.drawn = True
        self._refresh()

    def _visible_end_points(self) -> list[Point]:
        raise NotImplementedError

    def _refresh(self) -> None:
        if self.complete:
            _set_segment(self._line, self.p1, self.p2)
        else:
            self._line.setData([], [])
        points = self._visible_end_points()
        self._end_points.setData(x=[p[0] for p in points], y=[p[1] for p in points])
        self._on_change()

    def _show_preview(self, start: Point, end: Point) -> None:
        self._preview_start = start
        _set_segment(self._preview, start, end)
        self._preview.show()

    def _hide_previews(self) -> None:
        self._preview.hide()

    @staticmethod
    def _new_preview(plot_item: pg.PlotItem) -> pg.PlotCurveItem:
        preview = pg.PlotCurveItem(pen=pg.mkPen("k"))
        preview.setZValue(10)
        preview.hide()
        plot_item.addItem(preview)
        return preview


class StandardLineDrawer(LineDrawer):
    """Line with two free end points, for a head wave. Marks its intercept at x = 0 while drawn."""

    def __init__(self, plot_item: pg.PlotItem, color: str, symbol: str,
                 on_change: Callable[[], None]):
        super().__init__(plot_item, color, symbol, on_change)
        # Filled symbol on the y axis
        self._intercept_marker = PassiveScatter(
            symbol=symbol, size=SYMBOL_SIZE, pen=pg.mkPen(color), brush=pg.mkBrush(color))
        plot_item.addItem(self._intercept_marker)
        self._intercept_marker.setZValue(5)

    def set_enabled(self, enabled: bool) -> None:
        self.enabled = enabled
        if not enabled:
            self._hide_previews()
            if self.p2 is None:
                # Discard a line left with only its first point
                self.p1 = None
        self._refresh()

    def click(self, pos: Point, button: Qt.MouseButton) -> None:
        if button == Qt.MouseButton.LeftButton and not self.drawn:
            if self.p1 is None:
                self.p1 = pos
                self._show_preview(self.p1, pos)
            else:
                self.p2 = pos
                self._hide_previews()
                self.drawn = True
        elif button == Qt.MouseButton.RightButton and self.p1 is not None:
            if self.p2 is None:
                self.p1 = None
                self._hide_previews()
            else:
                # Erase the end point nearest to the click, then draw it again from the other one
                if abs(pos[0] - self.p1[0]) < abs(pos[0] - self.p2[0]):
                    self.p1, self.p2 = self.p2, self.p1
                self.p2 = None
                self.drawn = False
                self._show_preview(self.p1, pos)
        self._refresh()

    def draggable_points(self) -> list[tuple[int, Point]]:
        return [(0, self.p1), (1, self.p2)] if self.complete else []

    def start_drag(self, index: int) -> None:
        if index == 0:
            # The dragged point is always p2
            self.p1, self.p2 = self.p2, self.p1
        self.drawn = False
        self._refresh()

    def _visible_end_points(self) -> list[Point]:
        return [p for p in (self.p1, self.p2) if p is not None]

    def _refresh(self) -> None:
        if self.drawn:
            self._intercept_marker.setData(x=[0.0], y=[self.intercept])
        else:
            self._intercept_marker.setData(x=[], y=[])
        super()._refresh()


class OriginFixedLineDrawer(LineDrawer):
    """Line pinned to the origin (the shot), for the direct wave. Only its second point is free.

    While that point is being placed or dragged, a second preview mirrored across the y axis helps
    match both sides of the shot.
    """

    def __init__(self, plot_item: pg.PlotItem, color: str, symbol: str,
                 on_change: Callable[[], None]):
        super().__init__(plot_item, color, symbol, on_change)
        self._mirror = self._new_preview(plot_item)

    def set_enabled(self, enabled: bool) -> None:
        self.enabled = enabled
        if enabled:
            if not self.drawn:
                self.p1 = ORIGIN
                self._show_previews(ORIGIN)
        else:
            self._hide_previews()
            if self.p2 is None:
                self.p1 = None
        self._refresh()

    def click(self, pos: Point, button: Qt.MouseButton) -> None:
        if button == Qt.MouseButton.LeftButton and not self.drawn:
            self.p1 = ORIGIN
            self.p2 = pos
            self._hide_previews()
            self.drawn = True
        elif button == Qt.MouseButton.RightButton and self.drawn:
            self.p2 = None
            self.drawn = False
            self._show_previews(pos)
        self._refresh()

    def hover(self, pos: Point) -> None:
        super().hover(pos)
        if self._mirror.isVisible():
            _set_segment(self._mirror, ORIGIN, _mirrored(pos))

    def draggable_points(self) -> list[tuple[int, Point]]:
        return [(1, self.p2)] if self.complete else []

    def start_drag(self, index: int) -> None:
        self.drawn = False
        _set_segment(self._mirror, ORIGIN, _mirrored(self.p2))
        self._mirror.show()
        self._refresh()

    def drag_to(self, pos: Point) -> None:
        _set_segment(self._mirror, ORIGIN, _mirrored(pos))
        super().drag_to(pos)

    def finish_drag(self) -> None:
        self._mirror.hide()
        super().finish_drag()

    def set_points(self, p1: Point, p2: Point) -> None:
        super().set_points(ORIGIN, p2)

    def _show_previews(self, end: Point) -> None:
        self._show_preview(ORIGIN, end)
        _set_segment(self._mirror, ORIGIN, _mirrored(end))
        self._mirror.show()

    def _hide_previews(self) -> None:
        super()._hide_previews()
        self._mirror.hide()

    def _visible_end_points(self) -> list[Point]:
        # The origin point is fixed, so it is not shown
        return [self.p2] if self.p2 is not None else []


class Intersection:
    """Intersection of two adjacent lines, defined while both are drawn and not parallel."""

    def __init__(self, drawer1: LineDrawer, drawer2: LineDrawer):
        self.drawer1 = drawer1
        self.drawer2 = drawer2

    def point(self) -> Point | None:
        if not (self.drawer1.drawn and self.drawer2.drawn):
            return None
        return line_intersection(self.drawer1.p1, self.drawer1.p2, self.drawer2.p1, self.drawer2.p2)


def _mirrored(point: Point) -> Point:
    return -point[0], point[1]


def _set_segment(item: pg.PlotCurveItem, start: Point, end: Point) -> None:
    item.setData([start[0], end[0]], [start[1], end[1]])
