import math

import pytest

from refrac.interpretation import average_velocity, compute_interpretation, velocity

# Flat layers over a half-space
V = [600.0, 1800.0, 3000.0, 4500.0]  # m/s
H = [8.0, 18.0, 40.0]  # m


def flat_model_lines() -> tuple[dict[str, float], dict[str, float]]:
    """Slopes (ms/m) and intercepts (ms) of the travel-time lines of the flat model."""
    slopes = {"directL": -1000 / V[0], "directR": 1000 / V[0]}
    intercepts = {}
    for n in (1, 2, 3):
        intercept = 1000 * sum(2 * H[i] * math.cos(math.asin(V[i] / V[n])) / V[i]
                               for i in range(n))
        slopes[f"head{n}L"], slopes[f"head{n}R"] = -1000 / V[n], 1000 / V[n]
        intercepts[f"head{n}L"] = intercepts[f"head{n}R"] = intercept
    return slopes, intercepts


def test_flat_model_recovers_layers():
    thicknesses, velocities = compute_interpretation(*flat_model_lines())
    assert thicknesses == pytest.approx(H)
    assert velocities == pytest.approx(V)


def test_missing_side_falls_back_to_the_other():
    slopes, intercepts = flat_model_lines()
    for name in ("directR", "head1R", "head2R", "head3R"):
        slopes[name] = 0.0
    for name in ("head1R", "head2R", "head3R"):
        intercepts[name] = 0.0
    thicknesses, velocities = compute_interpretation(slopes, intercepts)
    assert thicknesses == pytest.approx(H)
    assert velocities == pytest.approx(V)


def test_layers_without_lines_stay_undefined():
    slopes, intercepts = flat_model_lines()
    slopes["head3L"] = slopes["head3R"] = 0.0
    thicknesses, velocities = compute_interpretation(slopes, intercepts)
    assert thicknesses == pytest.approx(H[:2] + [0.0])
    assert velocities == pytest.approx(V[:3] + [0.0])

    # Layer 3 needs layer 2
    slopes, intercepts = flat_model_lines()
    intercepts["head2L"] = intercepts["head2R"] = 0.0
    thicknesses, velocities = compute_interpretation(slopes, intercepts)
    assert thicknesses == pytest.approx(H[:1] + [0.0, 0.0])
    assert velocities == pytest.approx(V[:2] + [0.0, 0.0])


def test_nothing_interpretable_without_direct_wave():
    slopes, intercepts = flat_model_lines()
    slopes["directL"] = slopes["directR"] = 0.0
    assert compute_interpretation(slopes, intercepts) == ([0.0] * 3, [0.0] * 4)


def test_negative_intercept_is_undefined():
    slopes, intercepts = flat_model_lines()
    intercepts["head1R"] = -1.0
    thicknesses, _ = compute_interpretation(slopes, intercepts)
    assert thicknesses[0] == 0.0


def test_incoherent_interpretation_gives_nan():
    slopes, intercepts = flat_model_lines()
    # A refractor slower than the direct wave
    slopes["head1L"], slopes["head1R"] = -1000 / 400, 1000 / 400
    thicknesses, _ = compute_interpretation(slopes, intercepts)
    assert math.isnan(thicknesses[0])


def test_velocity_helpers():
    assert velocity(-2.0) == 500.0
    assert velocity(0.0) == 0.0
    assert average_velocity(1.0, -0.5) == pytest.approx(2000 / 1.5)
    assert average_velocity(1.0, 0.0) == 0.0
