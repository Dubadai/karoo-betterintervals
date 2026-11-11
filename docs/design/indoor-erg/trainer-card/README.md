# Trainer card (approved 2026-11-11: A, built)

Replaces the trainer sheet (`../trainer-sheet`, dropped: it took the gear away from the Workouts
page, so the workout couldn't be changed). The gear opens the Workouts page as before. On an indoor
ride with ERG on, a compact highlighted card sits under Back to ride: ERG on / off for this ride and
the trainer's state (ERG READY, ERG on, connecting, can't reach the trainer, off for this ride).
When paused, Match PM and the rest stay in Settings › Trainer (indoors).

- `mockup.src.html` → `mockup.html`: A (compact, on Workouts) and B (full card at the top of Settings)
- `shots/`: the mock, light only
- `device/01-erg-ready-light.png`, `device/02-erg-off-light.png`: on the test Karoo
