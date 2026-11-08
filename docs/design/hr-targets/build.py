"""Inline the Karoo frame, fonts, karoo-device.js and base/ screens into mockup.html. Run from this folder."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent
assets = here.parents[3] / 'design-assets'
def data_url(p, mime='image/png'): return f'data:{mime};base64,' + base64.b64encode(p.read_bytes()).decode()
base = {p.stem.replace('-full', ''): data_url(p) for p in sorted((here / 'base').glob('*.png'))}
html = (here / 'mockup.src.html').read_text()
html = html.replace('/*__KAROO_DEVICE_JS__*/', (assets / 'karoo-device.js').read_text())
html = html.replace('__FRAME__', data_url(assets / 'karoo-3-frame.png'))
html = html.replace('__FONT_COND__', data_url(assets / 'fonts' / 'IBMPlexSansCondensed-Medium.otf', 'font/otf'))
html = html.replace('__FONT_SEMI__', data_url(assets / 'fonts' / 'IBMPlexSans-SemiBold.ttf', 'font/ttf'))
html = html.replace('__FONT_MED__', data_url(assets / 'fonts' / 'IBMPlexSans-Medium.ttf', 'font/ttf'))
html = html.replace('__FONT_BOLD__', data_url(assets / 'fonts' / 'IBMPlexSans-Bold.ttf', 'font/ttf'))
html = html.replace('__FONT_REG__', data_url(assets / 'fonts' / 'IBMPlexSans-Regular.ttf', 'font/ttf'))
html = html.replace('/*__BASE__*/', json.dumps(base))
(here / 'mockup.html').write_text(html); print('wrote mockup.html')
