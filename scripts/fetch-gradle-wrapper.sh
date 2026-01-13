#!/usr/bin/env bash
set -euo pipefail

# Simple helper to download the Gradle wrapper JAR on-demand to keep binaries out of version control.
# Requires: curl, unzip.

SCRIPT_DIR="$(cd -- "$(dirname "$0")" >/dev/null 2>&1 && pwd)"
REPO_ROOT="${SCRIPT_DIR%/scripts}"
PROPS_FILE="$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties"
TARGET_JAR="$REPO_ROOT/gradle/wrapper/gradle-wrapper.jar"

if [[ -f "$TARGET_JAR" ]]; then
  echo "Gradle wrapper jar already present at $TARGET_JAR"
  exit 0
fi

if [[ ! -f "$PROPS_FILE" ]]; then
  echo "Missing $PROPS_FILE; cannot determine distributionUrl" >&2
  exit 1
fi

DISTRIBUTION_URL=$(grep '^distributionUrl=' "$PROPS_FILE" | cut -d'=' -f2-)
# distributionUrl in Gradle properties escapes ':' as '\\:'; normalize it for curl.
DISTRIBUTION_URL=${DISTRIBUTION_URL//\\:/:}
if [[ -z "$DISTRIBUTION_URL" ]]; then
  echo "distributionUrl not found in $PROPS_FILE" >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

ARCHIVE="$WORKDIR/gradle-dist.zip"
echo "Downloading Gradle distribution from $DISTRIBUTION_URL ..."
curl -sSL "$DISTRIBUTION_URL" -o "$ARCHIVE"

# Extract only the wrapper jar from the distribution (exclude gradle-wrapper-shared).
ZIP_PATH=$(unzip -Z1 "$ARCHIVE" | grep -E 'gradle-wrapper-[^/]*\.jar$' | grep -v 'gradle-wrapper-shared' | head -n 1)
if [[ -z "$ZIP_PATH" ]]; then
  echo "Gradle wrapper jar not found in distribution archive" >&2
  exit 1
fi

echo "Extracting wrapper jar from $ZIP_PATH..."
unzip -p "$ARCHIVE" "$ZIP_PATH" > "$TARGET_JAR"
chmod +x "$TARGET_JAR"
echo "Wrapper jar written to $TARGET_JAR"
