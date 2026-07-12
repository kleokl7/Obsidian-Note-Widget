# Obsidian Note Widget

Android home-screen widgets for Obsidian notes: rendered markdown, tappable
task checkboxes, and a popup editor — without opening Obsidian. Works by
reading/writing vault files directly; Obsidian Sync picks changes up next
time Obsidian runs.

## Install
1. Download the APK from the [Releases](../../releases) page onto your phone
   and tap it. Allow "install unknown apps" for your file manager when
   prompted.
2. Open **Obsidian Widget** → *Choose vault folder* → pick your vault.
3. Long-press the home screen → Widgets → Obsidian Widget → pick a note.

## Notes
- The vault must be in shared storage (a folder a file manager can see),
  not Obsidian's private app storage.
- Task checkboxes toggle `- [ ]`/`- [x]` in the file immediately.
- Each widget has a System / Light / Dark theme setting (warm paper light,
  warm charcoal dark — the [Claude Code Obsidian Theme](https://github.com/kleokl7/Claude-Code-Obsidian-Theme)
  palette). Pick it when adding the widget, or long-press the widget →
  edit/settings to change it later. In System mode the widget follows the
  device theme; after a device theme flip it catches up on the next refresh
  (interaction, opening the app, or the 15-min cycle).
- ✏️ opens the popup editor (raw markdown). ◆ opens the note in Obsidian.
- Widget refreshes on interaction, when the app opens, and every 15 min.

## Build
Needs Android SDK 34 (point `local.properties` at it, or set `ANDROID_HOME`).

- `./gradlew assembleDebug` — works out of the box, signed with the debug key.
- `./gradlew assembleRelease` — needs your own signing key. Generate one with
  `keytool -genkeypair -keystore release.keystore -alias <alias>` and create a
  `keystore.properties` in the project root (both are git-ignored):

  ```properties
  storeFile=release.keystore
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```

  Note: releases signed with a different key can't upgrade an existing
  install — uninstall/reinstall on the phone.

## License
[MIT](LICENSE)

This is an unofficial community project, not affiliated with or endorsed by
Obsidian.
