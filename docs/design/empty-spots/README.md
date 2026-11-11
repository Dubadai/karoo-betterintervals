# None for every spot (mock, 2026-11-11)

Every spot on the workout screen can be None, for riders who'd rather have it empty and the watts
bigger. `mockup.src.html` draws it on the app's own render (build with `./make.sh`, pictures with
`shoot.sh`; overviews in `shots/`).

- **Top row:** None / One / Two. None: only the gear and ⓘ stay, in their corners.
- **Under the watts:** None (was "Nothing").
- **Left and right tile:** every switch can be off. One tile off: the other goes full width. Both
  off: no tile row.
- **The watts take the room that's left:** from just under the gear and ⓘ (never under them) down to
  the interval row, as big as fits the height and the width. With no tiles the interval row sits
  right above NEXT UP.
