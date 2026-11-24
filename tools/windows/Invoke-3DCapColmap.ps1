param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$WorkspacePath,
    [Parameter(Mandatory = $true)][string]$ColmapPath,
    [ValidateSet("DRAFT", "BALANCED", "HIGH")][string]$Profile = "DRAFT"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-SafeArchiveEntry([string]$EntryName, [string]$RootPath) {
    if ([string]::IsNullOrWhiteSpace($EntryName) -or
        [System.IO.Path]::IsPathRooted($EntryName) -or
        $EntryName.Contains(":") -or
        $EntryName.IndexOf([char]0) -ge 0) {
        throw "Unsafe ZIP entry path: $EntryName"
    }

    $normalized = $EntryName.Replace("\", "/").TrimEnd("/")
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        throw "Unsafe ZIP entry path: $EntryName"
    }
    $segments = $normalized.Split("/")
    if ($segments -contains "" -or $segments -contains "." -or $segments -contains "..") {
        throw "ZIP path traversal or ambiguous path rejected: $EntryName"
    }

    $target = [System.IO.Path]::GetFullPath((Join-Path $RootPath $normalized))
    $root = [System.IO.Path]::GetFullPath($RootPath).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $target.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ZIP entry escapes workspace: $EntryName"
    }
}

function Invoke-ColmapStage([string]$Name, [string[]]$Arguments) {
    Write-Host "==> $Name"
    $exitCode = $null
    try {
        & $ColmapPath @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $WorkspacePath "logs/$Name.log")
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "COLMAP stage '$Name' failed with exit code $exitCode"
        }
        $script:StageResults += [ordered]@{ stage = $Name; status = "PASS"; exit_code = $exitCode }
    }
    catch {
        $script:StageResults += [ordered]@{
            stage = $Name
            status = "FAIL"
            exit_code = $exitCode
            error = $_.Exception.Message
        }
        throw
    }
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
    $seenEntries = @{}
    foreach ($entry in $archive.Entries) {
        Assert-SafeArchiveEntry $entry.FullName $WorkspacePath
        $entryKey = $entry.FullName.Replace("\", "/").TrimEnd("/").ToLowerInvariant()
        if ($seenEntries.ContainsKey($entryKey)) {
            throw "Duplicate ZIP entry path rejected: $($entry.FullName)"
        }
        $seenEntries[$entryKey] = $true
    }

    # The workspace is required to be empty, so the two-argument overload is
    # compatible with Windows PowerShell 5.1/.NET Framework.
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $WorkspacePath)
}
finally { $archive.Dispose() }

$manifestPath = Join-Path $WorkspacePath "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "manifest.json is missing" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$frames = @($manifest.frames)
if ($manifest.schema_version -ne 1 -or $frames.Count -eq 0) {
    throw "Unsupported or empty 3DCAP manifest"
}

$seenFrameNames = @{}
foreach ($frame in $frames) {
    $frameName = [string]$frame.file_name
    if ($frameName -notmatch '^3dcap_[A-Za-z0-9_-]+\.jpg$') {
        throw "Unsafe frame name: $frameName"
    }
    if ($seenFrameNames.ContainsKey($frameName)) {
        throw "Manifest contains duplicate frame filenames: $frameName"
    }
    $seenFrameNames[$frameName] = $true

    $sizeText = [string]$frame.byte_size
    [long]$expectedBytes = 0
    if ($sizeText -notmatch '^[0-9]+$' -or
        -not [long]::TryParse($sizeText, [ref]$expectedBytes) -or
        $expectedBytes -le 0) {
        throw "Invalid image byte_size for frame: $frameName"
    }
    $expectedHash = [string]$frame.sha256
    if ($expectedHash -notmatch '^[A-Fa-f0-9]{64}$') {
        throw "Invalid SHA-256 digest for frame: $frameName"
    }

    $imagePath = Join-Path $imagesPath $frameName
    if (-not (Test-Path -LiteralPath $imagePath -PathType Leaf)) {
        throw "Missing image: $frameName"
    }
    $item = Get-Item -LiteralPath $imagePath
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Reparse-point image files are not accepted: $frameName"
    }
    if ($item.Length -ne $expectedBytes) {
        throw "Image size mismatch: $frameName"
    }
    $hash = (Get-FileHash -LiteralPath $imagePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $expectedHash.ToLowerInvariant()) {
        throw "SHA-256 mismatch: $frameName"
    }
}

if (-not (Test-Path -LiteralPath $imagesPath -PathType Container)) {
    throw "Dataset images/ directory is missing"
}
$nestedDirectories = @(Get-ChildItem -LiteralPath $imagesPath -Directory -Force)
if ($nestedDirectories.Count -gt 0) {
    throw "Nested directories are not supported inside images/"
}
$imageFiles = @(Get-ChildItem -LiteralPath $imagesPath -File -Force)
if ($imageFiles.Count -eq 0) { throw "No input images found" }
if ($imageFiles.Count -ne $frames.Count) {
    throw "Image directory contains files not listed in the manifest"
}

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
        frame_count = $frames.Count
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
        source_zip = $ZipPath
        frame_count = $frames.Count
        error = $_.Exception.Message
        stages = $script:StageResults
    }
    $failure | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $WorkspacePath "run-summary.json") -Encoding UTF8
    throw
}
