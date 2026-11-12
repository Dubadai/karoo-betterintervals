# Workout cards: show they open (2026-11-12)

A tap on a workout card on the Workouts page opens its details, but nothing on the card says so.
Two options, both 20 dp in the card's top right corner on the name's line, in the card's grey:

- **A: ⓘ**, the workout screen's info sign.
- **B: ›**, the kit's sign for "opens a page" (settings rows, list cards). Picked B, then asked for
  it smaller and in a circle:
  - **B2:** a 12 dp › in a thin 22 dp ring, the card's grey.
  - **B3:** a 12 dp › on a 22 dp disc in the page colour (white / black).

Picked B3, then asked for other colours and the › centred better. The › is now drawn on the disc's
middle (3.5 × 7 dp, 1.75 dp stroke, 0.25 dp right of centre so it reads centred; the kit icon sat
1.5 dp right). Colour pairs from the Karoo's palette:
  - **C1:** the kit's tonal blue-grey (#9FB3BD, dark ›; night #214559, white ›).
  - **C2:** the card's small-text grey (#636363, white ›; night #BCBCBC, black ›).
  - **C3:** the selection blue (#007AFF / #0A84FF, white ›).

Then asked to try a dark grey disc, light at night:
  - **D1:** #313131 with a white ›; night #F2F2F2 (the text colour) with a black ›.
  - **D2:** a step softer: #484848 with a white ›; night #BCBCBC with a black ›.

**Picked D1, built:** `ic_card_open.xml` (colours `card_open_disc` / `card_open_arrow`), top right on the
name's first line, only on cards that open. With it, Remove workout moved down: 14 dp under the current
card instead of 8 (its blue ring hangs 5 dp below the card). `mockup.src.html` draws it (`./make.sh`, then `shoot.sh`).
Pictures (`shots/`): `01-now`, `02-info`, `03-chevron`, `04-ring` (B2), `05-disc` (B3), `06-tonal`, `07-grey`, `08-blue`
(C1–C3), `09-dark1`, `10-dark2` (D1, D2), light and dark;
`overview.png` (now, A, B), `overview-circle.png` (B, B2, B3 close up),
`overview-disc-colours.png` (C1–C3 close up),
`overview-dark-grey.png` (C1, D1, D2 close up). Built, on the test Karoo (night): `11-built-d1-dark.png`,
`12-built-d1-two-lines-dark.png` (a two-line name).
