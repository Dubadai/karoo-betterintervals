# Field gallery

The workout screen (ExampleScreen's made-up workout) in four looks: `classic`, `karoo` (Karoo
style), `gauge` and `colours` (coloured number and background), at full page (478×721) and half
page (478×336), light and dark. Drawn by the real ScreenRenderer on the Karoo 3 emulator:

- `fields/`: the screen alone · `screens/`: on a Karoo page · `artboard/` (not in git): in the
  Karoo 3 frame on Sand (light) / Night (dark), 1920 × 1080

```bash
bash ../design-assets/tools/field-gallery/gallery.sh karoo-betterintervals
```

Numbers are the showcase values (design-assets/tools/field-gallery/showcase.json): 280 W, 132 bpm,
ride time 1:12:40, clock 10:24; no debug demo data.
Seen in the gallery: at half page (478×336) the screen breaks in every look: the watts are gone and
the header overlaps the interval row.
