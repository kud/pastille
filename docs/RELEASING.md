# Releasing

One way to release: run the **android** workflow (`.github/workflows/ci.yml`) by hand on `main`, with a tag. It is the same shape as tutti's: the run first does the full check (tests, lint, debug and release builds, Room schemas) on that commit, and releases only if it passes.

Actions › android › Run workflow, branch `main`:

| `tag`            | What happens                                                                                                                       |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| empty            | The check only.                                                                                                                    |
| `vX.Y.Z-dev.N`   | A pre-release for testing: signed, `versionName` X.Y.Z-dev.N, no commit, tag on the commit you ran on. F-Droid ignores it.         |
| `vX.Y.Z`         | A final release, for GitHub and F-Droid (below).                                                                                   |

Tick `dry_run` to do everything except push, tag and release; it runs from any branch, and the run summary says what it would have published. The APK and notes are kept as the run's `pastille-release` artifact either way.

## A final release

Before running it, commit `fastlane/metadata/android/en-US/changelogs/<code>.txt` to `main`, where `<code>` is the current `versionCode` + 1, at most 500 characters. It becomes the GitHub release notes and F-Droid's "What's new".

The run then:

1. Refuses unless the tag is new, `X.Y.Z` is above the current `versionName`, and the changelog is there and short enough (`.github/scripts/release-version.sh`).
2. Bumps `versionName` to X.Y.Z and `versionCode` by one in `app/build.gradle.kts`, adds the build to `docs/fdroid/app.beansontoast.pastille.yml` and moves its `CurrentVersion*`, and commits `🏷️ release(pastille): bump version to X.Y.Z` as Erwann Mest.
3. Builds the signed release. The keystore is written for that one step and removed after it.
4. Checks the APK carries Pastille's certificate (the recipe's `AllowedAPKSigningKeys`) and the new version.
5. Proves it reproducible: rebuilds the unsigned release from a `git archive` of the bump commit in another directory, without `.git`, and runs `apksigcopier compare` against the signed APK. Any difference fails the release, since F-Droid would reject it.
6. Pushes the bump to `main` and the tag `vX.Y.Z` on it in one atomic push (if `main` moved during the run, neither lands: run it again), then creates the GitHub release with `pastille-X.Y.Z.apk` and the changelog as notes.

F-Droid then finds the tag (`UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$`), builds it, and publishes our APK once it matches. See `docs/fdroid/README.md`.

## Dev pre-releases and version codes

`versionCode` is a plain counter that F-Droid and the changelogs key on, so there is no number between two releases. A dev build keeps the shipped `versionCode` and only changes its name (`PASTILLE_VERSION_NAME`), signed with the same key: it installs over the last release (same code), a later dev build installs over it, and the next final release (code + 1) replaces it.

## Secrets and settings

- `PASTILLE_KEYSTORE_BASE64` and `PASTILLE_KEYSTORE_PASSWORD`: the release keystore (alias `pastille`), used by the release job only. Without them a release fails rather than ship unsigned.
- `PASTILLE_RELEASE_TOKEN` (optional): only needed if `main` ever gets a rule the workflow token can't meet (required pull requests or status checks, push restrictions). A fine-grained token on this repository with Contents read and write, from an account allowed to bypass that rule. Without it the workflow token pushes, as it can today.
