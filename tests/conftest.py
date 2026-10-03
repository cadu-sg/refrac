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
def line(tmp_path, synthetic_picks) -> Line:
    return Line.create(tmp_path / "line", synthetic_picks)
