"""final.html: every BetterIntervals screen in Karoo style from the field gallery, light and dark. Run from this folder."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'
gallery = here.parent / 'gallery' / 'screens'
def data_url(p): return 'data:image/png;base64,' + base64.b64encode(p.read_bytes()).decode()
rows = [('Before you start', 'ready'), ('Workout (the power gauge, the default)', 'gauge'),
        ('Workout, number instead of gauge', 'karoo'), ('Free ride', 'free'),
        ('Free ride, first lap (no last lap yet)', 'free-first-lap'), ('Workout done', 'done')]
screens = [[title, {t: data_url(gallery / f'{name}-full-{t}.png') for t in ('light', 'dark')}] for title, name in rows]
html = (here / 'final.src.html').read_text()
html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
html = html.replace('/*__SCREENS__*/', json.dumps(screens))
(here / 'final.html').write_text(html); print('wrote final.html')
