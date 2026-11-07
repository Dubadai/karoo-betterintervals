# Update notice

Tell the rider on the Karoo when a newer BetterIntervals release is on GitHub, and get them to
the Karoo's own UPDATE button.

**Status:** option B agreed by David (round 1), built and tested on David's Karoo 2026-11-07. Mockup: `mockup.html` (built from `mockup.src.html`
with `python3 build.py`), also at https://claude.ai/artifact/9nH7QbAPk4MDgVJ6Pr7J56

## Findings (2026-11-07, David's Karoo, ROM 1.665.2548.20)

- The Karoo's Settings › Apps › <app> page (`io.hammerhead.action.APP_INFO`, extra
  `packageName`) shows About, Release notes and an UPDATE button for side-loaded apps that name a
  `manifest.json` (`io.hammerhead.karooext.MANIFEST_URL` meta-data, `KarooAppManifest`). UPDATE
  installs `latestApkUrl` on the Karoo itself.
- The Karoo fetches that manifest only when the page is opened: it never tells the rider.
- `SystemNotification(style = UPDATE)` puts a card in Control Center › Notifications.

## Proposal (option B)

Check GitHub's `releases/latest/download/manifest.json` when the Karoo starts and once a day,
only when no ride is recording; post one card per new version; tapping it opens the Karoo's
App info page.

## Screenshots (`device/`)

- `02-cc-left-light.png`: Control Center › Notifications (myWindsock card)
- `03-karoo-app-info-update-light.png`: App info page of an app with a manifest and a newer version (kDouble)
- `04-karoo-app-info-no-manifest-light.png`: App info page of BetterIntervals 0.5.1 (no manifest)
- `11-notification-card-light.png`: the real card (Karoo's purple UPDATE style; the header wraps)
- `12-tap-opens-app-info-light.png`: tapping it opens the Karoo's page, reading our (test) manifest
- `14-updating-light.png`: UPDATE pressed: the Karoo downloads `betterintervals.apk` from GitHub

## Test (2026-11-07)

Release-key build (versionCode 7) with `-PmanifestUrl=http://localhost:8791/manifest.json`,
`adb reverse tcp:8791 tcp:8791`, a fake manifest saying 0.5.3 (99) with `latestApkUrl` = the real
release APK. Installed over 0.5.1 (data kept). About 60 s after start: "installed 7, latest 99",
card shown; tap → Karoo page with UPDATE; UPDATE installed the real v0.5.2 in about 4 s, data kept.
Not seen yet: the card in dark mode, whether it clears itself after updating, the Settings row on
the device.
