# Obsidian Note Widget

Android home-screen widget that renders an Obsidian note straight from the
vault folder (Storage Access Framework). Kotlin, AGP 8.5, Gradle 8.9,
minSdk 31, targetSdk 34. This is **not** an Obsidian plugin: there is no
`package.json`, `manifest.json` or TypeScript here.

## Layout

- `core/` — pure Kotlin, no Android types: markdown parser, inline styler,
  task toggler, `Outline` (fold/hide/count rules). Unit-tested in `app/src/test`.
- `widget/` — AppWidgetProvider, RemoteViews factory, the trampoline activity
  for row taps, per-widget prefs, the 15-minute refresh worker.
- `config/`, `setup/`, `editor/` — the app screens (widget settings, vault
  setup + widget list, popup viewer/editor).
- `vault/` — SAF file access.

## Check every change

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

- Needs JDK 17+ (`JAVA_HOME` or `java` on PATH) and Android SDK 34
  (`local.properties` or `ANDROID_HOME`).
- Add `--offline` when the network is unavailable; the Gradle cache holds
  every dependency, lint's included.
- Lint errors fail the build; the ~24 warnings don't. CI runs the same command
  (`.github/workflows/check.yml`).

## Widget rules (each one broke a release)

- Widget layouts (`widget_note.xml`, `row_task.xml`, `row_text.xml`) may only
  use RemoteViews-supported classes. A plain `<View>` spacer made launchers
  reject the widget ("Couldn't add widget", v0.4.3–v0.4.4). Lint's
  `RemoteViewLayout` check is fatal, so even `assembleRelease` stops on it.
- `WidgetConfigActivity` (the launcher's configure flow) keeps the default
  taskAffinity, or `setResult()` never reaches the launcher and the widget is
  removed. The ⚙ button and the app's widget list use `WidgetReconfigActivity`.
- Row taps go through `WidgetActionActivity` (empty taskAffinity, no display)
  so folding a task never brings the app's task forward.
- `RemoteViews.setViewPadding` sets all four sides; keep the vertical padding
  or the last line of a wrapped row gets clipped.
- Put rules that can be unit-tested in `core/` rather than in RemoteViews code.

## Device check

`scripts/device-check.sh` restarts adb once if the device shows
"unauthorized", installs the debug APK with `install -r` (existing widgets
survive), resets `/sdcard/Documents/TestVault` from `tools/test-vault/` (plus
today's daily note), taps the first task and checks that `Today.md` changed,
opens the popup and the settings, and saves screenshots to
`build/device-check/<time>/`. Look at the screenshots: layout problems only
show on a device. It needs a widget showing `TestVault/Today.md` on the
current home page. A device with the release build installed rejects the
debug APK (different signature); the script stops instead of uninstalling,
because uninstalling removes the user's widgets.

## Releases

`scripts/release.sh --notes <file>` — reads the version from
`app/build.gradle.kts`, refuses a dirty tree, a non-main branch, an existing
tag or a versionCode that didn't go up, runs the check above, builds and
verifies the signed APK against the pinned release certificate, then asks
before it pushes and creates the GitHub release. `--dry-run` stops before
publishing. Bump `versionCode` and `versionName` together in one commit
first. Signing needs `keystore.properties` + `release.keystore` in the repo
root (git-ignored).
