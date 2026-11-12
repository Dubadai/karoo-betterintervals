# Native tiles and Workouts page spacing (2026-11-12)

- **Tiles in the Karoo's own style.** A tile with two or more metrics used to drop its icon and show
  dots down its left edge for which metric it showed. Now every tile shows the metric's own icon and
  label, as the Karoo draws its fields; a tap still goes to the next metric.
- **Workouts page spacing.** The divider line touched the Scale workout stepper and the Remove workout
  button: the kit's divider sets its own top margin (0, or 12 dp under a switch row) and drops one the
  app sets. The pieces above now keep the room themselves: 14 dp under the stepper (like the kit's
  segmented control), 16 dp under Remove workout.

Pictures (field gallery, the app's own code; Sky / Night): `01-before-light|dark.png` (dots),
`02-after-light|dark.png` (icons). `03-workouts-spacing-before-after-dark.png`: under Remove workout on
the test Karoo, before (left) and after (right).
