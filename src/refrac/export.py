"""Export of the interpretation of a line to a CSV file, one row per shot worked on.

A shot has been worked on if any of its lines was saved. Its velocities and interface elevations
are computed from its saved lines: V1 is the velocity of the direct wave and V2 to V4 those of
the refracted arrivals, each from the average slowness of both sides or from the one side drawn.
Z1 to Z3 are the elevations of the interfaces, the layer thicknesses below the source elevation.
Values that cannot be computed are left empty.
"""

import csv
import math
from collections.abc import Sequence
from pathlib import Path

from refrac.geometry import line_coefficients
from refrac.interpretation import combined_velocity, compute_interpretation
from refrac.pick_chart import HEAD_NAMES, WAVES, unpack_draw_points
from refrac.storage import Line
from refrac.structs import UNDEFINED, Point, ShotMetadata

EXPORT_COLUMNS = ("FFID", "SOU_SLOC", "SOU_X", "SOU_Y", "SOU_ELEV",
                  "V1", "V2", "V3", "V4", "Z1", "Z2", "Z3")

# A row of EXPORT_COLUMNS values, None where a value cannot be computed
ExportRow = list[float | None]


def export_row(metadata: ShotMetadata, draw_points: list[Point]) -> ExportRow | None:
    """The export row of a shot from its saved draw points, or None if it has no lines."""
    lines = unpack_draw_points(draw_points)
    if not any(lines.values()):
        return None
    coefficients = {name: (UNDEFINED, UNDEFINED) if line is None else line_coefficients(*line)
                    for name, line in lines.items()}
    slopes = {name: slope for name, (slope, _) in coefficients.items()}
    intercepts = {name: coefficients[name][1] for name in HEAD_NAMES}
    thicknesses, _ = compute_interpretation(slopes, intercepts)

    velocities = [combined_velocity(slopes[left], slopes[right]) for _, left, right in WAVES]
    elevations = []
    elevation = metadata.sou_elev
    for thickness in thicknesses:
        # An interface is known only if all the layers above it are
        elevation = None if elevation is None or not _is_known(thickness) else elevation - thickness
        elevations.append(elevation)

    return [metadata.seq_num, metadata.sou_stat, metadata.sou_x, metadata.sou_y, metadata.sou_elev,
            *(v if _is_known(v) else None for v in velocities), *elevations]


def export_rows(line: Line) -> list[ExportRow]:
    """The export rows of the shots of the line that have been worked on, in shot order."""
    rows = (export_row(metadata, line.load_draw_points(shot_index))
            for shot_index, metadata in enumerate(line.shots_metadata))
    return [row for row in rows if row is not None]


def write_export_csv(path: Path, rows: Sequence[ExportRow]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(EXPORT_COLUMNS)
        for row in rows:
            writer.writerow([_format(value) for value in row])


def _is_known(value: float | None) -> bool:
    return value is not None and value != UNDEFINED and math.isfinite(value)


def _format(value: float | None) -> str:
    if value is None:
        return ""
    if isinstance(value, int):
        return str(value)
    return f"{value:.2f}"
