# Softer tonal tiles in light mode

Request (2026-11-08): the tonal grey behind Lap avg and Target is softer in dark mode than in light.

Measured: light mode #DDDDDD on white, dark mode #1F1F1F on black. Both are about 11 L* from the
page, but grey on white reads as a heavy block and dark grey on black recedes.

`mockup.html`: the app's own screens (field gallery) with only the light tile colour changed:
Now #DDDDDD, A #E6E6E6, B #EBEBEB (recommended), C #F0F0F0; dark mode for reference. Note the light
tile with boundaries on was picked to match the Karoo's page grey; a lighter one no longer matches it.

`base/`: field gallery screens the mockup is drawn on.
