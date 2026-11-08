# Heart-rate targets, two values at the top, scaling, gear while paused

Requests (2026-11-08):
- intervals.icu workouts with only HR targets show no graph (every step was read as power, so 0 W).
- Show the HR target in bpm, and also as % of LTHR.
- Gauge band red for heart rate (Karoo green is a power colour), and the number must not run into the gauge.
- Top row: one or two values (e.g. heart rate and cadence), picked in Settings.
- Scale the loaded workout (100% = as planned), like the Karoo's own SCALE − 100% + control,
  with clearer buttons and better contrast, at the top of Workouts (opened by the gear).
- While the ride is paused the Karoo covers the gear: move it down so the workout can be changed at the roadside.

Proposal (`mockup.html`, light and dark, drawn on field gallery screens and the app's own colours):
- HR step: heart rate is the big number, sized to the watts' space; the gauge band is the heart red.
  Power moves to the top. Tiles: LAP AVG = lap avg HR, TARGET = bpm range. Tapping the Target
  tile switches it to % LTHR (label "% LTHR", Next up follows). Chart: bpm in Karoo HR zone colours from 80 bpm.
- Mixed workouts: each step uses its own target; a step with both stays on power.
- Settings › Top row: One | Two, then Left and Right (Right can't repeat Left).
- Workouts: SCALE block above Current workout while one is loaded: − / + (1% steps), "of every
  power target"; the card shows the scale and its profile grows with it. HR targets don't scale.
- Ride paused: the gear moves to the start of the interval row, next to Workout paused.
