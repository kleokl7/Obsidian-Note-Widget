# Obsidian Note Widget

Android home-screen widgets for Obsidian notes: rendered markdown, tappable
task checkboxes, and a popup viewer/editor — without opening Obsidian. Works
by reading/writing vault files directly; Obsidian Sync picks changes up next
time Obsidian runs.

## Features

- **Rendered markdown** on the home screen: headings, bold/italic, links,
  `==highlights==`, inline code, quotes, bullets, task lists.
- **Tappable tasks** — toggling writes `- [ ]`/`- [x]` to the file
  immediately. Custom task states from the Tasks plugin render with their
  own glyphs (`/` in progress, `-` cancelled, `>` forwarded).
- **Folding** — tasks with sub-items fold; tapping a heading collapses its
  whole section; the ⊖/⊕ header button collapses or expands everything.
  Headings show an open-task count for their section.
- **Popup viewer/editor** — tapping a text row opens the note rendered, with
  tappable checkboxes; an Edit button switches to raw markdown.
- **Daily-note mode** — a widget can follow today's `YYYY-MM-DD.md`
  automatically.
- **Theming** — warm paper light mode and warm charcoal dark mode (the
  [Claude Code Obsidian Theme](https://github.com/kleokl7/Claude-Code-Obsidian-Theme)
  palette), following the device or forced per widget.
- ✏️ deep-links into the note in the Obsidian app; ⚙ opens the widget's
  settings.

## Install

1. Download the APK from the [Releases](../../releases) page onto your phone
   and tap it. Allow "install unknown apps" for your file manager when
   prompted.
2. Open **Obsidian Widget** → *Choose vault folder* → pick your vault.
3. Long-press the home screen → Widgets → Obsidian Widget → pick a note.

The vault must be in shared storage (a folder a file manager can see), not
Obsidian's private app storage (Settings → About → Vault location on
Android).

## Per-widget settings

Open with the widget's ⚙ button, or from the app's widget list (one card
per active widget). Each widget independently has:

- Theme: System / Light / Dark
- Background opacity (fades the card, not the text)
- Text size: Small / Default / Large
- Hide completed tasks (includes cancelled tasks and their sub-items)
- Open-task count in the widget title (off by default)
- The displayed note, or "Show today's daily note"

Widgets refresh on interaction, when the app opens, and every 15 minutes.
In System theme mode, a device light/dark flip is picked up on the next
refresh.

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
