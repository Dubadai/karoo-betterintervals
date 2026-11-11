# Settings kit

Settings built from the shared settings kit (karoo-starter `SettingsKit.kt`, design and rules in
`karoo-starter/docs/design/settings-kit/`), shot on our test Karoo, light (settings pictures are light only).

`settings-light.png`: top of the page with the Example card, Top row, and the end of the page:
intervals.icu account (a nav row), then the rows every app ends with: Update notices (a switch, was
Off / On), Show introduction, and the version row (name, Version, GitHub address: one line each).

What changed from BetterIntervals' own pieces: the kit's order (look, what the screen shows,
smoothing, account, app rows), Update notices as a switch, the dark page black (was #0B0B0B), and the
account's "Connected" in the kit's green.

Kit update 4: the kit's own heading icons (`ic_settings_*`), `ic_settings_intro` for Show introduction,
`updateHelp` for the Control Center text, and a debug-only `.DebugSettings` alias so adb can open
Settings on the emulator (`am start -n com.daviddokakis.karoointerval/.DebugSettings`).
