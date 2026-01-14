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
    $mainEntry =
      $zip.Entries |
      Where-Object { $_.FullName -match 'gradle-wrapper-main-[^/]*\.jar$' } |
      Select-Object -First 1
    $sharedEntry =
      $zip.Entries |
      Where-Object { $_.FullName -match 'gradle-wrapper-shared-[^/]*\.jar$' } |
      Select-Object -First 1
    $cliEntry =
      $zip.Entries |
      Where-Object { $_.FullName -match '(^|/)gradle-cli-[^/]*\.jar$' } |
      Select-Object -First 1
    $filesEntry =
      $zip.Entries |
      Where-Object { $_.FullName -match '(^|/)gradle-files-[^/]*\.jar$' } |
      Select-Object -First 1

    if (-not $mainEntry -or -not $sharedEntry -or -not $cliEntry -or -not $filesEntry) {
      throw "Gradle wrapper jars not found in distribution archive"
    }

    $mainJar = Join-Path $tempDir "wrapper-main.jar"
    $sharedJar = Join-Path $tempDir "wrapper-shared.jar"
    $cliJar = Join-Path $tempDir "gradle-cli.jar"
    $filesJar = Join-Path $tempDir "gradle-files.jar"

    foreach ($pair in @(@($mainEntry, $mainJar), @($sharedEntry, $sharedJar), @($cliEntry, $cliJar), @($filesEntry, $filesJar))) {
      $entry = $pair[0]
      $outFile = $pair[1]
      $s = $entry.Open()
      try {
        $fs = [System.IO.File]::Open($outFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
        try { $s.CopyTo($fs) } finally { $fs.Dispose() }
      } finally {
        $s.Dispose()
      }
    }

    $targetDir = Split-Path -Parent $targetJar
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    # Merge wrapper-main + wrapper-shared into a single executable jar with a Main-Class manifest.
    $outZip = [System.IO.Compression.ZipFile]::Open($targetJar, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
      $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)

      function Copy-JarEntries([string]$jarPath) {
        $jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        try {
          foreach ($e in $jar.Entries) {
            if ([string]::IsNullOrEmpty($e.Name)) { continue } # directory
            $name = $e.FullName
            if ($name -eq "META-INF/MANIFEST.MF") { continue }
            if ($name -match '^META-INF/.*\\.(SF|RSA|DSA)$') { continue }
            if ($seen.Contains($name)) { continue }
            $seen.Add($name) | Out-Null

            $dst = $outZip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
            $srcStream = $e.Open()
            try {
              $dstStream = $dst.Open()
              try { $srcStream.CopyTo($dstStream) } finally { $dstStream.Dispose() }
            } finally {
              $srcStream.Dispose()
            }
          }
        } finally {
          $jar.Dispose()
        }
      }

      Copy-JarEntries $mainJar
      Copy-JarEntries $sharedJar
      Copy-JarEntries $cliJar
      Copy-JarEntries $filesJar

      $manifestText = "Manifest-Version: 1.0`r`nMain-Class: org.gradle.wrapper.GradleWrapperMain`r`n"
      $mfEntry = $outZip.CreateEntry("META-INF/MANIFEST.MF", [System.IO.Compression.CompressionLevel]::Optimal)
      $mfStream = $mfEntry.Open()
      try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($manifestText)
        $mfStream.Write($bytes, 0, $bytes.Length)
      } finally {
        $mfStream.Dispose()
      }
    } finally {
      $outZip.Dispose()
    }
  } finally {
    $zip.Dispose()
  }

  Write-Host "Wrapper jar written to $targetJar"
} finally {
  Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
