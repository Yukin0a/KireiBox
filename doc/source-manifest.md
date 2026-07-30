# KireiBox source manifest

## Included

- KireiBox Compose launcher UI and Apple-style interaction system.
- Kirikiri, ONScripter, Tyrano and Artemis engine module; no other game format
  is scanned or dispatched.
- YukiHub metadata source clients and source-keyed cache controller.
- YukiHub ranked cover candidates, three-step bounded title planning,
  confidence-based automatic application and explicit low-confidence review;
  no Rinne cover queue or database is linked.
- Engine-specific KireiBox default covers for KRKR, ONS, Tyrano, Artemis and
  unknown/Android fallback states.
- YukiHub KRKR settings and launch bridge: engine version, compatibility mode,
  explicit XP3 selection, game-local `savedata` preflight and per-game
  Kirikiroid preference files. The retired central independent-save redirection
  is not part of the runtime.
- Android package launcher cards populated from the installed launcher
  activities reported by PackageManager; no storage permission is involved.
- Monotonic foreground-time journals for KRKR, ONScripter, Tyrano and Artemis;
  Android package cards retain only a successful launch count.
- Bounded cover work (three searches and two downloads), cached metadata alias
  search, configurable dashboard spotlight sources and opt-in redacted logs.
- Responsive Compose shell, continuous action sheets, per-game KRKR editor and
  non-destructive YukiHub `hidden`/undo library editing.
- User-provided pink chibi application icon, packaged as adaptive, legacy and
  Android 12+ splash assets from `app/src/main/res/drawable/ic_launcher_foreground.png`.

## Metadata sources

KireiBox uses the YukiHub source keys and labels:

`vndb` / VNDB, `bangumi` / Bangumi, `bangumi_mirror` / Bangumi镜像,
`ymgal` / 月幕Gal, `hikarinagi` / Hikarinagi.

Kungal is not included in KireiBox's source selector.

## Excluded

AI, translation, OCR, social, account, cloud/WebDAV sync, community tools,
PSP/GameHub/RPGMaker/Ren'Py/Godot launchers, legacy save screens, the global font
scale hook and external backup import formats have been removed from the app
runtime. RinneMobile remains a local visual/interaction reference only and is
not a linked module or artifact.

The historical `assets/rtp/sf.sf2` checkout is excluded by both Gradle packaging
and the AssetManager ignore pattern. It is not part of the public Git index or
the inspected Debug APK.

Historical RPGMaker hook assets and the unreferenced `libBugly.so` are also
excluded from the shipped runtime. Three KRKR engine variants and three Artemis
variants remain intentionally included for compatibility.

## Licenses

The upstream GPL-3 license remains at the project root. Engine-specific
provenance, SHA-256 values and upstream license links are recorded in
`THIRD_PARTY_NOTICES.md`.
