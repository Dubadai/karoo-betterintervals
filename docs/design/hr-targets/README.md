# Workouts with heart-rate targets

Request (2026-11-08): intervals.icu workouts with only HR targets show no graph. Today every
step is read as power, so an HR step becomes 0 W.

Proposal (`mockup.html`, drawn on the app's gauge screen from the field gallery):

- A step with an HR target (and no power target) runs on heart rate: the big number and the
  gauge are your heart rate against the step's bpm range.
- Power takes heart rate's place at the top (the top row setting still decides what's there).
- Tiles: LAP AVG shows lap average HR (lap arrow + heart); TARGET shows the bpm range (target + heart).
- Next up in bpm: "4m @ 138 bpm".
- The chart draws HR steps by bpm in the Karoo's heart-rate zone colours, from 80 bpm up so the
  steps don't all look the same height.
- Mixed workouts: each step uses its own target; a step with both uses power, as now.

`base/`: field gallery gauge screens the mockup is drawn on.
