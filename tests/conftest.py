import os
from pathlib import Path

import pytest

# Charts are created without a display
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from refrac.app import configure_plots  # noqa: E402
from refrac.storage import Line  # noqa: E402

configure_plots()


@pytest.fixture
def synthetic_picks() -> Path:
    """25 shots over 240 stations, from testdata/make_synthetic_picks.py."""
    return Path(__file__).parents[1] / "testdata" / "synthetic_picks.dat"


@pytest.fixture
def sou_elev_picks(tmp_path, synthetic_picks) -> Path:
    """The synthetic picks with a leading SOU_ELEV column of 100 + FFID, an elevation no station
    has."""
    header, *rows = synthetic_picks.read_text().splitlines()
    picks = tmp_path / "sou_elev_picks.dat"
    picks.write_text("\n".join([f"SOU_ELEV {header}"]
                               + [f"{100 + int(row.split()[0])} {row}" for row in rows]) + "\n")
    return picks


@pytest.fixture
def channel_picks(tmp_path) -> Path:
    """3 shots over receivers every 10 m from x = 0 to 80, at elevation 100 + x / 10, whose
    SRF_SLOC numbers the 5 channels of each shot rather than the receiver positions. Without a
    SOU_ELEV column, and the sources on a receiver (x = 20), between two (x = 45) and past the
    last one (x = 95)."""
    rows = ["FFID SOU_SLOC SRF_SLOC FB_PICK SOU_X SOU_Y REC_X REC_Y REC_ELEV OFFSET CDP"]
    for ffid, (sou_x, first_x) in enumerate([(20, 0), (45, 20), (95, 40)], start=1):
        for channel in range(1, 6):
            rec_x = first_x + 10 * (channel - 1)
            offset = float(rec_x - sou_x)
            rows.append(f"{ffid} {ffid} {channel} {abs(offset) / 2} {sou_x} 0 {rec_x} 0 "
                        f"{100 + rec_x / 10} {offset} {channel}")
    picks = tmp_path / "channel_picks.dat"
    picks.write_text("\n".join(rows) + "\n")
    return picks


@pytest.fixture
def flipped_offset_picks(tmp_path, channel_picks) -> Path:
    """The channel picks with OFFSET measured as SOU_X - REC_X."""
    header, *rows = channel_picks.read_text().splitlines()
    flipped = []
    for row in rows:
        fields = row.split()
        fields[9] = str(-float(fields[9]))
        flipped.append(" ".join(fields))
    picks = tmp_path / "flipped_offset_picks.dat"
    picks.write_text("\n".join([header, *flipped]) + "\n")
    return picks


@pytest.fixture
def line(tmp_path, synthetic_picks) -> Line:
    return Line.create(tmp_path / "line", synthetic_picks)
