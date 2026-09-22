# Obsidian Note Widget — Design

**Date:** 2026-07-10
**Status:** Approved (brainstorming session)

## What it is

A standalone Android app (Kotlin, min SDK 31 / Android 12) whose only job is
home-screen widgets that display Obsidian notes and let the user interact with
them: scroll, toggle task checkboxes, and edit text via a popup editor — all
without opening the Obsidian app.

The app never talks to Obsidian directly. It reads and writes the same
markdown files in the on-device vault folder; Obsidian Sync propagates changes
whenever Obsidian next runs.

This is a separate project from the Obsidian theme in this repo. The app
lives in its own directory/repo (`Obsidian-Note-Widget`); only this spec and
the implementation plan live here.

## Decisions made

| Question | Decision |
|---|---|
| Widget shape | Rendered note + tappable checkboxes + popup editor over the home screen |
| Note selection | Per widget instance, chosen from a searchable vault browser at placement time; multiple widgets allowed |
| Vault access | Storage Access Framework folder picker (persistable URI permission); vault is a local folder kept in sync by Obsidian Sync |
| Rendering | Rendered markdown (reading-view-like), not raw text |
| Distribution | Signed APK built on the Mac, sideloaded to the phone |
| Framework | Classic AppWidgetProvider + RemoteViewsService (ListView). Chosen over Jetpack Glance because Glance text cannot render per-span inline styles (bold/italic within a line), which the rendering requirement needs; RemoteViews TextViews accept SpannableString. |

## Components

### 1. Vault access layer
- One-time setup: app asks the user to pick the vault folder via the SAF
  folder picker; persists the grant with `takePersistableUriPermission`.
- Validates the pick by checking for `.obsidian/` and `.md` files; clear
  error state if the folder is unreachable (e.g. app-private storage).
- Exposes: list markdown files (recursive), read file, write file, get
  modification time.

### 2. Markdown model + renderer
- Parses a note into a list of blocks: heading (1–6), paragraph, bullet item,
  task item (checked/unchecked, with nesting depth), quote, code block,
  horizontal rule.
- Inline styling: bold, italic, highlight, inline code, strikethrough.
  Wikilinks `[[...]]` and markdown links render as styled text (accent color,
  no navigation). Syntax characters are hidden.
- Pure Kotlin, no Android dependencies → unit-testable.

### 3. The widget (Glance `AppWidget`)
- Scrollable `LazyColumn`, one row per block.
- Header bar: note name, ✏️ edit button, Obsidian icon (deep link
  `obsidian://open?vault=...&file=...`).
- Task rows: checkbox tap → toggle `- [ ]` ↔ `- [x]` on that source line,
  write the file, refresh the widget immediately.
- Any other row tap → opens the popup editor.
- Refresh triggers: user interaction, widget update broadcast, periodic
  `WorkManager` job (15 min — Android's floor), and whenever one of the app's
  activities resumes. (`FileObserver` doesn't work on SAF folder grants, so
  there is no file-watch; some lag after a background sync is accepted.)

### 4. Widget configuration activity
- Launched on widget placement (`ACTION_APPWIDGET_CONFIGURE`).
- Searchable list of vault notes; selection stored per `appWidgetId`
  (SharedPreferences — RemoteViews factories need synchronous reads).
  Re-configurable by tapping the widget header title.

### 5. Popup editor activity
- Dialog-themed activity floating over the home screen.
- Plain `EditText` with the raw markdown, Save / Cancel.
- Conflict safety: records the file's mtime on open; if the file changed
  before save, warns (keep mine / reload) instead of silently overwriting.
- Save writes the file and triggers widget refresh.

## Checkbox-toggle correctness

Toggling must edit the exact source line, not a re-serialized document:
the parser records each block's source line number; the toggle operation
re-reads the file, verifies the target line still matches the expected task
text, flips the `[ ]`/`[x]` marker in place, and writes back. If the line
moved/changed (concurrent sync), re-parse and refresh instead of writing.

## Out of scope (v1)

- Images, embeds, dataview, callout rendering
- Following links inside the widget
- WYSIWYG/rendered editing (popup shows raw markdown)
- Creating new notes
- Pixel-matching the user's Obsidian theme (use a tasteful dark/light default)

## Testing

- JUnit: markdown parser, inline styler, checkbox line-toggle (incl. nesting,
  unicode, CRLF, line-moved conflict case).
- On-device: manual verification of widget placement, config, scrolling,
  checkbox toggle round-trip with Obsidian, popup editor save + conflict warning,
  deep link into Obsidian.

## Deliverable

A Gradle project producing a signed release APK for sideloading, plus a short
README covering setup (folder grant, placing widgets) and rebuild instructions.
