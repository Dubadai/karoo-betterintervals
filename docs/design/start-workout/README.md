# Start workout (and today first in the list)

David (7 Nov 2026): often the workout is loaded before the ride but should only start some time
into it. Today the workout clock runs as soon as the ride records.

Proposal, round 1 (mockup, not decided):
- A loaded workout waits: a "ready" screen before the ride and while riding until you start it.
  The watts and heart rate are live; the workout clock doesn't run.
- **START WORKOUT** (Karoo yellow) starts it; the remote's pause button does the same, since
  taps on a field don't always arrive. ⏭ / ⏮ in the First up box pick a later step to start
  from.
- Options: **A** the normal screen with START WORKOUT where the tiles are (watts stay big),
  **B** a big START WORKOUT in the middle with smaller watts.
- Workouts page: today's workouts first, then the coming days, then the earlier days of the week.

- `mockup.src.html` + `build.py`: drawn on the clean gauge screens (`base-*.png`, from `../screens/`).
