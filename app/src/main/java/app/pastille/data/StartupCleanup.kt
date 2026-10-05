package app.pastille.data

/**
 * Runs once at app start. The bin is purged first, so the orphan sweep that follows sees the
 * purged rows gone and can delete their image files; a file whose snippet is still in the bin
 * stays referenced and survives.
 */
suspend fun cleanUpAtStart(
    purgeExpiredBin: suspend () -> Unit,
    referencedImageFiles: suspend () -> Set<String>,
    sweepOrphans: suspend (referenced: Set<String>) -> Unit,
) {
    purgeExpiredBin()
    sweepOrphans(referencedImageFiles())
}
