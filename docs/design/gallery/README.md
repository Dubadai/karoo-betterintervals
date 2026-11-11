# Field gallery

Every screen of BetterIntervals (ExampleScreen's made-up workout; the list is in
`app/src/debug/.../GalleryFields.kt`): the workout screen in every watts style, START WORKOUT and
its countdown, an interval ending, jumps, pause, Workout done, free ride with its tile states, Top
row, a route (`-route`: the field 79 px shorter), at full page (478×721; BetterIntervals is
always a full-page field), light and dark. Drawn by the real ScreenRenderer on the Karoo 3 emulator:

- `fields/`: the screen alone · `screens/`: on a Karoo page · `artboard/` (not in git): in the
  Karoo 3 frame on Sky (light) / Night (dark), 1920 × 1080

```bash
bash ../design-assets/tools/field-gallery/gallery.sh karoo-betterintervals
```

Numbers are the showcase values (design-assets/tools/field-gallery/showcase.json): 280 W, 132 bpm,
ride time 1:12:40, clock 10:24; no debug demo data.
