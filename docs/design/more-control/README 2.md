# More control over the workout screen (mock, 2026-11-11)

The ask after the indoor-ride build: let riders set every spot on the workout screen.
`mockup.src.html` draws it (build with `./make.sh`, pictures with `shoot.sh`). The default screen
is the app's own render (field gallery state `slot-defaults`); the rest is drawn over it.

## Proposed

- **Four spots, set in Settings › Workout screen**, one row each that opens its own page (an
  Example card with that spot ringed in blue):
  - **Top row:** One or Two; Left and Right each heart rate, cadence, target, calories or speed.
  - **Under the watts:** target, heart rate, cadence, calories, speed or nothing.
  - **Left tile and Right tile:** switches for Lap avg, Lap NP, Ride avg, Target, Heart rate,
    Cadence, Calories and Speed. With two or more on, a tap switches between them and dots show
    which one it is (up to 8). The last one on can't be switched off.
- **Defaults:** heart rate on top, the target under the watts, Lap avg on the left, Cadence on
  the right. Top row choices riders saved on 0.9.0 stay; everything else starts at the defaults.
- On a heart-rate step the power items swap for heart rate as today (the lap tile shows the lap's
  average heart rate).
- **Tap areas:** the gear and ⓘ both get 64 dp squares (now 48 dp), the same size.

Pictures: `shots/overview-screens.png`, `shots/overview-settings.png`.
