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

The Karoo grey (light and dark) is an estimate: measure it on the device before building.

- `mockup.src.html` + `build.py`: the mockup, drawn on device screenshots (run `python3 build.py`).
- `base-light.png`, `base-dark.png`: gauge screen, boundaries off (from `screens/`).
