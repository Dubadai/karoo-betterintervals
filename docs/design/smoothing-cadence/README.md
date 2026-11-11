# Power smoothing, heart rate or cadence

Asked (7 Nov 2026) for Settings to pick the watts smoothing (instant, 3, 5 or 10 s) and to
show cadence instead of heart rate, with its own smoothing, keeping the design as it is.

- **Power**: Instant / 3 s (default) / 5 s / 10 s. The Karoo's own POWER and 3/5/10 s average
  power streams.
- **Heart rate or cadence**: cadence takes every heart rate spot (top row next to the watts,
  the gauge screen's HR tile, the free-ride HR tile and the last-lap line). Cadence icon: a crank
  circle with a slash, like the Karoo's; Karoo green in Karoo style, else the text colour. No
  zone tint for cadence.
- **Cadence** smoothing (shown with Cadence picked): Instant (default, like the Karoo's Cadence
  field) / 3 s / 5 s / 10 s.

Device screenshots (light, our test Karoo, release-signed build):
- `device/01-settings-power-light.png`: Power row.
- `device/02-settings-cadence-light.png`: Heart rate or cadence, Cadence smoothing.
- `device/03-workout-cadence-light.png`: workout screen with cadence at the top (no sensor: --).

Not checked on the device: the gauge screen's tile and the free-ride tile with cadence.
