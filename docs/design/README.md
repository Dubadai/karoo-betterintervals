# Design history

One folder per feature: a README with what was agreed, sketches (`.html`, open in a browser)
and screenshots from the Karoo 3 (`device/`). Newest work first.

| Folder | Feature |
| --- | --- |
| `gallery/` | The workout screen in four looks, full page, light and dark (emulator, `gallery.sh`) |
| `settings-example/` | "Example" card on the Settings page (live workout screen) |
| `update-notice/` | "Update available" card in Notifications → Karoo App info page with UPDATE |
| `screens/` | Clean screens for the README visuals and the Karoo Artboard (no floating Karoo buttons) |
| `onboarding/` | First-run introduction: connect, look, add to a ride profile, tap the watts |
| `karoo-style/` | Karoo style setting (round 5: inverted countdown, centred "Move cancelled") (Karoo fonts, native-size cells, icons, yellow buttons), Settings page, Dark / Light / Auto |
| `power-gauge/` | 5th watts option: gauge arc, target range on top, Lap avg / HR tiles |
| `jump-flow/` | "Next up" box, choosing a jump, Confirm move (right), countdown |
| `target-toggle/` | Tap the Target tile: Target ↔ Target range |
| `lap-np-toggle/` | Tap the Lap avg tile: Lap avg ↔ Lap NP (normalised power) |
| `progress-bar/` | Tonal track for the interval bar, matching the tiles |
| `tonal-icons/` | Gear, pause and the cancel ✕ in tonal colours |
| `paused-workout/` | Yellow "Workout paused" box, green half-pill resume button |
| `watts-range-modes/` | Tap the watts: plain / coloured number / coloured background / both |
| `free-ride/` | Screen without a workout: watts, lap avg, HR zone tile, laps |
| `free-ride-native/` | Free ride at the Karoo's own sizes and HR zone colours (round 1) |
| `karoo-style-only/` | Karoo style always on: the switch leaves Settings and the introduction |
| `workout-top-row/` | Workout screen top row: heart rate, cadence or target (mockup) |
| `workout-karoo-sizes/` | Workout screen with free ride's Karoo tile geometry and type sizes (mockup) |
| `workouts-page/` | Workouts list, Account page, offline / Wi-Fi |
| `route/` | Layout with Karoo's route pill (field gets shorter, watts auto-size) |
| `interval-screen/` | The main workout screen, countdown and zoomed chart |
| `remote-confirm/` | Remote-made jump choices confirm themselves after 5 s |

`_wrap.py` turns a sketch snippet into a standalone HTML page.
- `indoor-ride-notes/`: the notes from the 2026-11-11 indoor ride: top bar, ⓘ view, right tile, ramps, done screen, workout updates, zone colours (built on ride-fixes).
- `chart-contrast/`: +0 / +10 / +15 % contrast on the graph's colours; +15 % picked.
- `more-control/`: every spot on the workout screen set in Settings (top row, under the watts, left and right tiles), bigger gear and ⓘ tap areas (mock).
- `workout-details/`: a details page for each workout, Select workout at the top right (mock).
- `intervals-z7/`: options for the intervals.icu palette's Z7 instead of grey (preview).
- `empty-spots/`: None for every spot; what's left grows (mock).
- `details-chart/`: the details picture on one scale for every workout, 80 dp; tap a block for its watts (A, the bubble, built).
