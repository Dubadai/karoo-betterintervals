# Free ride: Karoo native sizes

The screen without a workout, made to match the Karoo's own fields (data field boundaries on).

Round 1 (mockup.html), after David's notes:

- **Watts** higher up, in the Karoo's number font (IBM Plex Sans, 90% wide).
- **Lap time**: the same one-line row as before (green lap icon, LAP TIME, clock on the right),
  a step bigger: label 15 → 17sp, clock 23 → 28sp. A full Karoo field for it was too big.
- **Lap avg and HR**: the Karoo's 2-across fields (label 20 px caps, number 64 px tall, as
  measured on the Karoo's own SPEED and HR fields).
- **HR colour**: the Karoo's own HR zone colours, full colour like its HR field with Zone Color
  on, outline heart. For 5 zones the ride app uses `#60EEB2 #00B988 #FFF500 #FB8C65 #D60404`
  (its climb1, climb2, climb3, climb4, climb6 colours; other zone counts use other picks), the
  same in dark mode. Zone 1 measured on device: `#60EEB2`.
- **Last lap**: the same design as before (small grey label, bold condensed values), a step
  bigger: label 11 → 12sp, values ~15% bigger, above the Karoo's back and play buttons.

Device screenshots (`device/`):

- `00-current-light.png`: v0.5.3 on David's Karoo, no ride.
- `01-native-hr-zone-light.png`: the Karoo's own HR field with Zone Color (zone 1).
- `02-native-hr-combo-light.png`: the Karoo's HR combo field (its own, lighter blue).

Round 2: everything sits further down. Without a route, Last lap is back between the Karoo's back
and play buttons (shrinking to fit 150dp, as before) and the rest moves down with it. With a route
(`route-light.png`, `route-dark.png` as the base) the Karoo makes the field shorter, so Last lap
sits at the field's bottom edge above the turn pill and the watts get the space that's left.

Round 3: watts centred between the bottom of the visible gear (not its touch area) and the top of
the lap row. "LAP TIME" and its icon now use exactly the tiles' label font, size and icon, lined up
with the tiles' icons. Label and icon are centred on
the clock's digits.
Lap avg tile darker in light mode: `#DDDDDD`, the Karoo's own page grey (was `#EBEBEB`).
No last lap yet (first lap): LAST LAP and its values are hidden; the space stays, so nothing moves
when the first lap ends.
- `03-built-no-ride-dark.png`: the build on David's Karoo, dark mode, no ride (Last lap hidden).
