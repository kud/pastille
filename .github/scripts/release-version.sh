#!/usr/bin/env bash
# Checks a release tag against the app and, for a final release, writes the version bump.
#
#   release-version.sh check vX.Y.Z[-dev.N]   validate only; prints KEY=value lines for $GITHUB_ENV
#   release-version.sh bump  vX.Y.Z           validate, then bump app/build.gradle.kts and the F-Droid recipe
#
# Run from the repository root. Never commits: the workflow does that as Erwann.
set -euo pipefail

mode=${1:?usage: release-version.sh check|bump vX.Y.Z[-dev.N]}
tag=${2:?usage: release-version.sh check|bump vX.Y.Z[-dev.N]}
gradle=app/build.gradle.kts
recipe=docs/fdroid/app.beansontoast.pastille.yml
changelogs=fastlane/metadata/android/en-US/changelogs

fail() { echo "::error::$*" >&2; exit 1; }

[[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)(-dev\.([0-9]+))?$ ]] \
  || fail "Tag $tag is not vX.Y.Z or vX.Y.Z-dev.N"
version=${BASH_REMATCH[1]}
dev=${BASH_REMATCH[3]:-}

current_name=$(sed -n 's/^ *versionName = "\([^"]*\)"$/\1/p' "$gradle")
current_code=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\)$/\1/p' "$gradle")
[ -n "$current_name" ] && [ -n "$current_code" ] || fail "versionName or versionCode not found in $gradle"
[ "$(grep -c '^ *versionName = ' "$gradle")" = 1 ] || fail "$gradle must set versionName exactly once"
[ "$(grep -c '^ *versionCode = ' "$gradle")" = 1 ] || fail "$gradle must set versionCode exactly once"

# Both kinds name the version still to ship: 0.1.6-dev.1 and 0.1.6 both follow 0.1.5.
if [ "$version" = "$current_name" ] \
  || [ "$(printf '%s\n%s\n' "$current_name" "$version" | sort -V | tail -n 1)" != "$version" ]; then
  fail "$version must be above the current versionName $current_name"
fi

if [ -n "$dev" ]; then
  # A dev build keeps the shipped versionCode, so the next final release (code + 1) installs over it.
  echo "PRERELEASE=true"
  echo "VERSION_NAME=$version-dev.$dev"
  echo "VERSION_CODE=$current_code"
  [ "$mode" = check ] || fail "Only a final vX.Y.Z is bumped; a dev build overrides versionName at build time"
  exit 0
fi

next_code=$((current_code + 1))
changelog="$changelogs/$next_code.txt"
[ -s "$changelog" ] || fail "$changelog is missing: write the release notes for versionCode $next_code first"
chars=$(python3 -c 'import sys; print(len(open(sys.argv[1], encoding="utf-8").read()))' "$changelog")
[ "$chars" -le 500 ] || fail "$changelog is $chars characters; F-Droid allows 500"

echo "PRERELEASE=false"
echo "VERSION_NAME=$version"
echo "VERSION_CODE=$next_code"
echo "CHANGELOG=$changelog"
[ "$mode" = bump ] || exit 0

# The recipe's new build points at the tag: the bump commit cannot hold its own hash.
python3 - "$recipe" "$version" "$next_code" <<'PY'
import re, sys
path, name, code = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, encoding="utf-8").read()
builds = re.search(r"^Builds:\n((?:  .*\n|\n(?=  ))+)", text, re.M)
if not builds:
    sys.exit("::error::no Builds section in " + path)
entries = re.split(r"(?m)^  - ", builds.group(1))
last = "  - " + entries[-1].rstrip("\n") + "\n"
if re.search(rf"(?m)^    versionCode: {code}$", builds.group(1)):
    sys.exit(f"::error::{path} already has a build for versionCode {code}")
entry = re.sub(r"(?m)^(  - versionName: ).*$", rf"\g<1>{name}", last)
entry = re.sub(r"(?m)^(    versionCode: ).*$", rf"\g<1>{code}", entry)
entry = re.sub(r"(?m)^(    commit: ).*$", rf"\g<1>v{name}", entry)
text = text[: builds.end()].rstrip("\n") + "\n\n" + entry + "\n" + text[builds.end():].lstrip("\n")
for key, value in (("CurrentVersion", name), ("CurrentVersionCode", code)):
    text, n = re.subn(rf"(?m)^{key}: .*$", f"{key}: {value}", text)
    if n != 1:
        sys.exit(f"::error::{key} not found once in {path}")
open(path, "w", encoding="utf-8").write(text)
PY

sed -i "s/^\( *versionName = \)\"[^\"]*\"$/\1\"$version\"/; s/^\( *versionCode = \)[0-9][0-9]*$/\1$next_code/" "$gradle"

grep -q "^ *versionName = \"$version\"$" "$gradle" && grep -q "^ *versionCode = $next_code$" "$gradle" \
  || fail "The bump did not land in $gradle"
