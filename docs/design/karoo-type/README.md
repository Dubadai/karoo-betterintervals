# The Karoo's own typography, 1:1

Request (2026-11-08, after a ride; workspace KarooType): numbers in the Karoo's value font and
tiles that match its native fields exactly.

- **Every number is Relative** (Hammerhead Relative Mono 12, the Karoo's value font, loaded from the
  Karoo, never bundled) with −0.04 spacing: big watts, gauge, top row, tiles, clocks, done screen.
- **Tiles are drawn as one image** (TileArt) with KarooType's rules, in pixels: label caps 20 px,
  20 px from the top, 9 px from the right; icon boxes 12 px from the left on the label's baseline;
  the number auto-sized (line = tile − 34 px), 12 px above the bottom, 9 px from the right. Measured
  on the free-ride and workout tiles: the same as a native 236 × 140 field (64 px digits, 24 px under
  the label).
- **Workout tiles are native height** (75dp = 140 px): the chart dropped its watt scale and total
  time (bars the same size, 14dp freed) and the gauge gave 7dp. No timer icon before INTERVAL; the
  interval row sits 8 px lower.
- **Text lines** (Next up, First up, jump countdown, Last lap, lap number, INTERVAL) are Relative
  too, written alike ("3m · 150 W", "8:20 · 271 W · 126 bpm"): thin spaces, "@" "·" "/" in Plex
  Sans Condensed, plain hyphens (Relative has no en dash or minus, like the Karoo's "162-162").
- Labels stay Plex Sans Condensed, the Karoo's label font.

Pictures: `before-after-light.png` / `-dark.png` (workout, free ride: before, after), `text-lines.png`.
- Step lengths as a clock (2026-11-08): "3:00 · 150 W", "10:00 · 150-210 W", like Last lap.
- Top row drawn with BigWatts' heart-rate line rules (TopRowArt, 2026-11-08): Relative at the font's own spacing with a 2.8 % outline (the Karoo header's weight), icons 1.05 × the size, 0.3 × the size before the number, normal word gaps. `top-row-heart.png`.
