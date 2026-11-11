# Trainer sheet (dropped 2026-11-11: replaced by ../trainer-card)

A Karoo data field can't see swipes (taps only; the Karoo keeps swipes for page changes and its
menu), so the trainer settings can't be a swipe up on the workout screen. Instead, on an indoor ride
with ERG, the gear opens this sheet: it slides up over the ride (which keeps recording, its top row
still visible), and swipe down or Back returns to the workout.

Contents: ERG on / off, the trainer found on the Karoo, When paused (40 / 50 / 60 % FTP), the
Read-Only hint, and "All settings" for the usual Settings page.

- `mockup.src.html` → `mockup.html` (`design-assets/tools/mock/build.py`)
- `shots/01-ride-*`: riding with ERG on (tap the gear)
- `shots/02-sheet-*`: the sheet
- `shots/03-sheet-off-*`: ERG switched off in the sheet (the gauge is back behind it)
- `shots/04-slide-*.gif`: the sheet sliding up
