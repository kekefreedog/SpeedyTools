#!/usr/bin/env bash
# Bumps the mod version across every file that hardcodes it.
#
# Usage: scripts/bump-version.sh <new-version>
#   e.g. scripts/bump-version.sh 4.0.0
#
# Updates:
#   - build.gradle              (version = "X.Y.Z")
#   - SpeedyToolsMod.java       (@Mod(... version="X.Y.Z") and VERSION constant)
#
# Deliberately does NOT touch src/main/resources/mcmod.info: its version/mcversion
# fields are stale placeholders by design, expanded to the real values by the
# processResources Gradle task at build time (see CLAUDE.md).

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <new-version>" >&2
  exit 1
fi

NEW_VERSION="$1"

if [[ ! "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: version must look like X.Y.Z (got '$NEW_VERSION')" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BUILD_GRADLE="$ROOT_DIR/build.gradle"
MOD_JAVA="$ROOT_DIR/src/main/java/speedytools/SpeedyToolsMod.java"

CURRENT_VERSION=$(grep -m1 -oE '^version = "[0-9]+\.[0-9]+\.[0-9]+"' "$BUILD_GRADLE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')

if [[ -z "$CURRENT_VERSION" ]]; then
  echo "Error: could not find current version in $BUILD_GRADLE" >&2
  exit 1
fi

if [[ "$CURRENT_VERSION" == "$NEW_VERSION" ]]; then
  echo "Already at version $NEW_VERSION, nothing to do."
  exit 0
fi

sedi() {
  # portable in-place sed for both BSD (macOS) and GNU sed
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

sedi "s/^version = \"$CURRENT_VERSION\"/version = \"$NEW_VERSION\"/" "$BUILD_GRADLE"
sedi "s/version=\"$CURRENT_VERSION\"/version=\"$NEW_VERSION\"/" "$MOD_JAVA"
sedi "s/VERSION = \"$CURRENT_VERSION\"/VERSION = \"$NEW_VERSION\"/" "$MOD_JAVA"

echo "Bumped version: $CURRENT_VERSION -> $NEW_VERSION"
echo
echo "Changed lines:"
grep -n "version" "$BUILD_GRADLE" | grep -E "= \"$NEW_VERSION\"" || true
grep -n "$NEW_VERSION" "$MOD_JAVA" || true
