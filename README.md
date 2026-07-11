# Obsidian Note Widget

Android home-screen widgets for Obsidian notes: rendered markdown, tappable
task checkboxes, and a popup editor — without opening Obsidian. Works by
reading/writing vault files directly; Obsidian Sync picks changes up next
time Obsidian runs.

## Install
1. Copy `app-release.apk` to the phone (Drive, cable, …) and tap it.
   Allow "install unknown apps" for your file manager when prompted.
2. Open **Obsidian Widget** → *Choose vault folder* → pick your vault.
3. Long-press the home screen → Widgets → Obsidian Widget → pick a note.

## Notes
- The vault must be in shared storage (a folder a file manager can see),
  not Obsidian's private app storage.
- Task checkboxes toggle `- [ ]`/`- [x]` in the file immediately.
- ✏️ opens the popup editor (raw markdown). ◆ opens the note in Obsidian.
- Widget refreshes on interaction, when the app opens, and every 15 min.

## Build
`./gradlew assembleRelease` — needs Android SDK 34 (`local.properties`)
and `keystore.properties` + `release.keystore` (local-only personal
signing key; regenerate with `keytool -genkeypair` if lost, then
uninstall/reinstall on the phone).
