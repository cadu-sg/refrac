"""Least-squares fits that snap a drawn line to the picks it covers.

A line is fitted to the picks between its intersections with its neighbours, so the fit of each
line depends on the lines drawn next to it: the outermost head waves have one inner neighbour,
the other head waves two, and the direct waves start at the origin.
"""

import math
from collections.abc import Callable

import numpy as np

from refrac.drawers import ORIGIN, Intersection, LineDrawer
from refrac.geometry import least_squares

type PicksSource = Callable[[], tuple[np.ndarray, np.ndarray]]


class LineFit:

    def __init__(self, drawer: LineDrawer, picks: PicksSource):
        self.drawer = drawer
        self._picks = picks

    def fit(self) -> None:
        """Fits the line to the picks within its bounds, if it is drawn and covers two picks."""
        if not self.drawer.drawn:
            return
        start, end = sorted(self._bounds())
        x, y = self._picks()
        inside = (x >= start) & (x <= end)
        if np.count_nonzero(inside) < 2:
            return
        slope, intercept = least_squares(x[inside], y[inside])
        if math.isfinite(slope) and math.isfinite(intercept):
            self._apply(slope, intercept)

    def _bounds(self) -> tuple[float, float]:
        raise NotImplementedError

    def _apply(self, slope: float, intercept: float) -> None:
        # Keep the x of both end points and move them onto the fitted line
        x1, x2 = self.drawer.p1[0], self.drawer.p2[0]
        self.drawer.set_points((x1, slope * x1 + intercept), (x2, slope * x2 + intercept))


class LineFitEdgy(LineFit):
    """Outermost head wave: from its inner intersection to its end point farthest from the shot."""

    def __init__(self, drawer: LineDrawer, picks: PicksSource, intersection: Intersection):
        super().__init__(drawer, picks)
        self._intersection = intersection

    def _bounds(self) -> tuple[float, float]:
        x1, x2 = self.drawer.p1[0], self.drawer.p2[0]
        point = self._intersection.point()
        if point is None:
            return x1, x2
        return point[0], x1 if abs(x1) > abs(x2) else x2


class LineFitTwoIntersections(LineFit):
    """Inner head wave: between its intersections with both neighbours."""

    def __init__(self, drawer: LineDrawer, picks: PicksSource,
                 intersection1: Intersection, intersection2: Intersection):
        super().__init__(drawer, picks)
        self._intersections = (intersection1, intersection2)

    def _bounds(self) -> tuple[float, float]:
        x1, x2 = self.drawer.p1[0], self.drawer.p2[0]
        points = [point for point in (i.point() for i in self._intersections) if point is not None]
        if len(points) == 2:
            return points[0][0], points[1][0]
        if len(points) == 1:
            start = points[0][0]
            return start, _farthest_from(start, x1, x2)
        return x1, x2


class LineFitOriginFixed(LineFit):
    """Direct wave: from the origin to its intersection with the first head wave."""

    def __init__(self, drawer: LineDrawer, picks: PicksSource, intersection: Intersection):
        super().__init__(drawer, picks)
        self._intersection = intersection

    def _bounds(self) -> tuple[float, float]:
        point = self._intersection.point()
        return self.drawer.p1[0], point[0] if point is not None else self.drawer.p2[0]

    def _apply(self, slope: float, intercept: float) -> None:
        # Only the free end point moves, the line stays pinned to the origin
        x = self.drawer.p2[0]
        self.drawer.set_points(ORIGIN, (x, slope * x + intercept))


def _farthest_from(value: float, candidate1: float, candidate2: float) -> float:
    """The candidate whose distance from the shot differs most from that of value."""
    if abs(abs(value) - abs(candidate1)) > abs(abs(value) - abs(candidate2)):
        return candidate1
    return candidate2
