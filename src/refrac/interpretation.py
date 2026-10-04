"""Layer velocities and thicknesses from the slopes and intercepts of the drawn lines.

Slopes are in ms/m and intercepts in ms, as read off the pick chart: velocity is
|1000 / slope| (m/s) and intercept times are divided by 1000 to reach seconds. Thicknesses
follow the dipping-layer intercept-time method, chained from layer 1 down to layer 3. Left and
right apparent velocities are combined, and a missing side falls back to the other one.

Zero means undefined throughout. The arithmetic runs on numpy float64 scalars with floating
point errors ignored, so an incoherent interpretation (e.g. a refractor slower than the layer
above it) yields NaN instead of raising.
"""

from collections.abc import Mapping

import numpy as np

from refrac.structs import UNDEFINED


def velocity(slope: float) -> float:
    """Apparent velocity (m/s) of a line, or UNDEFINED if its slope is."""
    return UNDEFINED if slope == UNDEFINED else abs(1000 / slope)


def average_velocity(slope1: float, slope2: float) -> float:
    """Velocity (m/s) from the average slowness of both sides, or UNDEFINED if either is."""
    if slope1 == UNDEFINED or slope2 == UNDEFINED:
        return UNDEFINED
    return 2000 / (abs(slope1) + abs(slope2))


def compute_interpretation(
        slopes: Mapping[str, float], intercepts: Mapping[str, float],
) -> tuple[list[float], list[float]]:
    """Computes the interpretation of a shot from its drawn lines.

    :param slopes: slope (ms/m) of each of the eight lines, keyed by drawer name
    :param intercepts: intercept (ms) of each of the six head wave lines, keyed by drawer name
    :return: thicknesses [h1, h2, h3] (m) and velocities [v1, v2, v3, v4] (m/s); the layers that
        cannot be interpreted are left UNDEFINED
    """
    thicknesses = [UNDEFINED] * 3
    velocities = [UNDEFINED] * 4

    with np.errstate(all="ignore"):
        s = {name: np.float64(value) for name, value in slopes.items()}
        t = {name: np.float64(value) for name, value in intercepts.items()}

        if not (_any_defined(s["directL"], s["directR"])
                and _any_defined(s["head1L"], s["head1R"])
                and _any_defined(t["head1L"], t["head1R"])):
            return thicknesses, velocities

        v1 = _combined_velocity(s["directL"], s["directR"])
        va2, vb2 = _side_velocities(s["head1L"], s["head1R"])
        t2 = _combined_intercept(t["head1L"], t["head1R"])
        h1, v2, w2 = _layer1(v1, va2, vb2, t2)
        thicknesses[0] = h1
        velocities[0:2] = v1, v2

        if _any_defined(s["head2L"], s["head2R"]) and _any_defined(t["head2L"], t["head2R"]):
            va3, vb3 = _side_velocities(s["head2L"], s["head2R"])
            t3 = _combined_intercept(t["head2L"], t["head2R"])
            h2, v3, w3 = _layer2(v1, va3, vb3, t3, h1, v2, w2)
            thicknesses[1] = h2
            velocities[2] = v3

            if _any_defined(s["head3L"], s["head3R"]) and _any_defined(t["head3L"], t["head3R"]):
                va4, vb4 = _side_velocities(s["head3L"], s["head3R"])
                t4 = _combined_intercept(t["head3L"], t["head3R"])
                h3, v4 = _layer3(v1, va4, vb4, t4, h1, v2, w2, h2, v3, w3)
                thicknesses[2] = h3
                velocities[3] = v4

    return [float(h) for h in thicknesses], [float(v) for v in velocities]


def _any_defined(value1, value2) -> bool:
    return value1 != UNDEFINED or value2 != UNDEFINED


def _single_velocity(slope):
    return abs(1000 / slope) if slope != UNDEFINED else np.float64(UNDEFINED)


def _side_velocities(slope_a, slope_b):
    """Apparent velocities in the a (left) and b (right) directions, each falling back to the other."""
    va, vb = _single_velocity(slope_a), _single_velocity(slope_b)
    if va == UNDEFINED:
        va = vb
    if vb == UNDEFINED:
        vb = va
    return va, vb


def _combined_velocity(slope1, slope2):
    if slope1 != UNDEFINED and slope2 != UNDEFINED:
        return 1000 / ((abs(slope1) + abs(slope2)) / 2)
    if slope1 != UNDEFINED:
        return 1000 / abs(slope1)
    if slope2 != UNDEFINED:
        return 1000 / abs(slope2)
    return np.float64(UNDEFINED)


