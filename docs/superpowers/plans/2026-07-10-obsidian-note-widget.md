# Obsidian Note Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Android app providing home-screen widgets that display a rendered Obsidian note with tappable task checkboxes and a popup markdown editor, reading/writing the vault files directly.

**Architecture:** Classic `AppWidgetProvider` + `RemoteViewsService` ListView widget (RemoteViews TextViews accept SpannableString, which we need for inline bold/italic). A pure-Kotlin `core` package (parser, inline styler, task toggler) is fully unit-tested; the Android layer (SAF vault access, widget, config activity, dialog editor) is thin glue verified on-device.

**Tech Stack:** Kotlin 2.0, AGP 8.5.2, Gradle 8.9, compileSdk/targetSdk 34, minSdk 31, androidx documentfile + work-runtime, Material Components, JUnit 4. No Compose, no Glance, no DataStore.

**Spec:** `docs/superpowers/specs/2026-07-10-obsidian-note-widget-design.md` (in the Claude-Code-Obsidian-Theme repo).

## Global Constraints

- Project lives at `~/Desktop/Obsidian-Note-Widget` (its own git repo — NOT inside the theme repo). Executor sessions may need to approve writes to this directory.
- `applicationId` / namespace: `com.kleanthi.obsidianwidget`
- minSdk 31, compileSdk 34, targetSdk 34, JVM target 17
- App label: **Obsidian Widget**
- The app never uses `MANAGE_EXTERNAL_STORAGE` or any dangerous permission — vault access is via SAF persisted URI grant only.
- All core logic (`core/` package) must stay free of Android imports so it runs under plain JUnit.
- Every file write preserves the file's original newline style (CRLF vs LF).
- Commit after every task (frequent small commits within tasks are fine too).
- Run tests with `./gradlew test`, build with `./gradlew assembleDebug` — both from the project root.

---

### Task 1: Toolchain + project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, `.gitignore`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/setup/SetupActivity.kt` (placeholder shell; Task 5 fills it in)
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/drawable/ic_launcher_fg.xml`, `app/src/main/res/values/colors.xml`

**Interfaces:**
- Consumes: nothing
- Produces: a building Android project; package `com.kleanthi.obsidianwidget`; theme `Theme.ObsidianWidget` and `Theme.ObsidianWidget.Dialog` used by all later activities.

- [ ] **Step 1: Install the Android toolchain (skip pieces that already exist)**

```bash
# JDK 17+
java -version 2>&1 | head -1   # if missing or <17: brew install --cask temurin@21

# Android command-line tools + Gradle
brew install --cask android-commandlinetools
brew install gradle

# SDK packages (sdkmanager comes from android-commandlinetools)
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
yes | sdkmanager --licenses
```

Expected: sdkmanager reports the packages installed. Note the SDK root (`sdkmanager --version` output path, typically `/opt/homebrew/share/android-commandlinetools`).

- [ ] **Step 2: Create the project directory and git repo**

```bash
mkdir -p ~/Desktop/Obsidian-Note-Widget && cd ~/Desktop/Obsidian-Note-Widget && git init
```

- [ ] **Step 3: Write the Gradle scaffold**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "ObsidianNoteWidget"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}
```

`gradle.properties`:
```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
```

`local.properties` (adjust to the SDK root found in Step 1):
```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

`.gitignore`:
```
.gradle/
build/
local.properties
keystore.properties
*.keystore
.DS_Store
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kleanthi.obsidianwidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kleanthi.obsidianwidget"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Write manifest, placeholder activity, and resources**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.ObsidianWidget"
        android:allowBackup="true">
        <activity android:name=".setup.SetupActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/kleanthi/obsidianwidget/setup/SetupActivity.kt` (placeholder — replaced in Task 5):
```kotlin
package com.kleanthi.obsidianwidget.setup

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Obsidian Widget — setup coming in Task 5" })
    }
}
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Obsidian Widget</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.ObsidianWidget" parent="Theme.Material3.DayNight.NoActionBar" />
    <style name="Theme.ObsidianWidget.Dialog" parent="Theme.Material3.DayNight.Dialog">
        <item name="android:windowSoftInputMode">adjustResize</item>
    </style>
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="icon_bg">#1E1B2E</color>
    <color name="accent">#8B7EC8</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_fg.xml` (diamond, Obsidian-ish):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#8B7EC8"
        android:pathData="M54,26 L74,48 L60,82 L46,82 L34,50 Z" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_bg" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
```

- [ ] **Step 5: Generate the Gradle wrapper and build**

```bash
cd ~/Desktop/Obsidian-Note-Widget && gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "chore: Android project scaffold (Kotlin, AGP 8.5.2, minSdk 31)"
```

---

### Task 2: Markdown block parser (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/core/MarkdownParser.kt`
- Test: `app/src/test/java/com/kleanthi/obsidianwidget/core/MarkdownParserTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class BlockType { HEADING, PARAGRAPH, BULLET, TASK, QUOTE, CODE, RULE }`
  - `data class Block(val type: BlockType, val text: String, val level: Int = 0, val checked: Boolean = false, val indent: Int = 0, val sourceLine: Int)` — `sourceLine` is the 0-based line index in the ORIGINAL file (frontmatter lines included in the count); `text` is the block content with block-level syntax stripped but inline markdown intact.
  - `object MarkdownParser { fun parse(markdown: String): List<Block> }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kleanthi/obsidianwidget/core/MarkdownParserTest.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test fun `heading levels and text`() {
        val b = MarkdownParser.parse("## Hello **world**")
        assertEquals(1, b.size)
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(2, b[0].level)
        assertEquals("Hello **world**", b[0].text)
        assertEquals(0, b[0].sourceLine)
    }

    @Test fun `unchecked and checked tasks with nesting`() {
        val md = "- [ ] buy milk\n  - [x] sub done\n- [X] caps also checked"
        val b = MarkdownParser.parse(md)
        assertEquals(3, b.size)
        assertEquals(BlockType.TASK, b[0].type)
        assertEquals(false, b[0].checked)
        assertEquals("buy milk", b[0].text)
        assertEquals(0, b[0].indent)
        assertEquals(true, b[1].checked)
        assertEquals(1, b[1].indent)
        assertEquals(1, b[1].sourceLine)
        assertEquals(true, b[2].checked)
    }

    @Test fun `bullets quotes rules paragraphs`() {
        val md = "para here\n\n- a bullet\n> quoted\n---"
        val b = MarkdownParser.parse(md)
        assertEquals(listOf(BlockType.PARAGRAPH, BlockType.BULLET, BlockType.QUOTE, BlockType.RULE),
            b.map { it.type })
        assertEquals("a bullet", b[1].text)
        assertEquals("quoted", b[2].text)
    }

    @Test fun `frontmatter is skipped but line numbers stay absolute`() {
        val md = "---\ntags: [x]\n---\n# Title"
        val b = MarkdownParser.parse(md)
        assertEquals(1, b.size)
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(3, b[0].sourceLine)
    }

    @Test fun `code fences become one block and contents are not parsed`() {
        val md = "```\n- [ ] not a task\ncode line\n```\nafter"
        val b = MarkdownParser.parse(md)
        assertEquals(2, b.size)
        assertEquals(BlockType.CODE, b[0].type)
        assertTrue(b[0].text.contains("not a task"))
        assertEquals(BlockType.PARAGRAPH, b[1].type)
        assertEquals(4, b[1].sourceLine)
    }

    @Test fun `crlf input parses like lf`() {
        val b = MarkdownParser.parse("# A\r\n- [ ] t\r\n")
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(BlockType.TASK, b[1].type)
        assertEquals(1, b[1].sourceLine)
    }

    @Test fun `blank lines produce no blocks`() {
        assertEquals(0, MarkdownParser.parse("\n\n   \n").size)
    }

    @Test fun `star and plus bullets and tasks`() {
        val b = MarkdownParser.parse("* [ ] star task\n+ plus bullet")
        assertEquals(BlockType.TASK, b[0].type)
        assertEquals(BlockType.BULLET, b[1].type)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.kleanthi.obsidianwidget.core.MarkdownParserTest"`
