# Scale workout: a preview before the start (2026-11-12)

The card under Scale workout (the interval's watts before → after the scale) only showed while the
workout ran (`currentIntervalCard` returned nothing before the start), so a rider scaling a loaded
workout saw the change only on the workout's card (its picture and "101%").

Proposal: before the start the card shows the first interval:

- Title: "First up · <name> <time>" ("First up" as on the ride screen's box), e.g. "First up · Warm-up 20:00".
- Watts before → after, the unit once at the end: "150 → 152 W"; a ramp as its range: "120–179 → 121–181 W",
  the line shrinking to fit the card (autosize, 20 → 16 sp). The scaled watts in blue, as today.
- At 100%: only the watts ("120–179 W").
- Running: "Current interval", as today, in the same "150 → 152 W" format.
- Outlined, not tonal (feedback: less tonal on the page): 1 dp border in the Outline tiles' grey #888780,
  no fill, 14 dp corners. Before and while running.

**Built** (OK'd). On the test Karoo before the start at 102%: "First up · Warm-up 10:00", "165–191 → 168–195 W"
(`06-built-dark.png`). `mockup.src.html` draws it (`./make.sh`, then `shoot.sh`).
Pictures (`shots/`): `01-now`, `02-ramp`, `03-steady`, `04-plain` (100%), `05-running`, light and dark;
`overview-dark.png`, `overview-light.png`.
