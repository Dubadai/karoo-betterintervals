# Match PM (approved 2026-11-11, built)

One On/Off switch, **Match PM**, in Settings › Trainer (indoors), under When paused, shown only
while ERG is on. Off (the default): the trainer holds the target by its own watts. On: ERG makes the
bike's power meter (PM) show the target. Named "Match PM", not "PowerMatch" (TrainerRoad's name).

## How it matches (no fixed percentage)

Trainers drift differently across the power band (often more off at low watts) and read differently
cold and warm, so one percentage is wrong most of the time. Match PM looks at the watts all the time:

- **Live loop.** Every second, the trainer's own watts (FTMS Indoor Bike Data over Bluetooth) next to
  the Karoo's power from the PM, both averaged over ~10 s. The target sent to the trainer moves
  slowly (time constant ~30 s) until the PM shows the workout target. So warm-up drift is followed
  as it happens, without a separate warm-up model.
- **Per power band.** The correction is kept per 50 W band (100–150 W, 150–200 W, …). A jump to a
  new interval starts from that band's correction, so it lands close at once; the live loop then
  fine-tunes it. Bands never seen use the nearest band.
- **Between rides.** The band table is saved at the end of a ride and is the starting point for the
  next. While the trainer is cold (first minutes of a ride) the live loop trusts what it measures,
  not the saved table, and the saved table only takes in readings once they have settled.
- **Limits.** At most ±10 % from the plan's watts. The loop holds still during ramps, the first
  ~10 s after an interval change, soft starts, pauses and when you stop pedalling. If the PM drops
  out, ERG goes back to the band's correction (no live loop).
- **Which power is the PM.** The Karoo's power stream names its source (`DataPoint.sourceId`). If it's
  the trainer itself, Match PM doesn't run and Settings says so (shot 4).

## Shots (Settings pages: light only)

- `mockup.src.html` → `mockup.html` (`design-assets/tools/mock/build.py`)
- `shots/01-off-light.png`: off, with the help text
- `shots/02-new-light.png`: on, before the first ERG ride
- `shots/03-ok-light.png`: on, after a ride: trainer vs PM per band, cold start and when it warmed up
- `shots/04-warn-light.png`: on, but the Karoo's power came from the trainer

## Built (`PmMatcher.kt`, tests in `PmMatcherTest`)

- `PmMatcher`: the band table, live loop, settle and limits; `summary()` for Settings.
- `ErgTrainer` reads the trainer's watts from FTMS Indoor Bike Data (`parsePower`).
- `ErgController` sends `target × band ratio` while Match PM is on, makes a matcher when recording
  starts and saves the bands and the last-ride note when it ends. It logs the Karoo's power source
  (`Match PM: Karoo power from …`) so the first KICKR ride shows what `sourceId` looks like.
- Settings uses the power icon for the row: the mock's list icon (`ic_settings_priority`) is in
  karoo-common 1.5, the app still has 1.0.
- Not tested with a trainer yet: needs a ride on the KICKR with a power meter.
- `device/01-match-pm-new-light.png`: on the test Karoo, Match PM on before its first ride.