Expected: FAIL — unresolved reference `MarkdownParser`.

- [ ] **Step 3: Implement the parser**

`app/src/main/java/com/kleanthi/obsidianwidget/core/MarkdownParser.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

enum class BlockType { HEADING, PARAGRAPH, BULLET, TASK, QUOTE, CODE, RULE }

data class Block(
    val type: BlockType,
    val text: String,
    val level: Int = 0,
    val checked: Boolean = false,
    val indent: Int = 0,
    val sourceLine: Int
)

object MarkdownParser {
    private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
    private val taskRe = Regex("""^(\s*)[-*+]\s+\[( |x|X)\]\s?(.*)$""")
    private val bulletRe = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val quoteRe = Regex("""^>\s?(.*)$""")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<Block>()
        var i = 0

        // Skip YAML frontmatter but keep absolute line numbering.
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) i = end + 1
        }

        var inCode = false
        var codeStart = 0
        val codeBuf = StringBuilder()

        while (i < lines.size) {
            val line = lines[i]
            if (inCode) {
                if (line.trimStart().startsWith("```")) {
                    blocks.add(Block(BlockType.CODE, codeBuf.toString().trimEnd('\n'), sourceLine = codeStart))
                    inCode = false
                } else {
                    codeBuf.append(line).append('\n')
                }
                i++; continue
            }
            when {
                line.trimStart().startsWith("```") -> { inCode = true; codeStart = i; codeBuf.clear() }
                line.isBlank() -> {}
                headingRe.matches(line) -> {
                    val m = headingRe.find(line)!!
                    blocks.add(Block(BlockType.HEADING, m.groupValues[2].trim(),
                        level = m.groupValues[1].length, sourceLine = i))
                }
                taskRe.matches(line) -> {
                    val m = taskRe.find(line)!!
                    blocks.add(Block(BlockType.TASK, m.groupValues[3],
                        checked = m.groupValues[2].equals("x", ignoreCase = true),
                        indent = m.groupValues[1].length / 2, sourceLine = i))
                }
                line.trim() == "---" || line.trim() == "***" || line.trim() == "___" ->
                    blocks.add(Block(BlockType.RULE, "", sourceLine = i))
                bulletRe.matches(line) -> {
                    val m = bulletRe.find(line)!!
                    blocks.add(Block(BlockType.BULLET, m.groupValues[2],
                        indent = m.groupValues[1].length / 2, sourceLine = i))
                }
                quoteRe.matches(line) -> {
                    val m = quoteRe.find(line)!!
                    blocks.add(Block(BlockType.QUOTE, m.groupValues[1], sourceLine = i))
                }
                else -> blocks.add(Block(BlockType.PARAGRAPH, line.trim(), sourceLine = i))
            }
            i++
        }
        // Unterminated fence: emit what we collected so the note isn't silently truncated.
        if (inCode) blocks.add(Block(BlockType.CODE, codeBuf.toString().trimEnd('\n'), sourceLine = codeStart))
        return blocks
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.kleanthi.obsidianwidget.core.MarkdownParserTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: markdown block parser with absolute source-line tracking"
```

---

### Task 3: Inline styler (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/core/InlineStyler.kt`
- Test: `app/src/test/java/com/kleanthi/obsidianwidget/core/InlineStylerTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class InlineStyle { BOLD, ITALIC, HIGHLIGHT, CODE, STRIKE, LINK }`
  - `data class Span(val start: Int, val end: Int, val style: InlineStyle)` — indices into the OUTPUT text, end exclusive
  - `data class StyledText(val text: String, val spans: List<Span>)`
  - `object InlineStyler { fun style(raw: String): StyledText }` — strips inline markdown syntax, records spans. v1 explicitly does NOT nest styles (no bold-inside-italic); leftmost match wins, scanning resumes after it.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kleanthi/obsidianwidget/core/InlineStylerTest.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineStylerTest {

    @Test fun `bold syntax stripped and span recorded`() {
        val s = InlineStyler.style("a **bold** word")
        assertEquals("a bold word", s.text)
        assertEquals(listOf(Span(2, 6, InlineStyle.BOLD)), s.spans)
    }

    @Test fun `wikilink with alias shows alias`() {
        val s = InlineStyler.style("see [[Some Note|the note]] ok")
        assertEquals("see the note ok", s.text)
        assertEquals(listOf(Span(4, 12, InlineStyle.LINK)), s.spans)
    }

    @Test fun `plain wikilink shows target`() {
        val s = InlineStyler.style("[[Daily/2026-07-10]]")
        assertEquals("Daily/2026-07-10", s.text)
        assertEquals(listOf(Span(0, 16, InlineStyle.LINK)), s.spans)
    }

    @Test fun `markdown link shows label`() {
        val s = InlineStyler.style("go [here](https://x.com) now")
        assertEquals("go here now", s.text)
        assertEquals(listOf(Span(3, 7, InlineStyle.LINK)), s.spans)
    }

    @Test fun `highlight strike code italic`() {
        assertEquals(listOf(Span(0, 2, InlineStyle.HIGHLIGHT)), InlineStyler.style("==hi==").spans)
        assertEquals(listOf(Span(0, 4, InlineStyle.STRIKE)), InlineStyler.style("~~gone~~").spans)
        assertEquals(listOf(Span(0, 4, InlineStyle.CODE)), InlineStyler.style("`code`").spans)
        assertEquals(listOf(Span(0, 2, InlineStyle.ITALIC)), InlineStyler.style("*it*").spans)
        assertEquals(listOf(Span(0, 2, InlineStyle.ITALIC)), InlineStyler.style("_it_").spans)
    }

    @Test fun `multiple spans in one line`() {
        val s = InlineStyler.style("**a** and ==b==")
        assertEquals("a and b", s.text)
        assertEquals(listOf(Span(0, 1, InlineStyle.BOLD), Span(6, 7, InlineStyle.HIGHLIGHT)), s.spans)
    }

    @Test fun `plain text untouched`() {
        val s = InlineStyler.style("just words 1*2 a_b_c no")
        // lone * and snake_case shouldn't ideally trigger, but v1 accepts the tradeoff for a_b_c;
        // assert only that plain text without markers passes through
        val p = InlineStyler.style("just words, no markers")
        assertEquals("just words, no markers", p.text)
        assertEquals(0, p.spans.size)
    }

    @Test fun `bold not eaten by italic`() {
        val s = InlineStyler.style("**b**")
        assertEquals(listOf(Span(0, 1, InlineStyle.BOLD)), s.spans)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.kleanthi.obsidianwidget.core.InlineStylerTest"`
Expected: FAIL — unresolved reference `InlineStyler`.

- [ ] **Step 3: Implement the styler**

`app/src/main/java/com/kleanthi/obsidianwidget/core/InlineStyler.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

enum class InlineStyle { BOLD, ITALIC, HIGHLIGHT, CODE, STRIKE, LINK }

data class Span(val start: Int, val end: Int, val style: InlineStyle)

data class StyledText(val text: String, val spans: List<Span>)

object InlineStyler {
    private data class Pat(val regex: Regex, val style: InlineStyle, val textGroup: Int)

    // Order matters where matches start at the same index:
    // earlier entries win (bold `**` must beat italic `*`).
    private val pats = listOf(
        Pat(Regex("""\[\[([^\]|]+)\|([^\]]+)]]"""), InlineStyle.LINK, 2),
        Pat(Regex("""\[\[([^\]]+)]]"""), InlineStyle.LINK, 1),
        Pat(Regex("""\[([^\]]+)]\(([^)]+)\)"""), InlineStyle.LINK, 1),
        Pat(Regex("""`([^`]+)`"""), InlineStyle.CODE, 1),
        Pat(Regex("""\*\*([^*]+)\*\*"""), InlineStyle.BOLD, 1),
        Pat(Regex("""__([^_]+)__"""), InlineStyle.BOLD, 1),
        Pat(Regex("""==([^=]+)=="""), InlineStyle.HIGHLIGHT, 1),
        Pat(Regex("""~~([^~]+)~~"""), InlineStyle.STRIKE, 1),
        Pat(Regex("""\*([^*]+)\*"""), InlineStyle.ITALIC, 1),
        Pat(Regex("""_([^_]+)_"""), InlineStyle.ITALIC, 1),
    )

    fun style(raw: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<Span>()
        var pos = 0
        while (pos < raw.length) {
            var best: MatchResult? = null
            var bestPat: Pat? = null
            for (p in pats) {
                val m = p.regex.find(raw, pos) ?: continue
                if (best == null || m.range.first < best.range.first) {
                    best = m; bestPat = p
                }
            }
            if (best == null || bestPat == null) {
                out.append(raw, pos, raw.length)
                break
            }
            out.append(raw, pos, best.range.first)
            val inner = best.groupValues[bestPat.textGroup]
            val start = out.length
            out.append(inner)
            spans.add(Span(start, out.length, bestPat.style))
            pos = best.range.last + 1
        }
        return StyledText(out.toString(), spans)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.kleanthi.obsidianwidget.core.InlineStylerTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: inline markdown styler producing span lists"
```

---

### Task 4: Task toggler (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/core/TaskToggler.kt`
- Test: `app/src/test/java/com/kleanthi/obsidianwidget/core/TaskTogglerTest.kt`

**Interfaces:**
- Consumes: nothing (standalone; operates on raw file content)
- Produces:
  - `sealed class ToggleResult { data class Success(val newContent: String, val nowChecked: Boolean) : ToggleResult(); object LineMismatch : ToggleResult() }`
  - `object TaskToggler { fun toggle(content: String, lineIndex: Int, expectedText: String): ToggleResult }` — `lineIndex` is 0-based (matches `Block.sourceLine`); `expectedText` must equal the task body the widget displayed (matches `Block.text`), otherwise `LineMismatch` (file changed under us — caller refreshes instead of writing).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kleanthi/obsidianwidget/core/TaskTogglerTest.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTogglerTest {

    @Test fun `checks an unchecked task`() {
        val r = TaskToggler.toggle("- [ ] buy milk\n- [ ] other", 0, "buy milk")
        r as ToggleResult.Success
        assertEquals("- [x] buy milk\n- [ ] other", r.newContent)
        assertEquals(true, r.nowChecked)
    }

    @Test fun `unchecks a checked task including capital X`() {
        val r = TaskToggler.toggle("- [X] done thing", 0, "done thing")
        r as ToggleResult.Success
        assertEquals("- [ ] done thing", r.newContent)
        assertEquals(false, r.nowChecked)
    }

    @Test fun `preserves indentation and marker`() {
        val r = TaskToggler.toggle("  * [ ] nested", 0, "nested")
        r as ToggleResult.Success
        assertEquals("  * [x] nested", r.newContent)
    }

    @Test fun `mismatched text returns LineMismatch`() {
        val r = TaskToggler.toggle("- [ ] changed meanwhile", 0, "what widget showed")
        assertTrue(r is ToggleResult.LineMismatch)
    }

    @Test fun `non-task line returns LineMismatch`() {
        assertTrue(TaskToggler.toggle("just a paragraph", 0, "just a paragraph") is ToggleResult.LineMismatch)
    }

    @Test fun `out of range returns LineMismatch`() {
        assertTrue(TaskToggler.toggle("- [ ] a", 5, "a") is ToggleResult.LineMismatch)
    }

    @Test fun `crlf newlines are preserved`() {
        val r = TaskToggler.toggle("# t\r\n- [ ] win file\r\n", 1, "win file")
        r as ToggleResult.Success
        assertEquals("# t\r\n- [x] win file\r\n", r.newContent)
    }

    @Test fun `unicode task text round trips`() {
        val r = TaskToggler.toggle("- [ ] καφές ☕", 0, "καφές ☕")
        r as ToggleResult.Success
        assertEquals("- [x] καφές ☕", r.newContent)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.kleanthi.obsidianwidget.core.TaskTogglerTest"`
Expected: FAIL — unresolved reference `TaskToggler`.

- [ ] **Step 3: Implement the toggler**

`app/src/main/java/com/kleanthi/obsidianwidget/core/TaskToggler.kt`:
```kotlin
package com.kleanthi.obsidianwidget.core

sealed class ToggleResult {
    data class Success(val newContent: String, val nowChecked: Boolean) : ToggleResult()
    object LineMismatch : ToggleResult()
}

object TaskToggler {
    private val lineRe = Regex("""^(\s*[-*+]\s+\[)( |x|X)(]\s?)(.*)$""")

    fun toggle(content: String, lineIndex: Int, expectedText: String): ToggleResult {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"
        val lines = content.replace("\r\n", "\n").split("\n").toMutableList()
        if (lineIndex !in lines.indices) return ToggleResult.LineMismatch
        val m = lineRe.find(lines[lineIndex]) ?: return ToggleResult.LineMismatch
        if (m.groupValues[4] != expectedText) return ToggleResult.LineMismatch
        val nowChecked = m.groupValues[2] == " "
        val mark = if (nowChecked) "x" else " "
        lines[lineIndex] = m.groupValues[1] + mark + m.groupValues[3] + m.groupValues[4]
        return ToggleResult.Success(lines.joinToString(newline), nowChecked)
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: PASS — all parser, styler, and toggler tests green.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: safe in-place task toggling with line verification"
```

---

### Task 5: Vault access layer + setup activity

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/vault/VaultRepository.kt`
- Replace: `app/src/main/java/com/kleanthi/obsidianwidget/setup/SetupActivity.kt`
- Create: `app/src/main/res/layout/activity_setup.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing from earlier tasks
- Produces (used by Tasks 6–9):
  - `class VaultRepository(context: Context)` with:
    - `var vaultUri: Uri?` (persisted in SharedPreferences file `"vault"`, key `"tree_uri"`)
    - `val vaultName: String?`
    - `fun isValidVault(): Boolean` (root exists AND contains `.obsidian`)
    - `data class NoteRef(val relPath: String, val name: String)` — `relPath` like `"Daily/2026-07-10.md"`, `name` without `.md`
    - `fun listNotes(): List<NoteRef>` (recursive, skips dot-directories, sorted case-insensitively)
    - `fun readNote(relPath: String): String?`
    - `fun writeNote(relPath: String, content: String): Boolean`
    - `fun mtime(relPath: String): Long` (0 if missing)

- [ ] **Step 1: Implement VaultRepository**

`app/src/main/java/com/kleanthi/obsidianwidget/vault/VaultRepository.kt`:
```kotlin
package com.kleanthi.obsidianwidget.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class VaultRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    var vaultUri: Uri?
        get() = prefs.getString("tree_uri", null)?.let(Uri::parse)
        set(value) { prefs.edit().putString("tree_uri", value?.toString()).apply() }

    val vaultName: String?
        get() = root()?.name

    private fun root(): DocumentFile? = vaultUri?.let { DocumentFile.fromTreeUri(context, it) }

    fun isValidVault(): Boolean {
        val r = root() ?: return false
        return r.isDirectory && r.findFile(".obsidian") != null
    }

    data class NoteRef(val relPath: String, val name: String)

    fun listNotes(): List<NoteRef> {
        val r = root() ?: return emptyList()
        val out = mutableListOf<NoteRef>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (f.isDirectory) {
                    if (!name.startsWith(".")) walk(f, "$prefix$name/")
                } else if (name.endsWith(".md")) {
                    out.add(NoteRef("$prefix$name", name.removeSuffix(".md")))
                }
            }
        }
        walk(r, "")
        return out.sortedBy { it.relPath.lowercase() }
    }

    private fun find(relPath: String): DocumentFile? {
        var cur = root() ?: return null
        for (seg in relPath.split('/')) {
            if (seg.isEmpty()) continue
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    fun readNote(relPath: String): String? = try {
        find(relPath)?.let { f ->
            context.contentResolver.openInputStream(f.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }
    } catch (e: Exception) { null }

    fun writeNote(relPath: String, content: String): Boolean = try {
        val f = find(relPath) ?: return false
        context.contentResolver.openOutputStream(f.uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            true
        } ?: false
    } catch (e: Exception) { false }

    fun mtime(relPath: String): Long = find(relPath)?.lastModified() ?: 0L
}
```

- [ ] **Step 2: Implement SetupActivity (replaces the Task 1 placeholder)**

`app/src/main/res/layout/activity_setup.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp"
    android:gravity="center_horizontal">

    <TextView
        android:id="@+id/status_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="48dp"
        android:textSize="16sp"
        android:text="@string/setup_no_vault" />

    <Button
        android:id="@+id/pick_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="@string/setup_pick_vault" />
</LinearLayout>
```

Add to `app/src/main/res/values/strings.xml` (inside `<resources>`):
```xml
    <string name="setup_no_vault">No vault connected yet.\n\nPick your Obsidian vault folder to get started.</string>
    <string name="setup_pick_vault">Choose vault folder</string>
    <string name="setup_invalid">That folder doesn\'t look like an Obsidian vault (no .obsidian inside). If your vault uses Obsidian\'s private app storage, move it to a shared folder first (Settings → About → Vault location on Android).</string>
```

`app/src/main/java/com/kleanthi/obsidianwidget/setup/SetupActivity.kt`:
```kotlin
package com.kleanthi.obsidianwidget.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
import kotlin.concurrent.thread

class SetupActivity : AppCompatActivity() {
    private lateinit var repo: VaultRepository
    private lateinit var status: TextView

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repo.vaultUri = uri
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        repo = VaultRepository(this)
        status = findViewById(R.id.status_text)
        findViewById<Button>(R.id.pick_button).setOnClickListener { pickFolder.launch(null) }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        when {
            repo.vaultUri == null -> status.setText(R.string.setup_no_vault)
            !repo.isValidVault() -> status.setText(R.string.setup_invalid)
            else -> {
                status.text = "Connected: ${repo.vaultName}\nCounting notes…"
                thread {
                    val count = repo.listNotes().size
                    runOnUiThread {
                        status.text = "Connected: ${repo.vaultName}\n$count notes found.\n\n" +
                            "Long-press your home screen → Widgets → Obsidian Widget to place one."
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: SAF vault repository and setup activity with folder grant"
```

---

### Task 6: Widget prefs + widget configuration activity

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/WidgetPrefs.kt`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/config/WidgetConfigActivity.kt`
- Create: `app/src/main/res/layout/activity_config.xml`
- Modify: `app/src/main/AndroidManifest.xml` (add config activity)

**Interfaces:**
- Consumes: `VaultRepository.listNotes()` from Task 5
- Produces (used by Tasks 7–9):
  - `object WidgetPrefs`:
    - `fun setNote(context: Context, appWidgetId: Int, relPath: String)`
    - `fun getNote(context: Context, appWidgetId: Int): String?`
    - `fun remove(context: Context, appWidgetId: Int)`
  - `WidgetConfigActivity` — handles `ACTION_APPWIDGET_CONFIGURE`; on selection stores the note and finishes `RESULT_OK`. Task 7's provider triggers the first render after config. NOTE: it calls `NoteWidgetProvider.updateWidget(...)` which exists only after Task 7 — until then it compiles behind a `TODO` marker line described below, and Task 7 removes it.

To avoid a forward reference, Task 6 ships config WITHOUT the provider call and Task 7 adds it. The config activity still stores the selection and returns `RESULT_OK` (the system then sends `APPWIDGET_UPDATE`, which Task 7's provider handles — so no functionality is lost).

- [ ] **Step 1: Implement WidgetPrefs**

`app/src/main/java/com/kleanthi/obsidianwidget/widget/WidgetPrefs.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.content.Context

object WidgetPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun setNote(context: Context, appWidgetId: Int, relPath: String) =
        prefs(context).edit().putString("note_$appWidgetId", relPath).apply()

    fun getNote(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("note_$appWidgetId", null)

    fun remove(context: Context, appWidgetId: Int) =
        prefs(context).edit().remove("note_$appWidgetId").apply()
}
```

- [ ] **Step 2: Implement the config activity**

`app/src/main/res/layout/activity_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <EditText
        android:id="@+id/search_box"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/config_search_hint"
        android:inputType="text"
        android:maxLines="1" />

    <ListView
        android:id="@+id/note_list"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</LinearLayout>
```

Add to `strings.xml`:
```xml
    <string name="config_search_hint">Search notes…</string>
```

`app/src/main/java/com/kleanthi/obsidianwidget/config/WidgetConfigActivity.kt`:
```kotlin
package com.kleanthi.obsidianwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.WidgetPrefs
import kotlin.concurrent.thread

class WidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var allNotes: List<VaultRepository.NoteRef> = emptyList()
    private var shown: List<VaultRepository.NoteRef> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Must default to CANCELED so abandoning config removes the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_config)
        val list = findViewById<ListView>(R.id.note_list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        findViewById<EditText>(R.id.search_box).doAfterTextChanged { q ->
            filter(q?.toString().orEmpty())
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val note = shown[pos]
            WidgetPrefs.setNote(this, appWidgetId, note.relPath)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        thread {
            val notes = VaultRepository(this).listNotes()
            runOnUiThread { allNotes = notes; filter("") }
        }
    }

    private fun filter(query: String) {
        shown = if (query.isBlank()) allNotes
                else allNotes.filter { it.relPath.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { it.relPath.removeSuffix(".md") })
    }
}
```

- [ ] **Step 3: Register in the manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>`, after the setup activity:
```xml
        <activity android:name=".config.WidgetConfigActivity" android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew assembleDebug` — Expected: `BUILD SUCCESSFUL`.

```bash
git add -A && git commit -m "feat: per-widget note prefs and searchable widget config activity"
```

---

### Task 7: Widget rendering (provider, list factory, span mapping)

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/SpanMapper.kt`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetProvider.kt`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetService.kt`
- Create: `app/src/main/res/layout/widget_note.xml`, `app/src/main/res/layout/row_text.xml`, `app/src/main/res/layout/row_task.xml`
- Create: `app/src/main/res/xml/note_widget_info.xml`
- Create: `app/src/main/res/drawable/widget_bg.xml`
- Modify: `app/src/main/AndroidManifest.xml` (receiver + service)
- Modify: `app/src/main/res/values/strings.xml`, `colors.xml`

**Interfaces:**
- Consumes: `MarkdownParser.parse`, `Block`/`BlockType` (Task 2); `InlineStyler.style`, `StyledText`, `InlineStyle` (Task 3); `VaultRepository.readNote`/`vaultName` (Task 5); `WidgetPrefs.getNote` (Task 6)
- Produces (used by Tasks 8–10):
  - `NoteWidgetProvider` with `companion object { fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int); const val ACTION_ROW_CLICK = "com.kleanthi.obsidianwidget.ROW_CLICK"; const val EXTRA_WIDGET_ID = "widget_id"; const val EXTRA_LINE = "line"; const val EXTRA_EXPECTED = "expected"; const val EXTRA_IS_TASK = "is_task" }`
  - `object SpanMapper { fun render(block: Block): CharSequence; fun toSpannable(styled: StyledText, strike: Boolean = false): SpannableString }`
  - Layout ids used elsewhere: `R.id.block_list`, `R.id.widget_title`, `R.id.btn_edit`, `R.id.btn_obsidian`, `R.id.empty_view`, `R.id.block_text`, `R.id.task_row`, `R.id.task_checkbox`, `R.id.task_text`
- After this task the widget is placeable and shows the rendered note (read-only; taps wired in Task 8).

- [ ] **Step 1: Layouts and widget metadata**

`app/src/main/res/drawable/widget_bg.xml` (rounded card that follows day/night):
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="24dp" />
    <solid android:color="?android:attr/colorBackground" />
</shape>
```

`app/src/main/res/layout/widget_note.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/widget_bg"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingBottom="6dp">

        <TextView
            android:id="@+id/widget_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textStyle="bold"
            android:textSize="15sp"
            android:singleLine="true"
            android:ellipsize="end"
            android:textColor="?android:attr/textColorPrimary" />

        <TextView
            android:id="@+id/btn_edit"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="8dp"
            android:textSize="16sp"
            android:text="@string/widget_edit_glyph" />

        <TextView
            android:id="@+id/btn_obsidian"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="8dp"
            android:textSize="16sp"
            android:textColor="@color/accent"
            android:text="@string/widget_open_glyph" />
    </LinearLayout>

    <ListView
        android:id="@+id/block_list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:divider="@null"
        android:dividerHeight="0dp" />

    <TextView
        android:id="@+id/empty_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="@string/widget_empty"
        android:textColor="?android:attr/textColorSecondary" />
</LinearLayout>
```

`app/src/main/res/layout/row_text.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/block_text"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingVertical="3dp"
    android:textSize="14sp"
    android:textColor="?android:attr/textColorPrimary" />
```

`app/src/main/res/layout/row_task.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/task_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingVertical="4dp">

    <TextView
        android:id="@+id/task_checkbox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingEnd="8dp"
        android:textSize="16sp"
        android:textColor="@color/accent" />

    <TextView
        android:id="@+id/task_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:textColor="?android:attr/textColorPrimary" />
</LinearLayout>
```

`app/src/main/res/xml/note_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="180dp"
    android:targetCellWidth="4"
    android:targetCellHeight="3"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:initialLayout="@layout/widget_note"
    android:configure="com.kleanthi.obsidianwidget.config.WidgetConfigActivity"
    android:updatePeriodMillis="1800000" />
```

Add to `strings.xml`:
```xml
    <string name="widget_edit_glyph">✏️</string>
    <string name="widget_open_glyph">◆</string>
    <string name="widget_empty">No note selected or note unreadable.\nTap to open the app.</string>
```

- [ ] **Step 2: SpanMapper (Android span rendering of core StyledText/Block)**

`app/src/main/java/com/kleanthi/obsidianwidget/widget/SpanMapper.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.kleanthi.obsidianwidget.core.Block
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.InlineStyle
import com.kleanthi.obsidianwidget.core.InlineStyler
import com.kleanthi.obsidianwidget.core.StyledText

object SpanMapper {
    private const val LINK_COLOR = 0xFF8B7EC8.toInt()
    private const val HIGHLIGHT_BG = 0x59FFD54F // translucent amber
    private const val QUOTE_COLOR = 0xFF9E9E9E.toInt()

    fun toSpannable(styled: StyledText, strike: Boolean = false): SpannableString {
        val s = SpannableString(styled.text)
        for (span in styled.spans) {
            if (span.start >= span.end) continue
            val what: Any = when (span.style) {
                InlineStyle.BOLD -> StyleSpan(Typeface.BOLD)
                InlineStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                InlineStyle.HIGHLIGHT -> BackgroundColorSpan(HIGHLIGHT_BG)
                InlineStyle.CODE -> TypefaceSpan("monospace")
                InlineStyle.STRIKE -> StrikethroughSpan()
                InlineStyle.LINK -> ForegroundColorSpan(LINK_COLOR)
            }
            s.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (strike && s.isNotEmpty()) {
            s.setSpan(StrikethroughSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    fun render(block: Block): CharSequence = when (block.type) {
        BlockType.HEADING -> {
            val s = toSpannable(InlineStyler.style(block.text))
            val size = when (block.level) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            if (s.isNotEmpty()) {
                s.setSpan(RelativeSizeSpan(size), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            s
        }
        BlockType.BULLET ->
            SpannableStringBuilder("    ".repeat(block.indent) + "•  ")
                .append(toSpannable(InlineStyler.style(block.text)))
        BlockType.QUOTE -> {
            val b = SpannableStringBuilder("▎ ").append(toSpannable(InlineStyler.style(block.text)))
            b.setSpan(ForegroundColorSpan(QUOTE_COLOR), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.ITALIC), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b
        }
        BlockType.CODE -> {
            val s = SpannableString(block.text)
            if (s.isNotEmpty()) s.setSpan(TypefaceSpan("monospace"), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s
        }
        BlockType.RULE -> "──────────"
        BlockType.TASK, BlockType.PARAGRAPH -> toSpannable(InlineStyler.style(block.text))
    }
}
```

- [ ] **Step 3: RemoteViewsService + factory**

`app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetService.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.Block
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.InlineStyler
import com.kleanthi.obsidianwidget.core.MarkdownParser
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NoteRemoteViewsFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
}

class NoteRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var blocks: List<Block> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val path = WidgetPrefs.getNote(context, appWidgetId)
        val content = path?.let { VaultRepository(context).readNote(it) }
        blocks = content?.let { MarkdownParser.parse(it) } ?: emptyList()
    }

    override fun getCount() = blocks.size

    override fun getViewAt(position: Int): RemoteViews {
        val b = blocks[position]
        val density = context.resources.displayMetrics.density
        val rv: RemoteViews
        if (b.type == BlockType.TASK) {
            rv = RemoteViews(context.packageName, R.layout.row_task)
            rv.setTextViewText(R.id.task_checkbox, if (b.checked) "☑" else "☐")
            rv.setTextViewText(
                R.id.task_text,
                SpanMapper.toSpannable(InlineStyler.style(b.text), strike = b.checked)
            )
            rv.setViewPadding(R.id.task_row, (b.indent * 16 * density).toInt(), 0, 0, 0)
        } else {
            rv = RemoteViews(context.packageName, R.layout.row_text)
            rv.setTextViewText(R.id.block_text, SpanMapper.render(b))
        }
        // Fill-in intents completing the provider's pending-intent template (used from Task 8).
        val fillIn = Intent().apply {
            putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
            putExtra(NoteWidgetProvider.EXTRA_IS_TASK, b.type == BlockType.TASK)
            putExtra(NoteWidgetProvider.EXTRA_LINE, b.sourceLine)
            putExtra(NoteWidgetProvider.EXTRA_EXPECTED, b.text)
        }
        rv.setOnClickFillInIntent(
            if (b.type == BlockType.TASK) R.id.task_row else R.id.block_text, fillIn
        )
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
    override fun onDestroy() {}
}
```

- [ ] **Step 4: The provider**

`app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetProvider.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ROW_CLICK = "com.kleanthi.obsidianwidget.ROW_CLICK"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_LINE = "line"
        const val EXTRA_EXPECTED = "expected"
        const val EXTRA_IS_TASK = "is_task"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val notePath = WidgetPrefs.getNote(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_note)

            views.setTextViewText(
                R.id.widget_title,
                notePath?.substringAfterLast('/')?.removeSuffix(".md") ?: "Obsidian Widget"
            )

            val svcIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.block_list, svcIntent)
            views.setEmptyView(R.id.block_list, R.id.empty_view)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.block_list)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}
```

- [ ] **Step 5: Manifest entries**

In `AndroidManifest.xml` inside `<application>`:
```xml
        <receiver android:name=".widget.NoteWidgetProvider" android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/note_widget_info" />
        </receiver>

        <service
            android:name=".widget.NoteWidgetService"
            android:permission="android.permission.BIND_REMOTEVIEWS" />
```

- [ ] **Step 6: Build and commit**

Run: `./gradlew assembleDebug && ./gradlew test` — Expected: `BUILD SUCCESSFUL`, all tests pass.

```bash
git add -A && git commit -m "feat: rendered note widget (provider, list factory, span mapping)"
```

---

### Task 8: Widget interactions — checkbox toggle, row-tap edit, deep link

**Files:**
- Modify: `app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetProvider.kt`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/editor/EditorActivity.kt` (STUB ONLY in this task — a finish-immediately shell so intents compile; Task 9 implements it)
- Modify: `app/src/main/AndroidManifest.xml` (editor activity entry)

**Interfaces:**
- Consumes: `TaskToggler.toggle`/`ToggleResult` (Task 4); `VaultRepository` (Task 5); `WidgetPrefs` (Task 6); Task 7's provider/factory/ids
- Produces: fully interactive widget; `EditorActivity` intent contract: extra `AppWidgetManager.EXTRA_APPWIDGET_ID` (Int) identifying which widget's note to edit (Task 9 keeps this contract).

- [ ] **Step 1: Editor stub + manifest**

`app/src/main/java/com/kleanthi/obsidianwidget/editor/EditorActivity.kt`:
```kotlin
package com.kleanthi.obsidianwidget.editor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class EditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish() // Task 9 replaces this with the real popup editor.
    }
}
```

Manifest, inside `<application>`:
```xml
        <activity
            android:name=".editor.EditorActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.ObsidianWidget.Dialog" />
```

- [ ] **Step 2: Wire the provider's pending intents and toggle handling**

Replace `NoteWidgetProvider.kt` in full:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.TaskToggler
import com.kleanthi.obsidianwidget.core.ToggleResult
import com.kleanthi.obsidianwidget.editor.EditorActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ROW_CLICK = "com.kleanthi.obsidianwidget.ROW_CLICK"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_LINE = "line"
        const val EXTRA_EXPECTED = "expected"
        const val EXTRA_IS_TASK = "is_task"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val notePath = WidgetPrefs.getNote(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_note)

            views.setTextViewText(
                R.id.widget_title,
                notePath?.substringAfterLast('/')?.removeSuffix(".md") ?: "Obsidian Widget"
            )

            val svcIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.block_list, svcIntent)
            views.setEmptyView(R.id.block_list, R.id.empty_view)

            // Row-click template; rows complete it with fill-in extras.
            val rowIntent = Intent(context, NoteWidgetProvider::class.java)
                .setAction(ACTION_ROW_CLICK)
                .setData(Uri.parse("obsidianwidget://row/$appWidgetId"))
            views.setPendingIntentTemplate(
                R.id.block_list,
                PendingIntent.getBroadcast(
                    context, appWidgetId, rowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            // ✏️ opens the editor.
            val editIntent = Intent(context, EditorActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("obsidianwidget://edit/$appWidgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.btn_edit,
                PendingIntent.getActivity(
                    context, appWidgetId, editIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // ◆ deep-links into Obsidian at this note.
            val vaultName = VaultRepository(context).vaultName
            if (vaultName != null && notePath != null) {
                val deepLink = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "obsidian://open?vault=" + Uri.encode(vaultName) +
                            "&file=" + Uri.encode(notePath.removeSuffix(".md"))
                    )
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.btn_obsidian,
                    PendingIntent.getActivity(
                        context, appWidgetId, deepLink,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            // Tapping the empty state opens the app.
            views.setOnClickPendingIntent(
                R.id.empty_view,
                PendingIntent.getActivity(
                    context, appWidgetId,
                    Intent(context, com.kleanthi.obsidianwidget.setup.SetupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.block_list)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_ROW_CLICK) return
        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        if (intent.getBooleanExtra(EXTRA_IS_TASK, false)) {
            val line = intent.getIntExtra(EXTRA_LINE, -1)
            val expected = intent.getStringExtra(EXTRA_EXPECTED) ?: return
            val repo = VaultRepository(context)
            val path = WidgetPrefs.getNote(context, widgetId) ?: return
            val content = repo.readNote(path) ?: return
            when (val r = TaskToggler.toggle(content, line, expected)) {
                is ToggleResult.Success -> repo.writeNote(path, r.newContent)
                ToggleResult.LineMismatch -> { /* stale view — refresh below fixes it */ }
            }
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        } else {
            context.startActivity(
                Intent(context, EditorActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}
```

- [ ] **Step 3: Build, test, commit**

Run: `./gradlew assembleDebug && ./gradlew test` — Expected: `BUILD SUCCESSFUL`, tests pass.

```bash
git add -A && git commit -m "feat: widget interactions — task toggle, edit intents, obsidian deep link"
```

---

### Task 9: Popup editor with conflict detection

**Files:**
- Replace: `app/src/main/java/com/kleanthi/obsidianwidget/editor/EditorActivity.kt`
- Create: `app/src/main/res/layout/activity_editor.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `VaultRepository.readNote`/`writeNote`/`mtime` (Task 5); `WidgetPrefs.getNote` (Task 6); `NoteWidgetProvider.updateWidget` (Task 7); intent contract from Task 8 (`EXTRA_APPWIDGET_ID`)
- Produces: working popup editor; no new interfaces.

- [ ] **Step 1: Layout and strings**

`app/src/main/res/layout/activity_editor.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:minWidth="320dp">

    <TextView
        android:id="@+id/editor_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:textSize="16sp"
        android:paddingBottom="8dp" />

    <EditText
        android:id="@+id/editor_text"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:minHeight="220dp"
        android:maxHeight="420dp"
        android:gravity="top|start"
        android:inputType="textMultiLine|textNoSuggestions"
        android:typeface="monospace"
        android:textSize="14sp"
        android:scrollbars="vertical"
        android:background="@android:color/transparent" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end"
        android:paddingTop="8dp">

        <Button
            android:id="@+id/btn_cancel"
            style="?attr/borderlessButtonStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/editor_cancel" />

        <Button
            android:id="@+id/btn_save"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/editor_save" />
    </LinearLayout>
</LinearLayout>
```

Add to `strings.xml`:
```xml
    <string name="editor_save">Save</string>
    <string name="editor_cancel">Cancel</string>
    <string name="editor_conflict_title">Note changed on disk</string>
    <string name="editor_conflict_msg">This note was modified (probably by sync) while you were editing. Overwrite with your version, or reload the new one and lose your edits?</string>
    <string name="editor_overwrite">Overwrite</string>
    <string name="editor_reload">Reload</string>
    <string name="editor_load_error">Couldn\'t read the note. Check vault access in the app.</string>
    <string name="editor_save_error">Couldn\'t save the note.</string>
```

- [ ] **Step 2: Implement EditorActivity (replaces Task 8 stub)**

`app/src/main/java/com/kleanthi/obsidianwidget/editor/EditorActivity.kt`:
```kotlin
package com.kleanthi.obsidianwidget.editor

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.WidgetPrefs

class EditorActivity : AppCompatActivity() {
    private lateinit var repo: VaultRepository
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notePath: String? = null
    private var openedMtime = 0L
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        repo = VaultRepository(this)
        notePath = WidgetPrefs.getNote(this, widgetId)
        val path = notePath
        if (path == null) { finish(); return }

        val content = repo.readNote(path)
        if (content == null) {
            Toast.makeText(this, R.string.editor_load_error, Toast.LENGTH_LONG).show()
            finish(); return
        }
        openedMtime = repo.mtime(path)

        setContentView(R.layout.activity_editor)
        findViewById<TextView>(R.id.editor_title).text =
            path.substringAfterLast('/').removeSuffix(".md")
        editor = findViewById(R.id.editor_text)
        editor.setText(content)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { trySave() }
    }

    private fun trySave() {
        val path = notePath ?: return
        if (repo.mtime(path) != openedMtime) {
            AlertDialog.Builder(this)
                .setTitle(R.string.editor_conflict_title)
                .setMessage(R.string.editor_conflict_msg)
                .setPositiveButton(R.string.editor_overwrite) { _, _ -> save(path) }
                .setNegativeButton(R.string.editor_reload) { _, _ ->
                    val fresh = repo.readNote(path)
                    if (fresh != null) {
                        editor.setText(fresh)
                        openedMtime = repo.mtime(path)
                    }
                }
                .show()
            return
        }
        save(path)
    }

    private fun save(path: String) {
        // Preserve the file's newline style.
        val original = repo.readNote(path)
        var text = editor.text.toString()
        if (original != null && original.contains("\r\n")) {
            text = text.replace("\r\n", "\n").replace("\n", "\r\n")
        }
        if (!repo.writeNote(path, text)) {
            Toast.makeText(this, R.string.editor_save_error, Toast.LENGTH_LONG).show()
            return
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        }
        finish()
    }
}
```

- [ ] **Step 3: Build, test, commit**

Run: `./gradlew assembleDebug && ./gradlew test` — Expected: `BUILD SUCCESSFUL`, tests pass.

```bash
git add -A && git commit -m "feat: popup markdown editor with mtime conflict detection"
```

---

### Task 10: Periodic refresh + refresh-on-app-resume

**Files:**
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/WidgetRefresher.kt`
- Create: `app/src/main/java/com/kleanthi/obsidianwidget/widget/RefreshWorker.kt`
- Modify: `app/src/main/java/com/kleanthi/obsidianwidget/widget/NoteWidgetProvider.kt` (add `onEnabled`/`onDisabled`)
- Modify: `app/src/main/java/com/kleanthi/obsidianwidget/setup/SetupActivity.kt` (refresh widgets on resume)

**Interfaces:**
- Consumes: `NoteWidgetProvider.updateWidget` (Task 7)
- Produces: `object WidgetRefresher { fun refreshAll(context: Context) }`; `object RefreshScheduler { fun schedule(context: Context); fun cancel(context: Context) }`

- [ ] **Step 1: Refresher + worker + scheduler**

`app/src/main/java/com/kleanthi/obsidianwidget/widget/WidgetRefresher.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetRefresher {
    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
        ids.forEach { NoteWidgetProvider.updateWidget(context, mgr, it) }
    }
}
```

`app/src/main/java/com/kleanthi/obsidianwidget/widget/RefreshWorker.kt`:
```kotlin
package com.kleanthi.obsidianwidget.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}

object RefreshScheduler {
    private const val WORK_NAME = "widget_refresh"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun cancel(context: Context) =
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
}
```

- [ ] **Step 2: Hook lifecycle**

In `NoteWidgetProvider.kt`, add inside the class (after `onDeleted`):
```kotlin
    override fun onEnabled(context: Context) = RefreshScheduler.schedule(context)
    override fun onDisabled(context: Context) = RefreshScheduler.cancel(context)
```

In `SetupActivity.kt`, add at the end of `onResume()` (after `refreshStatus()`):
```kotlin
        com.kleanthi.obsidianwidget.widget.WidgetRefresher.refreshAll(this)
```

- [ ] **Step 3: Build, test, commit**

Run: `./gradlew assembleDebug && ./gradlew test` — Expected: `BUILD SUCCESSFUL`, tests pass.

```bash
git add -A && git commit -m "feat: 15-min WorkManager refresh and refresh-on-resume"
```

---

### Task 11: Release signing, APK, README, on-device checklist

**Files:**
- Modify: `app/build.gradle.kts` (release signing config)
- Create: `keystore.properties` (gitignored), `release.keystore` (gitignored)
- Create: `README.md`

**Interfaces:**
- Consumes: everything
- Produces: `app/build/outputs/apk/release/app-release.apk` — the deliverable.

- [ ] **Step 1: Generate a keystore (one-time)**

```bash
cd ~/Desktop/Obsidian-Note-Widget
keytool -genkeypair -v -keystore release.keystore -alias obsidianwidget \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass obsidianwidget -keypass obsidianwidget \
  -dname "CN=Obsidian Widget, OU=Personal, O=Personal, L=NA, ST=NA, C=SE"
```

`keystore.properties`:
```properties
storeFile=release.keystore
storePassword=obsidianwidget
keyAlias=obsidianwidget
keyPassword=obsidianwidget
```

(Local-only personal signing key for sideloading; both files are already in `.gitignore`. Losing it just means uninstall/reinstall on update.)

- [ ] **Step 2: Wire signing into the build**

In `app/build.gradle.kts`, add imports at the very top of the file:
```kotlin
import java.util.Properties
import java.io.FileInputStream
```

Inside `android { }`, before `buildTypes`:
```kotlin
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply { load(FileInputStream(propsFile)) }
                storeFile = rootProject.file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }
```

And change the release build type to:
```kotlin
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
```

- [ ] **Step 3: Build the release APK**

Run: `./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`; verify the artifact:

```bash
ls -la app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 4: Write README.md**

```markdown
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
and `keystore.properties` + `release.keystore` (see plan Task 11).
```

- [ ] **Step 5: Commit and hand over the APK**

```bash
git add -A && git commit -m "chore: release signing, README; v0.1.0 APK"
```

Send the APK to the user (e.g. `SendUserFile` on `app/build/outputs/apk/release/app-release.apk`) with install instructions.

- [ ] **Step 6: On-device verification checklist (user performs, one confirmation round)**

Ask the user to verify on their phone and report back:
1. App installs and opens; vault folder picked; status shows vault name + note count.
2. Widget placement shows the note picker (search works); picking a note renders it.
3. Scroll works; headings/bold/checkboxes render; checked tasks show strikethrough.
4. Tapping a checkbox toggles it in the widget AND in Obsidian after opening it.
5. Tapping a paragraph opens the popup editor; a save is visible in the widget and in Obsidian.
6. ◆ opens Obsidian at the right note.
7. Editing the note in Obsidian, then reopening the widget app (or waiting ≤15 min / tapping a row), refreshes the widget.
8. Deleting and re-adding a widget works; two widgets with different notes work side by side.

Fix whatever fails, bump `versionCode`/`versionName`, rebuild, resend.

---

## Plan Self-Review Notes

- Spec coverage: setup/SAF grant (T5), per-widget config with search (T6), rendered markdown incl. inline styles (T2/T3/T7), tappable checkboxes with line verification (T4/T8), popup editor with mtime conflict (T9), deep link + header bar (T7/T8), refresh triggers (T10), signed APK + README (T11). Out-of-scope items from the spec are not implemented anywhere. ✓
- Forward references: Task 6's config activity does NOT call the provider (system broadcast handles first render); Task 8 stubs `EditorActivity` before Task 9 implements it. ✓
- Type consistency: `Block(type, text, level, checked, indent, sourceLine)`, `ToggleResult.Success(newContent, nowChecked)`, `WidgetPrefs.setNote/getNote/remove`, `VaultRepository.NoteRef(relPath, name)`, layout ids match between layouts, factory, and provider. ✓
