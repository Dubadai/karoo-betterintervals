# Details page: the workout's picture (mock, 2026-11-12)

Two changes to the picture at the top of the workout details page. Picked: **A**, the bubble (built 2026-11-12).

- **One scale for every workout.** Today each picture is stretched to its own highest step
  (`ceil(peak × 1.1 / 100) × 100`), so an easy endurance ride looks as big as a VO2 session, and the
  FTP line is left out when nothing reaches it. New: the top is 130 % of FTP, or the highest step
  + 5 % when that is higher (sprints). The FTP line is always in, at the same height on every
  workout unless the peaks go past 130 %.
- **Shorter:** 80 dp tall instead of 120.
- **Tap a block to see its watts.** The tapped block keeps its colour, the rest fade to 30 %, and
  the interval shows its name, time, watts, % of FTP and zone. Tap it again, or anywhere else, to close.
  - **A:** a dark bubble over the picture, pointing at the block; the page doesn't move.
  - **B:** the interval's row from Intervals, right under the picture; the tiles move down while it shows.

`mockup.src.html` draws it (build with `./make.sh`, pictures with `shoot.sh … --light-only`).

Pictures (`shots/`): `01`–`04` now, `05`–`08` new (Tempo 3 × 15, Endurance 2 h, VO2 max 5 × 4,
Sprints 10 × 30 s), `09`–`10` tap A, `11`–`12` tap B; `overview-scale.png`, `overview-tap.png`.
