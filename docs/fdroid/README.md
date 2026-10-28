# F-Droid

`app.beansontoast.pastille.yml` is the recipe for [fdroiddata](https://gitlab.com/fdroid/fdroiddata), copied there as `metadata/app.beansontoast.pastille.yml`. It lives here so it is reviewed with the code it builds; fdroiddata holds the copy F-Droid reads.

## How it publishes

F-Droid builds each tag from source and compares the result with the APK the release workflow attaches to the GitHub release (`Binaries`). When they match apart from the signature, it publishes the GitHub APK, signed with Pastille's own key (`AllowedAPKSigningKeys`, the SHA-256 of the release certificate). One key everywhere: a phone can move between GitHub and F-Droid builds without reinstalling.

New releases are picked up from `vX.Y.Z` tags (`UpdateCheckMode`); pre-release tags (`vX.Y.Z-…`) are skipped. The version comes from `versionCode` and `versionName` in `app/build.gradle.kts`.

The release workflow (`docs/RELEASING.md`) keeps this copy in sync: each final release adds its build (`commit: vX.Y.Z`, since the bump commit cannot hold its own hash) and moves `CurrentVersion` and `CurrentVersionCode`. Once the app is in fdroiddata, its own `AutoUpdateMode: Version` does the same there from the tag, so nobody edits fdroiddata per release; this copy is then a mirror, worth keeping for review and for the first submission.

## Keeping the build reproducible

- Every zip entry carries a fixed timestamp, and the baseline profile (`assets/dexopt/`) is deterministic on AGP 8.7.
- `dependenciesInfo` is off: the dependency block is Google-encrypted and F-Droid rejects it.
- `vcsInfo` is off for release, so the build does not depend on a `.git` directory.
- The release workflow builds on Temurin 17 and fails a release whose clean unsigned rebuild does not match the signed APK. Keep it there; a change of JDK, AGP, Kotlin or Gradle is a change to check again.
- Paths do not leak: two clean builds from different directories are byte-identical.

To check a release by hand, build the tag's unsigned release and compare it with the published APK:

```sh
git checkout vX.Y.Z
./gradlew clean assembleRelease
apksigcopier compare pastille-X.Y.Z.apk --unsigned app/build/outputs/apk/release/app-release-unsigned.apk
```

`apksigcopier` (from PyPI) needs `apksigner` from the Android build-tools on the `PATH`. Silence means the two match.
