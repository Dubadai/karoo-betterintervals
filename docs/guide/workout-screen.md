# The workout screen

## Before you start

A workout you've picked waits until you start it, so you can load it before the ride and ride
for a while first (warm up on your own, get out of town).

<img src="images/ready.png" width="240" alt="Ready: START WORKOUT in the middle, the workout's name, First up">

- Your watts and heart rate (or cadence) are live; the workout's clock doesn't run.
- **START WORKOUT** starts the usual 5-second countdown. The button counts down too: tap it
  again (**TAP TO CANCEL**) to stop. A tap on the purple countdown below starts right away. A
  short rising tune plays when the workout starts. The remote's **Pause or resume workout** button
  works like START: it starts the countdown, or cancels it. ⏭ / ⏮ also cancel it.

<img src="images/start-countdown.png" width="240" alt="Start countdown: 3 in the yellow button, TAP TO CANCEL, and the purple countdown below">
- **First up** shows the interval it will start with. ⏭ / ⏮ choose another one, for example to
  skip the warm-up: the label then says **Start at 3 / 16** and the chart outlines that interval.
- When the ride ends (or no ride is on), the workout goes back to waiting at the first interval.

## Once it runs

<img src="images/workout-screen.png" width="240" alt="Workout screen with the power gauge: 280 watts in the target range 273–287, heart rate at the top">

From top to bottom:

| Part | What it shows |
| --- | --- |
| ⚙ (top left) | Opens the [Workouts page](workouts-and-account.md) |
| ❤ number | Heart rate |
| ⏸ (top right) | [Pauses the workout](pausing.md) |
| Big number | Power (3 s by default, see [Settings](settings.md)). Tap it to [change the colours](watts-colours.md) |
| Interval 1 / 16 | Which interval you're in, out of how many |
| Time | Time left in this interval, counting down (−2:18) |
| Bar | How much of the interval is done |
| Left tile | Lap avg or [Lap NP](tiles.md) for this interval |
| Right tile | Target or [Target range](tiles.md) for this interval |
| Next up | The next interval's length and watts, with ⏮ / ⏭ to [jump](jumping.md) |
| Chart | The whole workout |

## Heart-rate targets

A workout can have heart-rate targets instead of power (in intervals.icu, e.g. `4m 85% HR` or
`95% LTHR`). On such an interval:

- the big number is your heart rate, and the gauge's target band is red (green is for power);
- power moves to the top, where heart rate was;
- Lap avg is the interval's average heart rate;
- Target is the bpm range. **Tap it** for the percentage, as the workout is written: `% HR`
  (of max HR) or `% LTHR`. Next up follows (`2m @ 69% HR`). A workout written in bpm or zones
  shows % of your LTHR.

The chart draws heart-rate intervals in the Karoo's heart rate zone colours. A workout with only
heart-rate targets is drawn in bpm, starting at 80. A workout can mix both: each interval runs on
its own target.

<img src="images/hr-workout.png" width="240" alt="Heart-rate interval: 164 bpm in the red target band, power at the top"> <img src="images/hr-pct.png" width="240" alt="Target as % of max HR, as the workout is written">

## The chart

Each bar is one interval, in its power zone colour: the Karoo's seven power zones (Z1 grey,
Z2 blue, Z3 green, Z4 yellow, Z5 orange, Z6 red, Z7 purple), as % of the workout's FTP. Finished
intervals keep their colour, so you can still see what you'd jump back to. The current interval
is outlined, and a black marker (white in dark mode) shows where you are. The end time of the workout is on the right.

Tap the chart to zoom in on the current interval. Tap again to see the whole workout.

<img src="images/chart-zoomed.png" width="240" alt="Chart zoomed in on the current interval">

## When the workout is done

After the last interval a **Workout complete** screen shows the workout's own numbers: its time,
average power, NP and average heart rate. Tap **DONE** to take the workout off and go back to the
free-ride screen. The ride itself is still recording, as the blinking red dot under DONE says.

<img src="images/done.png" width="240" alt="Workout complete: a green check, the workout's name, time 52:00, average power 232, NP 251, average HR 132, and a yellow DONE button">

## Laps and beeps

- Every interval is its own lap in your ride file, so intervals.icu and Strava show them
  separately.
- Short beeps play in the last 3 seconds of each interval.

## With a route

When you follow a route the Karoo shows the route under the field and makes it shorter. The
watts shrink to fit; everything else stays the same.

<img src="images/with-route.png" width="240" alt="Workout screen with a route loaded: the field is shorter, the turn pill below it">
