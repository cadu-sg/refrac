import csv

import pytest

from refrac.export import EXPORT_COLUMNS, export_row, export_rows, write_export_csv
from refrac.pick_chart import DIRECT_NAMES, DRAWER_NAMES
from refrac.structs import UNDEFINED_POINT
from test_interpretation import H, V, flat_model_lines

# Offsets of the end points of the lines on the right of the shot, mirrored on the left
OFFSETS = {"direct": (0, 10), "head1": (30, 60), "head2": (90, 140), "head3": (200, 300)}


def flat_model_points(omit=()) -> list:
    """Draw points of the flat model lines, without the lines named in omit."""
    slopes, intercepts = flat_model_lines()
    points = []
    for name in DRAWER_NAMES:
        sign = -1 if name.endswith("L") else 1
        line = [(sign * x, slopes[name] * sign * x + intercepts.get(name, 0.0))
                for x in OFFSETS[name[:-1]]]
        if name in omit:
            line = [UNDEFINED_POINT] * 2
        points += line[1:] if name in DIRECT_NAMES else line
    return points


def test_flat_model_row(line):
    metadata = line.shots_metadata[0]
    row = export_row(metadata, flat_model_points())
    assert row[:5] == [1, 101, 500000.0, 7500000.0, 120.0]
    assert row[5:9] == pytest.approx(V)
    assert row[9:] == pytest.approx([120 - H[0], 120 - H[0] - H[1], 120 - sum(H)])


def test_one_side_uses_its_lone_velocity(line):
    metadata = line.shots_metadata[0]
    row = export_row(metadata, flat_model_points(omit=("directR", "head1L", "head2R", "head3R")))
    assert row[5:9] == pytest.approx(V)
    assert row[9:] == pytest.approx([120 - H[0], 120 - H[0] - H[1], 120 - sum(H)])


def test_velocities_average_the_slowness_of_both_sides(line):
    points = flat_model_points()
    points[6] = (-10, 10 * 1000 / 400)  # directL at 400 m/s, directR at 600 m/s
    row = export_row(line.shots_metadata[0], points)
    assert row[5] == pytest.approx(2000 / (1000 / 400 + 1000 / 600))


def test_layers_not_drawn_are_missing(line):
    metadata = line.shots_metadata[0]
    row = export_row(metadata, flat_model_points(omit=("head2L", "head2R", "head3L", "head3R")))
    assert row[5:7] == pytest.approx(V[:2])
    assert row[7:9] == [None, None]
    assert row[9] == pytest.approx(120 - H[0])
    assert row[10:] == [None, None]

    # Without the direct wave, no thickness can be computed
    row = export_row(metadata, flat_model_points(omit=DIRECT_NAMES))
    assert row[5] is None and row[6:9] == pytest.approx(V[1:])
    assert row[9:] == [None, None, None]


def test_incoherent_interpretation_has_no_elevations(line):
    points = flat_model_points()
    # Refracted 1 slower than the direct wave
    points[4:6] = [(-60, 155), (-30, 80)]
    points[8:10] = [(30, 80), (60, 155)]
    row = export_row(line.shots_metadata[0], points)
    assert row[6] == pytest.approx(400)
    assert row[9:] == [None, None, None]


def test_shots_not_worked_on_are_skipped(line):
    assert export_row(line.shots_metadata[0], [UNDEFINED_POINT] * 14) is None
    assert export_rows(line) == []

    line.save_draw_points(flat_model_points(), 3)
    line.save_draw_points(flat_model_points(omit=DRAWER_NAMES[1:]), 7)
    rows = export_rows(line)
    assert [row[:2] for row in rows] == [[4, 131], [8, 171]]
    # Only head3L drawn
    assert rows[1][5:8] == [None, None, None] and rows[1][8] == pytest.approx(V[3])
    assert rows[1][9:] == [None, None, None]


def test_write_export_csv(tmp_path):
    path = tmp_path / "export.csv"
    write_export_csv(path, [[4, 131, 500150.0, 7500000.25, 120.0,
                             600.0, 1800.123, None, None, 112.0, None, None]])
    with open(path, newline="", encoding="utf-8") as file:
        header, *rows = list(csv.reader(file))
    assert tuple(header) == EXPORT_COLUMNS
    assert rows == [["4", "131", "500150.00", "7500000.25", "120.00",
                     "600.00", "1800.12", "", "", "112.00", "", ""]]
