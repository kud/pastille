package app.pastille.model

/** Tags are stored lowercased, without spaces or a leading "#"; empty input is no tag. */
fun normaliseTag(raw: String): String =
    raw.trim().trimStart('#').lowercase().filterNot { it.isWhitespace() || it == ',' || it == '#' }

/** How a tag reads on screen: sentence case, never all caps ("email" → "Email"). */
fun displayTag(tag: String): String = tag.replaceFirstChar { it.titlecase() }

/**
 * Splits what was typed into the tag field: a comma or a space ends a tag. Returns the finished
 * tags and the text still being typed.
 */
fun splitTagInput(input: String): Pair<List<String>, String> {
    val parts = input.split(',', ' ', '\n')
    val finished = parts.dropLast(1).map(::normaliseTag).filter { it.isNotEmpty() }
    return finished to parts.last()
}

fun addTags(current: List<String>, added: List<String>): List<String> =
    (current + added.map(::normaliseTag)).filter { it.isNotEmpty() }.distinct()

/** Snippets carrying every selected tag. No selection keeps everything. */
fun filterByTags(snippets: List<SnippetRecord>, selected: Set<String>): List<SnippetRecord> =
    if (selected.isEmpty()) snippets else snippets.filter { it.tags.containsAll(selected) }

/** Search matches the title, the text and the tag names. */
fun matchesQuery(snippet: SnippetRecord, query: String): Boolean {
    val needle = query.trim().trimStart('#')
    if (needle.isEmpty()) return true
    return snippet.title.contains(needle, ignoreCase = true) ||
        snippet.text.contains(needle, ignoreCase = true) ||
        snippet.tags.any { it.contains(needle, ignoreCase = true) }
}

/** Existing tags that start with what is being typed, not already on the snippet. */
fun tagSuggestions(all: List<String>, typed: String, current: List<String>, limit: Int = 5): List<String> {
    val prefix = normaliseTag(typed)
    return all.filter { it !in current && (prefix.isEmpty() || it.startsWith(prefix)) }.take(limit)
}
