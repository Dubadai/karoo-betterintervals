# Karoo zone colours for workouts

Idea: colour the workout chart (workout screen and Workouts page) with the Karoo's power zone
colours that BigWatts uses (`karoo-bigwatts` WattsRenderer.kt, `zoneArc`) instead of today's
intervals.icu-style colours.

- Same % of FTP bands for Z1–Z5 (≤55, 56–75, 76–90, 91–105, 106–120 %), so each step keeps its
  zone; the Karoo adds Z6 (121–150 %, red) and Z7 (> 150 %, purple).
- Karoo Z1 is grey, almost the same as today's grey for past steps, so the two would merge
  (option A). Option B fades past steps to a light grey (dark mode: dark grey).

Round 1 (mockup, not decided): Now / A · Karoo zone colours / B · Karoo colours, past steps faded.

- `mockup.src.html` + `build.py`: recoloured from the clean screens (`../screens/`) and the
  Workouts page capture (`Karoo/design-assets/05-workouts-*`); `base-*.png` are those bases.
