"""review.html: every shot in shots/, light left and dark right, one row per screen."""
import base64, pathlib
here = pathlib.Path(__file__).parent; d = here / 'shots'
rows = [('01-erg-a', '1 · Workout: no ERG (all screens), no pause (indoors), icons close to their numbers, a small target under the watts (green icon), ⓘ top right, right tile rotating (calories, dots)'),
('03-ramp', '3 · A ramp: a green ramp icon instead of the target icon, written start-end like a range (141-181 W); NEXT UP writes 141 → 181 W'),
('04-info', '4 · The whole workout so far (tap ⓘ): name and where you are, 8 tiles, no chart; tap anywhere (×) to go back'),
('05-lap-ride', '5 · The lap tile: a tap cycles LAP AVG → LAP NP → RIDE AVG'),
('06-done-a', '6 · Done indoors, ride paused, A: a smaller check below the Karoo\'s PAUSE bar; no "ride is still recording"'),
('07-done-b', '7 · Done indoors, ride paused, B: a small check in front of WORKOUT COMPLETE'),
('07-done-final', '7 · Picked: done A, "Close summary" (calm) on a black button; the Karoo\'s play button closes it too, all of it above the Karoo\'s own end / resume buttons'),
('12-zones-karoo', '11 · Zone colours: Karoo (its own climb1..7); ramps fade from zone to zone'),
('12-zones-zwift', '11b · Zwift (6 zones: Z7 as Z6; colours approximate, to check)'),
('12-zones-icu', '11c · intervals.icu (its defaults; could use the rider\'s own colours from intervals.icu)'),
('13-zones-settings', '11d · Settings › Zone colours'),
('08-tiles', '8 · Settings: which metrics the right tile rotates through (any of them, the top row\'s too)'),
('09-tiles-end', '8 · …the end of the list'),
('10-update', '9 · Workouts page during a ride: changed on intervals.icu; neither button restarts, the box does'),
('11-update-end', '10 · Scale workout: the current interval\'s new target in a small card above the %')]
u = lambda p: 'data:image/png;base64,' + base64.b64encode(p.read_bytes()).decode()
h = ['<!doctype html><html><head><meta charset="utf-8"><title>Indoor Ride Notes</title><style>body{margin:0;background:#2A2A2A;color:#eee;font:16px -apple-system,sans-serif}main{max-width:820px;margin:0 auto;padding:24px 16px}.r{display:flex;gap:16px;margin:8px 0 32px}.r img{width:380px;max-width:48%;height:auto}h2{font-size:16px;font-weight:500;margin:0}</style></head><body><main><h1 style="font-size:22px">BetterIntervals · indoor ride notes, mock round 1</h1>']
for f, t in rows:
    imgs = [p for th in ('light', 'dark') for p in sorted(d.glob(f'??-{f[3:]}-{th}.png'))[:1]]
    h.append(f'<h2>{t}</h2><div class="r">' + ''.join(f'<img src="{u(p)}">' for p in imgs) + '</div>')
h.append('</main></body></html>')
(here / 'review.html').write_text(''.join(h))
