package app.pastille.model

fun moveCategory(categories: List<CategoryRecord>, id: Long, delta: Int): List<CategoryRecord> {
    val sorted = categories.sortedBy { it.position }
    val from = sorted.indexOfFirst { it.id == id }
    if (from == -1 || delta == 0) {
        return sorted.mapIndexed { index, category -> category.copy(position = index) }
    }
    val to = (from + delta).coerceIn(0, sorted.lastIndex)
    if (to == from) {
        return sorted.mapIndexed { index, category -> category.copy(position = index) }
    }
    val reordered = sorted.toMutableList()
    val moved = reordered.removeAt(from)
    reordered.add(to, moved)
    return reordered.mapIndexed { index, category -> category.copy(position = index) }
}

fun orderCategories(categories: List<CategoryRecord>, newOrder: List<Long>): List<CategoryRecord> {
    val byId = categories.associateBy { it.id }
    val ordered = newOrder.mapNotNull { byId[it] } + categories.filter { it.id !in newOrder }.sortedBy { it.position }
    return ordered.mapIndexed { index, category -> category.copy(position = index) }
}
