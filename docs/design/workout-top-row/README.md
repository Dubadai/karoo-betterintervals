# Workout screen: what the top row shows

Request (2026-11-07): make the top of the workout screen switchable between heart rate, target and
cadence, while the data an interval needs stays on screen.

Proposal (`mockup.html`, drawn from the app's own screens in the field gallery):

- Always on screen: the watts (range colours or gauge), the target, Lap avg (each interval is a
  lap), the interval time and progress, Next up and the chart.
- Settings › Top row: Heart rate, Cadence or Target. Whatever isn't at the top goes in the
  second tile next to Lap avg. With Target at the top the tile shows heart rate, or cadence if
  Settings › Heart rate or cadence says so. So the target never disappears and there is never
  more than one extra number.
- Defaults stay as now: number styles have heart rate at the top, the gauge has the target.
  (Open question: one setting for both, or the gauge keeps Target on top.)

`base/`: field gallery screens and emulator Settings shots the mockup is drawn from.
