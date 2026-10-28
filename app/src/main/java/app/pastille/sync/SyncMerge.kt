package app.pastille.sync

import java.util.UUID

/** One snippet as sync sees it: identified by [uid], its folder by [categoryUid], its tags by name. */
data class SyncSnippet(
    val uid: String,
    val title: String = "",
    val text: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long = 0,
    val categoryUid: String? = null,
    val imageFile: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val position: Int = 0,
    val deletedAt: Long? = null,
    val sticker: Boolean = false,
    val tags: List<String> = emptyList(),
)

data class SyncCategory(
    val uid: String,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class SyncTag(
    val uid: String,
    val name: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class SyncTombstone(
    val uid: String,
    val kind: String,
    val deletedAt: Long,
)

/** A whole collection: what a device holds, what the shared file holds, or what they last agreed on. */
data class SyncState(
    val snippets: List<SyncSnippet> = emptyList(),
    val categories: List<SyncCategory> = emptyList(),
    val tags: List<SyncTag> = emptyList(),
    val tombstones: List<SyncTombstone> = emptyList(),
)

/**
 * What a merge produced. [renamedSnippets] and [renamedCategories] map a local uid to the uid it
 * now has: the same item created on both phones (same text, same image, same folder name) becomes
 * one, and both phones keep the smaller uid, so they converge without deleting and re-adding rows.
 */
data class MergeResult(
    val state: SyncState,
    val renamedSnippets: Map<String, String> = emptyMap(),
    val renamedCategories: Map<String, String> = emptyMap(),
    val conflicts: Int = 0,
)

/**
 * Two-way sync between this phone ([local]) and the shared file ([remote]), given what they last
 * agreed on ([base], null before the first sync). Pure: no Android, no I/O, so every rule is a JVM
 * test.
 *
 * - Items match by uid. Each field merges three-way against the base: the side that changed it
 *   wins; when both changed it, the later edit (`updatedAt`) wins.
 * - Title and text (and an image's file) are one unit. When both sides edited it since the base and
 *   the results differ, both are kept: the later edit keeps the uid, the other becomes a copy whose
 *   uid is derived from the original uid and its content, so both phones derive the same copy.
 * - Without a base for an item, the whole record with the later edit wins.
 * - `lastUsedAt` is not an edit: the later use is kept, whoever wins.
 * - Deletion: a tombstone removes the item, unless the item was edited after the deletion (then the
 *   edit wins and the tombstone goes). An item missing on one side with no tombstone is kept: a
 *   merge never loses data on an absence. The bin is a field (`deletedAt`), merged like the others.
 * - Items only one side knows that are the same thing (same text, or same image file, in the same
 *   bin/sticker state; folders by name, case-insensitively) become one, under the smaller uid.
 * - Folder membership is by folder uid; a folder that no longer exists leaves the snippet unfiled.
 *   Tags are names (unique and never renamed, so the name is the identity); a tag carried by no
 *   snippet goes, as in the app.
 * - Tombstones older than [TOMBSTONE_KEEP_MS] are pruned.
 */
object SyncMerge {
    const val TOMBSTONE_KEEP_MS = 365L * 24 * 60 * 60 * 1000

    fun merge(base: SyncState?, local: SyncState, remote: SyncState, now: Long): MergeResult {
        val tombstones = (local.tombstones + remote.tombstones)
            .groupBy { it.uid }
            .mapValues { (_, all) -> all.maxBy { it.deletedAt } }
            .toMutableMap()

        val categories = mergeCategories(base, local, remote, tombstones)
        val snippets = mergeSnippets(base, local, remote, tombstones, categories.renames)

        val liveCategories = categories.items.associateBy { it.uid }
        val finalSnippets = snippets.items.map { snippet ->
            val folder = snippet.categoryUid?.let { categories.aliases[it] ?: it }
            snippet.copy(categoryUid = folder?.takeIf { it in liveCategories }, tags = normaliseTags(snippet.tags))
        }.sortedBy { it.uid }

        val usedTags = finalSnippets.flatMap { it.tags }.toSet()
        val tagsByName = (local.tags + remote.tags).groupBy { it.name.lowercase() }
        val finalTags = usedTags.map { name ->
            val known = tagsByName[name].orEmpty()
            if (known.isEmpty()) {
                SyncTag(uid = derivedUid("tag", name), name = name)
            } else {
                SyncTag(
                    uid = known.minOf { it.uid },
                    name = name,
                    createdAt = known.minOf { it.createdAt },
                    updatedAt = known.maxOf { it.updatedAt },
                )
            }
        }.sortedBy { it.uid }

        val horizon = now - TOMBSTONE_KEEP_MS
        val finalTombstones = tombstones.values.filter { it.deletedAt >= horizon }.sortedBy { it.uid }

        return MergeResult(
            state = SyncState(finalSnippets, categories.items.sortedBy { it.uid }, finalTags, finalTombstones),
            renamedSnippets = snippets.renames,
            renamedCategories = categories.renames,
            conflicts = snippets.conflicts,
        )
    }

    // ---- Folders ----

    private class CategoryOutcome(
        val items: List<SyncCategory>,
        val renames: Map<String, String>,
        // Every uid that ended up under another (from either side), for snippet references.
        val aliases: Map<String, String>,
    )

    private fun mergeCategories(
        base: SyncState?,
        local: SyncState,
        remote: SyncState,
        tombstones: MutableMap<String, SyncTombstone>,
    ): CategoryOutcome {
        val baseByUid = base?.categories?.associateBy { it.uid }.orEmpty()
        val localByUid = local.categories.associateBy { it.uid }
        val remoteByUid = remote.categories.associateBy { it.uid }
        val merged = (localByUid.keys + remoteByUid.keys).mapNotNull { uid ->
            val b = baseByUid[uid]
            val l = localByUid[uid]
            val r = remoteByUid[uid]
            val item = when {
                l != null && r != null -> mergeCategory(b, l, r)
                else -> l ?: r!!
            }
            survivesTombstone(uid, item.updatedAt, changedSinceBase = b == null || b != item, tombstones)
                ?.let { item }
        }
        // Same name, different uids: one folder, under the smaller uid, with the later edit's fields.
        val aliases = mutableMapOf<String, String>()
        val items = merged.groupBy { it.name.lowercase() }.map { (_, same) ->
            if (same.size == 1) return@map same.single()
            val survivor = same.minOf { it.uid }
            val latest = same.maxWith(compareBy<SyncCategory> { it.updatedAt }.thenBy { it.uid })
            same.forEach { if (it.uid != survivor) aliases[it.uid] = survivor }
            latest.copy(uid = survivor, createdAt = same.minOf { it.createdAt })
        }
        val renames = local.categories.mapNotNull { c -> aliases[c.uid]?.let { c.uid to it } }.toMap()
        return CategoryOutcome(items, renames, aliases)
    }

    private fun mergeCategory(b: SyncCategory?, l: SyncCategory, r: SyncCategory): SyncCategory {
        if (l == r) return l
        val localLater = l.updatedAt > r.updatedAt || (l.updatedAt == r.updatedAt && l.name >= r.name)
        if (b == null) return if (localLater) l else r
        return SyncCategory(
            uid = l.uid,
            name = pick(b.name, l.name, r.name, localLater),
            position = pick(b.position, l.position, r.position, localLater),
            createdAt = minOf(l.createdAt, r.createdAt),
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
        )
    }

    // ---- Snippets ----

    private class SnippetOutcome(val items: List<SyncSnippet>, val renames: Map<String, String>, val conflicts: Int)

    private fun mergeSnippets(
        base: SyncState?,
        local: SyncState,
        remote: SyncState,
        tombstones: MutableMap<String, SyncTombstone>,
        categoryRenames: Map<String, String>,
    ): SnippetOutcome {
        val baseByUid = base?.snippets?.associateBy { it.uid }.orEmpty()
        val localByUid = local.snippets.associateBy { it.uid }
        val remoteByUid = remote.snippets.associateBy { it.uid }
        val out = linkedMapOf<String, SyncSnippet>()
        var conflicts = 0

        fun put(item: SyncSnippet) {
            val existing = out[item.uid]
            out[item.uid] = if (existing == null) item else mergeSnippet(null, existing, item).first
        }

        // Items only one side knows that are the same thing become one, under the smaller uid.
        val localOnly = local.snippets.filter { it.uid !in remoteByUid }
        val remoteOnly = remote.snippets.filter { it.uid !in localByUid }
        val remoteOnlyByKey = remoteOnly.groupBy(::contentKey)
        val renames = mutableMapOf<String, String>()
        val paired = mutableMapOf<String, SyncSnippet>() // local uid -> remote twin
        val takenRemote = mutableSetOf<String>()
        localOnly.forEach { l ->
            if (tombstones[l.uid] != null) return@forEach
            val twin = remoteOnlyByKey[contentKey(l)].orEmpty()
                .firstOrNull { it.uid !in takenRemote && tombstones[it.uid] == null } ?: return@forEach
            takenRemote += twin.uid
            paired[l.uid] = twin
        }

        (localByUid.keys + remoteByUid.keys).forEach { uid ->
            if (uid in takenRemote) return@forEach
            val b = baseByUid[uid]
            val l = localByUid[uid]
            val r = remoteByUid[uid] ?: l?.let { paired[it.uid] }
            if (l != null && r != null && l.uid != r.uid) {
                val survivor = minOf(l.uid, r.uid)
                if (survivor != l.uid) renames[l.uid] = survivor
                put(mergeSnippet(null, l.copy(uid = survivor), r.copy(uid = survivor)).first)
                return@forEach
            }
            val merged: List<SyncSnippet> = when {
                l != null && r != null -> {
                    val (winner, copy) = mergeSnippet(b, l, r)
                    if (copy != null) conflicts++
                    listOfNotNull(winner, copy)
                }
                else -> listOf(l ?: r!!)
            }
            val first = merged.first()
            val changed = b == null || b != first
            if (survivesTombstone(uid, first.updatedAt, changed, tombstones) != null) put(first)
            merged.drop(1).forEach(::put)
        }
        val items = out.values.map { s -> s.categoryUid?.let { categoryRenames[it] }?.let { s.copy(categoryUid = it) } ?: s }
        return SnippetOutcome(items, renames, conflicts)
    }

    /** Returns the merged record and, when both sides edited the text differently, the other side's copy. */
    private fun mergeSnippet(b: SyncSnippet?, l: SyncSnippet, r: SyncSnippet): Pair<SyncSnippet, SyncSnippet?> {
        if (l == r) return l to null
        val localLater = l.updatedAt > r.updatedAt || (l.updatedAt == r.updatedAt && contentOf(l).toString() >= contentOf(r).toString())
        val lastUsedAt = maxOf(l.lastUsedAt, r.lastUsedAt)
        val createdAt = minOf(l.createdAt, r.createdAt)
        if (b == null) {
            val winner = if (localLater) l else r
            return winner.copy(lastUsedAt = lastUsedAt, createdAt = createdAt) to null
        }
        val lc = contentOf(l)
        val rc = contentOf(r)
        val bc = contentOf(b)
        var copy: SyncSnippet? = null
        val content = when {
            lc == rc -> lc
            lc == bc -> rc
            rc == bc -> lc
            else -> {
                val loser = if (localLater) r else l
                copy = loser.copy(uid = derivedUid(loser.uid, contentOf(loser).toString()), lastUsedAt = loser.lastUsedAt)
                if (localLater) lc else rc
            }
        }
        val merged = SyncSnippet(
            uid = l.uid,
            title = content.title,
            text = content.text,
            imageFile = content.imageFile,
            imageWidth = content.imageWidth,
            imageHeight = content.imageHeight,
            pinned = pick(b.pinned, l.pinned, r.pinned, localLater),
            categoryUid = pick(b.categoryUid, l.categoryUid, r.categoryUid, localLater),
            position = pick(b.position, l.position, r.position, localLater),
            deletedAt = pick(b.deletedAt, l.deletedAt, r.deletedAt, localLater),
            sticker = pick(b.sticker, l.sticker, r.sticker, localLater),
            tags = pick(b.tags.sorted(), l.tags.sorted(), r.tags.sorted(), localLater),
            createdAt = createdAt,
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
            lastUsedAt = lastUsedAt,
        )
        return merged to copy
    }

    private data class Content(
        val title: String,
        val text: String,
        val imageFile: String?,
        val imageWidth: Int?,
        val imageHeight: Int?,
    )

    private fun contentOf(s: SyncSnippet) = Content(s.title, s.text, s.imageFile, s.imageWidth, s.imageHeight)

    // The app's own duplicate rule: text by exact text, images by their content-addressed file.
    private fun contentKey(s: SyncSnippet): String =
        "${s.deletedAt != null}|${s.sticker}|" + (s.imageFile?.let { "image:$it" } ?: "text:${s.text}")

    // ---- Shared rules ----

    private fun <T> pick(base: T, local: T, remote: T, localLater: Boolean): T = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        localLater -> local
        else -> remote
    }

    /**
     * Null when a tombstone deletes the item. An item edited after its deletion survives, and its
     * tombstone goes, so the other phone doesn't delete it again.
     */
    private fun survivesTombstone(
        uid: String,
        updatedAt: Long,
        changedSinceBase: Boolean,
        tombstones: MutableMap<String, SyncTombstone>,
    ): Unit? {
        val tomb = tombstones[uid] ?: return Unit
        if (changedSinceBase && updatedAt > tomb.deletedAt) {
            tombstones.remove(uid)
            return Unit
        }
        return null
    }

    private fun normaliseTags(tags: List<String>): List<String> =
        tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()

    /** A uid both phones derive alike from the same inputs. */
    fun derivedUid(vararg parts: String): String =
        UUID.nameUUIDFromBytes(parts.joinToString("\u0000").toByteArray()).toString()
}
