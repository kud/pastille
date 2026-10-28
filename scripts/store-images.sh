#!/usr/bin/env sh
# Regenerates the F-Droid store images and the README icons from the real composables on invented data
# (app/src/test/java/app/pastille/redesign/RedesignRenders.kt, light theme, 1080x2340):
#   fastlane/metadata/android/en-US/images/phoneScreenshots/1..6-*.png
#   fastlane/metadata/android/en-US/images/icon.png (512px)
#   assets/icon.png and assets/icon-monochrome.png (1024px)
# Sacha's featureGraphic.png/.svg is hand-made and never touched here.
# Extra arguments go to Gradle, e.g. `./scripts/store-images.sh --offline`.
set -eu
cd "$(dirname "$0")/.."
renders="$(mktemp -d)"
trap 'rm -rf "$renders"' EXIT
shots=fastlane/metadata/android/en-US/images/phoneScreenshots
PASTILLE_RENDERS="$renders" ./gradlew "$@" :app:testDebugUnitTest \
  --tests 'app.pastille.redesign.ListLight' --tests 'app.pastille.redesign.StoreKeyboard' \
  --tests 'app.pastille.redesign.StoreClipboard' --tests 'app.pastille.redesign.StickersLight' \
  --tests 'app.pastille.redesign.EditorLight' --tests 'app.pastille.redesign.SettingsLight' \
  --tests 'app.pastille.redesign.StoreIcon'
# Copy only once every render exists, so a failed run never leaves a half-updated listing.
for f in list-light store-keyboard-light store-clipboard-light stickers-light editor-light settings-light \
  store-icon-512 store-icon-1024 store-icon-monochrome-1024; do
  [ -s "$renders/redesign-$f.png" ] || { echo "missing render: $f" >&2; exit 1; }
done
rm -f "$shots"/*.png "$shots"/*.jpg
cp "$renders/redesign-list-light.png" "$shots/1-snippet-list.png"
cp "$renders/redesign-store-keyboard-light.png" "$shots/2-keyboard-snippets.png"
cp "$renders/redesign-store-clipboard-light.png" "$shots/3-keyboard-clipboard.png"
cp "$renders/redesign-stickers-light.png" "$shots/4-stickers.png"
cp "$renders/redesign-editor-light.png" "$shots/5-snippet-editor.png"
cp "$renders/redesign-settings-light.png" "$shots/6-settings.png"
cp "$renders/redesign-store-icon-512.png" fastlane/metadata/android/en-US/images/icon.png
cp "$renders/redesign-store-icon-1024.png" assets/icon.png
cp "$renders/redesign-store-icon-monochrome-1024.png" assets/icon-monochrome.png
ls -1 "$shots" fastlane/metadata/android/en-US/images/icon.png assets/icon.png assets/icon-monochrome.png
