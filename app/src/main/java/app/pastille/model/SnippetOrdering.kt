package app.pastille.model

fun sortSnippets(records: List<SnippetRecord>): List<SnippetRecord> =
    records.sortedWith(
        compareBy<SnippetRecord> { it.position }
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

// The list on screen can lag behind the database after a quick second drag, so slots and the
// "already there" check come from the positions stored now, never from the shown records.
fun positionWrites(shown: List<SnippetRecord>, stored: Map<Long, Int>, newOrder: List<Long>): Map<Long, Int> {
    val fresh = shown.mapNotNull { record -> stored[record.id]?.let { record.copy(position = it) } }
    return reassignPositions(fresh, newOrder).filter { (id, position) -> stored[id] != position }
}

