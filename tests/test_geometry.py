import numpy as np
import pytest

from refrac.drawers import ORIGIN
from refrac.geometry import least_squares, line_intersection
from refrac.line_fit import LineFitEdgy, LineFitOriginFixed, LineFitTwoIntersections


def test_line_intersection():
    assert line_intersection((0, 0), (10, 10), (0, 10), (10, 0)) == pytest.approx((5, 5))


def test_parallel_lines_do_not_intersect():
    assert line_intersection((0, 0), (10, 10), (0, 5), (10, 15)) is None
    # Determinant of 5 ms·m, within the tolerance
    assert line_intersection((0, 0), (100, 1), (0, 5), (100, 6.05)) is None


def test_least_squares():
    x = np.array([0.0, 1.0, 2.0, 3.0])
    assert least_squares(x, 2 * x + 1) == pytest.approx((2, 1))


class FakeDrawer:
    def __init__(self, p1, p2, drawn=True):
        self.p1, self.p2, self.drawn = p1, p2, drawn

    def set_points(self, p1, p2):
        self.p1, self.p2 = p1, p2


class FakeIntersection:
    def __init__(self, point=None):
        self._point = point

    def point(self):
        return self._point


def v_shaped_picks():
    """Picks on t = 0.5 |x| + 3, every 10 m from -100 to 100."""
    x = np.arange(-100, 101, 10.0)
    return x, 0.5 * np.abs(x) + 3


def test_edgy_fit_goes_from_intersection_to_far_end():
    drawer = FakeDrawer((-60, 0), (-100, 0))
    fit = LineFitEdgy(drawer, v_shaped_picks, FakeIntersection((-40, 0)))
    assert fit._bounds() == (-40, -100)
    fit.fit()
    assert drawer.p1 == pytest.approx((-60, 33))
    assert drawer.p2 == pytest.approx((-100, 53))


def test_edgy_fit_without_intersection_uses_end_points():
    drawer = FakeDrawer((-60, 0), (-100, 0))
    assert LineFitEdgy(drawer, v_shaped_picks, FakeIntersection())._bounds() == (-60, -100)


def test_two_intersections_fit_bounds():
    drawer = FakeDrawer((20, 0), (70, 0))
    both = LineFitTwoIntersections(drawer, v_shaped_picks,
                                   FakeIntersection((10, 0)), FakeIntersection((60, 0)))
    assert both._bounds() == (10, 60)
    # With one intersection, the end point whose distance from the shot differs most from it
    inner = LineFitTwoIntersections(drawer, v_shaped_picks,
                                    FakeIntersection((10, 0)), FakeIntersection())
    assert inner._bounds() == (10, 70)
    outer = LineFitTwoIntersections(drawer, v_shaped_picks,
                                    FakeIntersection(), FakeIntersection((60, 0)))
    assert outer._bounds() == (60, 20)
    none = LineFitTwoIntersections(drawer, v_shaped_picks, FakeIntersection(), FakeIntersection())
    assert none._bounds() == (20, 70)


def test_origin_fixed_fit_only_moves_the_free_point():
    drawer = FakeDrawer(ORIGIN, (30, 0))
    fit = LineFitOriginFixed(drawer, v_shaped_picks, FakeIntersection((20, 0)))
    assert fit._bounds() == (0, 20)
    fit.fit()
    assert drawer.p1 == ORIGIN
    assert drawer.p2 == pytest.approx((30, 18))


def test_fit_needs_a_drawn_line_and_two_picks():
    undrawn = FakeDrawer((20, 0), (70, 0), drawn=False)
    LineFitEdgy(undrawn, v_shaped_picks, FakeIntersection()).fit()
    assert undrawn.p1 == (20, 0)

    narrow = FakeDrawer((21, 0), (29, 0))
    LineFitEdgy(narrow, v_shaped_picks, FakeIntersection()).fit()
    assert narrow.p1 == (21, 0)
