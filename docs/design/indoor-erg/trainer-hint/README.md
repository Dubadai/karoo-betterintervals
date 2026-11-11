# Trainer hint on an outdoor profile (approved 2026-11-11, built)

Before the ride, on a profile that isn't indoor, while the Karoo's power comes from the paired
trainer: an orange box under START WORKOUT says "Trainer connected: pick an indoor profile to use
ERG." It goes away when the ride starts or the trainer stops sending.

- Box: the kit's warning colours (`settings_warn` on `settings_warn_bg`), the trainer icon, 24 px.
- Aligned start screens: on every start screen (indoor, outdoor, with or without the hint) the
  watts sit in the top row (green bolt, next to HR) until the countdown, and the box below gets
  the big number's room. The workout name lands at the same height on all of them (`05-aligned`).
- Detection (`ErgController`): you pedal before the workout starts and the Karoo's power comes
  from the paired trainer (its `sourceId`). No Bluetooth scan (decided 2026-11-11). If a power
  meter is the Karoo's power source, the hint doesn't show.

## Pictures

- `mockup.src.html` and `shots/`: the rounds (A/B/C wording, then the orange box C)
- `built/01-watts-up-light.png`, `built/02-watts-up-dark.png`: the build on the emulator
  (gallery state `trainer-hint`), watts in the top row
- `built/03-outdoor-start-*.png`: the outdoor start without a trainer (`start-outdoor`)
- `built/05-aligned-start-screens.png`: outdoor, trainer hint, indoor, countdown
