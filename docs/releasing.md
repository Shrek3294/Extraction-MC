# Releasing Raid Extraction

This document outlines the manual release workflow for the plugin.

## Preconditions
- Clean working tree on the target branch (`main-cleanup`).
- Java 21 installed (or auto-download enabled).
- Gradle wrapper JAR present:
  - macOS/Linux: run `./scripts/fetch-gradle-wrapper.sh`
  - Windows (PowerShell): run `.\scripts\fetch-gradle-wrapper.ps1`
  - Or place `gradle/wrapper/gradle-wrapper.jar` locally (do not commit it).

## Version bump
1. Update the version in `gradle.properties` (`version=X.Y.Z`) so the jar name becomes `raid-extraction-X.Y.Z.jar`.
2. Update the version in `src/main/resources/plugin.yml`.
2. If you keep a changelog elsewhere, update it now.

## Build
- macOS/Linux: `./gradlew clean build --console=plain`
- Windows: `.\gradlew.bat clean build --console=plain`

## Smoke test (local Paper)
1. Ensure a gitignored `server/` folder exists with a Paper server JAR.
2. Copy the plugin JAR:
   - Windows: `.\gradlew.bat copyPluginToServer`
   - macOS/Linux: `./gradlew copyPluginToServer`
   - Override the target with `-PserverDir=../path/to/server` if needed.
3. Start Paper from `server/` and confirm:
   - Plugin enables without errors.
   - `/raid status` responds.
   - `/stash` opens and closes without errors.

## Tag and publish
1. Tag the release: `git tag vX.Y.Z`
2. Push tags: `git push origin vX.Y.Z`
3. Upload the built JAR from `build/libs/` to your release page.

## Notes
- Do not commit `server/` or `gradle/wrapper/gradle-wrapper.jar`.
