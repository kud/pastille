# ADR 0001: In-app updates, two flavours, and how the updater is shared

- Status: accepted (Erwann, 2026-10-30: « oui »)
- Decided by: Maya (CTO); design of the About row and the ⋮ item by Iris

## Context

Erwann installs Pastille from GitHub Releases and wants it to update itself the way tutti does
(« ça me soûle d'installer une autre app alors que c'est built-in là »; Obtainium rejected). Pastille's
founding rule was "no `INTERNET` permission". F-Droid would also flag a self-updating app
(`NonFreeNet`/self-update anti-features), and F-Droid users already get updates from F-Droid.

## Decision

**Two product flavours, one codebase** (`distribution` dimension in `app/build.gradle.kts`):

| | `github` | `fdroid` |
|---|---|---|
| Who gets it | GitHub Releases, `pastille-X.Y.Z.apk` | F-Droid, `pastille-X.Y.Z-fdroid.apk` (`Binaries`) |
| Permissions added | `INTERNET`, `REQUEST_INSTALL_PACKAGES` (`src/github/AndroidManifest.xml`) | none |
| Updater code | `src/github/java/app/pastille/update/` | none compiled in; `src/fdroid/.../ui/Updates.kt` draws nothing |
| applicationId, key | `app.beansontoast.pastille`, Pastille's key | the same |

- **Same applicationId and key for both.** Erwann's current install (0.1.5, which predates flavours) is
  a github build in all but name: the next GitHub release installs over it in place. F-Droid
  publishes our signed fdroid APK when it reproduces (`AllowedAPKSigningKeys`), so a phone can move
  between the two without reinstalling. A separate id for fdroid would have split one person's
  snippets across two apps for no gain.
- **The no-network guarantee is enforced, not conventional.** `checkNoNetwork<Variant>` reads every
  variant's merged manifest (so a library's permission counts too) and fails an fdroid variant that
  asks for `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE` or
  `REQUEST_INSTALL_PACKAGES`. Every `assemble<Variant>` and `lint<Variant>` depends on it. It fails a
  github variant that *lacks* `INTERNET`, which proves it reads the right file. The release job checks
  the built fdroid APK's badging a second time.
- **What the github build contacts:** `api.github.com` (the release list) and GitHub's download hosts
  (`github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`), HTTPS on
  the default port, system trust store, redirects followed by hand and each hop checked
  (`Hosts`). Only to check for and download updates. The request carries a `User-Agent` with the
  version and nothing else: no snippet, clipboard, image or identifier ever leaves the phone.
- **What it installs:** the release asset named `pastille-X.Y.Z.apk` (never `-fdroid` or `-debug`), only
  from `github.com/kud/pastille/releases/download/`, only if its SHA-256 equals the `digest` GitHub
  reports for the asset, and only if the file is this package at that exact version, never older
  (`ArchiveCheck`). A dev build keeps the shipped `versionCode`, so an equal code is accepted and the
  version name decides. Android's PackageInstaller then asks Erwann to confirm. Never in the
  background, never without that tap.
- **Channels:** "Test builds" (Settings › About) offers the `-dev.N` pre-release published on every
  push to `main`; off, only final releases. It defaults to on when a test build is installed.
- **Pure logic lives in `src/github/.../update/Updates.kt`**, Pastille having no `:protocol` module:
  plain Kotlin with no Android import, tested by `src/testGithub/.../UpdatesTest.kt`.

## How the updater is shared across apps ("On peut le standardiser ?")

Options weighed:

1. **A shared library repository** (Maven/JitPack or a Git submodule). One source of truth, but a new
   repository and a publishing pipeline, and F-Droid needs every dependency built from source, which
   a JitPack artifact or submodule complicates. Worth it from the third app, not the second.
2. **A Gradle module copied verbatim.** Clean boundary, but tutti's rules sit inside its `:protocol`
   module and Pastille has a single `:app`; a module per app for 250 lines is ceremony.
3. **One file copied verbatim, with a sync header** (chosen). `Updates.kt` is the whole rule set and is
   written to be identical across apps: everything app-specific (repository, which asset is this
   build's APK) comes in through `UpdateSource`, and the update channel through `Channel`. Each app
   keeps a small Android shell (`Updater.kt`, `UpdateInstallReceiver.kt`) because they differ in DI
   (tutti: Hilt and OkHttp; Pastille: a plain singleton and `HttpsURLConnection`, no new dependency).

Pastille's copy is now the reference: it generalises tutti's (`UpdateSource`, `Channel`,
`ArchiveCheck`). The next time tutti's updater is touched, port this file back over its
`protocol/update/Updates.kt` (only the package line differs) and drop its `Flavour` enum.

**Revisit** when a third app takes the updater, or the copies drift twice: then extract a small
`kud/updater` library (needs Erwann's go for the new repository).

## Consequences

- CI runs both flavours' unit tests and lint; the release job builds, signs and checks both APKs, and
  proves the fdroid one reproducible. F-Droid's recipe builds `gradle: [fdroid]`.
- A release now carries `pastille-X.Y.Z.apk`, `pastille-X.Y.Z-fdroid.apk` and `SHA256SUMS`; the
  workflow fails if GitHub's reported asset digests differ from the ones it computed.
