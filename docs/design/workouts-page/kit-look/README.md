# Workouts page in the settings kit's look (mock, 2026-11-09)

Proposal: the Workouts page uses the settings kit like Settings does. Not built yet.

Round 2: the scale moved out of Current workout; the selected blue instead of green and
orange; Remove is a red tonal button; more space around the sync line.
Round 3: Apple-style selection ring; Scale above the current workout.
Round 4: the ring is SmallerFields' Apple blue; the scale buttons have more contrast.
Round 5: no TODAY or LOADED tags.
Round 6: the loaded card faint blue instead of grey; no "This week" heading.
Round 7: Remove under the card, not in it; the loaded workout isn't listed twice.

- `01-now-vs-proposed-dark.png`: today's page on our test Karoo, next to the proposal.
- `02-proposed-light.png`, `03-proposed-dark.png`, left to right:
  1. Nothing loaded, Wi-Fi off.
  2. A workout loaded.
  3. Scrolled down: Remove workout, a divider, then the rest of the week.

The changes:
1. **Buttons:** Back to ride is the kit's yellow MAIN pill. Sync is the light blue-grey ADD pill, not
   a third style.
2. **One sync line:** "Synced 12:33 · Wi-Fi off · Turn on" replaces the three-line Wi-Fi paragraph and
   the big Turn on Wi-Fi button. It has room above and below. Wi-Fi off and Turn on use the settings'
   selected blue (segment_selected), not orange.
3. **Headings:** icon + 18 sp, as in Settings, not grey capitals. No "This week" heading: the day
   headings say it; a divider separates the current workout from the week.
4. **Scale workout** (only with a workout loaded) is its own section, above **Current workout**:
   − 100% + in a pill track (the buttons filled blue-grey, like a picked option), with help.
   **Remove workout** is a red tonal button right under the current workout's card, on the page.
   The loaded workout isn't listed again in the week; a day with nothing else left has no heading.
5. **Cards:** the kit's grey rounded card. The date isn't repeated under the day heading.
   No TODAY or LOADED tags: the day heading says Today, and the loaded card gets SmallerFields'
   selector, an Apple-blue ring (`select_blue` #007AFF, night #0A84FF), 3 dp, 2 dp outside the card,
   and its grey turns a faint blue (#E8F2FF, night #0F2440).
   A day already ridden is faded, with a RIDDEN tag.
6. **Dark mode:** black page (settings_bg), not #0B0B0B.

`mock.html#shot=<wifi|loaded|current|week>-<light|dark>` shows one screen. Each screen was rendered with
headless Chrome at 256 × 427 dp × 1.875, then placed in the Karoo frame with field-gallery's hero.swift.
The status bars are from our test Karoo. The workout names come from our week; their profiles are
drawn, not real.

`04-remove-light.png`, `04-remove-dark.png`: scrolled to the whole current workout, with Remove
workout under it and the week going on without it.
