param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$WorkspacePath,
    [Parameter(Mandatory = $true)][string]$ColmapPath,
    [ValidateSet("DRAFT", "BALANCED", "HIGH")][string]$Profile = "DRAFT"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-SafeArchiveEntry([string]$EntryName, [string]$RootPath) {
    if ([string]::IsNullOrWhiteSpace($EntryName) -or $EntryName.StartsWith("/") -or $EntryName.StartsWith("\")) {
        throw "Unsafe ZIP entry path: $EntryName"
    }
    $normalized = $EntryName.Replace("\", "/")
    if ($normalized.Split("/") -contains "..") { throw "ZIP path traversal rejected: $EntryName" }
    $target = [System.IO.Path]::GetFullPath((Join-Path $RootPath $EntryName))
    $root = [System.IO.Path]::GetFullPath($RootPath).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $target.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ZIP entry escapes workspace: $EntryName"
    }
}

function Invoke-ColmapStage([string]$Name, [string[]]$Arguments) {
    Write-Host "==> $Name"
    & $ColmapPath @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $WorkspacePath "logs/$Name.log")
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { throw "COLMAP stage '$Name' failed with exit code $exitCode" }
    $script:StageResults += [ordered]@{ stage = $Name; status = "PASS"; exit_code = $exitCode }
}

if (-not (Test-Path -LiteralPath $ZipPath -PathType Leaf)) { throw "Dataset ZIP not found: $ZipPath" }
if (-not (Test-Path -LiteralPath $ColmapPath -PathType Leaf)) { throw "COLMAP executable not found: $ColmapPath" }
$ZipPath = (Resolve-Path -LiteralPath $ZipPath).Path
$WorkspacePath = [System.IO.Path]::GetFullPath($WorkspacePath)
if ((Test-Path -LiteralPath $WorkspacePath) -and (Get-ChildItem -LiteralPath $WorkspacePath -Force | Measure-Object).Count -gt 0) {
    throw "Workspace must be empty; use a fresh folder to prevent stale files entering the reconstruction."
}
$imagesPath = Join-Path $WorkspacePath "images"
$logsPath = Join-Path $WorkspacePath "logs"
New-Item -ItemType Directory -Force -Path $WorkspacePath, $logsPath | Out-Null

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
try {
    foreach ($entry in $archive.Entries) { Assert-SafeArchiveEntry $entry.FullName $WorkspacePath }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $WorkspacePath, $true)
}
finally { $archive.Dispose() }

$manifestPath = Join-Path $WorkspacePath "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "manifest.json is missing" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schema_version -ne 1 -or -not $manifest.frames) { throw "Unsupported or empty 3DCAP manifest" }

foreach ($frame in $manifest.frames) {
    if ($frame.file_name -notmatch '^3dcap_[A-Za-z0-9_-]+\.jpg$') { throw "Unsafe frame name: $($frame.file_name)" }
    $imagePath = Join-Path $imagesPath $frame.file_name
    if (-not (Test-Path -LiteralPath $imagePath -PathType Leaf)) { throw "Missing image: $($frame.file_name)" }
    $item = Get-Item -LiteralPath $imagePath
    if ($item.Length -ne [long]$frame.byte_size) { throw "Image size mismatch: $($frame.file_name)" }
    $hash = (Get-FileHash -LiteralPath $imagePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $frame.sha256.ToLowerInvariant()) { throw "SHA-256 mismatch: $($frame.file_name)" }
}
if ((Get-ChildItem -LiteralPath $imagesPath -File | Measure-Object).Count -eq 0) { throw "No input images found" }

$database = Join-Path $WorkspacePath "database.db"
$sparse = Join-Path $WorkspacePath "sparse"
$dense = Join-Path $WorkspacePath "dense"
$maxSize = switch ($Profile) { "DRAFT" { "1200" } "BALANCED" { "2000" } "HIGH" { "3200" } }
New-Item -ItemType Directory -Force -Path $sparse, $dense | Out-Null
$script:StageResults = @()

try {
    Invoke-ColmapStage "feature_extractor" @("feature_extractor", "--database_path", $database, "--image_path", $imagesPath)
    Invoke-ColmapStage "exhaustive_matcher" @("exhaustive_matcher", "--database_path", $database)
    Invoke-ColmapStage "mapper" @("mapper", "--database_path", $database, "--image_path", $imagesPath, "--output_path", $sparse)

    $modelPath = Join-Path $sparse "0"
    foreach ($required in @("cameras.bin", "images.bin", "points3D.bin")) {
        if (-not (Test-Path -LiteralPath (Join-Path $modelPath $required) -PathType Leaf)) {
            throw "Sparse model incomplete: missing $required in $modelPath"
        }
    }

    Invoke-ColmapStage "image_undistorter" @("image_undistorter", "--image_path", $imagesPath, "--input_path", $modelPath, "--output_path", $dense, "--output_type", "COLMAP", "--max_image_size", $maxSize)
    Invoke-ColmapStage "patch_match_stereo" @("patch_match_stereo", "--workspace_path", $dense, "--workspace_format", "COLMAP", "--PatchMatchStereo.geom_consistency", "true")
    Invoke-ColmapStage "stereo_fusion" @("stereo_fusion", "--workspace_path", $dense, "--workspace_format", "COLMAP", "--input_type", "geometric", "--output_path", (Join-Path $dense "fused.ply"))

    if (-not (Test-Path -LiteralPath (Join-Path $dense "fused.ply") -PathType Leaf)) { throw "Dense point cloud fused.ply was not produced" }
    $summary = [ordered]@{
        status = "PASS"
        profile = $Profile
        source_zip = $ZipPath
        frame_count = $manifest.frames.Count
        sparse_model = $modelPath
        dense_point_cloud = (Join-Path $dense "fused.ply")
        stages = $script:StageResults
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $WorkspacePath "run-summary.json") -Encoding UTF8
    Write-Host "Reconstruction completed. Inspect run-summary.json and dense/fused.ply."
}
catch {
    $failure = [ordered]@{
        status = "FAIL"
        profile = $Profile
        error = $_.Exception.Message
        stages = $script:StageResults
    }
    $failure | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $WorkspacePath "run-summary.json") -Encoding UTF8
    throw
}
