"""Inline the Karoo frame, fonts, base screens and karoo-device.js into mockup.html.

Run: python3 build.py  (from this folder)
"""
import base64, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'          # Karoo/design-assets
def data_url(p, mime='image/png'): return f'data:{mime};base64,' + base64.b64encode(p.read_bytes()).decode()
for src in sorted(here.glob('*.src.html')):
  html = src.read_text()
  html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
  html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
  html = html.replace('__FONT_COND__', data_url(assets / 'fonts' / 'IBMPlexSansCondensed-Medium.otf', 'font/otf'))
  html = html.replace('__FONT_SEMI__', data_url(assets / 'fonts' / 'IBMPlexSans-SemiBold.ttf', 'font/ttf'))
  html = html.replace('__FONT_REG__', data_url(assets / 'fonts' / 'IBMPlexSans-Regular.ttf', 'font/ttf'))
  html = html.replace('__NATIVE__', data_url(here / 'native-light.png'))
  for key in ['light', 'dark']:
    html = html.replace(f'__SHOT_{key}__', data_url(here / f'base-{key}.png'))
    html = html.replace(f'__ROUTE_{key}__', data_url(here / f'route-{key}.png'))
  out = here / src.name.replace('.src.html', '.html')
  out.write_text(html)
  print('wrote', out)
