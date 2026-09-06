# ADR 0003 — Home rows are as wide as their name

Date: 2026-09-06. Status: accepted. Narrows one detail of the home-screen design recorded in
`docs/design/2026-09-06-build-scaffold-and-home-skeleton.md` (§ HomeListView), which specified
rows `MATCH_PARENT` wide so that the whole row is the tap target.

## Context

Phase 5 draws a notification count beside an app's name. The count is a compound drawable on the
row's `TextView`, which is the arrangement that makes the name shorten to leave room rather than
slide under the badge, and that keeps the row a single clickable, focusable view for TalkBack.

A `TextView` positions a start or end compound drawable at its own padding edge. It does not
consult the text's gravity. With rows the full width of the screen and the shipped default of
centred text, the count is therefore drawn hard against the edge of the screen while the name sits
in the middle: on a 1080-pixel screen, about 300 pixels of nothing between the name and its badge.
A review of the phase found this; the acceptance harness had not, because it reads counts from the
row's content description and never looked at where the badge landed.

Three ways out were considered.

1. Keep full-width rows and draw the badge in `onDraw` at a position computed from the measured
   text. The text then does not reserve room for the badge, so a long name overlaps it.
2. Keep full-width rows and put the badge in the row's text as a `ReplacementSpan`. This works, but
   a name long enough to ellipsize would truncate the badge away, and the row's text stops being
   just the label, which the accessibility string and three harnesses read.
3. Make the row as wide as its content, and let the list's `gravity` place it.

## Decision

Rows are `WRAP_CONTENT` wide. The vertical `LinearLayout`'s gravity does the horizontal alignment
it was always there to do, and the badge lands one gap after the last letter under every alignment.
Rows keep `minHeight` 48 dp and gain `minWidth` 48 dp, so a two-letter name is still a real touch
target. A name too long for the screen still ellipsizes, because a `WRAP_CONTENT` `TextView` is
measured at most the width of its parent.

## Consequences

- The tap target is the name, not the line it sits on. Tapping the empty space beside a name no
  longer launches it.
- That empty space belongs to the gesture layer instead, which is where a text-only launcher wants
  it: the long press that opens settings, and the swipes bound to actions, now have more room and
  fewer accidental launches to compete with.
- Anything that assumes a full-width row must be re-checked. `scripts/verify-badges.sh` asserts the
  row is wider with a badge than without one, is nothing like the width of the screen, and stays
  centred; the Phase 1 to 4 harnesses were re-run on both emulators after the change.
- The design document keeps its original wording, since it records what was decided then; this ADR
  is the current answer.
