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
- **Naming: content-addressed.** `<sha256 of the source bytes, first 32 hex>.<ext>`. Hash while copying from the input stream. Gives dedupe for free: if the file exists and a snippet references it, the share shows "Already in Pastille" (design spec §9.5 applies to images too). Write to `images/.tmp-<random>` then `renameTo` so a crash never leaves a half file under a real name.
- **Processing** (`ImageStore.import(uri): StoredImage`), on `Dispatchers.IO`:
  - Read bounds first (`BitmapFactory.Options.inJustDecodeBounds`). Refuse sources over **40 MB** or that don't decode ("Couldn't save this · Image too large / unreadable").
  - `image/gif`: copy bytes as-is up to 15 MB (keeps animation). Over that: refuse.
  - `image/png`: downscale to **2048 px long edge** if larger (design spec §9.4), re-encode PNG. Smaller: copy bytes as-is.
  - Everything else (`jpeg`, `webp`, `heic`, …): downscale to 2048 px, encode **JPEG q90**. HEIC and WebP are poorly accepted by `commitContent` hosts; JPEG is universal. Apply EXIF orientation before encoding (`androidx.exifinterface`, one small dependency) so phone photos don't land sideways.
  - Record `imageWidth`/`imageHeight` of the stored file.
- **Cap:** 20 images per share (design spec §9.4). No global quota; show total size in Settings later if it matters.
- **Cleanup:**
  - Delete with undo (keyboard §7.2, app): delete the **row** immediately, keep the **file**. Undo re-inserts the same row (same id, pin, category, imageFile) with `upsert`.
  - Share sheet "Undo": delete row and file at once (it's undoing a create, nothing else can reference it).
  - **Orphan sweep** at process start (`PastilleApplication.onCreate`, background): delete files in `images/` that no row references. Undo windows are 6 s and in-process, so a process restart is past every one of them.
  - Same sweep clears `cacheDir/shared/` files older than 24 h. That fixes today's leak.
- **Insertion (reuse the existing path):** add `<files-path name="images" path="images/" />` to `res/xml/file_paths.xml`. Extract the second half of `shareScreenshot` into `insertImage(contentUri: Uri, mimeType: String, label: String)`: `commitContent` with `INPUT_CONTENT_GRANT_READ_URI_PERMISSION` when the field accepts the MIME, else clipboard URI fallback, then `returnToPreviousKeyboardIfWanted()`. Screenshots keep copying to `cache/shared/` then call it; image snippets call it **directly** with `FileProvider.getUriForFile(…, File(filesDir, "images/$imageFile"))`, no copy. Image snippet tap also calls `recordUse`.
- Thumbnails in the keyboard and app: decode with `inSampleSize` to the card size, off the main thread, small in-memory LRU (`LruCache<String, Bitmap>`, ~8 MB). No Coil/Glide: one more dependency for a dozen thumbnails isn't worth it.

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

- **Setting:** `PastilleSettings.clipboardClearDelay`: Off / 5 s / 10 s / 30 s / 1 min, default Off. Settings › Clipboard › "Clear clipboard after copying".
- **Label:** `copySnippet()` labels every clip `pastille:<uuid>`. When the timer ends, `ClipboardClearer` reads `getPrimaryClipDescription().label`, never the content, so Android shows no "pasted from clipboard" notice, and clears only when the label is still ours. Null (empty, or unreadable on Android 10+ while Pastille is in the background and isn't the current keyboard) means leave it alone: it never wipes blind.
- **Timer:** a coroutine with `delay` on an `Application`-scoped `Dispatchers.Main` scope, so it outlives the picker sheet. A new copy replaces the pending timer. A clear missed because the process died is acceptable; no WorkManager.
- **Sensitive:** on API 33+ the clip carries `EXTRA_IS_SENSITIVE` whenever the timer is on.
- **App:** the snackbar reads "Copied · clears in 10s" with **Clear now** (same label check, at once). The picker sheet shows no snackbar.
- **Keyboard:** unaffected. It commits text straight into the field with `commitText`. Its one clipboard write, the image fallback in `PastilleImeService.insertImage` when a field can't take an image, does not go through `copySnippet()` and isn't timed.

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
