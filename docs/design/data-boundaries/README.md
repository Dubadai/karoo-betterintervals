# Data field boundaries

With **data field boundaries** on (Karoo Settings), the Karoo's page turns light grey and
BetterIntervals looks like one big white card. David always rides with boundaries on, so that
look comes first; boundaries off stays as it is.

The Karoo tells each field whether boundaries are on: `ViewConfig.boundariesEnabled`
(karoo-ext 1.1.2+), passed at `startView`.

Round 1 (mockup, not decided):
- **Now**: white field on the Karoo grey.
- **A · Grey page**: the field takes the Karoo grey, tiles keep a darker tonal shade, Next up keeps its outline.
- **B · White cards**: grey page, Lap avg / HR and Next up become white cards (no outline), like stock fields with boundaries.

Round 2 (**decided**, 7 Nov 2026): **white tile cards, Next up keeps only its border.** With
boundaries on the field takes the Karoo grey, Lap avg / HR become white cards (black in dark mode),
and the Next up box stays outline-only on the grey.

Measured on the Karoo 3 (light): page grey `#DDDDDD`; with boundaries on the Karoo puts every
field, ours included, in a white card with a 1 px `#7D7D7D` border and rounded corners. Our
field paints the page grey inside that card; the Karoo keeps its border and corners on top.
Dark mode (measured): page `#484848`, the Karoo's own fields are black cards, same `#7D7D7D`
border. Ours matches: `#484848` page, black tile cards, gauge track `#6E6E6A`.

Round 3 (mockup, not decided): on the device the grey page made the whole screen hard to see.
Back to the Karoo's own card colour for the field (white in light, black in dark), for full contrast:
- **A · grey tiles**: tiles in the Karoo's page grey (`#DDDDDD` / `#484848`).
- **B · outlined tiles**: tiles in the card colour with the same 2 px outline as Next up.

- `mockup-round3.src.html`: round 3 options next to what was built.
- `mockup.src.html` + `build.py`: the mockup, drawn on device screenshots (run `python3 build.py`).
- `base-light.png`, `base-dark.png`: gauge screen, boundaries off (from `screens/`).
- `device/00-stock-fields-light.png`: Karoo's own fields with boundaries on (white cards on grey).
- `device/01-free-ride-before-light.png`, `device/02-free-ride-after-light.png`: free ride before / after.
- `device/03-workout-light.png`: workout screen (classic) after, boundaries on.
- `device/04-stock-fields-dark.png`: Karoo's own fields with boundaries on, dark.
- `device/05-workout-guess-dark.png`: first try with the guessed dark grey (`#1C1C1C`), too dark.
- `device/06-workout-dark.png`: workout screen, dark, with the measured grey.