def _combined_intercept(intercept1, intercept2):
    """Intercept time (s) from the left and right intercepts (ms)."""
    if intercept1 < 0 or intercept2 < 0:
        return np.float64(UNDEFINED)
    if intercept1 != UNDEFINED and intercept2 != UNDEFINED:
        return (intercept1 + intercept2) / 2000
    if intercept1 != UNDEFINED:
        return intercept1 / 1000
    if intercept2 != UNDEFINED:
        return intercept2 / 1000
    return np.float64(UNDEFINED)


def _layer1(v1, va2, vb2, t2):
    """
    :param v1: velocity of layer 1 (m/s)
    :param va2, vb2: apparent velocities of layer 2 in the a and b directions (m/s)
    :param t2: intercept time of the refraction from interface 2 (s)
    :return: h1 (m), v2 (m/s) and w2 (rad)
    """
    alpha1_1 = np.arcsin(v1 / va2)
    beta1_1 = np.arcsin(v1 / vb2)

    h1 = (v1 / (np.cos(alpha1_1) + np.cos(beta1_1))) * t2
    v2 = v1 / np.sin((alpha1_1 + beta1_1) / 2)
    w2 = (alpha1_1 - beta1_1) / 2
    return h1, v2, w2


def _layer2(v1, va3, vb3, t3, h1, v2, w2):
    """
    :param v1: velocity of layer 1 (m/s)
    :param va3, vb3: apparent velocities of layer 3 in the a and b directions (m/s)
    :param t3: intercept time of the refraction from interface 3 (s)
    :param h1, v2, w2: results of layer 1
    :return: h2 (m), v3 (m/s) and w3 (rad)
    """
    alpha1_2 = np.arcsin(v1 / va3)
    beta1_2 = np.arcsin(v1 / vb3)

    a1_2 = alpha1_2 - w2
    b1_2 = beta1_2 + w2

    P2_2 = np.arcsin((v2 / v1) * np.sin(a1_2))
    Q2_2 = np.arcsin((v2 / v1) * np.sin(b1_2))

    alpha2_2 = P2_2 + w2
    beta2_2 = Q2_2 - w2

    h2 = ((v2 / (np.cos(alpha2_2) + np.cos(beta2_2)))
          * (t3 - (h1 * (np.cos(alpha1_2) + np.cos(beta1_2)) / v1)))
    v3 = v2 / np.sin((alpha2_2 + beta2_2) / 2)
    w3 = (alpha2_2 - beta2_2) / 2
    return h2, v3, w3


def _layer3(v1, va4, vb4, t4, h1, v2, w2, h2, v3, w3):
    """
    :param v1: velocity of layer 1 (m/s)
    :param va4, vb4: apparent velocities of layer 4 in the a and b directions (m/s)
    :param t4: intercept time of the refraction from interface 4 (s)
    :param h1, v2, w2: results of layer 1
    :param h2, v3, w3: results of layer 2
    :return: h3 (m) and v4 (m/s)
    """
    alpha1_3 = np.arcsin(v1 / va4)
    beta1_3 = np.arcsin(v1 / vb4)

    a1_3 = alpha1_3 - w2
    b1_3 = beta1_3 + w2

    P2_3 = np.arcsin((v2 / v1) * np.sin(a1_3))
    Q2_3 = np.arcsin((v2 / v1) * np.sin(b1_3))

    alpha2_3 = P2_3 + w2
    beta2_3 = Q2_3 - w2

    a2_3 = alpha2_3 - w3
    b2_3 = beta2_3 + w3

    P3_3 = np.arcsin((v3 / v2) * np.sin(a2_3))
    Q3_3 = np.arcsin((v3 / v2) * np.sin(b2_3))

    alpha3_3 = P3_3 + w3
    beta3_3 = Q3_3 - w3

    h3 = ((v3 / (np.cos(alpha3_3) + np.cos(beta3_3)))
          * (t4 - ((h1 * (np.cos(alpha1_3) + np.cos(beta1_3)) / v1)
                   + (h2 * (np.cos(alpha2_3) + np.cos(beta2_3)) / v2))))
    v4 = v3 / np.sin((alpha3_3 + beta3_3) / 2)
    return h3, v4
