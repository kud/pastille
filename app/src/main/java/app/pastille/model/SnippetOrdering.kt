package app.pastille.model

fun sortSnippets(records: List<SnippetRecord>): List<SnippetRecord> =
    records.sortedWith(
        compareByDescending<SnippetRecord> { it.pinned }
            .thenByDescending { it.lastUsedAt },
    )
