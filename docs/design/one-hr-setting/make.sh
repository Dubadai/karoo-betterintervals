#!/bin/bash
# Build mockup.html (design-assets/tools/mock/build.py), then inline Relative, the Karoo's number font.
set -e
here="$(cd "$(dirname "$0")" && pwd)"; karoo="$(cd "$here/../../../.." && pwd)"
python3 "$karoo/design-assets/tools/mock/build.py" "$here" >/dev/null
python3 - "$here/mockup.html" "$karoo/design-assets/fonts/karoo-system/Relative12-Regular.otf" <<'PY'
import base64, pathlib, sys
p = pathlib.Path(sys.argv[1]); f = base64.b64encode(pathlib.Path(sys.argv[2]).read_bytes()).decode()
p.write_text(p.read_text().replace('__RELATIVE__', 'data:font/otf;base64,' + f))
PY
