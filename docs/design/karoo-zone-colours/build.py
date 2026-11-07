"""Inline the Karoo frame, base screenshots and karoo-device.js into mockup.html.

Run: python3 build.py  (from this folder)
"""
import base64, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'          # Karoo/design-assets
def data_url(p, mime='image/png'): return f'data:{mime};base64,' + base64.b64encode(p.read_bytes()).decode()
html = (here / 'mockup.src.html').read_text()
html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
for name in ['screen', 'workouts']:
    for key in ['light', 'dark']:
        html = html.replace(f'__{name}_{key}__', data_url(here / f'base-{name}-{key}.png'))
(here / 'mockup.html').write_text(html)
print('wrote', here / 'mockup.html')
