# Empty states (mock)

Tiles and the big number show the Karoo's own words when a sensor gives no value, following the
shared rule (`KarooType.emptyPaint` / `emptyBaseline`, design-assets/karoo-type README "Empty states"):

- **No Sensor:** the sensor isn't paired (the Karoo's stream says NotAvailable).
- **Searching…:** it's paired but not connected yet.
- In tiles: the label font at 25 px caps, centred, its ink 39 px above the tile's bottom.
- The big number (and the gauge): the same text, centred where the number goes, as in BigWatts.
- The power tiles follow the power meter, the HR and cadence tiles their own sensor. On a heart-rate
  step, the big number follows the heart rate strap. The target never goes empty.
- The top row keeps "–".

Screens (light and dark): free ride, gauge, number with target at the top, start screen.

On the test Karoo (05-device-free-searching-light.png): sensors paired but not sending, so every tile and the big number say "Searching…", like the Karoo's own AVG POWER.
