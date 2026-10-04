"""Directory-based storage: a project is a directory of lines, a line a directory of four files.

| File                  | Class              | Role                                              |
|-----------------------|--------------------|---------------------------------------------------|
| `picks_origin.dat`    | PicksTxt           | Verbatim copy of the user's picks file            |
| `picks.bin`           | PicksBin           | Random-access mirror of the picks, edited in place |
| `draw_points.bin`     | DrawPointsBin      | The 14 line drawer points of each shot            |
| `interpretations.csv` | InterpretationsCSV | 21 interpretation values per shot                 |

Record N of `picks.bin`, block N of `draw_points.bin` and row N of `interpretations.csv` all
describe the shot with index N. The binary files are big-endian and byte-compatible with the
original Java version of refrac, so lines created by either version open in the other.
"""

import csv
import math
import shutil
import struct
from collections.abc import Sequence
from itertools import groupby, islice
from pathlib import Path

import numpy as np

from refrac.structs import Pick, Point, Shot, ShotMetadata, Station

PICKS_TXT_NAME = "picks_origin.dat"
PICKS_BIN_NAME = "picks.bin"
DRAW_POINTS_BIN_NAME = "draw_points.bin"
INTERPRETATIONS_CSV_NAME = "interpretations.csv"

_INT = struct.Struct(">i")
# pickAmount, seqNum, souStat, souX, souY, souElev
_SHOT_HEADER = struct.Struct(">iiifff")
# recStat, travelTime, recX, recY, recElev, offset, cdp, waveType
_PICK = struct.Struct(">ifffffif")


def _float32(text: str) -> float:
    # The Java version stores these columns as float, keep the same precision
    return float(np.float32(text))


def _detect_delimiter(lines: Sequence[str]) -> str:
    """Whitespace (a single space, as the lines are normalised) or semicolon."""
    sample = list(islice(filter(None, lines), 10))
    if not sample:
        return " "
    try:
        return csv.Sniffer().sniff("\n".join(sample), delimiters=" ;").delimiter
    except csv.Error as e:
        raise ValueError("Cannot read picks file: unable to detect the delimiter, "
                         "expected whitespace or semicolon") from e


class PicksTxt:
    """First-break picks text file.

    Columns, delimited by whitespace or by semicolons (detected with `csv.Sniffer`), US-ASCII, one
    header line that is always skipped:

    1. FFID: shot sequential number
    2. SOU_SLOC: source station number
    3. SRF_SLOC: receiver station number
    4. FB_PICK: travel time (ms)
    5. SOU_X, 6. SOU_Y: source coordinates
    7. REC_X, 8. REC_Y: receiver coordinates
    9. REC_ELEV: receiver station elevation
    10. OFFSET: signed source-receiver offset (m)
    11. CDP: station nearest to the source-receiver midpoint

    A shot is a run of consecutive rows with the same FFID.
    """

    def __init__(self, path: Path, shots: list[Shot], stations: list[Station]):
        self.path = path
        self.shots = shots
        self.stations = stations
        self.shots_metadata = [
            ShotMetadata(shot.seq_num, shot.sou_stat, shot.sou_x, shot.sou_y, len(shot.picks))
            for shot in shots]

    @property
    def shot_amount(self) -> int:
        return len(self.shots)

    @classmethod
    def open(cls, path: Path) -> PicksTxt:
        rows = []
        try:
            with open(path, newline="", encoding="ascii") as file:
                # Runs of spaces and tabs count as a single space, so aligned columns and
                # "; " separators come out uniform
                lines = [" ".join(line.split()) for line in file.read().splitlines()[1:]]
            reader = csv.reader(lines, delimiter=_detect_delimiter(lines), quoting=csv.QUOTE_NONE)
            for record in reader:
                fields = [field.strip() for field in record]
                while fields and not fields[-1]:  # a trailing semicolon is not another column
                    fields.pop()
                if not fields:
                    continue
                line_number = reader.line_num + 1  # the header line was skipped
                try:
                    if len(fields) != 11:
                        raise ValueError(f"expected 11 columns, found {len(fields)}")
                    rows.append((
                        int(fields[0]), int(fields[1]), int(fields[2]), _float32(fields[3]),
                        _float32(fields[4]), _float32(fields[5]), _float32(fields[6]),
                        _float32(fields[7]), _float32(fields[8]), _float32(fields[9]),
                        int(fields[10])))
                except ValueError as e:
                    raise ValueError(
                        f"Cannot read picks file: invalid line {line_number}: {e}") from e
        except FileNotFoundError as e:
            raise ValueError(f"Cannot read picks file: no such file: {path}") from e
        except UnicodeDecodeError as e:
            raise ValueError(f"Cannot read picks file: not an ASCII file: {path}") from e
        except csv.Error as e:
            raise ValueError(f"Cannot read picks file: {e}") from e
        if not rows:
            raise ValueError("Cannot read picks file: no picks found")

        stations: dict[int, Station] = {}
        for _, _, rec_stat, _, _, _, rec_x, rec_y, rec_elev, _, _ in rows:
            stations.setdefault(rec_stat, Station(rec_stat, rec_x, rec_y, rec_elev))

        shots = []
        for _, shot_rows in groupby(rows, key=lambda row: row[0]):
            shot_rows = list(shot_rows)
            seq_num, sou_stat, _, _, sou_x, sou_y, *_ = shot_rows[0]
            if sou_stat not in stations:
                raise ValueError(f"Unable to determine elevation for source station {sou_stat}")
            picks = [Pick(rec_stat, travel_time, rec_x, rec_y, rec_elev, offset, cdp)
                     for _, _, rec_stat, travel_time, _, _, rec_x, rec_y, rec_elev, offset, cdp
                     in shot_rows]
            shots.append(Shot(seq_num, sou_stat, sou_x, sou_y, stations[sou_stat].elev, picks))

        return cls(path, shots, [stations[num] for num in sorted(stations)])


