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

Decided and built (7 Nov 2026): **B**, big START WORKOUT in the middle; ⏮ / ⏭ pick where to start
("First up", or "Start at 3 / 16"); the remote's pause button starts it too; a fresh workout always
waits (no setting). Workouts page: today, then coming days, then past days.

Layouts `field_ready.xml` / `field_ready_karoo.xml` are generated from the workout layouts by
`tools/gen_ready_layouts.py` (top row, First up box and chart stay the same).

- `device/01-ready-light.png`: the ready screen on David's Karoo (Karoo style, light).
- `device/02-start-at-3-light.png`: after ⏭ twice: "Start at 3 / 16", the chart outlines step 3.
- `device/03-started-at-2-light.png`: ⏮ back to 2, then START: the workout screen at 2 / 16
  (the clock waits for the ride to record).

- `device/04-start-countdown-light.png`: after START: the 5 s countdown ("Starting with"), START NOW.

START runs the usual 5 s countdown (David, 7 Nov 2026), then a happier start tune (rising E6,
G#6, B6, E7) instead of the plain GO beep. START NOW or a tap on the countdown skips the wait;
the remote's pause button (or ⏮ / ⏭) stops it.

With no ride active, a workout always starts over waiting at the first interval, also after an
app restart (saved progress only carries over a restart mid-ride).

Not checked on the device: dark mode, the non-Karoo style, and today-first (no workout today in
the cached week).

Countdown in the button (mockup, not decided): `countdown-in-button.src.html`. David asked to
show the start countdown in the yellow button instead of the First up box. A · the number and
START NOW in the button; B · a big number with a small "Starting · tap to start now". In both the
button darkens from the left as the seconds run, and First up stays as it is.
