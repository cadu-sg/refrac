import math
import struct

import pytest

from refrac.storage import DrawPointsBin, Line, PicksTxt, Project
from refrac.structs import UNDEFINED_POINT, Shot


def test_create_reads_shots_and_stations(line):
    assert line.shot_amount == 25
    assert [station.num for station in line.stations] == list(range(101, 341))
    first = line.stations[0]
    assert (first.x, first.y, first.elev) == (500000.0, 7500000.0, 120.0)
    metadata = line.shots_metadata
    assert [m.sou_stat for m in metadata[:3]] == [101, 111, 121]
    assert metadata[-1].sou_stat == 340
    assert [m.pick_amount for m in metadata[:3]] == [73, 83, 93]


def test_load_shot(line):
    shot = line.load_shot(0)
    assert (shot.seq_num, shot.sou_stat, shot.sou_x, shot.sou_elev) == (1, 101, 500000.0, 120.0)
    assert len(shot.picks) == 73
    pick = shot.picks[1]
    assert (pick.rec_stat, pick.offset, pick.cdp) == (102, 5.0, 102)
    assert pick.travel_time == pytest.approx(8.28, abs=1e-5)


def test_load_shot_out_of_range(line):
    with pytest.raises(IndexError):
        line.load_shot(25)
    with pytest.raises(IndexError):
        line.load_shot(-1)


def test_picks_bin_is_big_endian_with_shot_positions(line):
    data = (line.home_dir / "picks.bin").read_bytes()
    (shot_amount,) = struct.unpack_from(">i", data)
    (first_position,) = struct.unpack_from(">q", data, 4)
    assert shot_amount == 25
    assert first_position == 4 + 8 * 25
    # pickAmount, seqNum, souStat of the first shot
    assert struct.unpack_from(">iii", data, first_position) == (73, 1, 101)


def test_save_shot_with_erased_picks(line):
    shot = line.load_shot(5)
    neighbour = line.load_shot(6)
    line.save_shot(Shot(shot.seq_num, shot.sou_stat, shot.sou_x, shot.sou_y, shot.sou_elev,
                        shot.picks[10:]), 5)

    reopened = Line.open(line.home_dir)
    assert reopened.load_shot(5).picks == shot.picks[10:]
    assert reopened.load_shot(6) == neighbour


def test_draw_points_start_undefined_and_round_trip(line):
    assert line.load_draw_points(4) == [UNDEFINED_POINT] * 14
    points = [(i - 7.5, i * 2.25) for i in range(14)]
    line.save_draw_points(points, 4)
    reopened = Line.open(line.home_dir)
    assert reopened.load_draw_points(4) == points
    assert reopened.load_draw_points(3) == [UNDEFINED_POINT] * 14


def test_draw_points_bin_rejects_invalid_size(line):
    path = line.home_dir / "draw_points.bin"
    path.write_bytes(path.read_bytes()[:-1])
    with pytest.raises(ValueError, match="invalid file"):
        DrawPointsBin.open(path)


def test_interpretations_round_trip(line):
    rows = line.load_all_interpretations()
    assert len(rows) == 25
    assert rows[2] == [2, 121] + [0.0] * 19

    row = [3, 121, 8.5, math.nan, math.inf] + [float(i) for i in range(16)]
    line.save_interpretation(row, 2)

    loaded = Line.open(line.home_dir).load_all_interpretations()
    assert loaded[2][:3] == [3, 121, 8.5]
    assert math.isnan(loaded[2][3])
    assert loaded[2][4:] == row[4:]
    assert loaded[3] == [3, 131] + [0.0] * 19
    # Java's CSV conventions: CRLF and Java spellings of non-finite values
    text = (line.home_dir / "interpretations.csv").read_bytes()
    assert text.startswith(b"sequentialNumber,sourceStation,z1,")
    assert b"\r\n" in text and b",NaN," in text and b",Infinity," in text


def test_missing_interpretations_file_reads_as_zeros(line):
    (line.home_dir / "interpretations.csv").unlink()
    assert Line.open(line.home_dir).load_all_interpretations() == [[0.0] * 21] * 25