class PicksBin:
    """Picks binary file: header `shotAmount (int) + shotPositions (long[])`, then for each shot a
    24-byte header followed by 32 bytes per pick."""

    def __init__(self, path: Path, shot_positions: Sequence[int]):
        self.path = path
        self.shot_positions = list(shot_positions)

    @classmethod
    def create(cls, path: Path, shots: Sequence[Shot]) -> PicksBin:
        header_size = _INT.size + 8 * len(shots)
        shot_positions = []
        body = bytearray()
        for shot in shots:
            shot_positions.append(header_size + len(body))
            body += _shot_to_bytes(shot)
        with open(path, "wb") as file:
            file.write(_INT.pack(len(shots)))
            file.write(struct.pack(f">{len(shots)}q", *shot_positions))
            file.write(body)
        return cls(path, shot_positions)

    @classmethod
    def open(cls, path: Path) -> PicksBin:
        try:
            with open(path, "rb") as file:
                (shot_amount,) = _INT.unpack(file.read(_INT.size))
                shot_positions = struct.unpack(f">{shot_amount}q", file.read(8 * shot_amount))
        except struct.error as e:
            raise ValueError(f"Cannot read {path.name}: invalid file") from e
        return cls(path, shot_positions)

    def load_shot(self, shot_index: int) -> Shot:
        with open(self.path, "rb") as file:
            file.seek(self._position(shot_index))
            try:
                pick_amount, seq_num, sou_stat, sou_x, sou_y, sou_elev = _SHOT_HEADER.unpack(
                    file.read(_SHOT_HEADER.size))
                picks = [Pick(*fields)
                         for fields in _PICK.iter_unpack(file.read(pick_amount * _PICK.size))]
            except struct.error as e:
                raise ValueError(f"Cannot load shot {shot_index + 1}: invalid {self.path.name}") from e
        return Shot(seq_num, sou_stat, sou_x, sou_y, sou_elev, picks)

    def save_shot(self, shot: Shot, shot_index: int) -> None:
        """Overwrites the shot in place, so it must not have more picks than when created."""
        with open(self.path, "r+b") as file:
            file.seek(self._position(shot_index))
            file.write(_shot_to_bytes(shot))

    def _position(self, shot_index: int) -> int:
        if not 0 <= shot_index < len(self.shot_positions):
            raise IndexError(f"Shot index out of range: {shot_index}")
        return self.shot_positions[shot_index]


def _shot_to_bytes(shot: Shot) -> bytes:
    header = _SHOT_HEADER.pack(
        len(shot.picks), shot.seq_num, shot.sou_stat, shot.sou_x, shot.sou_y, shot.sou_elev)
    return header + b"".join(
        _PICK.pack(pick.rec_stat, pick.travel_time, pick.rec_x, pick.rec_y, pick.rec_elev,
                   pick.offset, pick.cdp, pick.wave_type)
        for pick in shot.picks)


