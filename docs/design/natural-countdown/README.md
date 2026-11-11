# Countdown before an interval ends by itself (mock, 2026-11-10)

From a test ride: the purple countdown and its beeps only came for a jump (⏮ / ⏭ / remote) and for
START. When an interval ended by itself, the next one started with a single beep, with no warning.

Proposal: the same purple box in the last 5 seconds of an interval, with a beep for each number,
in place of Next up. It counts down to the next interval and shows it on the right
("3:00 · 150 W"). The chart marks where it goes, as for a jump. At 0 the next interval starts with
the usual go beep.

Drawn by the app's own renderer (field gallery states `natural-countdown` and
`natural-countdown-gauge`, at −0:03):

- `01-now-vs-proposed-light.png`, `02-now-vs-proposed-dark.png`, left to right:
  1. Today: Next up stays until the end.
  2. Proposed, gauge look.
  3. Proposed, number look.

Open questions:
- During it, ⏮ / ⏭ are hidden, as in a jump countdown. Taps on the box: none, since the interval
  ends either way.
- The 3 short warning beeps of today are replaced by the countdown's beeps.

## The last interval: Workout finished (round 2, built)

The last interval gets the same countdown, and the box says **Workout finished** where it would show the next
interval. It's in the Karoo's label font (IBM Plex Sans Condensed); interval numbers stay in
Relative. There's no jump marker on the chart and no "→" in the Target tile, since nothing comes
next. At 0, Workout done, as today. This replaces the last interval's 3 short warning beeps.

- `03-finish-light.png`, `04-finish-dark.png`, left to right:
  1. An interval ending, for comparison.
  2. The last one, gauge look.
  3. The last one, number look.

## One countdown everywhere

Every countdown is the same: 5 seconds, a beep for each number, the go beep at 0. That covers an
interval ending, the last one (Workout finished), a jump (⏮ / ⏭ / remote) and START. The last
interval's 3 short warning beeps are gone. While a jump is being chosen in an interval's last
seconds, the box shows the choice, and the same beeps still count the interval out.
