# Workouts page: where "Synced" goes (2026-11-12)

"Synced 08:41" sat on its own line between the ERG card and Scale workout, far from the Sync button it
belongs to, and felt lost. Three places closer to it; a problem (Offline, Wi-Fi off) shows in the same
place in amber, bold.

- **A: under Sync.** A small line centred under the Sync button (12 sp, grey).
- **B: in the Sync button.** The button's second line: "Sync" over "08:41" (11 sp).
- **C: by the title.** On the title's line, right-aligned before the gear (13 sp, grey).

**Picked A, built:** centred under Sync, 12 sp, shrinking to 9 sp to fit the button's 96 dp
("Synced Tue 08:41" on another day). Offline (Wi-Fi on, no internet) takes its place in amber. Wi-Fi off
keeps its own line with the Turn on button (it needs the room); the time stays under Sync. `mockup.src.html` draws it (`./make.sh`, then `shoot.sh`).
Pictures (`shots/`): `01-now`, `02-under`, `03-inside`, `04-title`, and the same Offline (`05`–`08`), light
and dark; `overview-dark.png`, `overview-light.png`. Built, on the test Karoo (night, while syncing):
`09-built-under-dark.png`.
