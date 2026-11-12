<!-- docs.sh: generated from docs/app.yml. Edit that file and run setup/docs.sh, not this block. -->
<a href="https://buymeacoffee.com/Dubadai"><img src="docs/images/support-banner.svg" alt="If you enjoy my work, buy me a gel (or a coffee) for my next ride: buymeacoffee.com/Dubadai" width="760"></a>

# BetterIntervals for Karoo 3

Listed on the Karoo as **DDB BetterIntervals**, next to the other DDB apps.

*Formerly Interval Screen. On the Karoo it is listed as **DDB BetterIntervals**, next to the other DDB apps.*

A full-page data field for the Hammerhead Karoo 3 that makes structured workouts easy to follow.
It loads your planned workouts from [intervals.icu](https://intervals.icu) and runs them itself, so
you can jump forward **and back** between intervals.

<img src="docs/images/betterintervals-light.gif" alt="BetterIntervals on three Karoo 3s during a workout, in three looks (gauge, coloured number, coloured background): the last seconds of interval 4, then the recovery starts and the watts drop to the new target">
<img src="docs/images/betterintervals-dark.jpg" alt="The same in dark mode, at one moment of the ride">

- **Before, during and after:** a workout waits for **START WORKOUT** (pick where to start with
  ⏮ / ⏭, then a 5-second countdown), runs interval by interval with beeps before each change,
  and ends on a **Workout complete** screen with its time, average power, NP and heart rate.
- **Big power** (instant or 3 / 5 / 10 s): starts on the **power gauge**; tap the watts for a plain,
  coloured number, coloured background or both. Blue is below the range, green in it, red above it.
- **Interval, time left and progress bar,** the target right under the watts, heart rate at the top,
  and two tiles (Lap avg and Cadence). **Settings › Workout screen** sets every spot: the top row,
  the line under the watts, and what each tile goes through when you tap it (lap or ride power,
  Lap NP, target, heart rate, cadence, calories, speed).
- **Workout details** on the Workouts page: the picture (one scale for every workout; tap a block
  for its watts), time, TSS, IF, NP, calories, the coach's notes, time in zones and every
  interval, then **Select workout**.
- **Next up with ⏮ / ⏭:** a jump starts a 5-second countdown with beeps, so you can get ready.
- **The whole workout as a chart** in zone colours with a "now" marker; tap it to zoom in.
- **Pause** stops the interval timer (for traffic, say) while the ride keeps recording. One lap per
  interval in your ride file, and short beeps before each interval ends.
- **Without a workout:** big watts, the current lap time, and two tiles you tap through: LAP AVG,
  LAP NP, RIDE AVG and HR, LAP HR, RIDE HR (heart rate in the Karoo's zone colours), plus the
  last lap.
- **The Karoo's own look:** its fonts, field sizes, icons and colours. **Dark / Light / Auto** follows the
  Karoo or stays fixed.
- **Remote button actions:** next interval, previous interval, pause or resume the workout.
- **Indoor with a smart trainer (ERG):** on an indoor ride profile the trainer follows the workout
  over Bluetooth. The Karoo's start button starts the ride and the workout together; **Match PM**
  corrects the trainer so your power meter shows the target. [More in the guide](docs/guide/indoor-erg.md).

### Without a workout

Big power and the current lap, and two tiles you tap through: the lap's average, the lap's NP and the
whole ride's average, and heart rate now, for the lap and for the ride.

<img src="docs/images/free-ride.png" alt="Free ride on a Karoo 3: 3S POWER 280, the current lap time 4:21, the tiles on LAP NP 289 and LAP HR 138 (heart rate in its zone colour), and the last lap">

<img src="docs/images/free-ride-dark.png" alt="The same in dark mode">

### Indoor with a trainer (ERG)

On an indoor ride profile the trainer holds each interval's watts. Press the Karoo's start button to
begin; the **Indoor ride** card on the Workouts page turns ERG off or on for the ride.
See the [indoor guide](docs/guide/indoor-erg.md).

<img src="docs/images/indoor-erg.png" alt="BetterIntervals indoors on three Karoo 3s: PRESS START TO BEGIN with the Karoo's start button flashing green and ERG READY at the top; riding with ERG holding 280 W; the Workouts page with the Indoor ride card, ERG on, Trainer 28436 ERG READY">

<img src="docs/images/indoor-erg-dark.png" alt="The indoor start and riding with ERG in dark mode">

### Settings

Each change shows straight away in the example at the top.

<img src="docs/images/settings.png" alt="Settings on three Karoo 3s: the example and Appearance; Power smoothing and Heart rate or cadence; Top row and Update notices">

Values show `--` when a sensor isn't connected. There's a step-by-step
[guide with pictures](docs/guide/README.md) for every feature.

Building it yourself or making a release: see [docs/DEVELOPING.md](docs/DEVELOPING.md).

## Install on your Karoo

1. On your phone, open the latest [release](../../releases/latest) and share the `.apk` download
   link to the **Hammerhead Companion** app. It installs the app on your paired Karoo.
   (With a computer: `adb install betterintervals.apk`.)
2. On the Karoo, open **DDB BetterIntervals** from the app list, tap the person icon and enter your
   intervals.icu API key (intervals.icu → Settings → Developer settings). Keep Athlete ID `0`.
3. Pick a workout from this week's list.
4. In a ride profile, add a page with one field and choose **DDB BetterIntervals → Workout screen**.
5. Optional: assign *Next interval*, *Previous interval* and *Pause or resume workout* to remote buttons.

Syncing uses the Karoo's Wi-Fi, or your phone through the Companion app. Your API key is stored
only on the Karoo.

Don't also start the same workout in Karoo's own workout player: this app marks a lap at every
interval change, which would move Karoo's player along too.

<!-- /docs.sh -->

## Licence

MIT, see `LICENSE`. The included Karoo SDK in `karoo-ext/` is Apache 2.0, see `karoo-ext/LICENSE`.