class DrawPointsBin:
    """Line drawer points binary file: header `shotAmount (int)`, then a fixed block of
    POINTS_AMOUNT points (two doubles each) per shot, zero-filled when created."""

    # head3L.p1, head3L.p2, head2L.p1, head2L.p2, head1L.p1, head1L.p2, directL.p2,
    # directR.p2, head1R.p1, head1R.p2, head2R.p1, head2R.p2, head3R.p1, head3R.p2
    POINTS_AMOUNT = 14
    _POINTS = struct.Struct(f">{2 * POINTS_AMOUNT}d")

    def __init__(self, path: Path, shot_amount: int):
        self.path = path
        self.shot_amount = shot_amount

    @classmethod
    def create(cls, path: Path, shot_amount: int) -> DrawPointsBin:
        with open(path, "wb") as file:
            file.write(_INT.pack(shot_amount))
            file.write(bytes(cls._POINTS.size * shot_amount))
        return cls(path, shot_amount)

    @classmethod
    def open(cls, path: Path) -> DrawPointsBin:
        try:
            with open(path, "rb") as file:
                (shot_amount,) = _INT.unpack(file.read(_INT.size))
        except FileNotFoundError as e:
            raise ValueError(f"Cannot read {path.name}: no such file") from e
        except struct.error as e:
            raise ValueError(f"Cannot read {path.name}: invalid file") from e
        if path.stat().st_size != _INT.size + cls._POINTS.size * shot_amount:
            raise ValueError(f"Cannot open {path.name}: invalid file")
        return cls(path, shot_amount)

    def save(self, points: Sequence[Point], shot_index: int) -> None:
        if len(points) != self.POINTS_AMOUNT:
            raise ValueError("Cannot save points: illegal number of points")
        with open(self.path, "r+b") as file:
            file.seek(self._position(shot_index))
            file.write(self._POINTS.pack(*(value for point in points for value in point)))

    def load(self, shot_index: int) -> list[Point]:
        with open(self.path, "rb") as file:
            file.seek(self._position(shot_index))
            values = self._POINTS.unpack(file.read(self._POINTS.size))
        return list(zip(values[::2], values[1::2]))

    def _position(self, shot_index: int) -> int:
        if not 0 <= shot_index < self.shot_amount:
            raise IndexError(f"Shot index out of range: {shot_index}")
        return _INT.size + self._POINTS.size * shot_index


class InterpretationsCSV:
    """Interpretations CSV file, one row of COLUMNS values per shot, rewritten in full on every save."""

    HEADER = ("sequentialNumber", "sourceStation", "z1", "z2", "z3",
              "v0", "v1", "v2", "v3", "t3L", "t2L", "t1L", "t1R", "t2R", "t3R",
              "x3L", "x2L", "x1L", "x1R", "x2R", "x3R")
    COLUMNS = len(HEADER)

    def __init__(self, path: Path, shot_amount: int):
        self.path = path
        self.shot_amount = shot_amount

    @classmethod
    def create(cls, path: Path, shot_station_numbers: Sequence[int]) -> InterpretationsCSV:
        interpretations_csv = cls(path, len(shot_station_numbers))
        interpretations_csv._save_all(
            [[shot_index, station] + [0] * (cls.COLUMNS - 2)
             for shot_index, station in enumerate(shot_station_numbers)])
        return interpretations_csv

    def load_all(self) -> list[list[float]]:
        rows = [[0.0] * self.COLUMNS for _ in range(self.shot_amount)]
        if not self.path.exists():
            return rows
        with open(self.path, newline="", encoding="ascii") as file:
            records = list(csv.reader(file))[1:]  # skip the header
        try:
            for shot_index, record in enumerate(records[:self.shot_amount]):
                if len(record) < self.COLUMNS:
                    raise ValueError(f"row {shot_index + 2} has fewer than {self.COLUMNS} values")
                rows[shot_index] = [float(value) for value in record[:self.COLUMNS]]
        except ValueError as e:
            raise ValueError(f"Cannot load {self.path.name}: {e}") from e
        return rows

    def save(self, interpretation: Sequence[float], shot_index: int) -> None:
        rows = self.load_all()
        rows[shot_index] = list(interpretation)
        self._save_all(rows)

    def _save_all(self, rows: Sequence[Sequence[float]]) -> None:
        with open(self.path, "w", newline="", encoding="ascii") as file:
            writer = csv.writer(file)  # CRLF line endings, like the Java version
            writer.writerow(self.HEADER)
            for row in rows:
                writer.writerow([int(row[0]), int(row[1])] + [_format_double(v) for v in row[2:]])


