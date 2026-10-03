"""Pick chart: travel time against offset for the loaded shots, with the tools that act on it."""

import math
from dataclasses import dataclass

import numpy as np
import pyqtgraph as pg
from PySide6.QtCore import QPointF, QRectF, Qt, Signal
from PySide6.QtWidgets import QGraphicsRectItem, QVBoxLayout, QWidget

from refrac.drawers import (ORIGIN, Intersection, LineDrawer, OriginFixedLineDrawer,
                            PassiveScatter, StandardLineDrawer)
from refrac.line_fit import LineFit, LineFitEdgy, LineFitOriginFixed, LineFitTwoIntersections
from refrac.structs import UNDEFINED, UNDEFINED_POINT, Point, Shot, is_point_defined

# The eight line drawers: head (refracted) waves 3, 2, 1 and the direct wave on the left of the
# shot, then the direct wave and head waves 1, 2, 3 on its right. This ordering is shared by the
# draw points packing, the interpretation and the UI.
DRAWER_NAMES = ("head3L", "head2L", "head1L", "directL", "directR", "head1R", "head2R", "head3R")
DIRECT_NAMES = ("directL", "directR")
HEAD_NAMES = tuple(name for name in DRAWER_NAMES if name not in DIRECT_NAMES)

# Color and symbol of each wave, a drawer name being its wave plus its side (L or R)
WAVE_STYLES = {
    "direct": ("#F3622D", "s"),
    "head1": ("#FBA71B", "d"),
    "head2": ("#57B757", "o"),
    "head3": ("#41A9C9", "s"),
}

# Adjacent lines whose intersection bounds their fits and is marked on the chart, in the order of
# the x3L..x3R interpretation columns
INTERSECTION_PAIRS = (("head3L", "head2L"), ("head2L", "head1L"), ("head1L", "directL"),
                      ("directR", "head1R"), ("head1R", "head2R"), ("head2R", "head3R"))

# Each line's fit range depends on its inner neighbour, so lines are fitted from the shot outwards
FIT_ORDER = ("directL", "head1L", "head2L", "head3L", "directR", "head1R", "head2R", "head3R")

ZOOM = "zoom"
ERASER = "eraser"

GRAB_RADIUS = 8  # px around a line end point that starts dragging it
MIN_ZOOM_SIZE = 15  # px, smaller zoom rectangles are ignored as accidental
ZOOM_COLOR = (135, 206, 250)
ERASER_COLOR = (224, 29, 29)


def wave_style(drawer_name: str) -> tuple[str, str]:
    return WAVE_STYLES[drawer_name[:-1]]


@dataclass(frozen=True)
class PickStyle:
    symbols_visible: bool = True
    symbol_size: float = 4.0  # px
    symbol_color: str = "#000000"
    line_visible: bool = False
    line_width: float = 2.0
    line_color: str = "#f3622d"


class _ToolViewBox(pg.ViewBox):
    """ViewBox that hands clicks and drags to the chart tools instead of panning and zooming."""

    def __init__(self, on_click, on_drag):
        super().__init__(enableMenu=False)
        self.setMouseEnabled(False, False)
        self._on_click = on_click
        self._on_drag = on_drag

    def mouseClickEvent(self, ev):
        ev.accept()
        self._on_click(ev)

    def mouseDragEvent(self, ev, axis=None):
        ev.accept()
        self._on_drag(ev)


