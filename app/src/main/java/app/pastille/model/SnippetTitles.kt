package app.pastille.model

fun uniqueTitle(title: String, taken: Collection<String>): String {
    val lowered = taken.mapTo(HashSet()) { it.lowercase() }
    if (title.lowercase() !in lowered) return title
    var suffix = 2
    while ("$title $suffix".lowercase() in lowered) suffix++
    return "$title $suffix"
}