def _format_double(value: float) -> str:
    # Spell non-finite values the way Java's Double.parseDouble reads them
    if math.isnan(value):
        return "NaN"
    if math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    return repr(float(value))


class Line:
    """A seismic line: a directory holding its picks, drawings and interpretations."""

    def __init__(self, home_dir: Path, picks_txt: PicksTxt, picks_bin: PicksBin,
                 draw_points_bin: DrawPointsBin, interpretations_csv: InterpretationsCSV):
        self.home_dir = home_dir
        self._picks_txt = picks_txt
        self._picks_bin = picks_bin
        self._draw_points_bin = draw_points_bin
        self._interpretations_csv = interpretations_csv

    @property
    def title(self) -> str:
        return self.home_dir.name

    @property
    def shot_amount(self) -> int:
        return self._picks_txt.shot_amount

    @property
    def stations(self) -> list[Station]:
        return self._picks_txt.stations

    @property
    def shots_metadata(self) -> list[ShotMetadata]:
        return self._picks_txt.shots_metadata

    @classmethod
    def create(cls, home_dir: Path, picks_file: Path) -> Line:
        try:
            home_dir.mkdir(parents=True, exist_ok=True)
        except OSError as e:
            raise OSError(f"Cannot create directory: {home_dir}") from e

        picks_txt_path = home_dir / PICKS_TXT_NAME
        if not (picks_txt_path.exists() and picks_txt_path.samefile(picks_file)):
            shutil.copyfile(picks_file, picks_txt_path)
        picks_txt = PicksTxt.open(picks_txt_path)

        return cls(
            home_dir,
            picks_txt,
            PicksBin.create(home_dir / PICKS_BIN_NAME, picks_txt.shots),
            DrawPointsBin.create(home_dir / DRAW_POINTS_BIN_NAME, picks_txt.shot_amount),
            InterpretationsCSV.create(home_dir / INTERPRETATIONS_CSV_NAME,
                                      [shot.sou_stat for shot in picks_txt.shots]))

    @classmethod
    def open(cls, home_dir: Path) -> Line:
        picks_txt = PicksTxt.open(home_dir / PICKS_TXT_NAME)
        return cls(
            home_dir,
            picks_txt,
            PicksBin.open(home_dir / PICKS_BIN_NAME),
            DrawPointsBin.open(home_dir / DRAW_POINTS_BIN_NAME),
            InterpretationsCSV(home_dir / INTERPRETATIONS_CSV_NAME, picks_txt.shot_amount))

    def load_shot(self, shot_index: int) -> Shot:
        return self._picks_bin.load_shot(shot_index)

    def save_shot(self, shot: Shot, shot_index: int) -> None:
        self._picks_bin.save_shot(shot, shot_index)

    def load_draw_points(self, shot_index: int) -> list[Point]:
        return self._draw_points_bin.load(shot_index)

    def save_draw_points(self, points: Sequence[Point], shot_index: int) -> None:
        self._draw_points_bin.save(points, shot_index)

    def load_all_interpretations(self) -> list[list[float]]:
        return self._interpretations_csv.load_all()

    def save_interpretation(self, interpretation: Sequence[float], shot_index: int) -> None:
        self._interpretations_csv.save(interpretation, shot_index)


class Project:
    """A project is just a directory whose subdirectories are lines."""

    def __init__(self, home_dir: Path):
        self.home_dir = home_dir

    @property
    def title(self) -> str:
        return self.home_dir.name

    @classmethod
    def create(cls, home_dir: Path) -> Project:
        try:
            home_dir.mkdir(parents=True, exist_ok=True)
        except OSError as e:
            raise OSError(f"Cannot create directory: {home_dir}") from e
        return cls(home_dir)

    def create_line(self, line_title: str, picks_file: Path) -> Line:
        return Line.create(self.home_dir / line_title, picks_file)

    def open_line(self, line_home_dir: Path) -> Line:
        return Line.open(line_home_dir)
