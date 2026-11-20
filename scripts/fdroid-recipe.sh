#!/usr/bin/env bash
# Prints the F-Droid recipe as fdroiddata wants it for a released tag:
#   ./scripts/fdroid-recipe.sh v0.1.6 > ../fdroiddata/metadata/app.beansontoast.pastille.yml
# The copy in docs/fdroid/ is written by the release bump, which cannot know its own commit hash, so
# its build says `commit: vX.Y.Z`. fdroiddata asks for the full hash (its merge request template:
# "Please don't use tag or branch in commit"). This reads the recipe as the tag holds it, checks that
# it is the tag's build, and swaps the tag for the hash it points at. Run from a clone with the tag.
set -euo pipefail
cd "$(dirname "$0")/.."

tag=${1:?usage: fdroid-recipe.sh vX.Y.Z}
recipe=docs/fdroid/app.beansontoast.pastille.yml
fail() { echo "fdroid-recipe: $*" >&2; exit 1; }

[[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || fail "$tag is not a final vX.Y.Z tag; F-Droid skips pre-releases"
version=${BASH_REMATCH[1]}
hash=$(git rev-parse --verify --quiet "refs/tags/$tag^{commit}") || fail "no tag $tag here (git fetch --tags?)"
text=$(git show "$hash:$recipe" 2>/dev/null) || fail "$tag has no $recipe"

RECIPE="$text" python3 - "$version" "$tag" "$hash" <<'PY'
import os, re, sys
version, tag, commit = sys.argv[1:]
text = os.environ["RECIPE"]
fail = lambda msg: sys.exit(f"fdroid-recipe: {msg}")
builds = re.findall(r"(?m)^  - versionName: (.*)$", text)
if builds != [version]:
    fail(f"{tag}'s recipe should hold exactly one build, {version}; it holds {builds}")
if not re.search(rf"(?m)^CurrentVersion: {re.escape(version)}$", text):
    fail(f"{tag}'s recipe does not name {version} as CurrentVersion")
text, n = re.subn(rf"(?m)^(    commit: )(?:{re.escape(tag)}|[0-9a-f]{{40}})$", rf"\g<1>{commit}", text)
if n != 1:
    fail(f"{tag}'s build has no `commit: {tag}` line to replace")
sys.stdout.write(text if text.endswith("\n") else text + "\n")
PY
