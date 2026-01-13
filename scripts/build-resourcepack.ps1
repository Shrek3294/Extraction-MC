param(
  [string]$OutDir = "build/resourcepack"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$packDir = Join-Path $projectRoot "resourcepack"

if (!(Test-Path $packDir)) {
  throw "Resource pack folder not found: $packDir"
}

$outPath = Join-Path $projectRoot $OutDir
New-Item -ItemType Directory -Force -Path $outPath | Out-Null

$zipPath = Join-Path $outPath "raidextraction-resourcepack.zip"
if (Test-Path $zipPath) {
  Remove-Item -Force $zipPath
}

$includeFiles = @(
  (Join-Path $packDir "pack.mcmeta"),
  (Join-Path $packDir "pack.png")
)

$assetsDir = Join-Path $packDir "assets"
if (Test-Path $assetsDir) {
  $includeFiles += (Get-ChildItem -Path $assetsDir -Recurse -File | Select-Object -ExpandProperty FullName)
}

$files = $includeFiles | Where-Object { Test-Path $_ }

# Use ZipArchive directly so all entries use forward slashes (Minecraft expects `/` inside zips).
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  $packRoot = (Resolve-Path $packDir).Path.TrimEnd('\', '/')
  foreach ($file in $files) {
    $full = (Resolve-Path $file).Path
    if (!$full.StartsWith($packRoot)) {
      throw "Unexpected file outside pack root: $full"
    }
    $relative = $full.Substring($packRoot.Length).TrimStart('\', '/')
    $entryName = $relative.Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $full, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
  }
} finally {
  $zip.Dispose()
}
$sha1 = (Get-FileHash -Algorithm SHA1 $zipPath).Hash.ToLowerInvariant()

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
  $entries = $z.Entries | Select-Object -ExpandProperty FullName
  $hasPack = $entries -contains "pack.mcmeta"
  $hasAssets = $entries | Where-Object { $_ -like "assets/*" } | Select-Object -First 1
  $hasBackslashes = $entries | Where-Object { $_ -like "*\\*" } | Select-Object -First 1
  $hasRootFolder = $entries | Where-Object { $_ -match "^[^/]+/pack\\.mcmeta$" } | Select-Object -First 1

  if (-not $hasPack -or -not $hasAssets -or $hasBackslashes -or $hasRootFolder) {
    Write-Host "ERROR: Zip structure is not Minecraft-compatible." -ForegroundColor Red
    Write-Host "Expected:" -ForegroundColor Red
    Write-Host "  pack.mcmeta" -ForegroundColor Red
    Write-Host "  assets/minecraft/..." -ForegroundColor Red
    if (-not $hasPack) { Write-Host "Missing pack.mcmeta at zip root." -ForegroundColor Red }
    if (-not $hasAssets) { Write-Host "Missing assets/ folder at zip root." -ForegroundColor Red }
    if ($hasBackslashes) { Write-Host "Zip contains backslash paths (\\); Minecraft expects /." -ForegroundColor Red }
    if ($hasRootFolder) { Write-Host "Zip contains a parent folder wrapper (e.g. resourcepack-release/pack.mcmeta)." -ForegroundColor Red }
    throw "Bad resource pack zip structure."
  }
} finally {
  $z.Dispose()
}

Write-Host "Built: $zipPath"
Write-Host "SHA1 : $sha1"
Write-Host ""
Write-Host "Server properties (needs a hosted URL):"
Write-Host "  resource-pack=<https://your.host/raidextraction-resourcepack.zip>"
Write-Host "  resource-pack-sha1=$sha1"
Write-Host "  require-resource-pack=true"
