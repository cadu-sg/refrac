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
def line(tmp_path, synthetic_picks) -> Line:
    return Line.create(tmp_path / "line", synthetic_picks)
