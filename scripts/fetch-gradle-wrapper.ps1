param()

$ErrorActionPreference = "Stop"

# Helper to download the Gradle wrapper JAR on-demand to keep binaries out of version control.
# Requires: PowerShell 5.1+ (or PowerShell 7+).

$repoRoot = Split-Path -Parent $PSScriptRoot
$propsFile = Join-Path $repoRoot "gradle/wrapper/gradle-wrapper.properties"
$targetJar = Join-Path $repoRoot "gradle/wrapper/gradle-wrapper.jar"

if (Test-Path $targetJar) {
  Write-Host "Gradle wrapper jar already present at $targetJar"
  exit 0
}

if (!(Test-Path $propsFile)) {
  throw "Missing $propsFile; cannot determine distributionUrl"
}

$distributionLine = (Select-String -Path $propsFile -Pattern '^distributionUrl=' | Select-Object -First 1).Line
if ([string]::IsNullOrWhiteSpace($distributionLine)) {
  throw "distributionUrl not found in $propsFile"
}

$distributionUrl = $distributionLine.Substring("distributionUrl=".Length)
$distributionUrl = $distributionUrl -replace '\\:', ':'
if ([string]::IsNullOrWhiteSpace($distributionUrl)) {
  throw "distributionUrl not found in $propsFile"
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
  $archive = Join-Path $tempDir "gradle-dist.zip"
  Write-Host "Downloading Gradle distribution from $distributionUrl ..."
  Invoke-WebRequest -Uri $distributionUrl -OutFile $archive

  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
  try {
    $entry =
      $zip.Entries |
      Where-Object { $_.FullName -match 'gradle-wrapper-[^/]*\.jar$' -and $_.FullName -notmatch 'gradle-wrapper-shared' } |
      Select-Object -First 1

    if (-not $entry) {
      throw "Gradle wrapper jar not found in distribution archive"
    }

    $targetDir = Split-Path -Parent $targetJar
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    $inputStream = $entry.Open()
    try {
      $outputStream = [System.IO.File]::Open($targetJar, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
      try {
        $inputStream.CopyTo($outputStream)
      } finally {
        $outputStream.Dispose()
      }
    } finally {
      $inputStream.Dispose()
    }
  } finally {
    $zip.Dispose()
  }

  Write-Host "Wrapper jar written to $targetJar"
} finally {
  Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}

