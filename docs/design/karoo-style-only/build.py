"""Inline the Karoo frame, karoo-device.js and the emulator shots into mockup.html. Run from this folder."""
import base64, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'
def data_url(p, mime='image/png'): return f'data:{mime};base64,' + base64.b64encode(p.read_bytes()).decode()
html = (here / 'mockup.src.html').read_text()
html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
for key, name in [('L1', '01-settings-now-light'), ('L2', '02-settings-below-light'), ('D1', '03-settings-now-dark'), ('D2', '04-settings-below-dark')]:
  html = html.replace(f'__{key}__', data_url(here / 'device' / f'{name}.png'))
(here / 'mockup.html').write_text(html); print('wrote mockup.html')