def test_invalid_picks_files(tmp_path):
    with pytest.raises(ValueError, match="no such file"):
        PicksTxt.open(tmp_path / "missing.dat")

    short_row = tmp_path / "short.dat"
    short_row.write_text("header\n1 101 101 0.0 1 2 3\n")
    with pytest.raises(ValueError, match="invalid line 2"):
        PicksTxt.open(short_row)

    no_source_station = tmp_path / "no_source.dat"
    no_source_station.write_text("header\n1 100 101 0.0 0 0 0 0 10.0 0.0 101\n")
    with pytest.raises(ValueError, match="source station 100"):
        PicksTxt.open(no_source_station)


def test_picks_file_tolerates_blank_lines(tmp_path, synthetic_picks):
    picks = tmp_path / "picks.dat"
    picks.write_text(synthetic_picks.read_text() + "\n\n")
    assert PicksTxt.open(picks).shot_amount == 25


def _reformat_picks(text, join, pad=None):
    """Rewrites every row of a whitespace-delimited picks file with another delimiter."""
    rows = [row.split() for row in text.splitlines()]
    return "\n".join(join(row if pad is None else [f"{f:>{pad}}" for f in row]) for row in rows)


@pytest.mark.parametrize("reformat", [
    pytest.param(lambda text: text, id="whitespace"),
    pytest.param(lambda text: _reformat_picks(text, "\t".join), id="tabs"),
    pytest.param(lambda text: _reformat_picks(text, " ".join, pad=12), id="aligned columns"),
    pytest.param(lambda text: _reformat_picks(text, "  ".join).replace("\n", "   \n"),
                 id="trailing spaces"),
    pytest.param(lambda text: _reformat_picks(text, ";".join), id="semicolon"),
    pytest.param(lambda text: _reformat_picks(text, "; ".join), id="semicolon and space"),
    pytest.param(lambda text: _reformat_picks(text, ";".join, pad=12), id="aligned semicolon"),
    pytest.param(lambda text: _reformat_picks(text, lambda row: ";".join(row) + ";"),
                 id="trailing semicolon"),
    pytest.param(lambda text: _reformat_picks(text, ";".join).replace("\n", "\r\n"), id="crlf"),
])
def test_picks_file_delimiter_is_detected(tmp_path, synthetic_picks, reformat):
    picks = tmp_path / "picks.dat"
    picks.write_text(reformat(synthetic_picks.read_text()) + "\n", newline="")
    expected = PicksTxt.open(synthetic_picks)
    assert PicksTxt.open(picks).shots == expected.shots
    assert PicksTxt.open(picks).stations == expected.stations


def test_picks_file_header_may_use_another_delimiter(tmp_path, synthetic_picks):
    picks = tmp_path / "picks.dat"
    lines = synthetic_picks.read_text().splitlines()
    lines[0] = "FFID;SOU_SLOC;SRF_SLOC;FB_PICK"
    picks.write_text("\n".join(lines) + "\n")
    assert PicksTxt.open(picks).shot_amount == 25


def test_picks_file_with_another_delimiter_is_rejected(tmp_path, synthetic_picks):
    picks = tmp_path / "picks.dat"
    picks.write_text(_reformat_picks(synthetic_picks.read_text(), ",".join) + "\n")
    with pytest.raises(ValueError, match="detect the delimiter"):
        PicksTxt.open(picks)


def test_picks_file_reports_the_line_of_a_semicolon_error(tmp_path, synthetic_picks):
    picks = tmp_path / "picks.dat"
    lines = _reformat_picks(synthetic_picks.read_text(), ";".join).splitlines()
    lines[20] = lines[20].replace(";", "", 1)  # merges two columns, past the rows used to sniff
    picks.write_text("\n".join(lines) + "\n")
    with pytest.raises(ValueError, match="invalid line 21"):
        PicksTxt.open(picks)


def test_project_creates_and_opens_lines(tmp_path, synthetic_picks):
    project = Project.create(tmp_path / "project")
    line = project.create_line("L1", synthetic_picks)
    assert line.home_dir == tmp_path / "project" / "L1"
    assert sorted(path.name for path in line.home_dir.iterdir()) == [
        "draw_points.bin", "interpretations.csv", "picks.bin", "picks_origin.dat"]
    assert project.open_line(line.home_dir).shot_amount == 25
