"""Line intersection and least-squares fitting in pick-chart data space (offset m, time ms)."""

import numpy as np

from refrac.structs import Point

# Lines whose determinant is within this tolerance (ms·m) are treated as parallel
PARALLEL_TOLERANCE = 10.0


def line_intersection(p1: Point, p2: Point, q1: Point, q2: Point) -> Point | None:
    """Intersection of the infinite lines through p1-p2 and q1-q2, or None if they are parallel."""
    # Line p as a1 x + b1 y = c1, line q as a2 x + b2 y = c2
    a1 = p2[1] - p1[1]
    b1 = p1[0] - p2[0]
    c1 = a1 * p1[0] + b1 * p1[1]
    a2 = q2[1] - q1[1]
    b2 = q1[0] - q2[0]
    c2 = a2 * q1[0] + b2 * q1[1]

    determinant = a1 * b2 - a2 * b1
    if -PARALLEL_TOLERANCE <= determinant <= PARALLEL_TOLERANCE:
        return None
    return (b2 * c1 - b1 * c2) / determinant, (a1 * c2 - a2 * c1) / determinant


def least_squares(x: np.ndarray, y: np.ndarray) -> tuple[float, float]:
    """Slope and intercept of the least-squares line through the points."""
    mean_x, mean_y = x.mean(), y.mean()
    with np.errstate(all="ignore"):
        slope = np.sum(x * (y - mean_y)) / np.sum(x * (x - mean_x))
    return float(slope), float(mean_y - slope * mean_x)
