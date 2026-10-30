package app.pastille.update

/**
 * Pastille's side of the shared rules. Releases carry `pastille-X.Y.Z.apk` (this, the github build)
 * and `pastille-X.Y.Z-fdroid.apk` (the F-Droid build, which never self-updates); a debug build has
 * no asset and never offers an update.
 */
val PastilleSource = UpdateSource("kud/pastille") { name ->
    name.startsWith("pastille-") && name.endsWith(".apk") &&
        !name.endsWith("-fdroid.apk") && !name.endsWith("-debug.apk")
}
