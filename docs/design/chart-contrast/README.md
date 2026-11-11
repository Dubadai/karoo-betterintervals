# Graph contrast

The Karoo's screen shows colours lighter than they're drawn (measured on ride shots and the
gallery: the Karoo's #FB8C65 shows as #FD9F77), so pale graph colours wash out on white. We tried
+0 / +10 / +15 % contrast on the graph's colours (zone bars, their outlines, heart-rate bars) for
all three zone palettes: `preview.html`, pictures in `shots/` and `crops/` (drawn by the app in the
field gallery).

**Picked: +15 %** (2026-11-11). More contrast means more saturated, darker on the white page,
brighter on the black one (`ZonePalette.boosted`, `ZonePalette.CONTRAST`). It applies wherever a
graph shows: the ride screen's chart, the Workouts page thumbnails and the palette cards in Settings.