class PickChart(QWidget):
    """Plots the picks of the loaded shots and hosts the tools that act on them: the eight line
    drawers, zoom and the pick eraser. At most one tool is active at a time.

    The end points of drawn lines can be dragged whatever the active tool. Vertical markers show
    where adjacent lines intersect.
    """

    drawingsChanged = Signal()
    activeToolChanged = Signal(object)  # tool name, or None
    zoomedChanged = Signal(bool)

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.active_tool: str | None = None
        self.zoomed = False

        self._view_box = _ToolViewBox(self._on_click, self._on_drag)
        self._view_box.disableAutoRange()
        self._plot_widget = pg.PlotWidget(viewBox=self._view_box)
        plot_item = self._plot_widget.getPlotItem()
        plot_item.setLabel("bottom", "Offset (m)")
        plot_item.setLabel("left", "Travel time (ms)")
        plot_item.showGrid(x=True, y=True, alpha=0.15)
        plot_item.setMenuEnabled(False)
        plot_item.hideButtons()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self._plot_widget)

        # Picks of the loaded shots, flattened. owner is the index in _shots of each pick's shot,
        # and alive is False for the picks erased with the eraser
        self._shots: list[Shot] = []
        self._main = 0
        self._x = np.empty(0)
        self._y = np.empty(0)
        self._owner = np.empty(0, dtype=int)
        self._alive = np.empty(0, dtype=bool)
        self._style = PickStyle()
        self._pick_line = pg.PlotCurveItem()
        self._pick_symbols = PassiveScatter(symbol="o", pen=None)
        plot_item.addItem(self._pick_line)
        plot_item.addItem(self._pick_symbols)

        self._drawers: dict[str, LineDrawer] = {}
        for name in DRAWER_NAMES:
            drawer_class = OriginFixedLineDrawer if name in DIRECT_NAMES else StandardLineDrawer
            color, symbol = wave_style(name)
            self._drawers[name] = drawer_class(plot_item, color, symbol, self._on_drawers_changed)

        self._intersections = [Intersection(self._drawers[name1], self._drawers[name2])
                               for name1, name2 in INTERSECTION_PAIRS]
        self._markers = []
        for _ in self._intersections:
            marker = pg.InfiniteLine(angle=90, pen=pg.mkPen("k"))
            marker.hide()
            plot_item.addItem(marker, ignoreBounds=True)
            self._markers.append(marker)

        d = self._drawers
        i3L2L, i2L1L, i1LdL, idR1R, i1R2R, i2R3R = self._intersections
        picks = self._alive_picks
        self._fits: dict[str, LineFit] = {
            "head3L": LineFitEdgy(d["head3L"], picks, i3L2L),
            "head2L": LineFitTwoIntersections(d["head2L"], picks, i3L2L, i2L1L),
            "head1L": LineFitTwoIntersections(d["head1L"], picks, i2L1L, i1LdL),
            "directL": LineFitOriginFixed(d["directL"], picks, i1LdL),
            "directR": LineFitOriginFixed(d["directR"], picks, idR1R),
            "head1R": LineFitTwoIntersections(d["head1R"], picks, idR1R, i1R2R),
            "head2R": LineFitTwoIntersections(d["head2R"], picks, i1R2R, i2R3R),
            "head3R": LineFitEdgy(d["head3R"], picks, i2R3R),
        }

        # Zoom and eraser selection rectangle
        self._rubber_band = QGraphicsRectItem()
        self._rubber_band.hide()
        self._rubber_band.setZValue(20)
        self._view_box.addItem(self._rubber_band, ignoreBounds=True)
        self._band_start: Point | None = None
        self._band_tool: str | None = None
        self._dragged_drawer: LineDrawer | None = None

        self._plot_widget.scene().sigMouseMoved.connect(self._on_mouse_moved)

    # Picks

    def plot(self, shots: list[Shot], main_index: int) -> None:
        """Plots the picks of the given shots, shots[main_index] being the one interpreted."""
        self._shots = shots
        self._main = main_index
        self._x = np.array([pick.offset for shot in shots for pick in shot.picks], dtype=float)
        self._y = np.array([pick.travel_time for shot in shots for pick in shot.picks], dtype=float)
        self._owner = np.repeat(np.arange(len(shots)), [len(shot.picks) for shot in shots])
        self._alive = np.ones(len(self._x), dtype=bool)
        self._refresh_picks()
        if not self.zoomed:
            self.reset_view()

    def get_main_shot(self) -> Shot:
        """The main shot, without the picks erased with the eraser."""
        main = self._shots[self._main]
        alive = self._alive[self._owner == self._main]
        return Shot(main.seq_num, main.sou_stat, main.sou_x, main.sou_y, main.sou_elev,
                    [pick for pick, keep in zip(main.picks, alive) if keep])

    def set_pick_style(self, style: PickStyle) -> None:
        self._style = style
        self._refresh_picks()

    def erase_picks(self, x_min: float, x_max: float, y_min: float, y_max: float) -> None:
        """Erases the picks of all loaded shots inside the rectangle."""
        inside = (self._x >= x_min) & (self._x <= x_max) & (self._y >= y_min) & (self._y <= y_max)
        self._alive &= ~inside
        self._refresh_picks()
        if not self.zoomed:
            self.reset_view()

    def _alive_picks(self) -> tuple[np.ndarray, np.ndarray]:
        return self._x[self._alive], self._y[self._alive]

    def _refresh_picks(self) -> None:
        x, y = self._alive_picks()
        style = self._style
        self._pick_symbols.setData(
            x=x, y=y, size=style.symbol_size, pen=None, brush=pg.mkBrush(style.symbol_color))
        self._pick_symbols.setVisible(style.symbols_visible)
        # The line joins the picks in offset order
        order = np.argsort(x, kind="stable")
        self._pick_line.setData(x[order], y[order],
                                pen=pg.mkPen(style.line_color, width=style.line_width))
        self._pick_line.setVisible(style.line_visible)

    # View range

    def reset_view(self) -> None:
        """Fits the view to the picks, always including the origin (the shot)."""
        x, y = self._alive_picks()
        x, y = np.append(x, 0.0), np.append(y, 0.0)
        self._view_box.setRange(xRange=(x.min(), x.max()), yRange=(y.min(), y.max()), padding=0.05)

    def zoom_to(self, x_min: float, x_max: float, y_min: float, y_max: float) -> None:
        """Zooms in on the rectangle, until reset_zoom is called."""
        self._view_box.setRange(xRange=(x_min, x_max), yRange=(y_min, y_max), padding=0)
        if not self.zoomed:
            self.zoomed = True
            self.zoomedChanged.emit(True)

    def reset_zoom(self) -> None:
        if self.zoomed:
            self.zoomed = False
            self.reset_view()
            self.zoomedChanged.emit(False)

    # Tools

    def set_active_tool(self, tool: str | None) -> None:
        """Activates a tool, ZOOM, ERASER or a drawer name, deactivating the previous one."""
        if tool == self.active_tool:
            return
        if self.active_tool in self._drawers:
            self._drawers[self.active_tool].set_enabled(False)
        self.active_tool = tool
        if tool in self._drawers:
            self._drawers[tool].set_enabled(True)
        self._band_start = None
        self._rubber_band.hide()
        self._update_cursor()
        self.activeToolChanged.emit(tool)

    # Line drawers

    def drawer(self, name: str) -> LineDrawer:
        return self._drawers[name]

    def slopes(self) -> dict[str, float]:
        return {name: drawer.slope for name, drawer in self._drawers.items()}

    def head_intercepts(self) -> dict[str, float]:
        return {name: self._drawers[name].intercept for name in HEAD_NAMES}

    def intersection_xs(self) -> list[float]:
        """X of each intersection in INTERSECTION_PAIRS, or UNDEFINED where there is none."""
        return [UNDEFINED if (point := intersection.point()) is None else point[0]
                for intersection in self._intersections]

    def get_draw_points(self) -> list[Point]:
        """The 14 points defining the lines, packed in the draw_points.bin order. The direct
        waves only contribute their free end point, and incomplete lines are UNDEFINED."""
        points = []
        for name, drawer in self._drawers.items():
            p1, p2 = (drawer.p1, drawer.p2) if drawer.complete else (UNDEFINED_POINT, UNDEFINED_POINT)
            points += [p2] if name in DIRECT_NAMES else [p1, p2]
        return points

    def set_draw_points(self, points: list[Point]) -> None:
        """Replaces the lines with those defined by the 14 points from get_draw_points."""
        self.clear_line_drawers()
        remaining = iter(points)
        for name, drawer in self._drawers.items():
            if name in DIRECT_NAMES:
                p2 = next(remaining)
                if is_point_defined(p2):
                    drawer.set_points(ORIGIN, p2)
            else:
                p1, p2 = next(remaining), next(remaining)
                if is_point_defined(p1):
                    drawer.set_points(p1, p2)

    def clear_line_drawers(self) -> None:
        if self.active_tool in self._drawers:
            self.set_active_tool(None)
        for drawer in self._drawers.values():
            drawer.clear()

    def fit(self, name: str) -> None:
        self._fits[name].fit()

    def fit_all(self) -> None:
        for name in FIT_ORDER:
            self._fits[name].fit()

    def fit_selected(self) -> None:
        """Fits the line selected for drawing, if any."""
        if self.active_tool in self._fits:
            self._fits[self.active_tool].fit()

    def _on_drawers_changed(self) -> None:
        for intersection, marker in zip(self._intersections, self._markers):
            point = intersection.point()
            marker.setVisible(point is not None)
            if point is not None:
                marker.setValue(point[0])
        self.drawingsChanged.emit()

    # Mouse

    def _view_pos(self, scene_pos: QPointF) -> Point:
        pos = self._view_box.mapSceneToView(scene_pos)
        return pos.x(), pos.y()

    def _on_click(self, ev) -> None:
        drawer = self._drawers.get(self.active_tool)
        if drawer is not None:
            drawer.click(self._view_pos(ev.scenePos()), ev.button())
            self._update_cursor(ev.scenePos())

    def _on_drag(self, ev) -> None:
        if ev.isStart():
            self._begin_drag(ev)
        pos = self._view_pos(ev.scenePos())
        if self._dragged_drawer is not None:
            self._dragged_drawer.drag_to(pos)
            if ev.isFinish():
                self._dragged_drawer.finish_drag()
                self._dragged_drawer = None
        elif self._band_start is not None:
            end = self._clamp_to_view(pos)
            self._rubber_band.setRect(QRectF(QPointF(*self._band_start), QPointF(*end)).normalized())
            if ev.isFinish():
                self._rubber_band.hide()
                self._apply_band(self._band_start, end)
                self._band_start = None
        self._update_cursor(ev.scenePos())

    def _begin_drag(self, ev) -> None:
        press = ev.buttonDownScenePos()
        left = ev.button() == Qt.MouseButton.LeftButton
        grabbed = self._end_point_at(press) if left else None
        if grabbed is not None:
            drawer, index = grabbed
            drawer.start_drag(index)
            self._dragged_drawer = drawer
        elif left and self.active_tool in (ZOOM, ERASER):
            self._band_tool = self.active_tool
            self._band_start = self._clamp_to_view(self._view_pos(press))
            color = ZOOM_COLOR if self.active_tool == ZOOM else ERASER_COLOR
            self._rubber_band.setPen(pg.mkPen(color=(*color, 204)))
            self._rubber_band.setBrush(pg.mkBrush((*color, 51)))
            self._rubber_band.setRect(QRectF(QPointF(*self._band_start), QPointF(*self._band_start)))
            self._rubber_band.show()
        elif self.active_tool in self._drawers:
            # A press that moved a little is still a click for the drawer
            self._drawers[self.active_tool].click(self._view_pos(press), ev.button())

    def _apply_band(self, start: Point, end: Point) -> None:
        x_min, x_max = sorted((start[0], end[0]))
        y_min, y_max = sorted((start[1], end[1]))
        if self._band_tool == ZOOM:
            corner1 = self._view_box.mapViewToScene(QPointF(*start))
            corner2 = self._view_box.mapViewToScene(QPointF(*end))
            if (abs(corner1.x() - corner2.x()) >= MIN_ZOOM_SIZE
                    and abs(corner1.y() - corner2.y()) >= MIN_ZOOM_SIZE):
                self.zoom_to(x_min, x_max, y_min, y_max)
        else:
            self.erase_picks(x_min, x_max, y_min, y_max)

    def _clamp_to_view(self, pos: Point) -> Point:
        (x_min, x_max), (y_min, y_max) = self._view_box.viewRange()
        return min(max(pos[0], x_min), x_max), min(max(pos[1], y_min), y_max)

    def _end_point_at(self, scene_pos: QPointF) -> tuple[LineDrawer, int] | None:
        """The draggable line end point within GRAB_RADIUS of the position, if any."""
        nearest = None
        for drawer in self._drawers.values():
            for index, point in drawer.draggable_points():
                pos = self._view_box.mapViewToScene(QPointF(*point))
                distance = math.hypot(pos.x() - scene_pos.x(), pos.y() - scene_pos.y())
                if distance <= GRAB_RADIUS and (nearest is None or distance < nearest[0]):
                    nearest = (distance, drawer, index)
        return None if nearest is None else nearest[1:]

    def _on_mouse_moved(self, scene_pos: QPointF) -> None:
        pos = self._view_pos(scene_pos)
        for drawer in self._drawers.values():
            drawer.hover(pos)
        self._update_cursor(scene_pos)

    def _update_cursor(self, scene_pos: QPointF | None = None) -> None:
        drawer = self._drawers.get(self.active_tool)
        if self._dragged_drawer is not None:
            cursor = Qt.CursorShape.ClosedHandCursor
        elif scene_pos is not None and self._end_point_at(scene_pos) is not None:
            cursor = Qt.CursorShape.OpenHandCursor
        elif drawer is not None and not drawer.drawn:
            cursor = Qt.CursorShape.CrossCursor
        else:
            cursor = Qt.CursorShape.ArrowCursor
        self._plot_widget.viewport().setCursor(cursor)
