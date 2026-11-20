# F-Droid

`app.beansontoast.pastille.yml` is the recipe for [fdroiddata](https://gitlab.com/fdroid/fdroiddata), copied there as `metadata/app.beansontoast.pastille.yml`. It lives here so it is reviewed with the code it builds; fdroiddata holds the copy F-Droid reads.

## How it publishes

F-Droid builds each tag's `fdroid` flavour from source (no `INTERNET`, no updater; [ADR 0001](../adr/0001-in-app-updates.md)) and compares the result with `pastille-X.Y.Z-fdroid.apk`, which the release workflow attaches to the GitHub release (`Binaries`). The release's plain `pastille-X.Y.Z.apk` is the github flavour, which updates itself; F-Droid never ships it. When they match apart from the signature, it publishes the GitHub APK, signed with Pastille's own key (`AllowedAPKSigningKeys`, the SHA-256 of the release certificate). One key everywhere: a phone can move between GitHub and F-Droid builds without reinstalling.

New releases are picked up from `vX.Y.Z` tags (`UpdateCheckMode`); pre-release tags (`vX.Y.Z-…`) are skipped. The version comes from `versionCode` and `versionName` in `app/build.gradle.kts`.

The release workflow (`docs/RELEASING.md`) keeps this copy in sync: each final release replaces the build with its own (`commit: vX.Y.Z`, since the bump commit cannot hold its own hash) and moves `CurrentVersion` and `CurrentVersionCode`. Once the app is in fdroiddata, its own `AutoUpdateMode: Version` adds each new tag's build there, so nobody edits fdroiddata per release; this copy is then a mirror, worth keeping for review and for the first submission.

## Submitting to fdroiddata

The 0.1.5 build in this copy cannot be built: `v0.1.5` predates the two flavours, so it has no `fdroid` flavour to assemble and its release has no `pastille-0.1.5-fdroid.apk` for `Binaries`. The first version F-Droid can take is the next final release, 0.1.6 (`versionCode` 17, whose changelog is already written); its bump replaces the 0.1.5 build here.

fdroiddata's merge request template asks for the full commit hash rather than a tag, only the latest build, and no disabled builds. Once `vX.Y.Z` is released, print the recipe as fdroiddata wants it:

```sh
git fetch --tags
./scripts/fdroid-recipe.sh vX.Y.Z > ../fdroiddata/metadata/app.beansontoast.pastille.yml
```

It refuses a tag whose recipe holds anything but that one build. The merge request itself must follow fdroiddata's template (`.gitlab/merge_request_templates/Default.md`) section by section, ticked: a first attempt (fdroiddata!51195, October 2026) was closed for not following it. The template asks to pay special attention to the inclusion policy's quality point 6, three months of active development, which Pastille reaches on 2027-01-04 (first commit 2026-10-04).

## Keeping the build reproducible

- Every zip entry carries a fixed timestamp, and the baseline profile (`assets/dexopt/`) is deterministic on AGP 8.7.
- `dependenciesInfo` is off: the dependency block is Google-encrypted and F-Droid rejects it.
- `vcsInfo` is off for release, so the build does not depend on a `.git` directory.
- The release workflow builds on Temurin 17 and fails a release whose clean unsigned rebuild does not match the signed APK. Keep it there; a change of JDK, AGP, Kotlin or Gradle is a change to check again.
- Paths do not leak: two clean builds from different directories are byte-identical.

To check a release by hand, build the tag's unsigned release and compare it with the published APK:

```sh
git checkout vX.Y.Z
./gradlew clean assembleFdroidRelease
apksigcopier compare pastille-X.Y.Z-fdroid.apk --unsigned app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
```

`apksigcopier` (from PyPI) needs `apksigner` from the Android build-tools on the `PATH`. Silence means the two match.
