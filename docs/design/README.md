# Design history

One folder per feature: a README with what was agreed, sketches (`.html`, open in a browser)
and screenshots from the Karoo 3 (`device/`). Newest work first.

| Folder | Feature |
| --- | --- |
| `jump-flow/` | "Next up" box, choosing a jump, Confirm move (right), countdown |
| `target-toggle/` | Tap the Target tile: Target ↔ Target range |
| `lap-np-toggle/` | Tap the Lap avg tile: Lap avg ↔ Lap NP (normalised power) |
| `progress-bar/` | Tonal track for the interval bar, matching the tiles |
| `tonal-icons/` | Gear, pause and the cancel ✕ in tonal colours |
| `paused-workout/` | Yellow "Workout paused" box, green half-pill resume button |
| `watts-range-modes/` | Tap the watts: plain / coloured number / coloured background / both |
| `free-ride/` | Screen without a workout: watts, lap avg, HR zone tile, laps |
| `workouts-page/` | Workouts list, Account page, offline / Wi-Fi |
| `route/` | Layout with Karoo's route pill (field gets shorter, watts auto-size) |
| `interval-screen/` | The main workout screen, countdown and zoomed chart |

`_wrap.py` turns a sketch snippet into a standalone HTML page.
