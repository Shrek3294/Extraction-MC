#!/usr/bin/env bash
set -euo pipefail

# Simple helper to download the Gradle wrapper JAR on-demand to keep binaries out of version control.
# Requires: curl, unzip, and either `jar` (from a JDK) or `zip`.

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
DISTRIBUTION_URL=${DISTRIBUTION_URL//$'\r'/}
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

# Gradle 8+ ships wrapper bits as two jars (main + shared). The project wrapper uses a single executable jar
# (Main-Class: org.gradle.wrapper.GradleWrapperMain), so we merge the two jars into gradle/wrapper/gradle-wrapper.jar.
MAIN_PATH=$(unzip -Z1 "$ARCHIVE" | grep -E 'gradle-wrapper-main-[^/]*\.jar$' | head -n 1)
SHARED_PATH=$(unzip -Z1 "$ARCHIVE" | grep -E 'gradle-wrapper-shared-[^/]*\.jar$' | head -n 1)
if [[ -z "$MAIN_PATH" || -z "$SHARED_PATH" ]]; then
  echo "Gradle wrapper jars not found in distribution archive" >&2
  exit 1
fi

unzip -p "$ARCHIVE" "$MAIN_PATH" > "$WORKDIR/wrapper-main.jar"
unzip -p "$ARCHIVE" "$SHARED_PATH" > "$WORKDIR/wrapper-shared.jar"

MERGE_DIR="$WORKDIR/merged"
mkdir -p "$MERGE_DIR"
unzip -q "$WORKDIR/wrapper-main.jar" -d "$MERGE_DIR"
unzip -q "$WORKDIR/wrapper-shared.jar" -d "$MERGE_DIR"

rm -f "$MERGE_DIR/META-INF/MANIFEST.MF" "$MERGE_DIR/META-INF/"*.SF "$MERGE_DIR/META-INF/"*.RSA "$MERGE_DIR/META-INF/"*.DSA || true
mkdir -p "$MERGE_DIR/META-INF"
cat > "$MERGE_DIR/META-INF/MANIFEST.MF" <<'EOF'
Manifest-Version: 1.0
Main-Class: org.gradle.wrapper.GradleWrapperMain
EOF

echo "Building wrapper jar at $TARGET_JAR ..."
if command -v jar >/dev/null 2>&1; then
  jar cfm "$TARGET_JAR" "$MERGE_DIR/META-INF/MANIFEST.MF" -C "$MERGE_DIR" .
elif command -v zip >/dev/null 2>&1; then
  (cd "$MERGE_DIR" && zip -qr "$TARGET_JAR" .)
else
  echo "Missing required tool: expected `jar` (JDK) or `zip`." >&2
  exit 1
fi

echo "Wrapper jar written to $TARGET_JAR"
