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
- Workouts: Back to ride in Karoo yellow (the main action). "Scale workout" above Current workout while one is loaded: outlined box, black (white in dark) − / + buttons (1% steps), just the percentage; scales the loaded workout, a newly picked one starts at 100%;
  the card shows the scale and its profile grows with it. HR targets don't scale.
- Only when the Karoo itself has the ride paused or auto-paused (its yellow bar covers the gear and top row): the gear moves to the start of the interval row and the pill shortens to PAUSED / AUTO-PAUSED so the clock fits (today "WORKOUT AUTO-PAUSED" pushes the clock off screen). Pausing only the workout while riding changes nothing.
- `base/karoo-ride-paused-real.png`: our test Karoo with the ride auto-paused (2026-11-08).

Top row icons are centred on the digits (icon shape centre = digit centre) and sized to them.

intervals.icu HR step format (read from a real HR-only workout, 2026-11-08):
`{"duration":240,"hr":{"value":85,"units":"%hr"},"_hr":{"value":175.0,"start":172.0,"end":178.0}}`.
`_hr` (resolve=true) is in bpm, with the range in start/end. The workout_doc also has `lthr` and
`"target":"HR"`, so % of LTHR is bpm / lthr. Repeat blocks are as for power.

Change after review (2026-11-08): the Target tile's % shows the percentage the way the workout is
written: "% HR" (of max HR, intervals.icu "85% HR") or "% LTHR"; a workout in bpm or zones shows % of
LTHR worked out from bpm. Next up follows ("2m @ 69% HR").
