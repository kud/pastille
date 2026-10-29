# Pastille: final feature set, architecture

> Working architecture notes from development. Visual and interaction detail lives in [design-spec.md](design-spec.md).

Read against `kud/pastille` at `194fd72` (dev.2 + settings screen). The design spec (`design-spec.md`) owns every visual and interaction detail; this file owns schema, storage, mechanics and build order. Where this file answers one of the design spec's "Engineering to confirm" lines, it says so.

## 0. What the code is today (the facts the decisions rest on)

- Room 2.6.1 via KSP. `PastilleDatabase` is **version 1, `exportSchema = false`**, one entity `SnippetEntity(id, title, text, pinned, createdAt, updatedAt, lastUsedAt)`. No migrations, no schema JSON, no `room-testing`.
- CI runs `assembleDebug testDebugUnitTest lintDebug`. **No emulator, no `androidTest`.** Any migration test must run as a JVM unit test.
- `SnippetBackup` writes `{ "version": 1, "snippets": [...] }`, `ignoreUnknownKeys = true`, and `decode` does `require(version == 1)`.
- Settings live in `PastilleSettings` (SharedPreferences). No DataStore dependency.
- Image insertion: `shareScreenshot` copies the MediaStore item to `cacheDir/shared/`, serves it through the existing FileProvider (`cache-path shared/`), tries `commitContent`, falls back to a clipboard URI. Nothing ever deletes `cache/shared/` (a slow leak; fixed below).
- Deep links already exist (dev.2): `MainActivity.EXTRA_NEW_SNIPPET` and `EXTRA_EDIT_SNIPPET_ID`, parsed by `toLaunchRequest()`, delivered via `onNewIntent` with `NEW_TASK | CLEAR_TOP | SINGLE_TOP`.

## 0.1 Schema history (where the database is now)

Sections 2 and below record the v1 → v2 design as it was decided. The database has moved on since; each step has a hand-written migration, copied from the exported schema JSON in `app/schemas/`, and a case in `MigrationTest`. Never a destructive fallback.

| Version | Migration | What changed |
|---|---|---|
| 2 | `MIGRATION_1_2` | `categories`, and `categoryId` / image columns on `snippets` (§2). |
| 3 | `MIGRATION_2_3` | `snippets.position` for the manual order, seeded from pinned-then-recent. |
| 4 | `MIGRATION_3_4` | The bin: nullable `snippets.deletedAt` with an index (§2.1). |
| 5 | `MIGRATION_4_5` | Tags: `tags` and `snippet_tags` (§2.2). |
| 6 | `MIGRATION_5_6` | Stickers: `snippets.sticker INTEGER NOT NULL DEFAULT 0` (§2.3). |

### 2.1 The bin (v4)

