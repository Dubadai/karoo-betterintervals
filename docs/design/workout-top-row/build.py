"""Inline the Karoo frame, karoo-device.js and the base screens (base/) into mockup.html. Run from this folder."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'
def data_url(p): return 'data:image/png;base64,' + base64.b64encode(p.read_bytes()).decode()
base = {}
for p in sorted((here / 'base').glob('*.png')):
    base[p.stem.replace('-full', '')] = data_url(p)
html = (here / 'mockup.src.html').read_text()
html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
html = html.replace('/*__BASE__*/', json.dumps(base))
(here / 'mockup.html').write_text(html); print('wrote mockup.html')
