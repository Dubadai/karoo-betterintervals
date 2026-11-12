# Settings without "Heart rate or cadence" (mock, 2026-11-12)

Since every spot on the workout screen is set on its own (Settings › Workout screen), "Heart rate or
cadence" no longer changes the workout screen; its help ("What shows next to the watts and in the HR
tile") was out of date. It still:

- picked the free ride's second tile: HR → LAP HR → RIDE HR, or the same in cadence;
- picked the Workout done screen's fourth number: Avg HR or Avg cadence;
- hid Cadence smoothing unless Cadence was picked, though cadence is on the right tile by default.

Proposal: the setting goes; Cadence smoothing always shows, right under Power; free ride and Workout
done keep heart rate (as they are by default today). A rider who had picked Cadence gets heart rate there.

Waiting for an OK. `mockup.src.html` draws it (`./make.sh`, then `shoot.sh`).
Pictures (`shots/`): `01-before-top`, `02-after-top`, `03-before-smooth`, `04-after-smooth` (Settings),
`05-free`, `06-done` (unchanged, from the field gallery), light and dark; `overview-dark.png`.
