# Indoor ride notes (2026-11-11)

The layout items from the notes after an indoor ride on 0.9.0, drawn on the app's own
screens (field gallery). `mockup.src.html` holds the drawing; build it with `./make.sh`, which
runs `design-assets/tools/mock/build.py` and adds Relative, the Karoo's number font. Make the
pictures with `shoot.sh`. `review.py` writes `review.html`: every screen, light and dark.

## Picked (round 2)

1. **Workout screen:**
   - The top bar has the gear, then heart rate and cadence (icons right next to their numbers),
     then ⓘ in the top right corner.
   - No ERG on any screen. No pause button on indoor workouts.
   - The target sits small under the watts, with a green target icon.
   - NEXT UP has no outline and keeps its jump buttons.
2. **Ramp:** a green ramp icon instead of the target icon, written start to end like a range
   (141-181 W). NEXT UP writes a ramp as 141 → 181 W.
3. **Workout info (tap ⓘ): the whole workout so far**, not just the interval.
   - At the top: the workout's name, then where you are and its length (INTERVAL 4 / 10 · 52:00
     IN ALL), and UpAhead's close button in the top right (67 px black rounded square, white ✕;
     white with a black ✕ in dark mode).
   - Tiles drawn the way the app draws them (icon top left, label top right, the number in
     Relative, no unit): elapsed, time left, avg power, NP, avg HR, avg cadence, calories, max, TSS, IF (10 tiles, the
     workout's own: TSS and IF from its NP and the intervals.icu FTP).
   - No chart and no jump buttons. Tap anywhere to go back.
4. **Lap tile:** a tap cycles LAP AVG → LAP NP → RIDE AVG.
5. **Right tile:** a tap moves through the metrics switched on in Settings › Right tile. Any of
   them can be on, heart rate and cadence included. No icon; dots run down the left edge, centred on
   the tile's height, one per metric switched on (6 at most: target, calories, speed, lap NP, heart
   rate, cadence).
6. **Workout done with the ride paused (indoors): A.** A smaller check sits below the Karoo's
   PAUSE bar, and "ride is still recording" is gone.
   - The button reads **Close summary** instead of DONE: calm copy in sentence case, Plex Medium
     38 px, on a black button (white in dark mode, like the ▷| button). It closes the summary and
     goes back to free ride with no workout selected. Not "Finish": it would sit right above the
     Karoo's own finish (flag) button, but it doesn't end the ride. Outdoors uses the same label.
   - Pressing the Karoo's own play (resume) button while the summary shows does the same: the
     summary closes and the ride goes on in free ride, no workout selected.
   - Everything sits above the Karoo's own end and resume buttons, which float over the bottom
     (from about y 683) while the ride is paused. The check is smaller, and the title, tiles and
     button move up 40 px.
7. **Workouts page:**
   - A workout changed on intervals.icu mid-ride gets an orange card with dark text for contrast:
     "Changed on intervals.icu" / "New watt targets.", then Keep current workout / Use new
     targets. Neither restarts the workout: it carries on where it is (Use new targets switches
     to the new watts from here on).
   - Under the buttons, an unchecked box: "Restart workout with new targets". Ticked, Use new
     targets starts the workout over from interval 1.
   - Scale workout gets a small card above the % control: "Current interval" in the text
     colour, then 280 W → **294 W**, both the same size, the new one in Apple blue.
8. **Zone colours (Settings):** Karoo, Zwift or intervals.icu, as choice cards showing the seven zones.
   **Default: Karoo** (picked). The app's own palette (from BigWatts) goes; riders on it get Karoo's
   colours after the update.
   - Karoo: its own climb1..7 (#60EEB2 #00B988 #FFF500 #FB8C65 #FE581F #D60404 #B700A2).
   - Zwift: grey, blue, green, yellow, orange, red; 6 zones, so Z7 shares Z6's red. Hex values
     approximate, to check against Zwift before building.
   - intervals.icu: #009E80 #009E00 #FFCB0E #FF7F0E #DD0447 #6633CC #504861 (Z2 confirmed in its
     API). Its API also sends the rider's own zone colours, so this could follow custom colours.
   - A ramp fades from its start zone's colour to its end zone's.

## Built (branch ride-fixes)

Pictures in `built/`, drawn by the app's own code (field gallery; Settings on the emulator):
01 indoor workout screen, 02 a ramp, 03 the ⓘ view, 04 the right tile with all six metrics,
05 the lap tile on RIDE AVG, 06 Workout done with the ride paused, 07 intervals.icu zone colours,
08–09 Settings › Right tile and Zone colours.

- **intervals.icu colours** were read off its own charts (light and dark), replacing the mock's
  guesses: fills light `#8FC4B6 #8FC380 #FAE191 #F7BA86 #E38395 #B6A3E6`, dark `#458376 #458234
  #BFA34B #BC773C #A6374E #5D41A5`, each bar with a stronger outline (light `#5CAD98 #5CAC43
  #F9D660 #F49D4E #D84B64 #7A58D1`, dark `#52A590 #52A438 #F2CE57 #E28F48 #D0415A #714FC9`).
  Its six hues on Z1…Z6 as intervals.icu draws them (teal recoveries, green endurance, orange
  threshold); Z7 is a dark grey (light `#8C8C8C`, dark `#4A4A4A`), so all seven differ. (A grey Z1
  was tried first: it shifted every colour a zone up and looked paler than intervals.icu.)
- **Graph contrast +15 %** for every palette (docs/design/chart-contrast).
- **Workouts page buttons:** Keep current workout and Use new targets are both tonal, not yellow:
  Back to ride stays the page's one yellow button (the kit rule).
- **Right tile:** with one metric on, it keeps its icon; with two or more, the dots replace it. The
  last one on can't be switched off. Settings starts with what the tile shows today switched on.
- **ⓘ** only on indoor workouts, in the top right (outdoors the pause button has the corner). A
  countdown closes the ⓘ view, so the interval change is never missed.
- **⏮** is an outline button (the old NEXT UP outline) on the workout and start screens, so the
  page has less tonal fill.
- **Close summary** (black, white in dark mode) everywhere; the "ride is still recording" line
  only while the ride records. The Karoo's play button on the summary closes it too.
- Not checked yet: the Workouts page cards (they need a ride running) and the Karoo's own PAUSE
  bar and buttons over the done screen (the gallery doesn't draw them).
