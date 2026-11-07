# Karoo zone colours for workouts

Idea: colour the workout chart (workout screen and Workouts page) with the Karoo's power zone
colours that BigWatts uses (`karoo-bigwatts` WattsRenderer.kt, `zoneArc`) instead of today's
intervals.icu-style colours.

- Same % of FTP bands for Z1–Z5 (≤55, 56–75, 76–90, 91–105, 106–120 %), so each step keeps its
  zone; the Karoo adds Z6 (121–150 %, red) and Z7 (> 150 %, purple).
- Karoo Z1 is grey, almost the same as today's grey for finished steps, so the two would merge.

Round 1: Now / A · Karoo zone colours / B · past steps faded. David: don't fade finished steps,
you may need to jump back to them.

Round 2 (mockup, not decided): Now / A · Karoo zone colours, finished steps grey (as today) /
B · finished steps keep their zone colour; where you are shows by the outlined current step and
the marker above it. On the device screenshot the finished steps are grey, so their zones come
from the same workout on the Workouts page (warm-up ramp Z1, first step Z3).

Open point: the jump target is drawn solid blue (Karoo style: purple), which would clash with
Karoo Z2 blue (Z7 purple) and needs another look.

- `mockup.src.html` + `build.py`: recoloured from the clean screens (`../screens/`) and the
  Workouts page capture (`Karoo/design-assets/05-workouts-*`); `base-*.png` are those bases.

Current step outline (mockup, not decided): today the 2dp outline sits right on the zone colour,
which is hard to read with bright Karoo colours. `outline-gap.src.html`: Now / A · 1dp gap /
B · 1.5dp gap of page colour between the outline and the fill (the fill moves in, the outline
stays where it is; the "now" line still runs through the bar).