- Deleting sets `deletedAt`; restoring (or Undo) clears it. No separate table, so a binned snippet keeps its folder, position and, later, its tags. Deleting its folder meanwhile sets `categoryId` to `NULL` as for any snippet.
- Every live query filters `deletedAt IS NULL`: `observeAll`, `getAll`, `findTextDuplicate`, `findByImageFile`, `titlesInCategory`. Binned snippets never reach the keyboard, search, the share sheet's duplicate check or the backup.
- `allImageFiles()` deliberately does **not** filter: a binned image keeps its file.
- **At app start** (`PastilleApplication`, `cleanUpAtStart`): one transaction deletes the rows binned more than 30 days ago, **then** the orphan sweep runs. That order is what lets the sweep delete a purged image's file while keeping a binned one's; `BinTest` pins it. No WorkManager: a phone that never opens the app has nothing to purge for.
- "Delete forever" and "Empty bin" are the only other hard deletes (plus the share sheet's Undo, which undoes a create).

### 2.3 Stickers (v6)

- A sticker is an image snippet with `sticker = 1`: no new table, no new kind of file. `SnippetEntity.sticker` carries `@ColumnInfo(defaultValue = "0")` to match the migration's `DEFAULT 0`.
- **The filter lives in the DAO.** Every snippet reader adds `sticker = 0`: `observeAll` (the All list, folder contents, search and the picker sheet's Snippets list all read it) and `findTextDuplicate`. The Stickers tab has its own query, `observeStickers()`: `sticker = 1 AND imageFile IS NOT NULL AND deletedAt IS NULL ORDER BY position`. `StickerFilterTest` has one test per reader.
- Left unfiltered on purpose: `getAll()` (the export, whose summary counts stickers with the image snippets it leaves out), `findByImageFile` (dedupe must see stickers), the bin, and `allImageFiles()` (the orphan sweep).
- No CHECK constraint (`ALTER` can't add one without rebuilding the table) and no index. "A sticker must be an image" is enforced by `SnippetRepository.setSticker`, which refuses a row with no `imageFile`, clears `categoryId` and appends to the sticker order; turning it off puts the row back at the top level.
- Stickers keep `categoryId = null` in v1. Sticker folders, if they come, reuse `categoryId` with no migration.
- One import path, `SnippetRepository.importImages(uris, categoryId, sticker, importer, titleFor)`, for the share sheet and the app's photo picker: at most 20, content-addressed. Into Stickers, an image that is already an image snippet is flipped to a sticker (`converted`, kept as it was so Undo restores its folder); anything already a sticker, or any duplicate saved as an image, is left alone (`alreadyThere`).
- Backup: stickers travel in the v4 archive with the other images (§2.5), and come back as stickers.

### 2.2 Tags (v5)

- `tags(id, name COLLATE NOCASE, createdAt)` with a unique index on `name`; `snippet_tags(snippetId, tagId)` with a composite primary key and an index on `tagId`. No SQLite foreign keys: every hard delete of a snippet (delete forever, empty bin, purge, share Undo) removes its `snippet_tags` rows in the same transaction, then drops tags nobody carries.
- Names are stored normalised: lowercased, no spaces, no leading `#` (`normaliseTag`). The UI shows them in sentence case (`displayTag`), never all caps.
- `observeSnippets()` combines the snippets flow with one `snippet_tags JOIN tags` flow and attaches tags in memory: never a query per row. Binned snippets keep their links.
- Filtering by several tags keeps the snippets carrying all of them; search also matches tag names (`model/Tags.kt`).
- **Backup v3:** each snippet carries `"tags": ["work", "otp"]` by name. `decode` accepts versions 1–3; older files have no tags. Import creates missing tags, matching names case-insensitively.
- The keyboard shows no tags in v1.

## 1. Naming

Code says **category** (`Category`, `categoryId`, `categories` table), whatever the UI label ends up being. Reason: "folder" is already taken by feature 5, where it means a MediaStore bucket, and two different things called folder in one codebase is a bug waiting to be written. In code, MediaStore folders are **image sources** (`ImageSource`, `bucketId`). The user-facing word is the owner's call; the UI strings can say "Folder" without touching the code.

## 2. Schema (database version 1 → 2, one migration)

### Entities

```kotlin
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val position: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "snippets",
    indices = [Index(value = ["categoryId"])],
)
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String,                 // image snippets: "" (title carries the caption)
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val categoryId: Long? = null,     // null = uncategorised (lives under All)
    val imageFile: String? = null,    // file NAME under filesDir/images/, never an absolute path
    val imageWidth: Int? = null,      // stored pixel size, so cards lay out without decoding
    val imageHeight: Int? = null,
)
```

Decisions:

- **No `kind` column.** A snippet is an image snippet iff `imageFile != null`. One source of truth; nothing can disagree with it. `SnippetRecord` gets the same four fields plus `val isImage get() = imageFile != null`.
- **No SQLite foreign key.** Deleting a category runs, in one `@Transaction`, `UPDATE snippets SET categoryId = NULL WHERE categoryId = :id` then `DELETE FROM categories WHERE id = :id`. Reason: adding an FK to an existing table means Room's table-rebuild dance, and Room's FK validation is a classic migration-failure source. For a single-user local app, the repository is the integrity boundary. Matches the design spec's rule: snippets are never deleted with their category.
- **Store the file name, not the path.** `filesDir` differs across user profiles and work profiles; a relative name survives.
- **No MIME column.** Derived from the extension (`png`, `jpg`, `gif`, `webp`), which we control because we write the files.
- Pinned stays a boolean and a sort (the design spec's ruling). `observeAll()` keeps `ORDER BY pinned DESC, lastUsedAt DESC`; category filtering is a `WHERE categoryId = :id` variant, or filter in memory: the list is hundreds of rows, not millions. I'd filter in memory in the repository (one Flow, one source) unless profiling says otherwise.
- Duplicate detection (design spec §7.1, §9.5): `SELECT * FROM snippets WHERE imageFile IS NULL AND text = :text LIMIT 1`. No index; not worth one at this size. Image duplicates fall out of content-addressed file names (§3).

### Migration

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `categoryId` INTEGER")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageFile` TEXT")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageWidth` INTEGER")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageHeight` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snippets_categoryId` ON `snippets` (`categoryId`)")
    }
}
```

- Copy the DDL **from the generated `schemas/…/2.json`**, not from this file; Room's validator is literal about it. No `DEFAULT` clauses on the nullable columns and no `defaultValue` on the entity, so the two match.
- Wire with `.addMigrations(MIGRATION_1_2)` in `DatabaseHolder`. **No `fallbackToDestructiveMigration`, ever**: the owner has real snippets on their phone since dev.1.
- Manual `Migration`, not `@AutoMigration`: it's seven lines, it's readable, and it's what the test exercises.

### Migration test

1. **First commit of build A, before touching the entity:** set `exportSchema = true`, add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, build at version 1, commit `app/schemas/app.pastille.data.PastilleDatabase/1.json`. This captures the v1 shape we shipped. Do not hand-write it.
2. Then change the entities, bump to `version = 2`, build, commit `2.json`.
3. Test as a **Robolectric unit test** (CI has no emulator): `room-testing` (same Room version) + `robolectric` + `androidx.test:core` as `testImplementation`; `testOptions.unitTests.isIncludeAndroidResources = true`; expose the schemas to the test via `sourceSets["debug"].assets.srcDir("$projectDir/schemas")` (harmless in a debug APK). `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PastilleDatabase::class.java)`:
   - create v1, insert two raw rows (one pinned);
   - `runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`;
   - assert both rows survive with `categoryId`/`imageFile` null, and that a second category named with different case violates the unique index.
4. Fallback if MigrationTestHelper's asset loading fights Robolectric: same test with raw v1 DDL via `FrameworkSQLiteOpenHelperFactory`, then open with `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)` and query, which makes Room validate the schema on open. Either is acceptable; the first is preferred.

Also unit-test (plain JVM, existing style): `SnippetBackup` v1 → v2 decode, category merge-by-name on import, auto-title, link title, meaningful-file-name rule.

### JSON backup v2

```json
{
  "version": 2,
  "categories": [{ "name": "Work", "position": 0 }],
  "snippets": [
    {
      "title": "",
      "text": "…",
      "pinned": true,
      "createdAt": 0,
      "updatedAt": 0,
      "lastUsedAt": 0,
      "category": "Work"
    }
  ]
}
```

- `CURRENT_VERSION = 2`. `decode` accepts `version in 1..CURRENT_VERSION`; a v1 file decodes with `categories = emptyList()` and `category = null` via defaults. Newer than we know → the same clear error as today.
- **Snippets reference categories by name, not id.** Ids are local to a database; names are what a human restores. Import matches case-insensitively, creates missing categories at the end of the position order.
- **Image snippets are not exported** in v2 JSON. Base64 in JSON is the wrong container and the export would balloon. Export reports "Exported 42 snippets (3 image snippets aren't included)". A ZIP backup with `images/` is a later, separate decision if the owner wants it. Also not in `android:allowBackup` scope worth relying on.
- Import skips exact text duplicates (same rule as §2 duplicate detection), so re-importing the same file twice is idempotent. Today it doubles everything; small fix, same area.

Old app reading a new file: dev.2 rejects version 2 with its own error. Acceptable; nobody else runs dev.2.

## 3. Image storage

- **Location:** `filesDir/images/`. Private, not in cache (the OS may purge cache), not on shared storage.
- **Naming: content-addressed.** `<sha256 of the source bytes, first 32 hex>.<ext>`, the extension from what was actually written, so the name is set after encoding. Hash while copying from the input stream. Gives dedupe for free, matched on the hash whatever the extension: if the file exists and a snippet references it, the share shows "Already in Pastille" (design spec §9.5 applies to images too). Write to `images/.tmp-<random>` then `renameTo` so a crash never leaves a half file under a real name.
- **Processing** (`ImageStore.import(uri): StoredImage`), on `Dispatchers.IO`:
  - Read bounds first (`BitmapFactory.Options.inJustDecodeBounds`). Refuse sources over **40 MB** or that don't decode ("Couldn't save this · Image too large / unreadable").
  - `storagePlan(mime, width, height, bytes, animated)` (pure, `ImageMath.kt`) returns `Copy`, `Transcode(maxEdge)` or `Refuse(reason)`:
    - `image/gif`, and animated WebP (`isAnimatedWebp`: `RIFF....WEBPVP8X` with bit 1 of the flags byte): copy bytes as-is up to 15 MB (`MAX_ANIMATED_BYTES`), whatever the dimensions, since resizing would drop the animation. Over that: refuse. An animated image is never transcoded.
    - Static WebP or PNG with a long edge up to 2048 px: copy bytes as-is (keeps transparency).
    - Everything else, or larger: transcode to 2048 px long edge.
  - **Transcode** picks the format from the decoded bitmap, not the source type: `hasAlpha()` gives PNG, otherwise **JPEG q90**. Apply EXIF orientation before encoding (`androidx.exifinterface`, one small dependency) so phone photos don't land sideways.
  - WebP-born JPEGs from before this rule stay as they are: the original is gone, and the hash still matches them, so re-sharing that WebP reports it as already there until the old snippet is deleted forever.
  - Record `imageWidth`/`imageHeight` of the stored file.
- **Cap:** 20 images per share (design spec §9.4). No global quota; show total size in Settings later if it matters.
- **Cleanup:**
  - Delete with undo (keyboard §7.2, app): delete the **row** immediately, keep the **file**. Undo re-inserts the same row (same id, pin, category, imageFile) with `upsert`.
  - Share sheet "Undo": delete row and file at once (it's undoing a create, nothing else can reference it).
  - **Orphan sweep** at process start (`PastilleApplication.onCreate`, background): delete files in `images/` that no row references. Undo windows are 6 s and in-process, so a process restart is past every one of them.
  - Same sweep clears `cacheDir/shared/` files older than 24 h. That fixes today's leak.
- **Insertion (reuse the existing path):** add `<files-path name="images" path="images/" />` to `res/xml/file_paths.xml`. Extract the second half of `shareScreenshot` into `insertImage(contentUri: Uri, mimeType: String, label: String)`: `commitContent` with `INPUT_CONTENT_GRANT_READ_URI_PERMISSION` when the field accepts the MIME, else clipboard URI fallback, then `returnToPreviousKeyboardIfWanted()`. Screenshots keep copying to `cache/shared/` then call it; image snippets call it **directly** with `FileProvider.getUriForFile(…, File(filesDir, "images/$imageFile"))`, no copy. Image snippet tap also calls `recordUse`.
- Thumbnails in the keyboard and app: decode with `inSampleSize` to the card size, off the main thread, one shared in-memory LRU (`LruCache<String, Bitmap>`) sized at an eighth of `ActivityManager.memoryClass`, between 8 and 32 MB. No Coil/Glide: one more dependency for a dozen thumbnails isn't worth it.

## 4. Share target

The design spec's shape stands: `ShareActivity`, translucent theme, `ModalBottomSheet`, `excludeFromRecents`, `taskAffinity=""`, label "Save to Pastille", filters `SEND text/plain`, `SEND image/*`, `SEND_MULTIPLE image/*`, never `*/*`.

Mechanics:

- Save on `onCreate` (when `savedInstanceState == null`, so a rotation doesn't save twice).
- **Copy images while the activity is alive.** The URI grant comes with the intent; do the copy in `lifecycleScope` started from `onCreate`, show "Saving…", and if the user dismisses mid-copy, let `finish()` wait for the job (it's seconds). Don't hand the URI to a background worker; the grant isn't guaranteed to outlive the activity.
- Read `EXTRA_STREAM` with `IntentCompat.getParcelableExtra` / `getParcelableArrayListExtra` (API 33 deprecations), and also `intent.clipData` for `SEND_MULTIPLE` (some senders only fill that).
- Title rules, link detection and the 50,000-character cap are the design spec's (§9.2–9.4); put them in a pure `ShareParsing.kt` so they unit-test on the JVM.
- **No `INTERNET` permission.** Confirmed: no page-title fetch.
- Pin/category taps update the saved rows directly through the repository; Undo deletes the batch (rows and files).

## 5. Deep link (design spec §4 question)

**Already there.** dev.2 added `EXTRA_NEW_SNIPPET` and `EXTRA_EDIT_SNIPPET_ID`; `onNewSnippet` and the current long-press already use them. `onOpenApp` itself is a plain launch with no extra, and should stay that way (it means "open the app").

What's needed is extending, not a new route:

- `LaunchRequest.NewSnippet(categoryId: Long? = null)`, read from a new `EXTRA_CATEGORY_ID` (absent = none). The editor pre-fills it.
- `LaunchRequest.Edit(id)` unchanged; used by keyboard "Edit in Pastille app" and the share sheet's "Edit in Pastille".

## 6. Keyboard management: reading the host field

### Never read

Before any `InputConnection` read, `isSensitiveField(editorInfo)` returns true for:

- `TYPE_CLASS_TEXT` with variation `PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`;
- `TYPE_CLASS_NUMBER` with `NUMBER_VARIATION_PASSWORD`;
- `imeOptions` with `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito tabs, banking apps).

Pure function on `inputType`/`imeOptions` ints → unit-tested on the JVM.

Clipboard: on API 33+, if `clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)` is true, don't show the preview ("Sensitive content"); still allow an explicit save tap. Password managers set that flag.

### Read order (answers design spec §7.1)

Her order, with one change: prefer one IPC to two.

1. **Selection**, if the current selection is non-empty (track `onUpdateSelection`'s `newSelStart != newSelEnd`; seed from `EditorInfo.initialSelStart/End`): `getSelectedText(0)`. Label "From selection".
2. **Whole field:** `getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 50_000 }, 0)?.text`. Flag `0`, never `GET_EXTRACTED_TEXT_MONITOR`: we want a snapshot, not a subscription.
3. **Fallback** when (2) returns null (some WebViews, some custom editors): on API 31+, `getSurroundingText(10_000, 10_000, 0)?.text`; below, `getTextBeforeCursor(10_000, 0) + getTextAfterCursor(10_000, 0)`.
4. Still nothing → disabled row "This app doesn't share its text".

When to read: once when Add opens, and on `onUpdateSelection` **only while Add is visible**, debounced 300 ms. Every read is a synchronous IPC to the host app; reading on every cursor move while browsing would be rude to the host. Truncate to two lines for the preview; read the full text again at save time.

### 7.4 "type through the previous keyboard"

**Drop it.** Not "later", dropped. The three risks the design spec lists are all real: `switchToPreviousInputMethod()` unbinds the input view and the service can be destroyed; `EditorInfo.fieldId` is `0`/unstable in plenty of apps (WebView, Compose `TextField`); and it parks snippet text in someone's message box one stray tap from Send. "Edit in Pastille app" covers the need with no failure mode. the design spec's 7.3 chooser collapses to a single action: Edit opens the app directly.

### Panel state

The design spec's single-panel state machine (`Browse | Add | Actions(snippetId)`) lives in the service as one `mutableStateOf<PanelState>`, not in the composable, so system back (`onKeyDown(KEYCODE_BACK)` while not Browse → Browse, return true) can drive it. Delete-with-undo holds the deleted `SnippetRecord` in service memory for the strip's 6 s.

## 7. Image source folders (feature 5)

- No schema. `ScreenshotReader` becomes `ImageSourceReader`:
  - `listSources()`: query `BUCKET_ID`, `BUCKET_DISPLAY_NAME`, `DATE_ADDED` over `EXTERNAL_CONTENT_URI`, group in Kotlin (ContentResolver can't `GROUP BY` on API 29+), sort by most recent image, return `ImageSource(bucketId, name, count)`.
  - `readRecent(bucketId)`: `WHERE BUCKET_ID = ?`.
  - Default source = the bucket whose `RELATIVE_PATH` (API 29+) / `DATA` (API 28) contains `Screenshots` (today's logic, kept as the default resolver).
- Persist the chosen `bucketId` in `PastilleSettings` (SharedPreferences, like everything else). If the bucket disappears, fall back to Screenshots. **No DataStore**: one prefs mechanism, not two. This also covers the design spec's "selected tab persists" and "keyboard remembers its own category" (two keys).
- Partial media access (`READ_MEDIA_VISUAL_USER_SELECTED`): buckets list only what was granted; fine, no special case beyond the existing permission prompt.
- "Choose album" is a panel state listing all sources, not a dialog (design spec §7.0 rule).

## 8. Quick Settings tile (feature 6)

### Now: the tile opens the snippet picker sheet

The tile no longer switches keyboards. It is a stateless button, always `STATE_INACTIVE`, labelled "Pastille" with the subtitle "Snippets" and the Pastille mark as its icon, like Android's screen recorder and QR scanner tiles.

- **Launch:** `onClick` starts `picker.PickerActivity` through the same two branches as before (`startActivityAndCollapse(PendingIntent)` on API 34+, the deprecated `Intent` overload below), wrapped in `unlockAndRun` when `isLocked`. Snippets never show over the keyguard.
- **Container:** `PickerActivity` is translucent like `ShareActivity` (`Theme.Pastille.Translucent`, `excludeFromRecents`, `taskAffinity=""`) and holds a `ModalBottomSheet` that opens at half height and drags to full. It uses the keyboard palette (`keyboardPalette` with the saved keyboard style), so the sheet looks like the panel.
- **Shared cards:** the panel's mode switch, folder chips, snippet tiles, image tiles and both grids live in `ime/SnippetCards.kt` (`SnippetGrid`, `ImageGrid`, `ModeSwitch`, `FolderChipRow`). `KeyboardPanel` and the sheet both call them; the sheet passes its own `KeyboardActions`, so tap and long-press mean copy and actions there. The keyboard's behaviour is unchanged by the extraction.
- **Copy:** every copy, from the sheet and from the app, goes through `clipboard.copySnippet()`. Text is a plain clip; an image snippet is a `FileProvider` URI clip; a recent image is first copied to `cache/shared/` with `images.copyToSharedCache` (the same fallback the keyboard uses, now shared). A tapped snippet also calls `recordUse` on the application's `backgroundScope`, so closing the sheet can't cancel it. Below Android 13 the sheet shows a "Copied" toast; 13+ shows the system confirmation.
- **[⌨] in the header** starts `ImePickerActivity`, the system keyboard picker, and closes the sheet.
- **Android 14+ (targetSdk 34+, we're on 35):** `startActivityAndCollapse(Intent)` throws `UnsupportedOperationException`, hence the `PendingIntent` overload on API 34+ and the deprecated `Intent` overload below (with `@Suppress("DEPRECATION")`). This is the one hard Android 14 restriction that bites here.

### The keyboard picker

`InputMethodManager.showInputMethodPicker()` is ignored when the caller has no focused window. So [⌨] launches a tiny transparent `ImePickerActivity` (no UI, `excludeFromRecents`, `noHistory`) that calls `showInputMethodPicker()` in `onWindowFocusChanged(true)` and finishes on the next focus change. Pastille never writes the default keyboard itself and needs no special permission: the user always picks in the system dialog.

## 8b. Clipboard auto-clear

- **Setting:** `PastilleSettings.clipboardClearDelay`: Off / 5 s / 10 s / 30 s / 1 min, default Off. Settings › Clipboard › "Clear after copying".
- **Label:** `copySnippet()` labels every clip `pastille:<uuid>`. When the timer ends, `ClipboardClearer` reads `getPrimaryClipDescription().label`, never the content, so Android shows no "pasted from clipboard" notice, and clears only when the label is still ours. Null (empty, or unreadable on Android 10+ while Pastille is in the background and isn't the current keyboard) means leave it alone: it never wipes blind.
- **Timer:** a coroutine with `delay` on an `Application`-scoped `Dispatchers.Main` scope, so it outlives the picker sheet. A new copy replaces the pending timer. A clear missed because the process died is acceptable; no WorkManager.
- **Sensitive:** on API 33+ the clip carries `EXTRA_IS_SENSITIVE` whenever the timer is on.
- **App:** the snackbar reads "Copied · clears in 10s" with **Clear now** (same label check, at once). The picker sheet shows no snackbar.
- **Keyboard:** unaffected. It commits text straight into the field with `commitText`. Its one clipboard write, the image fallback in `PastilleImeService.insertImage` when a field can't take an image, does not go through `copySnippet()` and isn't timed.

## 8c. Recent clipboard (2026-10-28)

- **Why it can only see some copies:** Android 10+ lets only the focused app or the active keyboard read the clipboard. So Pastille notes the clip when its keyboard opens (`onStartInputView`), on every `OnPrimaryClipChangedListener` call while it is shown (`isInputViewShown`), and when `MainActivity` gains window focus. Copies made while Pastille is neither shown nor in front are never seen; that is the platform, not a bug.
- **Rules** (`clipboard/RecentClips.kt`, pure, `RecentClipsTest`): text items only (`ClipData.Item.text`, never `coerceToText`, so a URI or intent clip is skipped: images later, if ever); skipped when the description carries `android.content.extra.IS_SENSITIVE` (read by key on every API level, since password managers set it before 33), when the label starts with `pastille:` (Pastille's own `copySnippet`: it is a snippet already), or when the keyboard sits in a field `isSensitiveField` refuses (passwords, `IME_FLAG_NO_PERSONALIZED_LEARNING`). Same text again moves to the front; at most 10; each expires an hour after it was last seen; one stamped in the future (clock moved back) is dropped. Texts are cut at `MAX_TEXT_CHARS`.
- **Each copy is looked at once,** by `ClipDescription.getTimestamp()`. Removing or clearing a clip therefore sticks while that copy is still on the clipboard; copying the same text again is a new stamp and comes back. The description is checked before the content, so a seen, own or sensitive clip is never read at all. A stamp of 0 (unknown) is always looked at.
- **Storage:** memory only, in `PastilleApplication.recentClipboard` (`RecentClipboard`, a `StateFlow`), shared by the keyboard and the app in one process. Nothing touches disk, so nothing reaches Auto Backup, device transfer, export, Share or the sync file, and process death empties it. A coroutine drops each clip when it expires rather than at the next read.
- **Setting:** `PastilleSettings.recentClipboard`, Settings › Keyboard › "Recent clipboard", **on by default**. The exposure is small and bounded: Pastille only reads while it is shown or in front, keeps 10 clips for an hour in memory, and skips sensitive and password-field copies; off by default would hide the feature from the people it is for. Gboard ships the same default with a longer life. Turning it off clears at once, and no read happens while it is off.
- **Keyboard:** a 48dp row at the top of Snippets, above the folder chips, only while there are clips (`ime/RecentClipsRow.kt`). Tap pastes with `commitText` and honours "Return after a snippet"; long-press opens `PanelState.Clip` (Paste, Save as snippet, Remove); a trailing "Clear" empties the row. Save as snippet goes through the same path as Add › From clipboard (duplicate check, Undo strip) into the current folder, and drops the clip from the row.
- **Not a tab:** a fourth tab does not fit the toolbar. Icon tabs take at least 48dp each and the right-hand actions 192dp, so four tabs need about 392dp in compact mode (360dp phones have 168dp left) and about 454dp with the selected label (Erwann's phone is 411dp).
- No `INTERNET`, no new permission.

## 9. Build split

Yes to two sequential, CI-green sets, with two adjustments.

**Build A: data, categories, image snippets, share** (one agent, sequential commits, each green)

1. `exportSchema = true` at v1, commit `1.json` + test deps (no behaviour change).
2. Schema v2 + `MIGRATION_1_2` + migration test + repository (categories CRUD, move, delete-to-All, delete/restore) + backup v2 + its tests.
3. `ImageStore` + `files-path images/` + `insertImage` extraction + orphan sweep (fixes the `cache/shared` leak).
4. App: category tabs, editor category field, image snippet rows; `EXTRA_CATEGORY_ID`.
5. **Keyboard: category chips and image snippet cards that insert.** (Moved into A: without it, a shared image is invisible where the owner tests tonight.) Long-press keeps opening the editor for now.
6. `ShareActivity` (text, links, one and several images).

**Build B: keyboard management + image sources** (after A lands; same agent or a fresh one)

1. Panel state machine + status strip + `isSensitiveField` + read order.
2. Add (field / selection / clipboard), with category row.
3. Long-press actions: Pin, Edit (app), Delete + Undo, Move to category. Replaces A's long-press.
4. Image sources: `ImageSourceReader`, chip row, "Choose album" state, persisted bucket.

**Build T: Quick Settings tile, in parallel with A** (separate agent, `isolation: "worktree"`)

It touches only new files, the manifest, `PastilleSettings` (one key) and the README. Conflicts with A are a few manifest lines. That's the one place I'd split differently: no reason to make the tile wait for the keyboard work.

Version bumps and the release go through `/k-release`, not hand-edited `versionCode`.

## 10. Not doing

- 7.4 edit-through-host-field (dropped, §6).
- Network title fetch for links (no `INTERNET`, design spec §9.3).
- Image export in JSON (§2).
- Per-category direct-share shortcuts (design spec §9.6 "later").
- DataStore, Coil/Glide, SQLite foreign keys.


## 2.5 Backup archive, sharing and automatic backup (2026-10-28)

Pastille keeps no `INTERNET` permission and no Play-services dependency: everything below goes through Android's own components (the Storage Access Framework, the share sheet, the FileProvider).

**Format: backup v4, a plain ZIP** (`backup/BackupArchive.kt`).

```
pastille.json    SnippetBackup JSON, "version": 4
images/<name>    one entry per image snippet or sticker, named as in filesDir/images/
```

- A ZIP, not a custom extension or MIME type: Android, file managers and Drive all know `application/zip` and `.zip`, while custom extensions are unreliable. Whether a zip is a Pastille backup is decided by its contents (`pastille.json`), never its name. The JSON stays readable.
- v4 adds, per snippet: `image` (the file name), `imageWidth`, `imageHeight`, `sticker`. Every v1–v3 field is unchanged. Old bare-JSON exports (v1–v3) still import: `BackupArchive.isZip` tells them apart by the first four bytes.
- Image names must match the content-addressed pattern (`^[0-9a-f]{8,64}\.(png|jpg|jpeg|webp|gif)$`); anything else in an archive is ignored, so a crafted zip can't write outside `images/`. A record whose image file is missing on export is left out rather than named without its bytes.
- Manual Export, Import, Share backup and the automatic backup all use this one format.

**Import** (`SnippetRepository.importBackup`): image entries are copied into `filesDir/images/` first (temp name, then rename; an existing file with the same name is the same image), then one transaction adds the rows. Duplicates are skipped as before: text by exact text, images and stickers by their file. Stickers append to the sticker order; the bin is never exported. If the transaction fails, the start-up orphan sweep removes the copied files.

**Quick Share** (`share/ShareOut.kt`): a snippet shares as `text/plain`; an image or sticker as its `FileProvider` URI (`files-path images/`) with a read grant on the intent and its `ClipData`; the whole collection is built into `cache/shared/pastille-backup-<date>.zip` (swept after 24 h) and shared as `application/zip`. Pastille's own targets are excluded from the chooser. Receiving: `backup/ImportBackupActivity` (label "Import to Pastille") takes `VIEW` and `SEND` for `application/zip`, `application/x-zip-compressed` and `application/x-zip`. Quick Share saves a received file and its "Open" fires `VIEW`. The activity copies the file to the cache, reads it, asks once ("Import this backup?"), and imports on the application scope. Text and images keep going to `ShareActivity`.

**Send to another phone** (long-press menu, beside Share): Share sends text as `text/plain`, which Quick Share drops on the other phone's clipboard. "Send to another phone" instead builds a one-item v4 archive (`BackupFiles.buildToSend` → `SnippetRepository.exportItems` → `BackupArchive.only`: that live snippet or sticker, its folder, its image; no bin, tags list or tombstones) in `cache/shared/send-<uuid>/Pastille - <title>.zip` (the folder keeps two same-titled sends apart; the 24 h sweep removes it) and shares it as `application/zip`. On the other phone "Open" lands in `ImportBackupActivity`, which sees a single entry and asks "Add “<title>” to Pastille?" with the text below, or says at once that it is already there (`SnippetRepository.holds`: same live uid, same text, or same image file). Import keeps the file's uids, snippets and folders alike, unless a row here already uses one (bin included: `upsert` replaces on a uid conflict), and skips any entry whose uid is live here, so a later sync sees one item. That applies to every import, not just sends.

**QR code** (long-press menu, text snippets only): `app.pastille.qr.SnippetQr` encodes the snippet's text in byte mode over its UTF-8 bytes, with no ECI header (camera apps and Google Lens read UTF-8 without one; some readers mishandle ECI), at error correction M, raised to Q or H when that fits in the same version (`boostEcl`). The ceiling is a version 40 code at M: 2,331 bytes (`SnippetQr.MAX_BYTES`, pinned against the library in `SnippetQrTest`). Above it, or for empty text, `encode` returns null and the menu item is disabled with the reason, so a cut or unreadable code is never shown. `SnippetQrDialog` draws the modules on a `Canvas`, snapped to whole pixels, black on white with a four-module quiet zone, and sets `FLAG_KEEP_SCREEN_ON` and full brightness on the dialog's own window, so both end with it. The encoder is Project Nayuki's QR Code generator (MIT), vendored unmodified as Java under `app/src/main/java/io/nayuki/qrcodegen/` from upstream commit `3c6d0b3`; see [third-party notices](third-party-notices.md). There is no scanner and no `CAMERA` permission: the other phone reads the code with its own camera and shares the text into Pastille through the existing share target. `SnippetQrTest` holds known vectors (SHA-256 of the module grid) that ZXing's C++ reader decoded back to their exact text, emoji and a full version 40 code included, when they were pinned.

**Automatic backup** (`backup/AutoBackup.kt`, Settings › Backup):

- A folder first (Erwann, 2026-10-29): `OPEN_DOCUMENT_TREE`, a persisted grant on the tree, and one file in it named `SyncFolder.FILE_NAME` (`pastille-backup.zip`, the name sync always suggested). `SyncFolder.choose` lists the children and matches the exact name (folders, " (1)" copies and other cases skipped): a match is merged with, none is created, so a provider that renames duplicates is never asked to make one. Several matches use the most recently modified, and Settings says how many and which. A listing that fails never reads as "no file". When the file disappears from the folder, the next sync looks it up again by name (`AutoBackup.refind`) and starts a new merge base. The tree URI, its name, the match count and the used file's modified time live in `PastilleSettings`.
- The single-file fallback (Erwann, 2026-10-29) sits inside the Sync flow: turning Sync on asks once where to sync (`SyncPlaceDialog`): the existing sync file (`ACTION_OPEN_DOCUMENT`, listed first, merged in), a new one (`ACTION_CREATE_DOCUMENT`, `pastille-backup.zip` suggested), which becomes the sync file and is written at once through the verified write path), or a folder (listed first: it finds or creates the file itself, and Google Drive offers folders, seen on Erwann's phone on 2026-10-29). Android has no picker that both opens and creates, and Drive allows duplicate names without saying so, so the user makes that one choice. The file paths stay for storage apps that offer no folders to `OPEN_DOCUMENT_TREE`. A sync set up before folders keeps its file untouched until a folder is chosen. The URI, its display name, the last write time, a content fingerprint and the last problem live in `PastilleSettings`.
- Triggers, all in the app process, no WorkManager: a Room `InvalidationTracker` observer on `snippets`, `categories`, `tags`, `snippet_tags` (the app, the keyboard and the share sheet are one process), debounced 5 s; `MainActivity.onStop`; and every process start, which catches a change whose debounce the process didn't outlive.
- Nothing is written when the fingerprint (the v4 JSON with `lastUsedAt` and `updatedAt` zeroed) matches the last write, so using a snippet from the keyboard doesn't rewrite a file in Drive.
- Data safety: the archive is built in `cache/backup/`, re-opened and verified (JSON decodes, every named image is present), and only then copied over the user's file with mode `"wt"` (then `"rwt"`). Plain `"w"` doesn't truncate on some versions, so it is used only when a provider refuses both *and* the new archive is at least as long as the largest size anyone reported for the file (`SyncDiagnosis.plainWriteIsSafe`); otherwise nothing is written (`CantOverwrite`). The file is read back and its SHA-256 compared, again after 0.5, 1.5 and 3 s if it differs, since a cloud provider may serve the old bytes while it uploads; still different is `Mismatch`, and nothing is recorded as agreed. A SAF document can't be renamed atomically over Drive or pCloud (with a single-file grant there is no parent to create a sibling in, so no write-then-swap), so the copy itself is the one unprotected window, and it is closed by bookkeeping instead: before the first byte the SHA-256 of the new archive is committed as `syncPendingWrite` (`commit()`, not `apply()`, so a killed process still has it) and cleared only once the file reads back. A file that then isn't a whole archive (`BrokenArchive`: the zip or its JSON entry won't open) is this phone's own cut-off write, everything it was meant to hold is still in the database, and the next sync writes it whole again (`SyncDiagnosis.mayReplaceBroken`). A broken file nobody here was writing, or a whole archive from a newer Pastille, is never touched.
- Stale reads: before each write the hash of what the file held is kept as `syncReplacedHash`. A later read that returns exactly those bytes is the provider's cache (pCloud offline, Drive mid-upload), not news: merging it would undo this phone's own write, so it is neither merged nor written over (`StaleRead`, shown as "Waiting for …").
- Failures are specific (`BackupProblem`, worded by `backupStatus` with the storage app's name from the URI authority, `SyncDiagnosis.providerLabel`): `Access` (permission gone, file missing), `NotKept` (the provider didn't persist the grant: `takePersistableUriPermission` threw or persisted nothing; the picker's grant still syncs until a restart), `ReadOnly` (the document's flags lack `FLAG_SUPPORTS_WRITE`), `CantOverwrite`, `Mismatch`, `Read` ("Waiting for pCloud"), `Write`. Each failure also goes to the diagnostic log (`CrashLog.recordSync`, shown with crash reports): provider authority, step, exception classes with their causes, document flags, whether size and modified time are known, the persisted grant; never exception messages, which can quote the file. The same failure is logged once.
- A file just created by "Sync automatically" that can't be read yet but is reported empty or unsized is written anyway (`SyncDiagnosis.mayWriteUnread`): some providers can't serve a file they haven't uploaded, and waiting for it would wait forever. A file with bytes in it is never overwritten unread. A modified time of 0 counts as unknown, so the file is always hashed.
- Restore is the same import, pointed at that file, behind the same confirmation.

**Two-way sync** replaced the one-way copy on 2026-10-28; see §2.6.

## 2.6 Two-way sync through the backup file (database v7, 2026-10-28)

Two phones syncing with the same file (Settings › Backup › "Sync automatically": start it on one, choose it on the other, or the same folder on both) keep one collection. Still no `INTERNET` permission and no Google dependency: Drive's own document provider moves the bytes.

**Schema v7** (`MIGRATION_6_7`): `uid` (random UUID, backfilled by SQLite per row, unique index) on `snippets`, `categories` and `tags`; `updatedAt` on `categories` and `tags` (backfilled from `createdAt`); a `tombstones(uid, kind, deletedAt)` table. `snippets.updatedAt` now means the last *edit*: text, title, pin, folder, tags, order, sticker, bin and restore all move it; `touch()` (a use) moves only `lastUsedAt`. Rows edited before v7 keep whatever `updatedAt` they had, which may be a use time; that only biases their first merge towards the phone that used them last.

**Tombstones**: Delete forever, Empty bin, the 30-day purge, share Undo and deleting a folder each leave `(uid, kind, deletedAt)`; they outlive the row, so the deletion reaches the other phone. A merge prunes tombstones older than a year (`SyncMerge.TOMBSTONE_KEEP_MS`): a phone that hasn't synced for a year could bring such an item back, which is the safe direction. The bin itself is a field (`deletedAt`), not a tombstone.

**File**: still `"version": 4`, still read by every older build, because sync only adds keys: `uid`, `categoryUid` and (in the bin) `deletedAt` per snippet; `uid`, `createdAt`, `updatedAt` per folder; top-level `tags` (uid per name), `bin` and `tombstones`. Binned snippets sit under `bin`, never `snippets`, so an import (or an older build) never takes the bin as live. Export and Share leave the three sync-only keys out. Entries without a uid (v1–v3 files, older v4) get one derived from their content (`SnippetBackup.legacySnippetUid`), so the same old file reads alike on both phones.

**Merge** (`sync/SyncMerge.kt`, pure Kotlin, `SyncMergeTest`): three-way per item, by uid, against the base (what this phone and the file last agreed on, `filesDir/sync/base.json`).

- Each field: the side that changed it since the base wins; both changed it differently → the later `updatedAt`. No base (first sync, or the base was lost) → the whole record with the later edit.
- Title, text and image are one unit: both sides edited it differently → both are kept. The later edit keeps the uid; the other becomes a copy whose uid derives from the original uid and its content, so whichever phone merges first, both produce the same copy.
- `lastUsedAt` is not an edit: the later use is kept.
- A tombstone deletes, unless the item was edited since the base *and* after the deletion; then the edit wins and the tombstone goes. An item missing on one side without a tombstone is kept: absence never deletes.
- Unmatched items that are the same thing (exact text, or the same image file, in the same bin/sticker state; folders by name, ignoring case) become one under the smaller uid, on both phones (`renamedSnippets`/`renamedCategories`: the local row takes the new uid, keeping its id). This is also what pairs two phones that held the same collection before their first sync.
- Folder membership is by folder uid; a folder gone leaves its snippets unfiled. Tags are names (unique, normalised, never renamed), so the name is the identity; the tag's uid is unified per name and carried for a future rename.
- Images go by their content-addressed name; missing ones are extracted from the file's `images/`. A snippet whose image still isn't there after that is left as it was locally and retried next time.

**When** (`backup/AutoBackup.kt`): every sync reads before it writes. Triggers: process start, `MainActivity.onStart`, the keyboard opening (at most once a minute), every change (debounced 5 s) and leaving the app. Reading is skipped when the provider's size and modified time match what we last saw; otherwise the file is copied to the cache, hashed, and merged only if the hash moved. The merge is applied in one Room transaction (`SnippetRepository.applySync`), touching only rows that differ, so the keyboard and share sheet, which read the same Room flows, see merged data at once. The file then becomes the base, and whatever this phone holds that the file doesn't is written back through the same build, verify, write and read-back as before; what was written becomes the base. A read that fails while the provider still lists the file is "Waiting for Drive" (`BackupProblem.Read`), and **nothing is written** until it reads: a phone never overwrites changes it hasn't seen.

**Clocks**: "later edit" compares wall clocks from two phones. Phones on network time agree to well under a second, which is far below how fast one person edits on two phones; a phone with a wrong clock loses or wins ties it shouldn't, never data (conflicting text keeps both).

**Unverified without two phones and Drive**: how fast Drive's provider surfaces the other phone's write (the read may be stale; the merge is safe with stale input, only later), whether Drive grants a persistable write permission on a file picked with `ACTION_OPEN_DOCUMENT`, whether it reports `COLUMN_LAST_MODIFIED` and `SIZE` (without them every trigger downloads and hashes the file), and what Drive returns offline (treated as "Waiting for Drive" while it still lists the file).

**pCloud (2026-10-28)**: reported working on Drive and not on pCloud, with no detail. Nothing public confirms how pCloud's document provider behaves (no tracker issue found in KeePassDX, Keepass2Android, Markor, Joplin or Obsidian that names it), so every suspect is handled rather than one guessed: a grant not persisted, a read-only grant on a picked file, `"wt"`/`"rwt"` refused, missing or zero size and modified time, stale reads after a write, and a new file it can't serve yet. The status line and the diagnostic log name which one it was.
