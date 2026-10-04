"""Plain records shared by storage, charts and the main window."""

from dataclasses import dataclass, field

# Zero is the sentinel for "not set": a slope, intercept or thickness of 0 and the
# point (0, 0) all mean absent
UNDEFINED = 0.0

type Point = tuple[float, float]

UNDEFINED_POINT: Point = (UNDEFINED, UNDEFINED)


def is_point_defined(point: Point) -> bool:
    return point != UNDEFINED_POINT


@dataclass(slots=True)
class Pick:
    rec_stat: int
    travel_time: float  # ms
    rec_x: float
    rec_y: float
    rec_elev: float
    offset: float  # m, negative to the left of the source
    cdp: int
    wave_type: float = 0.0


@dataclass(slots=True)
class Station:
    num: int
    x: float
    y: float
    elev: float


@dataclass(slots=True)
class ShotMetadata:
    seq_num: int
    sou_stat: int
    sou_x: float
    sou_y: float
    sou_elev: float
    pick_amount: int

    def station(self) -> Station:
        return Station(self.sou_stat, self.sou_x, self.sou_y, self.sou_elev)


@dataclass(slots=True)
class Shot:
    seq_num: int
    sou_stat: int
    sou_x: float
    sou_y: float
    sou_elev: float
    picks: list[Pick] = field(default_factory=list)

    def station(self) -> Station:
        return Station(self.sou_stat, self.sou_x, self.sou_y, self.sou_elev)
