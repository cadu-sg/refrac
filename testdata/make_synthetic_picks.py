#!/usr/bin/env python3
"""
Generates a synthetic first break picks file in the format read by PicksTxt.

Model: 3 layers over a half-space (V0..V3), gentle topography and laterally
varying interfaces. First breaks are the minimum of the direct wave and the
three head waves, computed with the delay-time approximation (delay under the
source + delay under the receiver + |offset| / Vrefractor), plus Gaussian noise.

Units: coordinates/elevations/offsets in m, travel times in ms.

Usage: python3 make_synthetic_picks.py [output_path]
"""
import math
import random
import sys

SEED = 42
NOISE_MS = 0.4  # standard deviation of the pick noise

# Layer velocities (m/s): weathering, sub-weathering, two consolidated layers
V = [600.0, 1800.0, 3000.0, 4500.0]

# Geometry
FIRST_STATION = 101
STATION_AMOUNT = 240
STATION_SPACING = 5.0     # m
X0, Y0 = 500000.0, 7500000.0  # UTM-like origin, line runs West -> East
SHOT_EVERY = 10           # stations between shots
HALF_SPREAD = 72          # receivers on each side of the source (rolling split spread)


def station_x(stat):
    return X0 + (stat - FIRST_STATION) * STATION_SPACING


def surface_elev(x):
    d = x - X0
    return 120.0 + 4.0 * math.sin(2 * math.pi * d / 900.0) + 0.004 * d


def thicknesses(x):
    """Vertical thicknesses (m) of layers 0, 1 and 2 below the point at x."""
    d = x - X0
    h0 = 8.0 + 2.5 * math.sin(2 * math.pi * d / 400.0)   # weathering layer
    h1 = 18.0 + 0.01 * d                                  # thickens to the East
    h2 = 40.0 - 0.008 * d + 5.0 * math.cos(2 * math.pi * d / 1100.0)
    return h0, h1, h2


def delay_time(x, refractor):
    """One-way delay time (s) at x for the head wave travelling in layer `refractor` (1..3)."""
    h = thicknesses(x)
    vr = V[refractor]
    return sum(h[i] * math.sqrt(1.0 - (V[i] / vr) ** 2) / V[i] for i in range(refractor))


def first_break(sx, sz, rx, rz):
    """First arrival travel time (ms) between source and receiver."""
    if sx == rx:
        return 0.0
    dist = abs(rx - sx)
    times = [math.hypot(dist, rz - sz) / V[0]]  # direct wave
    for n in (1, 2, 3):
        times.append(delay_time(sx, n) + delay_time(rx, n) + dist / V[n])
    return 1000.0 * min(times)


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "synthetic_picks.dat"
    rng = random.Random(SEED)

    last_station = FIRST_STATION + STATION_AMOUNT - 1
    shot_stations = list(range(FIRST_STATION, last_station + 1, SHOT_EVERY))
    if shot_stations[-1] != last_station:
        shot_stations.append(last_station)

    lines = ["FFID SOU_SLOC SRF_SLOC FB_PICK SOU_X SOU_Y REC_X REC_Y REC_ELEV OFFSET CDP"]
    for ffid, sou in enumerate(shot_stations, start=1):
        sx, sy = station_x(sou), Y0
        sz = surface_elev(sx)
        first_rec = max(FIRST_STATION, sou - HALF_SPREAD)
        last_rec = min(last_station, sou + HALF_SPREAD)
        for rec in range(first_rec, last_rec + 1):
            rx, ry = station_x(rec), Y0
            rz = surface_elev(rx)
            t = first_break(sx, sz, rx, rz)
            if rec != sou:
                t = max(0.0, t + rng.gauss(0.0, NOISE_MS))
            offset = rx - sx  # signed: negative to the West of the source
            cdp = int(math.floor((sou + rec) / 2.0 + 0.5))
            lines.append(f"{ffid} {sou} {rec} {t:.2f} {sx:.2f} {sy:.2f} "
                         f"{rx:.2f} {ry:.2f} {rz:.2f} {offset:.2f} {cdp}")

    # Exactly one trailing newline: PicksTxt breaks on a trailing blank line
    with open(out_path, "w", encoding="ascii", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    print(f"Wrote {out_path}: {len(shot_stations)} shots, {len(lines) - 1} picks, "
          f"{STATION_AMOUNT} stations")


if __name__ == "__main__":
    main()
