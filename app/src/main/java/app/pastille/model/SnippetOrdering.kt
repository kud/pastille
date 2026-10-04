package app.pastille.model

fun sortSnippets(records: List<SnippetRecord>): List<SnippetRecord> =
    records.sortedWith(
        compareByDescending<SnippetRecord> { it.pinned }
            .thenBy { it.position }
            .thenByDescending { it.lastUsedAt },
    )

// A drag reorders one visible list (a folder, or the top level) without disturbing the rest:
// the same position values are handed out again in the new order.
fun reassignPositions(shown: List<SnippetRecord>, newOrder: List<Long>): Map<Long, Int> {
    val slots = shown.map { it.position }.sorted().let { positions ->
        // Ties (a fresh migration, or an import) would make the order collapse, so spread them first.
        if (positions.toSet().size == positions.size) positions else positions.indices.map { positions.first() + it }
    }
    val byId = shown.associateBy { it.id }
    val ordered = newOrder.mapNotNull { byId[it] } + shown.filter { it.id !in newOrder }
    return ordered.zip(slots).associate { (record, slot) -> record.id to slot }
}

// The new order keeps pinned snippets ahead of the rest, whatever the drag did.
fun keepPinnedFirst(shown: List<SnippetRecord>, newOrder: List<Long>): List<Long> {
    val pinned = shown.filter { it.pinned }.map { it.id }.toSet()
    return newOrder.filter { it in pinned } + newOrder.filter { it !in pinned }
}
