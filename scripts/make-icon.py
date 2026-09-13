#!/usr/bin/env python3
"""
Draws the launcher icon, so that 900 characters of path data are never edited by hand.

    scripts/make-icon.py            # rewrite the drawable
    scripts/make-icon.py --check    # fail if the drawable is not what these numbers say

The mark is a single-storey `a` built from the two shapes the launcher itself draws: a ring at the
weight of a home row, and a row stood on end for the stem. Its counter holds the nucleus the name
asks for. Nothing here is taken from a font — the app's text is set in IBM Plex Sans, whose `a` is
double-storey, so the mark and the words under it are deliberately different letters.

Every number is in the 108-unit space of the adaptive canvas. A launcher shows the middle 72 and
throws the rest away, and cuts that to a shape of its own choosing; only a 66-unit circle is
promised to survive, which is what SAFE_RADIUS checks against.
"""
import argparse
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
ICON = REPO / "app/src/main/res/drawable/ic_launcher_foreground.xml"

CENTRE = 54
BOWL_RADIUS = 18      # Outer edge of the ring; also half the ink, since the stem sits inside it.
STROKE = 7            # The home row's own thickness, so the mark is drawn in the app's weight.
NUCLEUS = 4           # Small enough that the counter stays a counter at 32px.
FILL = "#FFF2F2F2"
SAFE_RADIUS = 33


def circle(cx: float, cy: float, r: float) -> str:
    return f"M{cx - r},{cy} a{r},{r} 0 1 0 {r * 2},0 a{r},{r} 0 1 0 {-r * 2},0 z"


def bar(cx: float, top: float, bottom: float, thickness: float) -> str:
    """A home row stood on end: a stadium, capped with half-circles like every row on the list."""
    r = thickness / 2
    return f"M{cx - r},{top + r} a{r},{r} 0 0 1 {thickness},0 v{bottom - top - thickness} a{r},{r} 0 0 1 {-thickness},0 z"


def paths() -> list[tuple[str, str]]:
    bowl = circle(CENTRE, CENTRE, BOWL_RADIUS) + " " + circle(CENTRE, CENTRE, BOWL_RADIUS - STROKE)
    stem = bar(CENTRE + BOWL_RADIUS - STROKE / 2, CENTRE - BOWL_RADIUS, CENTRE + BOWL_RADIUS, STROKE)
    nucleus = circle(CENTRE, CENTRE, NUCLEUS)
    return [("evenOdd", bowl), ("nonZero", stem), ("nonZero", nucleus)]


def drawable() -> str:
    corner = (BOWL_RADIUS**2 + BOWL_RADIUS**2) ** 0.5
    if corner > SAFE_RADIUS:
        sys.exit(f"ink corners reach {corner:.1f} of the {SAFE_RADIUS} safe radius — shrink BOWL_RADIUS")
    drawn = "\n".join(
        f'    <path\n        android:fillColor="{FILL}"\n'
        + (f'        android:fillType="{rule}"\n' if rule != "nonZero" else "")
        + f'        android:pathData="{d}" />'
        for rule, d in paths()
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    A single-storey `a`, constructed rather than typed: the bowl is an orbit drawn at the thickness
    of a home row ({STROKE} units), the stem is a row stood on end, and the counter holds a nucleus. The
    app's own text is IBM Plex Sans, whose `a` is double-storey — the mark is not that letter, and
    is not any font's.

    Ink is {BOWL_RADIUS * 2} x {BOWL_RADIUS * 2} units centred on the 108 canvas; its corners reach {corner:.1f} of the {SAFE_RADIUS} a mask
    promises to keep. Regenerate with scripts/make-icon.py, whose check mode fails on any drift.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{drawn}
</vector>
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the drawable is out of date")
    args = parser.parse_args()

    wanted = drawable()
    if args.check:
        if ICON.read_text() != wanted:
            sys.exit(f"{ICON.relative_to(REPO)} is out of date; run scripts/make-icon.py")
        print(f"{ICON.relative_to(REPO)} matches the geometry.")
    else:
        ICON.write_text(wanted)
        print(f"wrote {ICON.relative_to(REPO)}")
