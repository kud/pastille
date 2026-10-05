# Pastille polish: design spec

> Working design document from development. Sections describe intent at the time of writing; not everything here has shipped. See [architecture.md](architecture.md) for storage and mechanics.

Source: the owner's two test-build screenshots, their reference app's list screen (layout only), `KeyboardPanel.kt`, `Theme.kt`, `PastilleImeService.kt`, `SnippetListScreen.kt`.

## Shipping split

- **Build A, quick polish (no schema change):** sections 1–5, plus section 6.1 (list row restyle). Ship first.
- **Build B, categories (Room migration):** sections 6.2–6.4. In build B, the keyboard's All/Pinned chips (section 3) are replaced by the category chip row (6.4).

## Root causes

- **Near-black icons:** the panel root is a `Column` with `.background(...)`, not a `Surface`, so `LocalContentColor` is never set and every untinted `Icon` falls back to `Color.Black`. This covers the clipboard, settings and back icons, and the placeholder icon on the blank screenshot tile.
- **"All" looks disabled:** it is. `enabled = showPinnedOnly` disables the chip exactly when it is the active filter. In the screenshots "All" is active (the visible snippets have no pin icon), so the selected chip gets the disabled style and the unselected "Pinned" chip looks lit.
- **The + is missing:** the FAB exists (`SnippetListScreen.kt:133`), but at `targetSdk 35` the keyboard covers it. The search field opens the keyboard, and the Scaffold doesn't pad for the IME.
- **Panel under the gesture bar:** the IME content ignores navigation-bar insets.
- **Dynamic colour** is already on in `Theme.kt` (Android 12+). Keep it; baseline M3 schemes below 12. Once content colour flows, the panel follows Material You in light and dark.

## 1. Insets and padding (`KeyboardPanel`)

- [ ] Root: `Surface(color = colorScheme.surfaceContainer, contentColor = colorScheme.onSurface)`, filling to the screen edge so the background runs under the gesture bar.
- [ ] Inner `Column` gets `Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))`.
- [ ] Inner padding, on top of the insets: 12dp horizontal, 8dp top, 8dp bottom.
- [ ] Toolbar row height: 48dp.
- [ ] Vertical gaps: 8dp toolbar → screenshots, 8dp screenshots → snippets.
- [ ] Grid and `LazyRow`: 8dp spacing.
- [ ] Screenshot `LazyRow`: `contentPadding = PaddingValues(horizontal = 12.dp)`. Remove the panel's horizontal padding from that row only, so thumbnails scroll to the edge instead of clipping at the padding.
- [ ] Snippet cards: 12dp inner padding (was 10dp), `heightIn(min = 56.dp)`.

## 2. Colour roles (the same roles in light and dark)

| Element               | Role                                                                                                            |
| --------------------- | --------------------------------------------------------------------------------------------------------------- |
| Panel background      | `surfaceContainer`                                                                                              |
| Toolbar icons         | `onSurfaceVariant` (inherited through the `Surface`; no hardcoded tint)                                         |
| Filter chips          | see section 3                                                                                                   |
| Snippet card          | `CardDefaults.cardColors(containerColor = surfaceContainerHighest)`; title `onSurface`, body `onSurfaceVariant` |
| Pin icon on card      | `primary` (unchanged)                                                                                           |
| Thumbnail placeholder | `surfaceContainerHigh`                                                                                          |
| Empty-state text      | `onSurfaceVariant`                                                                                              |
| "New" chip in toolbar | container `primary`, label and icon `onPrimary` (the one filled element on the panel, deliberately)             |

## 3. Toolbar

### All / Pinned filter chips

- [ ] Replace both `AssistChip`s with `FilterChip`s. Drop `enabled` entirely; both chips are always tappable.
- [ ] Never use the disabled state for selection. Disabled means "you can't tap this".

```kotlin
FilterChip(
    selected = !showPinnedOnly,          // "Pinned": selected = showPinnedOnly
    onClick = { showPinnedOnly = false },
    label = { Text("All") },
    leadingIcon = if (!showPinnedOnly) {
        { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
    } else null,
    colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = colorScheme.onSurfaceVariant,
        selectedContainerColor = colorScheme.secondaryContainer,
        selectedLabelColor = colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = colorScheme.onSecondaryContainer,
    ),
    border = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = !showPinnedOnly,
        borderColor = colorScheme.outlineVariant,
        selectedBorderColor = Color.Transparent,
    ),
)
```

| State      | Container                     | Label and icon         | Border               | Extra                    |
| ---------- | ----------------------------- | ---------------------- | -------------------- | ------------------------ |
| Selected   | `secondaryContainer` (filled) | `onSecondaryContainer` | none                 | leading check icon, 18dp |
| Unselected | transparent                   | `onSurfaceVariant`     | 1dp `outlineVariant` | no icon                  |

- [ ] 8dp between chips; default 32dp chip height.
- [ ] The check icon means selection doesn't rely on colour alone, so it survives a low-contrast dynamic `secondaryContainer`.
- [ ] `FilterChip` provides `selected` semantics, so TalkBack announces it. No extra work.

### Actions (right side, left to right)

