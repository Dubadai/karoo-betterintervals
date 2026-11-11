# Needle and number from one source (proposal)

Ride 2026-11-10, 12:55:27, interval 15/16, target 178–218 W. The number showed 232 W (3 s power,
Settings › Power) while the needle sat inside the green: since commit 1e98134 the needle reads the
Karoo's own 10 s average power (about 207 W at that moment).

- `01-now-vs-proposed.png`: left, the ride as it was; right, the same moment with the needle at the
  number's 232 W, past the green.
- `now-ride-125527.png`: the screen shot from the ride, unchanged.
- `proposed-needle-follows-number.png`: the same shot with the needle redrawn where 232 W sits on
  the arc (178–218 band, arc 158–238 W).

Proposal: the needle reads the same power as the number (Settings › Power: Instant / 3 s / 5 s /
10 s), so they never disagree. Anyone who finds the needle too lively picks 5 s or 10 s.
