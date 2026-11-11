# Workout details (mock, 2026-11-11)

Tapping a workout card on the Workouts page opens its details; Select workout sits at the top
right (the page's one yellow button). Remove workout stays where it is on the Workouts page.
`mockup.src.html` draws it (build with `./make.sh`, pictures with `shoot.sh`, light only).

- The name, the day and its length; the workout's picture in the rider's zone colours, ramps
  fading, FTP as a dashed line.
- Tiles, every number the same size: time (short, 2h20), TSS, IF, average power, NP and estimated calories (the work in kJ, about the same
  in kcal). intervals.icu's own load and IF when it sends them, else worked out from the steps.
- Notes: the workout's description from intervals.icu (hidden when there is none).
- Time in zones: each zone's name, time and share, a bar in its colour, its watts.
- Intervals: every step with its name, time, watts, % FTP and zone; repeats grouped (3 ×).

Pictures: `shots/overview.png`.
