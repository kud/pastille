#!/usr/bin/env sh
# Regenerates the store and README screenshots, and F-Droid's feature graphic, from the real
# composables on invented fixtures (app/src/test/java/app/pastille/screenshots/StoreScreenshots.kt).
# Writes fastlane/metadata/android/en-US/images/phoneScreenshots/*.png, images/featureGraphic.png and
# images/icon.png, and the README icon assets/icon.png (both from the adaptive launcher icon).
# Extra arguments go to Gradle, e.g. `./scripts/screenshots.sh --offline`.
set -eu
cd "$(dirname "$0")/.."
# Clear every output first, so the closing ls proves this run wrote them.
rm -f fastlane/metadata/android/en-US/images/phoneScreenshots/*.png fastlane/metadata/android/en-US/images/featureGraphic.png \
  fastlane/metadata/android/en-US/images/icon.png assets/icon.png
./gradlew "$@" -Pscreenshots :app:testDebugUnitTest --tests 'app.pastille.screenshots.*'
ls -1 fastlane/metadata/android/en-US/images/phoneScreenshots/*.png fastlane/metadata/android/en-US/images/featureGraphic.png fastlane/metadata/android/en-US/images/icon.png assets/icon.png