- [ ] **New:** `AssistChip` with leading `Icons.Filled.Add`, label "New", colours `primary` / `onPrimary`. It opens the app straight into the editor (see section 4, deep link).
- [ ] **Save clipboard:** `IconButton` with `Icons.Outlined.ContentPasteGo` (fall back to `Icons.Outlined.AddCircleOutline` if it's missing from the icon set), wrapped in `TooltipBox` with `PlainTooltip { Text("Save clipboard as snippet") }`.
  - Feedback after saving: the new card appears first in the grid, and a `labelMedium` line in `onSurfaceVariant` reading "Saved from clipboard" shows under the toolbar for 1.5s. An IME can't use a Toast, so the feedback stays in the panel.
  - When the clipboard is empty, show "Clipboard is empty" in the same slot for 1.5s.
- [ ] **Open Pastille:** `Icons.Outlined.OpenInNew` with the tooltip "Open Pastille". Replaces `Settings`, which is wrong because the button opens the app, not settings.
- [ ] **Switch keyboard:** `Icons.Outlined.Keyboard` with the tooltip "Switch keyboard". Replaces `KeyboardBackspace`, which reads as "go back a screen" or "delete".
- [ ] Every action icon is `Outlined` so the set is consistent, and every icon button has a tooltip. The toolbar has no room for text labels except "New".

## 4. Adding snippets

### Deep link to the editor

- [ ] "New" (keyboard toolbar) and "New snippet" (keyboard empty state) launch `MainActivity` with an extra such as `EXTRA_OPEN_EDITOR = true` and `FLAG_ACTIVITY_NEW_TASK`. `MainActivity` routes straight to `SnippetEditorScreen` for a new snippet. Back from the editor returns to the list. Engineering to confirm whether `onOpenApp` already carries an extra or needs a new route.

### Keyboard empty state (replaces "Add your first snippet in the Pastille app")

- [ ] Centred `Column`, 24dp vertical padding:
  - `bodyMedium` "No snippets yet", `onSurfaceVariant`
  - 12dp spacer
  - `Row` with 8dp spacing: `FilledTonalButton("Save clipboard")` (disabled when the clipboard is empty) and `OutlinedButton("New snippet")` (deep link above).
- [ ] With the Pinned filter on and nothing pinned, show "No pinned snippets. Long-press a snippet to pin it." in `bodyMedium` / `onSurfaceVariant` instead, with no buttons.

### App (`SnippetListScreen`)

- [ ] FAB: `ExtendedFloatingActionButton(text = { Text("New snippet") }, icon = { Icon(Icons.Filled.Add, null) })`.
- [ ] Scaffold: `contentWindowInsets = WindowInsets.safeDrawing`; FAB gets `Modifier.imePadding()` so it sits above the keyboard.
- [ ] The search field doesn't autofocus on launch. An empty list doesn't need a keyboard.
- [ ] App empty state (replaces "No snippets yet. Tap + to add your first one."): centred `Column`, `bodyLarge` "No snippets yet" in `onSurfaceVariant`, 16dp spacer, `FilledTonalButton("Add your first snippet")` that opens the editor. Don't point at a control that isn't guaranteed to be on screen.
- [ ] With search active and no results: "No snippets match "<query>"" in `bodyMedium`, no button.

## 5. Screenshot thumbnails and the blank-tile placeholder

- [ ] Size 72dp square (screenshots show around 72dp; code says 64dp), `RoundedCornerShape(8.dp)`.
- [ ] Loading: a `surfaceContainerHigh` box with **no icon** and a subtle shimmer, alpha animating 0.6 → 1.0 over 900ms, `infiniteRepeatable`, `RepeatMode.Reverse`.
- [ ] Loaded: `Crossfade` (150ms) from the placeholder to the bitmap, `ContentScale.Crop`.
- [ ] Failed (bitmap still null after load completes): `Icons.Outlined.BrokenImage` at 20dp, tint `onSurfaceVariant`, centred on the `surfaceContainerHigh` box. Track loading and failed as separate states so a failure never shimmers forever.
- [ ] `contentDescription` stays `item.displayName` on the image; the placeholder has `contentDescription = null` with the tile's click label "Insert screenshot".

## 6. App list and categories (from the owner's reference app)

Take the structure, not the look: native Material 3, dynamic colour, Pastille's own type. Never copy content from the reference screenshot; it holds personal data.

What we deliberately **don't** take: the bottom nav. Pastille has one primary screen, so a three-item bar is chrome without destinations. Search stays in the top area. The pencil FAB becomes our extended FAB, since a label beats an icon for discoverability, which is the complaint we're fixing.

### 6.1 List rows — **Build A (quick polish)**

- [ ] Replace cards with flat rows in a `LazyColumn`, separated by `HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)`. No divider after the last row.
- [ ] Row: `Modifier.fillMaxWidth().clickable { openEditor(id) }.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)`, `heightIn(min = 72.dp)`. `Row` with content column `weight(1f)` and a trailing copy button.
- [ ] Content column, top to bottom:
  - Title: `titleMedium`, `onSurface`, `maxLines = 1`, ellipsis. If the title is blank, the body is the first line and gets no separate title.
  - 2dp spacer, then the body: `bodyMedium`, `onSurfaceVariant`, `maxLines = 3` collapsed.
  - **Show more:** only when the text actually overflows (detect via `onTextLayout { hasVisualOverflow }`). Render as a `TextButton`-styled `labelLarge` "Show more" in `primary`, on its own line under the body, 0dp start padding, `heightIn(min = 32.dp)`. Tapping it expands the row in place (`animateContentSize()`), and the label becomes "Show less". Don't append it inline at the end of the truncated text; inline links on truncated text are fiddly to hit and fight `Ellipsis`.
  - 6dp spacer, then a meta row: timestamp in `labelSmall`, `onSurfaceVariant` at 0.8 alpha; if pinned, an 8dp gap then `Icons.Filled.PushPin` at 14dp in `primary` with `contentDescription = "Pinned"`. In build B, the category name is also shown here (`labelSmall`) but only under the "All" tab.
- [ ] Timestamp format: relative for recent ("Just now", "5 min ago", "Yesterday"), then a locale short date (`DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`). No seconds and no raw ISO. Use the updated time, falling back to created.
- [ ] Trailing copy: `IconButton` (48dp target) with `Icons.Outlined.ContentCopy` in `onSurfaceVariant`, `contentDescription = "Copy <title>"`, aligned to the row's vertical centre. On tap: copy to the clipboard and show a `Snackbar` "Copied". On Android 13+ the system shows its own clipboard confirmation, so skip the snackbar there.
- [ ] Long-press row: the existing pin/delete actions in a `DropdownMenu` (Pin / Unpin, Delete). Delete keeps its confirmation or undo snackbar, whichever exists today.
- [ ] Ordering stays as `sortSnippets` does it today: pinned first, then by recency.
- [ ] FAB: `ExtendedFloatingActionButton` "New snippet" with `Icons.Filled.Add` (section 4), always visible, with `imePadding()`. Give the list `contentPadding = PaddingValues(bottom = 88.dp)` so the last row's copy button is never under the FAB. The FAB collapses to icon-only while scrolling down and expands when scrolling up or at the top: `expanded` derived from `listState` (first visible index and offset compared with the previous frame). Never hide it outright.

### 6.2 Data — **Build B (categories)**

- [ ] Snippet gets an optional `categoryId: Long?` (null = uncategorised). New `Category` table: `id`, `name` (unique, case-insensitive), `position` (for tab order). Room migration that adds the column and table; existing snippets stay null. Engineering owns the schema shape and migration.
- [ ] Backup export/import carries categories and the snippet's category name. Import of an older backup must still work (no categories, everything uncategorised).

### 6.3 Categories in the app — **Build B**

- [ ] Under the top app bar: `PrimaryScrollableTabRow` (M3), `edgePadding = 16.dp`, `containerColor = colorScheme.surface`, indicator `primary`, selected label `primary`, unselected `onSurfaceVariant`, `titleSmall`. This is the native M3 equivalent of the reference's tab strip; don't build a custom pill.
- [ ] Tabs, in order: **All**, then each category by `position`. No "Uncategorised" tab; those snippets live under All. No "Pinned" tab (see the pinned decision below).
- [ ] Last item in the row: a trailing `IconButton` with `Icons.Outlined.Add`, `contentDescription = "New category"`, opening an `AlertDialog` with one `OutlinedTextField` ("Category name") and Create / Cancel.
- [ ] Manage categories (rename, delete, reorder): long-press a tab → `DropdownMenu` with Rename, Move left, Move right, Delete. Delete confirms with "Snippets in <name> move to All." Snippets are never deleted with their category.
- [ ] Hide the tab row entirely while no categories exist. Nobody should meet an empty "All" tab alone on day one; the "New category" entry then lives in the top app bar overflow menu.
- [ ] Selected tab persists across launches (`DataStore`), and resets to All if that category is deleted.
- [ ] The FAB's "New snippet" pre-fills the category of the current tab.
- [ ] Editor: a category field under the body as an `ExposedDropdownMenuBox` (label "Category", options "None" + categories + "New category…").
- [ ] Search runs across all categories regardless of the selected tab, and results show their category name in the meta row.

### Pinned: the decision

**Pinned becomes a sort, not a tab or filter.** Pinned snippets float to the top of whichever category you're in (this is already how `sortSnippets` orders them), with the pin glyph in the meta row and on keyboard cards. Reasoning: a Pinned tab duplicates items across tabs and makes "where does this live?" ambiguous; a filter plus categories is two filtering axes on a keyboard that has room for one. Pinning answers "what do I reach for most", and the top of the list already answers that.

- Build A keeps the All/Pinned `FilterChip`s from section 3, because there are no categories yet and pinning needs some visible payoff.
- Build B removes them.

### 6.4 Categories in the keyboard — **Build B**

- [ ] Replace the All/Pinned chips with a horizontally scrolling chip row: `LazyRow`, `horizontalArrangement = spacedBy(8.dp)`, `contentPadding = PaddingValues(horizontal = 12.dp)`, `weight(1f)` so it shares the 48dp toolbar row with the action icons on the right. Fade the row's trailing edge (16dp gradient from transparent to `surfaceContainer`) so it reads as scrollable.
- [ ] Chips: `FilterChip`s with exactly the selected/unselected styling in section 3 (`secondaryContainer` / `onSecondaryContainer` + check when selected; transparent, `outlineVariant` border, `onSurfaceVariant` when not). First chip "All", then categories by `position`. Single-select.
- [ ] When no categories exist, the row shows nothing and the action icons keep their place. No lone "All" chip.
- [ ] The keyboard remembers its own last category (separate from the app's) and scrolls the selected chip into view when it opens.
- [ ] "New" in the keyboard toolbar opens the editor with the current keyboard category pre-filled.
- [ ] Empty category in the keyboard: "Nothing in <name> yet" (`bodyMedium`, `onSurfaceVariant`) and `OutlinedButton("New snippet")` deep-linking to the editor with that category.
- [ ] Keyboard snippet cards stay cards (two-column grid, section 1). The flat-row style is for the app, where there is room to read; the keyboard is for tapping fast.

## 7. Manage in the keyboard — **Build C (keyboard-first management)**

The owner's steer: everything happens in the keyboard; the app is the secondary place. The constraint: Pastille has no typing keys, so **no text field ever appears in the panel**. All text comes from the host field, the clipboard, or the app.

Category chips in this section depend on Build B. Without B, the same flows ship with the category rows omitted. Whether C rides with A, B or alone is a product call; it is the largest of the three.

### 7.0 Panel rules for every state

- [ ] **One panel, swapping states, never dialogs or `ModalBottomSheet`.** An IME window can't host them cleanly. Below the toolbar the panel shows exactly one of: **Browse** (default), **Add**, **Snippet actions**, **Editing banner** (7.4). `AnimatedContent` with a 150ms fade + 8dp vertical slide.
- [ ] **Constant panel height** across states (the Browse height). Changing height makes the host app's layout jump on every tap.
- [ ] Every non-Browse state has a leading `IconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back")` in the toolbar in place of the chip row, and the state's title in `titleSmall`, `onSurface`. System back also returns to Browse.
- [ ] Transient feedback lives in a **status strip**: a 40dp row directly above the content, `surfaceContainerHighest`, `bodyMedium` `onSurface`, optional trailing `TextButton` in `primary` ("Undo", "Open"). It auto-dismisses after 4s (6s when it carries Undo). This replaces the 1.5s text line in section 3.
- [ ] **Secure and sensitive fields:** when the host field's `inputType` is a password variation (`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `NUMBER_VARIATION_PASSWORD`) or `IME_FLAG_NO_PERSONALIZED_LEARNING` is set, Pastille never reads the field. "From text field" is disabled with the supporting line "Not available in this field".

### 7.1 Add (replaces the toolbar's separate clipboard icon)

The toolbar collapses to one entry point. "New" (the `primary` `AssistChip`) opens **Add**. The standalone save-clipboard `IconButton` from section 3 is removed in this build, so there's one way in, not two that half-overlap.

Panel layout, **Add** state:

```
[←] New snippet
────────────────────────────────────────
 [icon] From text field                    ›
        "first two lines of host text…"
 [icon] From clipboard                     ›
        "first two lines of clipboard…"
 [icon] Write in Pastille app              ↗
────────────────────────────────────────
 Category   (None) (Games) (Work) …        ← Build B only
```

- [ ] Three `ListItem`s (M3), `containerColor = Color.Transparent`, each `heightIn(min = 64.dp)`, 16dp horizontal padding, full-row `clickable`:
  - **From text field:** leading `Icons.Outlined.TextFields`; headline "From text field" (or "From selection" when the host has a selection); supporting text is a 2-line preview in `bodySmall` `onSurfaceVariant`, ellipsised. One tap saves.
  - **From clipboard:** leading `Icons.Outlined.ContentPaste`; same preview pattern.
  - **Write in Pastille app:** leading `Icons.Outlined.EditNote`, trailing `Icons.AutoMirrored.Outlined.OpenInNew` at 18dp; deep-links to the editor (section 4).
- [ ] Empty source: row disabled (`ListItemDefaults.colors` disabled roles = `onSurface` at 0.38 alpha) with supporting text "Text field is empty" / "Clipboard is empty" / "Not available in this field" / "This app doesn't share its text". Never hide the row; a disabled row with a reason teaches how it works.
- [ ] Source reading order (implementation detail for Engineering to confirm): `getSelectedText(0)` if a selection exists, else `getExtractedText(ExtractedTextRequest(), 0)?.text`, else `getTextBeforeCursor(N) + getTextAfterCursor(N)`. Read when Add opens and again on `onUpdateSelection`, so the preview is live.
- [ ] **Category row (Build B):** under a `HorizontalDivider`, a `labelMedium` "Category" then a single-select `FilterChip` `LazyRow` (styling from section 3), first chip "None", preselected to the Browse tab's current category. The chosen category applies to whichever source is tapped next. Categories can't be _created_ here (no typing); the last chip is an `AssistChip` "New in app" with an `OpenInNew` icon.
- [ ] **On save:** return to Browse, scroll to the new card, give it a 600ms `secondaryContainer` highlight that fades out, and show the status strip "Saved "<title>"" + "Undo".
- [ ] **Duplicate:** if identical text already exists, don't create a second one. Status strip: "Already saved as "<title>"" + "Show", which scrolls to and highlights it.
- [ ] **Host field untouched:** saving never clears or edits the host field.

**Auto title**

- [ ] Title = first non-blank line, trimmed, cut at 40 characters on a word boundary with "…". If the text is a single line of 40 characters or fewer, the title stays blank and the card shows the body alone (same rule as the list rows in 6.1), to avoid a title that repeats the body.
- [ ] Titles are edited in the app only.

### 7.2 Snippet actions (long-press a card)

Long-press opens **Snippet actions** for that snippet. Tap still inserts, unchanged. Give long-press haptic feedback (`HapticFeedbackType.LongPress`).

Panel layout, **Snippet actions** state:

```
[←] <title or first line>
────────────────────────────────────────
 "preview of the text, up to 3 lines…"
────────────────────────────────────────
 (📌 Pin)   (✎ Edit)   (🗑 Delete)
────────────────────────────────────────
 Category   (None) (Games) (Work) …        ← Build B only
```

(The emoji in the diagram stand for the Material icons named below.)

- [ ] Preview: `bodyMedium`, `onSurfaceVariant`, `maxLines = 3`, 16dp horizontal padding, 8dp vertical, inside a `surfaceContainerHigh` rounded box (12dp corners).
- [ ] Action row: three equal-width `FilledTonalButton`-style vertical buttons (icon 24dp over a `labelMedium` label, `heightIn(min = 64.dp)`, 8dp gaps):
  - **Pin / Unpin:** `Icons.Outlined.PushPin` / `Icons.Filled.PushPin`. Toggles immediately, stays in this state, label flips. Status strip "Pinned" / "Unpinned".
  - **Edit:** `Icons.Outlined.Edit`. Opens the edit chooser (7.3).
  - **Delete:** `Icons.Outlined.Delete`, colours `errorContainer` / `onErrorContainer`. No confirmation step: delete immediately, return to Browse, status strip "Deleted "<title>"" + "Undo" (6s). Undo restores the same id, pin and category.
- [ ] **Move to category (Build B):** the same category chip row as Add, with the snippet's current category selected. Tapping a chip moves it at once, and the status strip says "Moved to <name>". There's no Save button; every action here is immediate and reversible.
- [ ] TalkBack: these actions are also exposed as `customActions` on each card (`semantics { customActions = listOf(Pin, Edit, Delete) }`), so a screen-reader user doesn't depend on the long-press.

### 7.3 Edit

The text itself can't be edited in the panel. The **Edit** button opens a two-row chooser in the same panel state:

- [ ] **"Edit in Pastille app"** (`ListItem`, `OpenInNew`): deep-links to `SnippetEditorScreen` for that id. Reliable; the default; listed first.
- [ ] **"Edit in this text field"** (`ListItem`, `Icons.Outlined.Keyboard`, supporting text "Uses your other keyboard", marked **experimental**, shown only when the host field is not secure; see 7.4).

### 7.4 Experimental: edit through the host field and the previous keyboard ⚠ feasibility unconfirmed

There's no clean way to type _inside_ the panel. Android runs one IME at a time, and `switchToPreviousInputMethod()` hides Pastille entirely. The workable version uses the host field as the editor:

1. "Edit in this text field" inserts the snippet text at the cursor (`commitText`), stores a **pending edit** (snippet id + inserted range) in DataStore, and calls `switchToPreviousInputMethod()`.
2. The user edits the text with their normal keyboard and switches back to Pastille (globe key or IME switcher).
3. Pastille opens in the **Editing banner** state: a `secondaryContainer` banner, 56dp, with "Editing "<title>"", a `FilledTonalButton("Save from field")` and a `TextButton("Discard")`. "Save from field" replaces the snippet's text with the current field text (same read order as 7.1) and auto-retitles only if the title was auto-generated. It does **not** delete the text from the host field; the owner removes it themselves.
4. The pending edit expires after 10 minutes or when the host app or field changes (`EditorInfo.packageName` / `fieldId`), after which the banner doesn't appear.

Risks for engineering:

- Whether the IME service is destroyed on switch, so the state must live in DataStore, not memory.
- Whether `fieldId` is stable enough to match.
- Leaving text in the user's message field is a real footgun, for example hitting send by accident.

Recommendation: ship 7.1–7.3 without this. Build 7.4 only if the owner finds the app hop annoying in practice.

### 7.5 What stays app-only

- [ ] Creating, renaming, reordering and deleting **categories** (needs typing).
- [ ] Editing a snippet's **title or text** (except via the 7.4 experiment).
- [ ] Import / export and permissions.

The toolbar's "Open Pastille" icon stays for these. Every app-only action in the panel carries the `OpenInNew` glyph, so it's predictable when the keyboard is about to hand off.

## 8. App header — **Build A (quick polish)**

(Numbered §8 because §7 is already the keyboard-management section.)

The current `TopAppBar` has "Pastille" at default title weight and two bare arrows, which nobody can decode. Replace it with a wordmark header that collapses.

### 8.1 Component and behaviour

- [ ] `LargeTopAppBar` with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior(snapAnimationSpec = null)`, wired via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the `Scaffold`. **The header moves only when the list scrolls** (issue #8): the bar gets the behaviour through a wrapper that reports `isPinned = true`, which in M3 1.3 only switches off the bar's own `draggable`, so a drag or a wobbly tap on the header does nothing. No snap: a gesture that stops half-way leaves the header half-way, and the wordmark never resizes on its own.
- [ ] Expanded state (at rest, top of list): the wordmark sits bottom-left, 16dp start. Collapsed (scrolled): it settles into the 64dp bar beside the actions. The large title also collapses on short lists: dragging a list too short to scroll still feeds the header through nested scroll.
- [ ] Build B: the category `PrimaryScrollableTabRow` sits **below** the app bar in the `topBar` slot (`Column { LargeTopAppBar; TabRow }`) and stays pinned; only the large title collapses.
- [ ] `windowInsets = TopAppBarDefaults.windowInsets` (status bar), which is the default. The app is edge-to-edge at targetSdk 35, so don't add manual status-bar padding.

### 8.2 Wordmark typography

- [ ] Expanded: `MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)`, which is 32sp bold with slightly tightened tracking. Bold is what gives it presence; size alone just reads loud.
- [ ] Collapsed: `titleLarge.copy(fontWeight = FontWeight.Bold)` (22sp). `LargeTopAppBar` takes a single `title` slot, so drive the style from `scrollBehavior.state.collapsedFraction`: lerp the font size from 32 to 22sp and keep the weight bold throughout.
- [ ] **The pastille mark:** a 10dp filled circle in `colorScheme.primary` before the word, 8dp gap, vertically centred on the cap height (`Modifier.offset(y = 1.dp)` if it sits low). It's the brand's one ornament, it echoes the name, and it carries Material You colour into the header. Keep it at 10dp in both states. `contentDescription = null`; it is decoration.
- [ ] Text "Pastille", system font (Roboto / Google Sans per device). No custom font in Build A.

### 8.3 Actions (right side)

Replace the two arrows with:

- [ ] **Search:** `IconButton(Icons.Outlined.Search, "Search snippets")`. It opens an M3 `SearchBar` in expanded mode (full-screen, `inputField` focused, IME open by intent). Results show the 6.1 row style across all categories. The in-content `OutlinedTextField` search is **removed**, which also fixes the IME covering the FAB on launch (section 4): nothing focuses a text field until the user asks.
- [ ] **Overflow:** `IconButton(Icons.Filled.MoreVert, "More options")`, opening a `DropdownMenu` with leading icons in `onSurfaceVariant`, in this order:
  - "Import snippets" — `Icons.Outlined.FileDownload`
  - "Export snippets" — `Icons.Outlined.FileUpload`
  - `HorizontalDivider`
  - "Keyboard setup" — `Icons.Outlined.Keyboard` (opens the existing enable/switch steps, or `ACTION_INPUT_METHOD_SETTINGS`)
  - "Last crash report" — `Icons.Outlined.BugReport`, **only when a crash report exists** (if dev.2 has added crash capture; omit otherwise)
  - Build B: "New category" (see 6.3)
- [ ] Don't add a Settings item until there is a settings screen. An entry that leads to nothing is worse than no entry.

### 8.4 Colour roles

| Element                           | Role                                                                   |
| --------------------------------- | ---------------------------------------------------------------------- |
| Bar container, expanded           | `surface`                                                              |
| Bar container, collapsed/scrolled | `surfaceContainer` (`LargeTopAppBar` colours `scrolledContainerColor`) |
| Wordmark                          | `onSurface`                                                            |
| Pastille mark                     | `primary`                                                              |
| Action icons                      | `onSurfaceVariant`                                                     |
| Menu container / items            | defaults (`surfaceContainer` / `onSurface`)                            |

The colour change on scroll is the elevation cue, so no shadow is needed. These are the same roles in light and dark, with dynamic colour on Android 12+.

### 8.5 Keyboard panel header

**No separate header row.** The 48dp toolbar already is the panel's header, and every extra dp there is taken from the host app's screen. One touch only: when the toolbar has no chips to show (no categories in Build B, or the Build A state before pinning matters), the leading slot shows the 8dp pastille mark plus "Pastille" in `titleSmall`, bold, `onSurface`, so the panel identifies itself instead of starting with a gap. Whenever chips exist, the chips take the slot.

## 9. Share → Pastille — **Build C (dev.3)**

Pastille becomes a share target: text, links and images shared from any app are saved as snippets. Tap-only, no typing.

### 9.0 The call: save first, adjust after

Sharing **saves immediately**, then shows a small bottom sheet that confirms the save and offers adjustments (Pin, category, Undo).

- A straight save with a Toast is fast but strands Pin and category.
- A sheet that waits for a Save button adds a tap to every share.

Save-first costs zero taps for the common case, and one tap per adjustment otherwise.

- [ ] Implementation shape (Engineering to confirm): a dedicated `ShareActivity` with a translucent theme (`Theme.Material3.DayNight.NoActionBar` + `windowIsTranslucent`, `windowBackground` transparent), hosting a Compose `ModalBottomSheet`. The source app stays visible behind the scrim. `excludeFromRecents="true"`, `taskAffinity=""`, finishes when the sheet is dismissed.
- [ ] No auto-dismiss timer. The user leaves by tapping outside, swiping down, pressing back, or tapping "Done", which is one tap whatever happens. A sheet that vanishes while you're reading it is worse than one that waits.

### 9.1 Sheet layout (all types)

```
            ───                              ← drag handle
 ● Saved to Pastille                 Undo
┌──────────────────────────────────────┐
│ <auto title>                          │   ← preview (varies by type, 9.2–9.4)
│ <body preview / thumbnail>            │
└──────────────────────────────────────┘
 (📌 Pin)  (None) (Games) (Work) …           ← Pin + category chips (Build B)
 Edit in Pastille ↗                   [Done]
```

(The 📌 in the diagram stands for the `Icons.Outlined.PushPin` chip below.)

- [ ] `ModalBottomSheet(containerColor = surfaceContainerLow, dragHandle = { BottomSheetDefaults.DragHandle() })`. Content padding: 24dp horizontal, 8dp top, 16dp bottom, plus `WindowInsets.navigationBars`.
- [ ] **Header row (56dp):** the 8dp `primary` pastille dot, 8dp gap, `titleMedium` "Saved to Pastille" in `onSurface`. Trailing `TextButton("Undo")` in `primary`.
- [ ] **Preview:** a `surfaceContainerHigh` box, 12dp corners, 12dp inner padding. Contents per type below.
- [ ] **Chip row (16dp above, `LazyRow`, 8dp spacing):**
  - First chip: a Pin `FilterChip` with leading `Icons.Outlined.PushPin` (filled when selected), label "Pin". Styling as §3.
  - Build B: a 1dp × 24dp `outlineVariant` vertical divider, then single-select category chips ("None" + categories). "None" is preselected unless the keyboard's last category is set.
  - Every tap applies instantly. There is no save step.
- [ ] **Bottom row (16dp above):**
  - Leading `TextButton("Edit in Pastille")` with trailing `Icons.AutoMirrored.Outlined.OpenInNew` at 18dp. Opens the editor for this snippet (the §4 deep link) and finishes the share activity.
  - Trailing `FilledTonalButton("Done")`.
- [ ] TalkBack: announce "Saved to Pastille" on open (`liveRegion = Polite` on the header).

### 9.2 Text

- [ ] Source: `ACTION_SEND`, `text/plain`, with `EXTRA_TEXT`. Use `EXTRA_SUBJECT` when present.
- [ ] Title: `EXTRA_SUBJECT` if non-blank, otherwise the §7.1 auto-title rule (first line, 40 characters, blank when the text is a short single line).
- [ ] Preview: title in `titleSmall` `onSurface` (if any), then the text in `bodyMedium` `onSurfaceVariant`, `maxLines = 4`, ellipsis.
- [ ] Very long text: save up to 50,000 characters. Above that, save the first 50,000 and add a `labelSmall` line under the preview: "Long text — saved the first 50,000 characters".

### 9.3 Links

A link is detected when the shared text contains exactly one URL (`Patterns.WEB_URL`). Many apps send "Page title\nhttps://…" or "Some text https://…".

- [ ] Snippet text = the whole shared text, unchanged, so insertion reproduces exactly what was shared.
- [ ] Title, in priority order:
  1. `EXTRA_SUBJECT`
  2. the non-URL text, if any (auto-title rule)
  3. the host without `www.` plus the first path segment, e.g. `example.com/recipes`
- [ ] **No network fetch for page titles.** It would be slow, it would need the `INTERNET` permission Pastille shouldn't have, and it would leak what you share.
- [ ] Preview:
  - title in `titleSmall`
  - a row with `Icons.Outlined.Link` at 16dp and the host in `labelMedium`, both `primary`
  - the full URL in `bodySmall` `onSurfaceVariant`, `maxLines = 1`, middle ellipsis (`TextOverflow.MiddleEllipsis` if available, else end)
- [ ] Shared text holding several URLs: treat it as plain text (9.2).

### 9.4 Images ⚠ needs image snippets (model change)

Snippets are text-only today (`SnippetEntity`: title, text, pinned, timestamps). Saving an image needs an image snippet type: a nullable `imagePath` pointing at a **copy in app-private storage**. A shared `content://` URI is a temporary grant and goes dead after the share. Do this in the same Room migration as Build B categories, not as a second migration. Engineering owns the schema. If images can't make dev.3, ship 9.2–9.3 and leave the image intent filters out, rather than accepting a share that then fails.

**One image** (`ACTION_SEND`, `image/*`, `EXTRA_STREAM`)

- [ ] Copy the image on a background dispatcher. Downscale to a 2048px long edge and keep the original format (PNG stays PNG, which matters for screenshots).
- [ ] Title: `EXTRA_TEXT` first line if present (some apps send a caption), otherwise the display name if it's meaningful, otherwise "Image · <date>" using the §6.1 date format. A name counts as not meaningful when it matches `IMG_\d+`, `Screenshot_\d+`, `PXL_\d+` or a UUID.
- [ ] Preview: thumbnail 96dp tall, width by aspect ratio up to the full width, 8dp corners, `ContentScale.Fit` on `surfaceContainerHighest`, with the title beside it in `titleSmall`. While copying, use the shimmer placeholder from §5, and the header reads "Saving…" with Undo disabled.

**Several images** (`ACTION_SEND_MULTIPLE`, `image/*`)

- [ ] Each image becomes its own snippet. Grouping them would invent a concept the keyboard doesn't have.
- [ ] Header: "Saved 4 images".
- [ ] Preview: a row of up to four 64dp square thumbnails, 8dp spacing and 8dp corners. If there are more, the fourth tile is a `surfaceContainerHighest` tile reading "+N" in `titleMedium`.
- [ ] Pin and category apply to the whole batch. Undo removes the whole batch.
- [ ] Cap at 20 per share. Over that, save the first 20 and the header reads "Saved 20 of 34 images".

**Mixed** (`EXTRA_STREAM` + `EXTRA_TEXT`)

- [ ] Save as an image snippet with the text as its caption/title, not as two snippets.

**In the keyboard**

- [ ] Image snippets appear in the snippet grid as cards with a 56dp-tall thumbnail and the title under it. Tapping inserts the image the same way screenshots are inserted today.

### 9.5 Confirmation, duplicates, undo, failure

- [ ] **Undo:** deletes what was just saved, including its stored image copies. The header changes to "Removed" in `onSurfaceVariant`, the chips and preview fade to 0.38 alpha, and the sheet closes after 800ms. Undo is available until the sheet closes; after that, deleting happens in the keyboard (§7.2) or the app.
- [ ] **Duplicate text:** if identical text already exists, don't save again. The header becomes "Already in Pastille". Pin and category act on the existing snippet, and Undo is hidden because nothing was created.
- [ ] **Failure** (unreadable stream, unsupported type): the header becomes "Couldn't save this" in `error`, with a one-line reason in `bodySmall` underneath. Only "Done" is shown.
- [ ] **Empty share** (no text, no stream): same failure sheet, reason "Nothing to save".

### 9.6 Share-sheet label and icon

- [ ] `ShareActivity` `android:label="Save to Pastille"`. The system share sheet shows that under the app icon, which says what will happen. The app name alone wouldn't.
- [ ] Icon: the launcher icon (default). No separate share icon; the stacked pastilles are the recognisable part.
- [ ] Intent filters:
  - `SEND` with `text/plain`
  - `SEND` with `image/*` (only if 9.4 ships)
  - `SEND_MULTIPLE` with `image/*` (only if 9.4 ships)
  - Not `*/*`. Accepting files we can't save is how a share target loses trust.
- [ ] Optional later (Build B+): sharing shortcuts per category ("Pastille · Work") via `ShortcutManagerCompat` + `shortcuts.xml` `share-target`, so a category can be picked from the share sheet's direct-share row. Only the three most recently used categories. Not for dev.3.

## 10. dev.3 feedback: modes, Gboard look, folders, titles — **dev.3, overrides earlier sections**

Source: the owner on the dev.3 preview. Where this conflicts with §1–§9, **§10 wins**. Overridden:

- §2: the "New" chip colour
- §3: the toolbar
- §1: screenshots and cards sharing one panel
- §6.3 and §6.4: category tabs and chips become folders
- §7.1: the blank-title rule, and the standalone clipboard icon
- §7.0: state-change motion (now §10.6)

UI copy says **"folder"** everywhere. Code can keep `category`.

### 10.1 Keyboard layout: one mode at a time

```
[ Snippets | Images ]              (+)  ⚙  ⌨     ← 48dp toolbar
[← Work · 6]                                     ← 40dp folder bar, only inside a folder (Snippets)
[Screenshots] [Camera] [Downloads] …             ← 40dp source chips, only in Images
┌────────────────────────────────────────────┐
│ content area, fixed 224dp, scrolls inside   │
└────────────────────────────────────────────┘
```

- [ ] **Mode switch:** `SingleChoiceSegmentedButtonRow` with two `SegmentedButton`s, labels "Snippets" and "Images" (`labelLarge`), no icons. The default selected check is fine. 40dp tall, leading slot of the toolbar.
- [ ] Colours: `SegmentedButtonDefaults.colors(activeContainerColor = secondaryContainer, activeContentColor = onSecondaryContainer, inactiveContainerColor = Color.Transparent, inactiveContentColor = onSurfaceVariant, activeBorderColor = outlineVariant, inactiveBorderColor = outlineVariant)`.
- [ ] The mode persists in DataStore and defaults to Snippets.
- [ ] **Snippets mode:** the folder bar (only inside a folder) plus the title-button grid (10.5). No screenshots anywhere.
- [ ] **Images mode:** the image-source chips row, as built (single-select `FilterChip`s, §3 styling), plus a 3-column `LazyVerticalGrid` of thumbnails, 8dp spacing, 6dp corners. Thumbnails are square, `aspectRatio(1f)`, with the §5 shimmer and broken states. No folders here. The + button is hidden; images arrive by share (§9) or screenshot.
- [ ] **Constant height:** the toolbar plus a fixed **224dp content area** in both modes, so switching never makes the host app jump. The 40dp folder or source row is inside those 224dp, not added on top. Empty states centre in the same area.
- [ ] Switching modes: see the motion spec in §10.6.

### 10.2 Toolbar (replaces §3 actions)

- [ ] Right side, left to right:
  - **Add:** `FilledTonalIconButton` (40dp) with `Icons.Filled.Add`, colours `secondaryContainer` / `onSecondaryContainer`, tooltip "New snippet". Opens the §7.1 Add state, pre-filled with the current folder. Snippets mode only.
  - **Open Pastille:** `IconButton` with `Icons.Outlined.Settings`, tooltip "Open Pastille". Opens the app's main screen.
  - **Switch keyboard:** `IconButton` with `Icons.Outlined.Keyboard`, tooltip "Switch keyboard".
- [ ] **Remove** the standalone save-clipboard icon (`ContentPasteGo`). Clipboard saving lives in the Add state's "From clipboard" row (§7.1). Two adjacent icons for adding was the confusion.
- [ ] I'm reversing my §3 call on the gear: on a keyboard the gear is _the_ convention for "go to this keyboard's app" (Gboard does exactly this), and the convention beats literal semantics. `OpenInNew` stays only on text links that say where they go ("Edit in Pastille ↗").
- [ ] Width check at 360dp: segmented about 176dp, Add 40dp, two icons 96dp, padding 24dp, about 336dp in total. If the label scale pushes past that (large font settings), the segments drop to icons only (`Icons.Outlined.ShortText` and `Icons.Outlined.Image`, with the same content descriptions). Decide with `BoxWithConstraints` at < 360dp available.

### 10.3 Looking at home next to Gboard

**Not achievable:** reading Gboard's actual theme. It's private to Gboard; there's no API or shared setting. Custom Gboard themes (photo backgrounds, solid colour themes, the "key borders" toggle) can't be followed.

**Achievable:** Gboard's default theme _is_ Material You from the wallpaper. With dynamic colour on (it already is), using the same tonal roles as Gboard makes Pastille sit beside it as a sibling. Roles:

| Gboard element                | Pastille element                                 | Role, light              | Role, dark           |
| ----------------------------- | ------------------------------------------------ | ------------------------ | -------------------- |
| Keyboard background           | Panel background                                 | `surfaceContainer`       | `surfaceContainer`   |
| Letter keys                   | Title buttons, folder buttons                    | `surfaceContainerLowest` | `surfaceBright`      |
| Function keys (shift, delete) | Add button, active segment                       | `secondaryContainer`     | `secondaryContainer` |
| Enter key                     | (nothing; only one accent, and we don't need it) | —                        | —                    |
| Key labels                    | Button text                                      | `onSurface`              | `onSurface`          |
| Toolbar icons                 | Toolbar icons                                    | `onSurfaceVariant`       | `onSurfaceVariant`   |

- [ ] Add a `keySurface` helper: `if (isDark) surfaceBright else surfaceContainerLowest`. That's the "raised key on a tinted tray" look; cards on `surfaceContainerHighest` read as cards, not keys.
- [ ] **Shapes:** keys are rounded rectangles, `RoundedCornerShape(8.dp)`, not pills. Chips stay M3 default (8dp). Tune against their phone; Gboard versions vary between about 6 and 10dp.
- [ ] **Icons:** `Icons.Outlined` throughout, 24dp. Gboard uses outlined Material Symbols at regular weight. No filled icons in the toolbar.
- [ ] **No elevation or shadows** on buttons, matching Gboard's flat keys.
- [ ] **Dark/light** follows the system (`isSystemInDarkTheme()`), as Gboard's default does.
- [ ] **Nav-bar strip:** in the screenshot the strip under the panel (⌄ and keyboard switcher) is black while the panel is grey. The panel background must extend under the navigation bar (§1). Set the IME window's navigation bar colour to transparent (`window.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false); it.navigationBarColor = Color.TRANSPARENT }`), so the strip shows `surfaceContainer` like Gboard's does. Engineering to confirm on API 35 behaviour.
- [ ] **Optional, not dev.3:** a Settings toggle "Match keyboard with key borders" for people who use Gboard's key borders, adding a 1dp `outlineVariant` border to keys. Only if the owner asks.

### 10.4 Folders (replaces §6.3 tabs and §6.4 chip row)

One level only, no nesting. A snippet is either at the top level (no folder) or in exactly one folder.

**Keyboard (Snippets mode)**

- [ ] **Top level:** folder buttons first (alphabetical, case-insensitive), then top-level snippets (pinned first, then recent; §6.1 ordering). Same grid, same button size (10.5).
- [ ] **Folder button:** `keySurface`, leading `Icons.Outlined.Folder` at 18dp in `onSurfaceVariant`, 8dp gap, name in `titleSmall` `onSurface` with ellipsis, trailing count in `labelSmall` `onSurfaceVariant`. Tap opens the folder.
- [ ] **Inside a folder:** the 40dp folder bar appears at the top of the content area: `IconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to all snippets")`, then folder name in `titleSmall`, then " · 6" count in `labelMedium` `onSurfaceVariant`. Below it, that folder's snippets.
- [ ] **Back:** the arrow, or the system back gesture while inside a folder. Intercept `KEYCODE_BACK` in `PastilleImeService.onKeyDown` only while not at top level; otherwise let back hide the keyboard as usual.
- [ ] The keyboard reopens in the folder you were last in. Folder state persists in DataStore; it resets to top level if that folder is deleted.
- [ ] Moving a snippet: the §7.2 long-press actions keep the single-select chip row, labelled "Folder", first chip "None (top level)".
- [ ] Creating, renaming and deleting folders stays in the app (needs typing).

**App**

- [ ] **List screen top level:** folder rows first, then top-level snippet rows (§6.1 style).
  - Folder row: `ListItem` with leading `Icons.Outlined.Folder` in `onSurfaceVariant`, headline = name (`titleMedium`), supporting text "6 snippets", trailing `Icons.AutoMirrored.Outlined.KeyboardArrowRight`. Same hairline dividers.
- [ ] **Folder screen:** a new destination.
  - Top bar: a `TopAppBar` (not large) with a back arrow and the folder name in `titleLarge`, plus an overflow with "Rename folder" and "Delete folder". Delete confirms: "Snippets in Work move to the top level." Snippets are never deleted with a folder.
  - FAB "New snippet" pre-fills this folder.
- [ ] **New folder:** header overflow "New folder" (§8.3), with an `AlertDialog` containing one text field.
- [ ] **Search** (§8.3) searches everything, with the folder name in each result's meta row.

### 10.5 Titles, and title-only buttons in the keyboard

**Every snippet has a title** (replaces the §7.1 "blank when short" rule)

- [ ] Wherever a snippet is created without a typed title (clipboard, Add from field, share, the editor left blank), store an auto title: first non-blank line, trimmed, cut at 40 characters on a word boundary with "…". A short single line becomes its own title.
- [ ] **Backfill:** existing snippets with a blank title get the auto title once, at app start after upgrade (one-off, idempotent). Engineering to place it: migration or startup task.
- [ ] **Identical titles in the same folder:** append " 2", " 3"… Three buttons with the same title are indistinguishable. Also keep the §7.1 duplicate-text check; the screenshot's three cards look like the same clipboard saved three times.

**Editor (`SnippetEditorScreen`)**

- [ ] Fields, in order:
  - `OutlinedTextField` label "Title", `singleLine`, supporting text "Shown on the keyboard button". When empty, the placeholder shows the live auto title in `onSurfaceVariant`.
  - 16dp gap.
  - `OutlinedTextField` label "Content", `minLines = 6`, fills the remaining height.
  - 16dp gap.
  - The "Folder" dropdown (§6.3 `ExposedDropdownMenuBox`, options "None (top level)" + folders).
- [ ] New snippet: focus goes to **Content** (you paste or type the content first; the title follows). Editing an existing snippet: no autofocus.
- [ ] Saving with an empty title stores the auto title.

**Keyboard buttons** (replace `SnippetCard` content cards in the keyboard)

- [ ] 2-column `LazyVerticalGrid`, 8dp spacing.
- [ ] Each snippet is a `Surface(onClick = insert, shape = RoundedCornerShape(8.dp), color = keySurface)`, `height(48.dp)`, 12dp horizontal padding. Row:
  - optional `Icons.Filled.PushPin` at 14dp in `primary` + 6dp gap
  - optional `Icons.Outlined.Image` at 18dp in `onSurfaceVariant` for image snippets
  - **title only**: `titleSmall`, `onSurface`, `maxLines = 1`, ellipsis
- [ ] **Tap** inserts the content (text, or image via the existing commit path). **Long-press** opens §7.2 actions, whose preview is where the content shows.
- [ ] `semantics`: `contentDescription = "Insert <title>"`, plus the §7.2 custom actions.
- [ ] The 224dp area fits 4 rows (4 × 48 + 3 × 8) at the top level, 3 rows inside a folder (below the folder bar), and scrolls beyond that.
- [ ] Empty states (§4, §6.4) keep their wording, centred in the 224dp area, with "folder" instead of "category": "Nothing in Work yet".

### 10.6 Motion (replaces the §7.0 transition)

There are three motions, and each has a different meaning, so they never blur together:

- **Modes slide sideways**, because they sit side by side in the switch.
- **Folders zoom**, because you go _into_ them.
- **Add and actions rise**, because they come up over the browse view.

Everything is about 220ms and stays inside the fixed 224dp content area.

**Shared tokens** (one `PastilleMotion` object, `ime/` package)

```kotlin
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)  // entering
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)  // leaving
const val ENTER_MS = 220
const val EXIT_MS = 120
const val FADE_IN_DELAY_MS = 60   // outgoing fades first, so the two never overlap muddily
```

- [ ] Wrap every `AnimatedContent` below in `Box(Modifier.height(224.dp).clipToBounds())`, so slides never draw over the toolbar and the panel height never changes. Pass `contentAlignment = Alignment.TopStart` and `transitionSpec = { … using SizeTransform(clip = false) { _, _ -> snap() } }`, which means no size animation.
- [ ] Interruptions: rapid taps (mode toggled twice, folder opened then backed out) are handled by `AnimatedContent` retargeting. Don't queue or debounce.

**1. Snippets ↔ Images (shared axis X, small offset)**

- [ ] Key the `AnimatedContent` on `mode`. Direction follows the switch: going to Images (right segment), content moves left; going back, it moves right.

```kotlin
val toImages = targetState == Mode.Images
val dir = if (toImages) 1 else -1
val offset = with(density) { 32.dp.roundToPx() }
(slideInHorizontally(tween(ENTER_MS, easing = EmphasizedDecelerate)) { dir * offset } +
    fadeIn(tween(ENTER_MS - FADE_IN_DELAY_MS, delayMillis = FADE_IN_DELAY_MS))) togetherWith
(slideOutHorizontally(tween(EXIT_MS, easing = EmphasizedAccelerate)) { -dir * offset } +
    fadeOut(tween(EXIT_MS)))
```

- [ ] 32dp offset, not full width. A full-width slide at keyboard scale feels like a page change and costs reading time.
- [ ] The source-chip row in Images mode belongs inside the animated content, so it travels with the grid.
- [ ] The segmented button's own selection animation is left as Material ships it.

**2. Opening and closing a folder (shared axis Z: scale + fade)**

- [ ] Key on `currentFolderId`. Opening means `target != null && initial == null`.

```kotlin
if (opening) {
    (scaleIn(tween(ENTER_MS, easing = EmphasizedDecelerate), initialScale = 0.92f) +
        fadeIn(tween(ENTER_MS - FADE_IN_DELAY_MS, delayMillis = FADE_IN_DELAY_MS))) togetherWith
    (scaleOut(tween(EXIT_MS, easing = EmphasizedAccelerate), targetScale = 1.04f) + fadeOut(tween(EXIT_MS)))
} else {
    (scaleIn(tween(ENTER_MS, easing = EmphasizedDecelerate), initialScale = 1.04f) +
        fadeIn(tween(ENTER_MS - FADE_IN_DELAY_MS, delayMillis = FADE_IN_DELAY_MS))) togetherWith
    (scaleOut(tween(EXIT_MS, easing = EmphasizedAccelerate), targetScale = 0.92f) + fadeOut(tween(EXIT_MS)))
}
```

- [ ] `transformOrigin`: opening scales from the tapped folder button's centre. Pass it as `TransformOrigin(x / width, y / height)` captured from the button's `onGloballyPositioned`. Closing scales back towards the centre (`TransformOrigin.Center`). If capturing the origin is fiddly, use `Center` for both. The zoom alone carries the meaning.
- [ ] The 40dp folder bar is part of the folder's content and zooms in with it. Don't animate it separately.
- [ ] The system back gesture inside a folder runs the same closing transition. Predictive back (`PredictiveBackHandler`) isn't needed for dev.3.

**3. Add state and long-press actions (shared axis Y: rise + fade)**

- [ ] Key the panel-state `AnimatedContent` on `PanelState` (`Browse`, `Add`, `Actions(snippetId)`, `EditingBanner`).

```kotlin
val rise = with(density) { 24.dp.roundToPx() }
if (targetState != PanelState.Browse) {          // entering Add / Actions
    (slideInVertically(tween(ENTER_MS, easing = EmphasizedDecelerate)) { rise } +
        fadeIn(tween(ENTER_MS - FADE_IN_DELAY_MS, delayMillis = FADE_IN_DELAY_MS))) togetherWith
    fadeOut(tween(90))
} else {                                          // back to Browse
    fadeIn(tween(ENTER_MS - FADE_IN_DELAY_MS, delayMillis = FADE_IN_DELAY_MS)) togetherWith
    (slideOutVertically(tween(EXIT_MS + 30, easing = EmphasizedAccelerate)) { rise } +
        fadeOut(tween(EXIT_MS + 30)))
}
```

- [ ] Browse doesn't move; it only fades, so the eye stays on what's arriving.
- [ ] The **toolbar's leading slot** (segmented switch ↔ back arrow + state title) crossfades on the same key: `AnimatedContent` with `fadeIn(tween(150, delayMillis = 60)) togetherWith fadeOut(tween(90))`, no slide. The right-hand icons don't move.
- [ ] Long-press: the haptic fires at press time, then the Actions state rises. Keep the button's own ripple; no extra press scale.
- [ ] After saving from Add: the return to Browse uses the "back to Browse" transition, then the §7.1 highlight on the new button (600ms `animateColorAsState` from `secondaryContainer` to `keySurface`) starts once the transition has settled. Gate it on `transition.isRunning == false`.
- [ ] Status strip (§7.0): `AnimatedVisibility` with `expandVertically(tween(180, easing = EmphasizedDecelerate)) + fadeIn(tween(180))` / `shrinkVertically(tween(120)) + fadeOut(tween(120))`. It lives inside the 224dp area and pushes content down within it, never growing the panel.

**Reduced motion**

- [ ] Compose's built-in animations follow the system's **animator duration scale** through the frame clock. Verify on device: Developer options → Animator duration scale 5x should visibly slow these transitions. Don't multiply durations by hand on top, or the scale gets applied twice.
- [ ] **Animations off** (scale 0, which is what Accessibility → "Remove animations" sets): check `ValueAnimator.areAnimatorsEnabled()` once when the panel opens and expose it as `LocalReduceMotion`. When true, every `transitionSpec` above returns `EnterTransition.None togetherWith ExitTransition.None`, `AnimatedVisibility` uses `EnterTransition.None` / `ExitTransition.None`, and the post-save highlight shows as a static `secondaryContainer` for 600ms then snaps back. Content swaps instantly; nothing is lost.
- [ ] Shimmer placeholders (§5) also stop when animations are off: a flat `surfaceContainerHigh` box.

## 11. Gboard-faithful styling + keyboard style setting — **dev.3, overrides §2 and §10.2–§10.3**

The owner's Gboard uses its plain **Dark** theme, not Material You, so dynamic colour can't match it. The keyboard gets its own palettes, sampled from their phone, and a setting to pick one. The **app** stays on Material You (§2); this section only changes the keyboard panel and its share/IME surfaces.

### 11.1 Gboard Dark, sampled from their screenshot (1080×2340, about 2.625 px/dp)

| Token            | Hex       | Where it was sampled                                                                   |
| ---------------- | --------- | -------------------------------------------------------------------------------------- |
| `strip`          | `#292E32` | Gboard's top icon strip                                                                |
| `tray`           | `#363B3F` | key area, and the nav-bar area below it (Gboard's tray runs under the gesture bar)     |
| `key`            | `#565B5F` | the visible key fills: `?123` and the space bar                                        |
| `keyPressed`     | `#646A6E` | **derived**, not visible in the screenshot: `key` lightened about 6% L. Tune on device |
| `stripButton`    | `#45494C` | the circle behind Gboard's mic in the strip                                            |
| `label`          | `#FFFFFF` | key and space-bar text                                                                 |
| `labelSecondary` | `#B1B2B5` | the small hint digits on keys                                                          |
| `icon`           | `#ACADB1` | strip icons (clipboard, GIF, emoji, translate), consistent across all five             |
| `accent`         | `#5F97F6` | the Enter key, Gboard's only colour                                                    |
| `onAccent`       | `#FFFFFF` | the Enter glyph                                                                        |

Geometry measured from the same screenshot:

- The strip is about 44dp tall.
- Function keys (space, `?123`) are **full pills**, about 36dp tall.
- Space-bar text is about 14sp.
- Strip icons are about 20dp glyphs in a 24dp box, evenly spaced across the width.
- No shadows, and no borders (their key-borders setting is off).

### 11.2 Gboard Light ⚠ provisional, not sampled

I don't have a screenshot of Gboard Light, so these are my best recollection of its default light theme and **must be re-sampled** before release. The owner: one screenshot from the other phone with Gboard open, and I'll replace them. Sampling is the same `magick -crop … -resize 1x1` average used for 11.1.

| Token            | Provisional hex |
| ---------------- | --------------- |
| `strip`          | `#E8EAED`       |
| `tray`           | `#F1F3F4`       |
| `key`            | `#FFFFFF`       |
| `keyPressed`     | `#E3E5E8`       |
| `stripButton`    | `#DADCE0`       |
| `label`          | `#202124`       |
| `labelSecondary` | `#5F6368`       |
| `icon`           | `#5F6368`       |
| `accent`         | `#1A73E8`       |
| `onAccent`       | `#FFFFFF`       |

### 11.3 How the palette reaches the UI

- [ ] `data class KeyboardPalette(strip, tray, key, keyPressed, stripButton, label, labelSecondary, icon, accent, onAccent)`, with `GboardDark`, `GboardLight` and `fromMaterialYou(colorScheme)` constructors.
- [ ] The panel root wraps its content in `MaterialTheme(colorScheme = palette.toColorScheme(base))`, so every M3 component from §7–§10 (chips, `ListItem`s, buttons, segmented control) picks up the palette with no per-component work:

| M3 role                                                                                      | ← palette                             |
| -------------------------------------------------------------------------------------------- | ------------------------------------- |
| `surface`, `surfaceContainer`, `surfaceContainerLow`                                         | `tray`                                |
| `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceBright`, `surfaceContainerLowest` | `key`                                 |
| `onSurface`                                                                                  | `label`                               |
| `onSurfaceVariant`                                                                           | `icon`                                |
| `outline`, `outlineVariant`                                                                  | `key`                                 |
| `secondaryContainer`                                                                         | `stripButton`                         |
| `onSecondaryContainer`                                                                       | `label`                               |
| `primary`                                                                                    | `accent`                              |
| `onPrimary`                                                                                  | `onAccent`                            |
| `error`                                                                                      | the base scheme's `error` (unchanged) |

- [ ] Also provide `LocalKeyboardPalette` for the three things M3 roles don't cover: `strip` (toolbar background), `keyPressed`, and `labelSecondary`.
- [ ] `fromMaterialYou`: superseded by the Gboard-dynamic mapping in §11.9.

### 11.4 Toolbar strip (overrides §10.2)

- [ ] Background `strip`, height 48dp (Gboard's 44dp plus the minimum touch target). The content area and nav-bar area below it are `tray`. This two-tone split is Gboard's.
- [ ] **No filled pills.** "+ New" becomes a plain `IconButton(Icons.Outlined.Add)` tinted `icon`, tooltip "New snippet", exactly like the other strip icons.
- [ ] **Mode switch:** the M3 `SegmentedButton` goes; Gboard has nothing like it. Use two text buttons, "Snippets" and "Images", in `labelLarge`. The selected one sits in a `stripButton` pill (`RoundedCornerShape(50)`, 32dp tall, 12dp horizontal padding) with text in `label`. The unselected one has no fill and text in `icon`. This echoes Gboard's mic circle, the only filled thing in its strip. The 120ms crossfade between pill positions is a `Modifier.background(animateColorAsState(...))`, with no sliding indicator. Same accessibility as before: `Role.Tab`, `selected` semantics.
- [ ] Layout: `Row { ModeSwitch(); Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) { Add; Settings; Keyboard } }`. The icons spread evenly like Gboard's strip, rather than bunching to the right. In Images mode, Add is hidden and the remaining two respace; that's acceptable.
- [ ] Icons: `Icons.Outlined.*`, 24dp, tint `icon`. Nothing filled, and no accent.

### 11.5 Keys: title, folder and action buttons (overrides §10.5's button styling)

- [ ] Title buttons and folder buttons are styled like Gboard's function keys: `Surface(color = key, shape = RoundedCornerShape(50))`, i.e. **full pills**, as measured, not the 8dp I guessed in §10.3.
  - Height 44dp, inside a 48dp touch row (`Modifier.minimumInteractiveComponentSize()`).
  - 16dp horizontal padding, 8dp grid gaps.
- [ ] Text: 14sp, `FontWeight.Medium`, colour `label`, `maxLines = 1`, ellipsis, **centred**, as Gboard centres its key labels. The pin glyph (14dp, `accent`) and folder/image glyphs (18dp, `icon`) sit before the text within the centred group.
- [ ] Pressed state: background animates to `keyPressed` (`animateColorAsState`, 60ms). Use `indication = null` instead of the M3 ripple; Gboard keys don't ripple.
- [ ] Folder buttons: the count in `labelSecondary`, 12sp, after the name. No trailing alignment; the content stays a centred group.
- [ ] §7 Add and Actions states: `ListItem`s on `tray`, action buttons as `key` pills, Delete stays `errorContainer`.
- [ ] **Accent** is used in exactly two places, like Gboard's Enter: the pin glyph and the post-save highlight (§7.1, now an `accent` at 25% alpha flash on the new button). Nowhere else.
- [ ] Image thumbnails (Images mode): 8dp corners on `key` placeholders. Unchanged otherwise.

### 11.6 No black strip under the panel

- [ ] **Android 12–14** (their phone is 12, API 31/32): in `onCreateInputView` / `onWindowShown`, set `window.window?.navigationBarColor = palette.tray.toArgb()` and `WindowInsetsControllerCompat(window, decorView).isAppearanceLightNavigationBars = palette.isLight`. Gboard does this; the gesture-bar area becomes `tray`, and the ⌄ and keyboard-switch glyphs flip to dark on Gboard Light.
- [ ] **Android 15+** (edge-to-edge enforced, `navigationBarColor` ignored): `WindowCompat.setDecorFitsSystemWindows(window, false)`. The panel's `tray` background draws under the nav bar, and content is padded by `WindowInsets.navigationBars` (§1).
- [ ] Re-apply both whenever the style setting changes while the keyboard is open.
- [ ] Verify on the Android 12 phone: no black band in either palette, gesture pill visible on both.

### 11.7 Keyboard style setting (in the app's Settings screen)

> The layout of this section is superseded by §12 (grouped cards). The options, preview and persistence below still hold.

- [ ] New section **"Keyboard style"** at the top of Settings, a single-choice list of `ListItem` + `RadioButton` rows (whole row clickable, `Modifier.selectable(role = Role.RadioButton)`):
  1. **Auto** (default): redefined in §11.9 as "Gboard's default for this Android version".
  2. **Match Gboard: Dark**: always `GboardDark`.
  3. **Match Gboard: Light**: always `GboardLight`.
  4. **Match Gboard: Material You**. Supporting text "Your wallpaper's colours". Uses the §11.9 mapping and follows system dark mode. Disabled below Android 12, with supporting text "Needs Android 12 or later".
- [ ] **Preview** above the list: a non-interactive render of the real keyboard panel at 0.8 scale inside a `Box` with 12dp corners and a 1dp `outlineVariant` border.
  - It shows the strip and four sample keys: folder "Work", "Home address", "Wi-Fi password" (pinned) and "Thanks!". Use fixed sample data, never the user's snippets, so the setting is never an accidental display of private text.
  - It updates instantly as a row is selected (`Crossfade`, 150ms).
  - It reuses the `KeyboardPanel` composable with a `previewMode` flag. Don't build a second drawing of the keyboard; it will drift.
- [ ] Persist in DataStore (`keyboard_style`: `auto | gboard_dark | gboard_light | material_you`). The IME collects it as a `Flow`, so a change applies the next time the panel draws, without reopening the keyboard.
- [ ] The setting affects the keyboard panel only. The app's own UI stays Material You (§2) whatever is picked.

### 11.8 What isn't possible

- **Reading Gboard's theme.** Gboard's choice (Dark, Light, Material You, colour or photo themes) is private to Gboard. There's no API or shared setting, which is why 11.7 is a manual setting.
- **Following custom Gboard themes:** photo backgrounds, colour themes, gradients.
- **Following the key-borders toggle.** Both of their phones would need it set the same; it could become a "Key borders" switch under 11.7 later if they ask.
- **Pixel-exact identity.** Gboard's font, key heights and spacing differ by version and device. The palettes are sampled from their phone (Dark) and recollection (Light), so they're close, not guaranteed identical.

### 11.9 Addendum: Gboard's Material You look, and asking on first run

The owner's Android 16 phone runs Gboard in its Material You (dynamic) theme; their Android 12 phone runs Gboard Dark. "Material You" therefore has to look like **Gboard's** dynamic theme, not generic M3, and the keyboard asks which style to use the first time it opens.

⚠ **Not sampled.** I have no screenshot of their Android 16 Gboard, so the mapping and Expressive shapes below are from my knowledge of Gboard's dynamic theme and M3 Expressive, and need checking against a screenshot. Same sampling method as 11.1. Ask the owner for one, with the keyboard open, in light and in dark if they can.

**Gboard-dynamic palette (`fromMaterialYou`, replaces the 11.3 bullet)**

Gboard's dynamic theme is a tinted neutral tray with lighter (or darker) neutral letter keys, secondary-tinted function keys, and a primary Enter key. Mapped from `dynamicLight/DarkColorScheme`:

| Token                                                  | Light                                                            | Dark                      |
| ------------------------------------------------------ | ---------------------------------------------------------------- | ------------------------- |
| `tray`                                                 | `surfaceContainer`                                               | `surfaceContainerLow`     |
| `strip`                                                | same as `tray` (Gboard's dynamic strip doesn't two-tone; verify) | same as `tray`            |
| `key` (title and folder buttons, like letter keys)     | `surfaceContainerLowest`                                         | `surfaceContainerHighest` |
| `keyPressed`                                           | `surfaceContainerHigh`                                           | `surfaceBright`           |
| `stripButton` (selected mode pill, like function keys) | `secondaryContainer`                                             | `secondaryContainer`      |
| `label`                                                | `onSurface`                                                      | `onSurface`               |
| `labelSecondary`, `icon`                               | `onSurfaceVariant`                                               | `onSurfaceVariant`        |
| `accent` / `onAccent` (like Enter)                     | `primary` / `onPrimary`                                          | `primary` / `onPrimary`   |

- [ ] The nav-bar area takes `tray` (11.6 applies unchanged).

**Expressive shapes (Android 16 only, Material You style only)**

The Gboard-flat styles (11.1/11.2) keep their pills on every Android version, because that's what Gboard Dark and Light look like. With Material You on API 36+, keys follow M3 Expressive:

- [ ] Rest shape `RoundedCornerShape(16.dp)` on the 44dp key. Rounder than a rectangle, squarer than a pill.
- [ ] **Press morph:** corners animate 16dp → 8dp while pressed and spring back on release. Use `animateDpAsState(if (pressed) 8.dp else 16.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))`, combined with the `keyPressed` colour. This shape change is the signature Expressive touch; keep it subtle.
- [ ] Strip icon buttons: with the Expressive style, the selected-mode pill uses the same spring when it moves between "Snippets" and "Images".
- [ ] Below API 36, Material You uses the 11.5 pill shapes. If the screenshot shows otherwise, follow the screenshot.
- [ ] With animations off (§10.6), the morph is skipped and only the colour changes.

**Auto, redefined: "Gboard's default for this Android version"**

We can't know what Gboard is set to, but we know what it ships with:

- Android 12+: Gboard defaults to its dynamic theme, so **Auto = Match Gboard: Material You**.
- Android 11 and below: **Auto = Gboard Dark / Light**, following system dark mode.

On their phones, Auto is right on the Android 16 one and wrong on the Android 12 one (where they chose Dark). That's what the first-run picker is for.

**First-run style picker (in the keyboard)**

- [ ] On the first panel opening (DataStore `keyboard_style_chosen == false`), the panel opens in a new **Style** state (a §7.0 panel state) instead of Browse, inside the same constant height. Motion: the §10.6 "rise".
- [ ] Layout, inside the 224dp content area:
  - `titleSmall` "Match your keyboard" plus `bodySmall` "Pick the look closest to your usual keyboard. You can change it later in Pastille settings.", both in `label`/`labelSecondary`, 16dp horizontal padding.
  - 12dp gap.
  - A `Row` of **three tiles** with 8dp gaps: "Material You", "Dark", "Light". "Material You" is omitted below Android 12, leaving two tiles.
    - Each tile is about 104 × 112dp, with 12dp corners.
    - A tile is a miniature drawn **with that palette**: its `strip` band (16dp) with three tiny icon dots, then two `key` pills on its `tray`, with the label under it in `labelMedium`.
    - Selected tile: 2dp `accent` border (the current palette's accent) plus a check badge.
  - 12dp gap.
  - A bottom row: `TextButton("Not now")` on the left, `FilledTonalButton("Done")` on the right.
- [ ] **Live preview:** tapping a tile applies that style to the **whole panel immediately** (strip, tray, nav-bar area, buttons; 150ms colour crossfade). The keyboard itself is the preview, sitting right next to the host app, which is the only comparison that matters. Tiles are only the choices.
- [ ] "Auto" is preselected when the picker opens, so the panel already shows Auto's guess.
- [ ] "Done" saves the chosen style, sets `keyboard_style_chosen = true`, and goes to Browse (the §10.6 "back to Browse" transition).
- [ ] "Not now" keeps **Auto** and sets the flag, so the picker doesn't nag. Back does the same.
- [ ] The app's setup card (the existing "Enable Pastille" steps) gets a step 3: "Pick a keyboard style", deep-linking to the 11.7 setting. The setting's preview shows the same three palettes plus Auto.
- [ ] TalkBack: the tiles are `Role.RadioButton` in a `selectableGroup()`, labelled "Material You style", "Dark style", "Light style".

### 11.10 Addendum: panel height (overrides the fixed 224dp in §10.1)

The owner's intent: "let the user have a clear vision of photos and snippets." The height is sized for that first and for Gboard second.

**Measured on their Android 12 phone** (screenshots at 1080×2340; from the panel's top edge to the nav-bar line at y = 2214 px):

- Gboard: 837 px = **319dp**.
- Pastille dev.3: 714 px = **272dp** (48dp toolbar + 224dp content).

That confirms 2.625 px/dp and a 411dp-wide screen.

**Arithmetic used below** (411dp width, 12dp side padding, 8dp top padding):

- Snippet rows: n rows = n × 48 + (n − 1) × 8 + 8.
- Image tiles: 3 columns, 6dp gaps, so each tile is 125dp. Source-chip row 48dp.

**Presets (portrait; total panel height, nav bar excluded)**

| Preset                    | Total     | Content   | Snippets visible, top level / in a folder | Photos visible (chips showing) |
| ------------------------- | --------- | --------- | ----------------------------------------- | ------------------------------ |
| Compact                   | 272dp     | 224dp     | 8 / 6                                     | 1 row + a third                |
| Gboard                    | 320dp     | 272dp     | 8 + peek / 8                              | 1 row + three-quarters         |
| **Comfortable (default)** | **368dp** | **320dp** | **10 + peek / 10**                        | **2 rows + a peek**            |
| Tall                      | 432dp     | 384dp     | 12 + peek / 12                            | 2½ rows                        |

- [ ] **Default: Comfortable, 368dp total**, 49dp taller than Gboard on purpose. It's the smallest height that meets the owner's goals on their phone:
  - 10 title buttons visible even inside a folder
  - two full rows of photos with a peek of the third, which tells you it scrolls

  On their 891dp-tall screen that's 41% of the height.

- [ ] **Portrait cap:** `min(preset, 0.5 × window height)`, so a small or split-screen window keeps at least half for the host app.
- [ ] **Landscape** is stored separately (`panel_height_landscape`), with the same four names as fractions of window height: Compact 38%, Gboard 44%, Comfortable 50% (default), Tall 55%. 55% is the hard cap, so the host's text field and its last messages stay usable. Wider grids (below) compensate for the lower height.
- [ ] Content area = total − 48dp toolbar. Every "224dp" in §7–§10 now means `contentHeight`. **Constant height still holds:** Browse, Add, Actions, Style, Preview and both modes all use the same `contentHeight` for the chosen preset. Only the setting changes it.

**Grids that use the height**

- [ ] **Snippets:** columns = `(width / 168dp).coerceIn(2, 4)`, which gives 2 in portrait and 3–4 in landscape. Rows as §11.5, 8dp bottom padding.
- [ ] **Images:** a scrolling `LazyVerticalGrid`, columns = `(width / 112dp).coerceIn(2, 6)`, which gives 3 on 336–447dp phones, 2 on narrower ones, and 5–6 in landscape.
  - **Square tiles** (`aspectRatio(1f)`), `ContentScale.Crop` with **`Alignment.TopCenter`**. Screenshots carry their meaning at the top (app bar, headline), so the crop keeps the top rather than the middle. Taller 3:4 tiles would cost a whole row: about 167dp each, only 1.6 rows at the default.
  - 6dp gaps, 8dp corners.
  - The source-chip row is the grid's first full-span item (`item(span = { GridItemSpan(maxLineSpan) })`), so it scrolls away and the photos get the whole height once you scroll.
- [ ] **Image preview (new):** long-press a thumbnail to open a **Preview** panel state (§10.6 "rise").
  - The image is fitted (`ContentScale.Fit`) in the content area on `tray`.
  - The §7.0 back arrow and title ("Screenshot · 14:32", or the file name if it's meaningful) appear in the toolbar.
  - A bottom `FilledTonalButton("Insert")`.
  - Tap still inserts straight away. This is the "clear vision" for one photo without leaving the keyboard.

**Setting (app → Settings, under "Keyboard style")**

- [ ] **"Keyboard height":** a `SingleChoiceSegmentedButtonRow` (this is the app, so M3 is right here) labelled "Portrait", with Compact / Standard / Roomy / Tall (one line each, no check icon; "Gboard" read as a brand rather than a height, and "Comfortable" wrapped). Under it, a second row labelled "Landscape" with the same four options. The stored keys stay `gboard` and `comfortable`.
- [ ] **Preview:** the 11.7 preview gains a phone silhouette.
  - A rounded outline at the device's real aspect ratio, about 200dp tall.
  - The panel drawn at its true share of the screen, with the host area above it in `surfaceContainerHighest` and three placeholder text lines.
  - Changing preset animates the panel height (`animateDpAsState`, 220ms, `EmphasizedDecelerate`), so the trade-off ("how much of my app do I give up") is visible at a glance.
  - While the landscape row is being changed, it shows a landscape silhouette.
- [ ] Persist in DataStore. The IME collects it as a `Flow` and resizes the next time the panel draws (11.7 rule).

**Drag handle: no, not in dev.3**

A drag strip in an IME fights grid scrolling and the system's swipe-down-to-dismiss, and dragging lands on arbitrary heights the grids weren't designed for. Gboard resizes from a menu, not a drag. The four presets cover the range. If the owner later wants fine control, it becomes a long-press on the toolbar that enters a resize state snapping between presets, never a free drag.

**First run:** the §11.9 Style picker doesn't ask about height. Comfortable is the default, and two questions on first open is one too many. Setup step 3 links to both settings.

### 11.11 Clipboard-model tiles (overrides §10.5 buttons, §11.5 layout, §11.10 grids)

The owner's model is now Gboard's clipboard panel: a fixed height, and a 2-column grid of **equal-size rounded tiles** for text and images alike. One tile geometry is used in every style (Gboard Dark, Gboard Light, Material You); only the colours change.

**Tile geometry**

- [ ] 2 columns in portrait (landscape per §11.10), `LazyVerticalGrid`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)`, 8dp gaps both ways.
- [ ] Every tile is **88dp tall** and full column width (about 195dp on their 411dp phone): Gboard's text tile measured 86dp. Never content-sized. Equal tiles are the point.
- [ ] `Surface(color = key, shape = RoundedCornerShape(10.dp))` (measured), no border, no elevation. Pressed state: `keyPressed`, 60ms, `indication = null` (§11.5 rule).

**Text snippet tile**

- [ ] 12dp padding, top-aligned `Column`:
  - Title: 16sp `FontWeight.Medium`, `label`, `maxLines = 1`, ellipsis.
  - 2dp gap.
  - Content preview: 14sp, `label` at 72% alpha, `maxLines = 2`, ellipsis.
- [ ] When the content equals the title (a short one-liner), hide the preview line and let the title take `maxLines = 3`.
- [ ] Pinned: `Icons.Filled.PushPin` at 14dp in `accent`, top-end at an 8dp inset (`Box` + `Alignment.TopEnd`). Pad the title end by 18dp when pinned.
- [ ] This keeps "the title is what you read" (§10.5) and adds Gboard's clipboard-style glimpse of the content. Tap inserts the content; long-press opens Actions (§7.2).

**Folder tile** (Snippets mode, top level, folders first)

- [ ] Same 88dp tile. A `Row`, vertically centred, 12dp padding:
  - leading `Icons.Outlined.Folder` at 24dp in `label`
  - 12dp gap
  - a `Column(weight 1f)` with the name (16sp Medium, `maxLines = 2`) and the count ("6 snippets", 14sp at 72% alpha)
  - trailing `Icons.AutoMirrored.Outlined.KeyboardArrowRight` at 24dp
- [ ] This is Gboard's tools-menu tile layout at clipboard-tile size.

**Image tile** (Images mode, and image snippets in Snippets mode)

- [ ] The image fills the tile: `ContentScale.Crop`, **`Alignment.TopCenter`**, clipped to the same 10dp shape. The top of a screenshot is its legible part.
- [ ] Placeholder and broken states as §5, on `key`.
- [ ] **Images mode:** no label, like Gboard's clipboard images. `contentDescription` = file name or "Screenshot, 14:32".
- [ ] **Image snippets in Snippets mode:** a bottom scrim (`Brush.verticalGradient(Transparent → Black 45%)`, 32dp tall) carrying the title at 14sp Medium, white, one line, 12dp start padding. This tells a saved image apart from the photos in Images mode.
- [ ] Wide 195 × 88dp tiles crop screenshots hard. That's the cost of equal tiles. Long-press opens the §11.10 **Preview** for the full image.

**Fixed height**

- [ ] The grid always fills `contentHeight` (§11.10). With only two snippets the panel is still full height, with empty `tray` below the tiles. It never shrinks to its content. That's the owner's "always the same, like the max size".
- [ ] Empty states centre in that same fixed area.

**Visible counts at the default (Comfortable, 328dp content):** 6 tiles plus a peek at the top level, 6 inside a folder, and about 2¾ rows of images with the source chips showing (table in §11.10).

### 11.12 Addendum: calm, neutral-first accents in the app, every API level (overrides §2 and §8.4 accent roles) — **Build B**

The owner, twice: "a bit too neon" (Android 16, cyan FAB `#00C4FF` and links) and "really cool but too neon vibe" (Android 12, lilac focus outline `#D0BCFF`, purple FAB `#4F378B`, lilac links, Settings headers and switch). Two causes. On API 34+, `dynamic*ColorScheme` reads Android 16's vibrant role resources. On every level, M3's `primary` is drawn from the **accent1** palette, the most chromatic one. They don't want it in either case. _(History: the first draft of this section moved `primary` to `system_accent1_*`; Android 12 showed that wasn't calm enough.)_

**The rule: neutral first, accent in small doses, and the accent comes from the low-chroma `accent2` palette.** It still follows the wallpaper, since accent2 is the wallpaper's own secondary palette, just quieter.

**The fix lives in one place, `Theme.kt`.** Keep `dynamic*ColorScheme(context)` for surfaces, neutrals, secondary, tertiary and error. Then `.copy(...)` the primary family from `system_accent2_*`, on **every** API 31+ level, not only 34+. No per-component colour overrides. FAB, `TextButton`, focused `OutlinedTextField` outline and label, tab indicator, `Switch`, Settings section headers (`colorScheme.primary`), pin glyph and links all inherit the change.

| Role                                                                                  | Dark                       | Light                      |
| ------------------------------------------------------------------------------------- | -------------------------- | -------------------------- |
| `primary` (links, focus outline and label, headers, tab indicator, pin, switch track) | `system_accent2_200` (T80) | `system_accent2_600` (T40) |
| `onPrimary` (switch thumb, filled-button text)                                        | `system_accent2_800` (T20) | `system_accent2_0` (T100)  |
| `primaryContainer` (the "New snippet" FAB)                                            | `system_accent2_700` (T30) | `system_accent2_100` (T90) |
| `onPrimaryContainer` (FAB icon and label)                                             | `system_accent2_100` (T90) | `system_accent2_900` (T10) |
| `inversePrimary`                                                                      | `system_accent2_600`       | `system_accent2_200`       |
| `surfaceTint`                                                                         | = `primary` above          | = `primary` above          |

- [ ] Read them with `colorResource(android.R.color.system_accent2_*)` inside the `SDK_INT >= S` branch.
- [ ] **Below Android 12** (static fallback), use the same idea with M3 baseline _secondary_ values in place of the purple primaries:
  - Dark: `primary #CCC2DC`, `onPrimary #332D41`, `primaryContainer #4A4458`, `onPrimaryContainer #E8DEF8`.
  - Light: `primary #625B71`, `onPrimary #FFFFFF`, `primaryContainer #E8DEF8`, `onPrimaryContainer #1D192B`.
- [ ] ⚠ **Check first:** `#D0BCFF` and `#4F378B` are _exactly_ M3's static baseline dark `primary` and `primaryContainer`. A real purple wallpaper rarely lands on those values, so on their Android 12 phone the editor or the whole app is probably not getting dynamic colour (a screen outside `PastilleTheme`, or `dynamicColor = false` somewhere). Confirm that every activity, the editor and the share sheet are wrapped in `PastilleTheme`, or the fix won't reach them.
- [ ] The FAB keeps its default colours (`primaryContainer`); don't set `containerColor`. In dark it becomes a muted slate tint of the wallpaper with pale text, a step above the surface.
- [ ] The focused text field keeps the M3 2dp outline. The change from a 1dp `outline` to a 2dp pale `primary` (T80, low chroma) is still clearly visible, so focus stays obvious without the glow.
- [ ] "Add your first snippet" (`FilledTonalButton`, `secondaryContainer`) is unchanged; it was already accent2.
- [ ] Contrast: T90 on T30, T20 on T80 and T10 on T90 all clear 4.5:1 for labels; check once on device in both themes.

**Keyboard consistency.** The keyboard doesn't change. In its Material You style (§11.9) it's built from accent1 at T10 and T20, which are near-neutral at those tones. Its only accent is the small T40 "active" circle, the same "accent in small doses" rule. The app and panel share the wallpaper's hue family and the same restraint; the app's accents are simply a shade quieter. The Gboard Dark and Light styles (§11.1–§11.2) are fixed palettes and are unaffected.

### 11.13 Motion pass: one vocabulary for the app and the keyboard (extends §10.6) — **Build B, last in the queue**

The owner: "We need effect between main root and opening a folder", then "nice and neat and subtle animations everywhere". As of `main` (c4d2715), the keyboard already zooms into folders (§10.6.2, `FolderContent`). The **app** cuts: `MainActivity` swaps screens with a bare `if / else` chain. This pass gives both surfaces one small vocabulary.

**Principles**

- **Every motion means something**, and the meaning is the same in the app and the keyboard:
  - _sideways (X)_: a sibling or the next screen;
  - _zoom (Z)_: into a folder;
  - _rise (Y)_: something coming up over the view;
  - _fade_: things appearing or leaving in place.
- **Quick:** nothing runs longer than 220ms, and nothing bounces (no springs with `dampingRatio < 1`).
- **Never in the way of pasting:** a tile's `onClick` commits the text at once, and no animation delays it, gates it or waits for one to finish. Taps during a transition are honoured, never queued or debounced (`AnimatedContent` retargets).

**Tokens.** Move `PastilleMotion` from `ime/` to a shared `ui/motion/` package so the app uses the same object. Add the `Standard` easing and two durations; keep the rest as they are.

```kotlin
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)  // entering
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)  // leaving
val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)                   // in-place changes (new)
const val PRESS_MS = 60     // press colour
const val QUICK_MS = 90     // fade-outs (new name for the existing 90s)
const val SHORT_MS = 150    // in-place state: colour, selection, item fade-in (new)
const val EXIT_MS = 120
const val ENTER_MS = 220
const val FADE_IN_DELAY_MS = 60
val AxisOffset = 32.dp      // X slides
val Rise = 24.dp            // Y rises
const val ZOOM_IN = 0.92f; const val ZOOM_OUT = 1.04f
```

Every spec below is made from these values; no literals elsewhere.

**Where each one goes**

| Place                                                                        | Motion           | Spec                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **App screens**: list ↔ editor, list ↔ settings, folder ↔ editor             | X                | `AnimatedContent` keyed on a `Screen` sealed type (`List`, `Folder(id)`, `Settings`, `Editor(id?, categoryId?)`), each with a depth (List 0, Folder/Settings 1, Editor 2). Deeper = forward: incoming `slideIn(+AxisOffset)` `ENTER_MS` `EmphasizedDecelerate`, plus `fadeIn(ENTER_MS − 60, delay 60)`; outgoing `slideOut(−AxisOffset)` `EXIT_MS` `EmphasizedAccelerate`, plus `fadeOut(EXIT_MS)`. Shallower = mirrored. The same shape as §10.6.1. `SizeTransform(clip = false)`, no size animation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **App folder** open / close                                                  | Z                | List ↔ `Folder(id)` overrides the X rule with the §10.6.2 zoom: open `scaleIn(ZOOM_IN)` + fade, and the list `scaleOut(ZOOM_OUT)` + fade; close mirrored. `transformOrigin` = the tapped `FolderRow`'s centre, from `onGloballyPositioned`, on open; `Center` on close.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Keyboard folder** open / close                                             | Z                | Already built (§10.6.2). Verify it still plays with the §11.11 tiles. If it cuts, the tile grid is being rendered outside `FolderContent`'s `AnimatedContent`. Add the same tapped-tile `transformOrigin`. **No container transform** (the tile literally growing into the view): at keyboard scale it's heavy and fussy, and zooming from the tile gives the same "I went into that" for free.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Back gesture** (app and keyboard)                                          | —                | Plays the backward version of whichever transition brought you there. Predictive back is out of scope.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Snippets ↔ Images** (keyboard)                                             | X                | §10.6.1, unchanged. The selected-pill background crossfade (§11.4) moves from 120ms to `SHORT_MS` with `Standard`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **Keyboard Add / Actions states**, status strip                              | Y                | §10.6.3, unchanged.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **List items**: add, delete, undo, reorder (app `LazyColumn`, keyboard grid) | fade + placement | `Modifier.animateItem(fadeInSpec = tween(SHORT_MS, easing = Standard), placementSpec = tween(ENTER_MS, easing = Standard), fadeOutSpec = tween(QUICK_MS))` on every item with a stable key. Undo brings the item back with the same fade-in while its neighbours part. No slide-in from an edge.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Swipe actions** (where `SwipeToDismissBox` exists)                         | colour           | **Thresholds:** delete (end to start) commits only once the row has travelled **40%** of its width; the secondary action (start to end, edit today) at **25%**. A fast flick doesn't count on its own: M3's fling threshold isn't configurable, so `confirmValueChange` refuses any action whose real offset (`requireOffset()`, against the width from `onSizeChanged`) is short of its threshold, and the row snaps back. The rule lives in `SwipeRules.kt`. **Feedback switches exactly at the threshold:** the background goes from `surfaceContainerHigh` to the action colour (`animateColorAsState`, `SHORT_MS`, `Standard`), the icon scales 0.85 → 1, and `HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE` plays (API 34+, else `CONTEXT_CLICK`). Dragging back below the threshold reverts the colour and scale, with `GESTURE_THRESHOLD_DEACTIVATE` on API 34+ (nothing below). The snap-back after an action has fired plays no haptic. On commit, the row leaves through `animateItem`'s fade-out, and the Undo snackbar is unchanged. |
| **Press feedback**, keyboard tiles                                           | colour only      | `key` → `keyPressed`, `tween(PRESS_MS)`, no ripple (§11.5). **Remove the `keyCorner` spring** in `KeyboardPanel`'s key composable (`animateDpAsState`, `dampingRatio = 0.6f`). That's the Expressive corner morph §11.9 dropped, and it bounces. The shape is fixed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Press feedback**, app rows and buttons                                     | ripple           | Material's default ripple. Nothing added.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Long-press menu** (app)                                                    | —                | `DropdownMenu`'s own scale-and-fade from its anchor, as shipped. A `LONG_PRESS` haptic at press time.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Long-press** (keyboard)                                                    | Y                | A `LONG_PRESS` haptic at press time, then the Actions state rises (§10.6.3).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **FAB**                                                                      | —                | `ExtendedFloatingActionButton`'s built-in expand/collapse on scroll. It travels with its screen in screen transitions and never animates on its own.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Settings switches, segmented buttons, radio rows**                         | —                | Material defaults, untouched; they're already quiet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Style picker tiles** (keyboard §11.9, app §11.7)                           | colour + check   | Border `animateColorAsState(SHORT_MS, Standard)`. The check badge uses `scaleIn(0.6f) + fadeIn`, `SHORT_MS`, `Standard`, and fades out over `QUICK_MS`. Palette crossfade 150ms, as specced.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Empty states** (app list, folder, keyboard)                                | fade             | The empty-state block uses `fadeIn(ENTER_MS − 60, delay 60)` when it appears and `fadeOut(QUICK_MS)` when the first item arrives. No illustration motion.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Post-save highlight**                                                      | colour           | §7.1 / §11.5, unchanged (600ms flash once the transition settles). It's the one deliberately longer motion, because it's a "here it is" cue rather than a transition.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

**Reduced motion: fades only.** This amends §10.6, where reduced motion meant no transitions at all.

- [ ] Provide `LocalReduceMotion` in `MainActivity` as well as the IME, from the same `PastilleMotion.reduceMotion()`.
- [ ] When it's true, every X, Z and Y transition above becomes `fadeIn(tween(SHORT_MS)) togetherWith fadeOut(tween(QUICK_MS))`, with no offset and no scale. `animateItem` keeps its fades and passes `placementSpec = null`. Swipe, press and selection colour changes stay: they're feedback, not movement. The check badge only fades.
- [ ] With "Remove animations" on (animator scale 0), Compose's clock makes those fades instant anyway. That's correct; nothing extra to do, and no hand-multiplying of durations.

## 12. Settings: grouped cards (overrides the layout of §11.7 and the §11.10 setting) — **v0.3**

The Settings screen was one long column of `ListItem`s under `primary` section headers. It becomes a few grouped cards, each row on an icon tile, so the screen can be scanned by shape before it's read. The reference app's grouped settings are borrowed for their organisation only: Pastille's palette and tokens, one accent for every tile, never a colour per row.

**Top bar**

- [x] `LargeTopAppBar` that collapses on scroll (`exitUntilCollapsedScrollBehavior`, wired with `nestedScroll` on the `Scaffold`).
- [x] The back arrow sits in a `FilledTonalIconButton`.

**Groups**, in this order: **Keyboard**, **Clipboard**, **Photos**, then a destructive card when there is one.

- [x] Each group is a `surfaceContainer` card: 20dp corners, 16dp side margins, 12dp between groups.
- [x] The group header sits above its card in `titleSmall` `onSurfaceVariant`, inset 16dp so it lines up with the card's content.
- [x] Rows inside a card are separated by a 1dp `outlineVariant` hairline that starts at the text, 72dp in (16dp padding + 36dp tile + 20dp gap).
- [ ] A group with no rows isn't drawn, header included.

| Group | Rows |
|---|---|
| Keyboard | Style (preview + the inline radio list), Height (preview, Portrait and Landscape segmented rows, the try-it field), Show in the keyboard (Snippets / Images), Return after a snippet, Switch back to |
| Clipboard | Clear clipboard after copying (a dialog of radio rows: Off, 5 s, 10 s, 30 s, 1 min) |
| Photos | Photo access, Return after an image |

**Row anatomy** (shared composables in `ui/SettingsGroup.kt`: `SettingsGroup`, `SettingsRow`, `SettingsRowDetail`, `SettingsHairline`, `SettingsChevron`)

- [x] **Icon tile:** 36dp, 10dp corners, `secondaryContainer`, with a 20dp `onSecondaryContainer` icon. The same accent on every row.
- [x] **Text:** title `bodyLarge` at medium weight in `onSurface`; subtitle `bodyMedium` `onSurfaceVariant`. Disabled rows draw both at 38%.
- [x] **Chevron** (`KeyboardArrowRight`, `onSurfaceVariant`) only on rows that open something: Switch back to (a menu) and Photo access while it can still ask (the system permission dialog). Switch rows keep their trailing `Switch`.
- [x] **The style radio list stays inline:** each option is a row whose 36dp leading slot holds the `RadioButton`, so its text lines up with the tiles above. No hairlines between the options; they're one choice.
- [x] **Detail content** that belongs to a row (previews, segmented buttons) sits under it at the card's full inner width (16dp each side), so the four height options never squeeze.
- [x] The whole row is the touch target (`selectable`, `toggleable` or `clickable` on the row), with Material's ripple.

**Destructive** (none today)

- [ ] When a destructive setting arrives, it gets a card of its own, last, with no header: an `errorContainer` tile with an `onErrorContainer` icon, and the title in `error`. `SettingsRow(destructive = true)` draws it. "Empty bin" stays in the Bin screen, not here.

**Motion**

- [x] §11.13 applies unchanged: ripple on press, Material defaults for switches, radios and segmented buttons, the style preview's 150ms crossfade and the height preview's `ENTER_MS` resize. The cards and tiles themselves never animate.

## 13. Quick Settings tile: the snippet picker sheet

The tile opens a sheet of snippets and images to copy, over whatever app is open. It no longer switches keyboards.

**Tile**

- [ ] Label "Pastille", subtitle "Snippets", the Pastille mark as the icon.
- [ ] A stateless button, always `STATE_INACTIVE`, like Android's screen recorder and QR scanner tiles. It is no longer an on/off toggle.
- [ ] Locked → unlock first (`unlockAndRun`). Snippets never show over the keyguard.

**Sheet**

```
  ─────                       [⌨]
 [🔍 Search snippets          ]
 (Snippets | Images)
 (All) (▭ Personal) (▭ Prompts)
 ┌────┐ ┌────┐ ┌────┐
 │ …  │ │ …  │ │ …  │
```

- [ ] A `ModalBottomSheet` in a translucent `PickerActivity`. It opens at half height and drags to full. It wears the keyboard palette, so it reads as the panel.
- [ ] The content is the keyboard panel's own layout (shared composables, not a copy): the Snippets/Images switch, the folder chips, the 88dp tile grid, and recent images under Images. `ShareSheet` is not reused: that is a save flow, this is a pick flow.
- [ ] **Search** sits at the top and is not focused on open, so the keyboard doesn't jump up. Under Snippets it matches title or text across every folder, and the folder chips hide while it has a query. Under Images it matches the file name.
- [ ] **Tap** copies, records the use, then closes the sheet. Text is a plain clip; an image is a `FileProvider` URI clip. Android 13+ shows its own clipboard confirmation; below 13, a "Copied" toast.
- [ ] **Long-press** opens the actions in the sheet: Edit in app, Delete (with Undo in a strip at the bottom), Share for image snippets, and the Folder chips to move it. On a recent image: Copy and Share.
- [ ] **[⌨]** in the header opens the system keyboard picker. The keyboard switch lives here now.
