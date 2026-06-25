param(
    [string]$Path = "assets/ui/skins",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot "gradlew.bat"

if (-not (Test-Path $gradle)) {
    throw "Could not find gradlew.bat at '$gradle'."
}

$resolvedPath =
    if ([System.IO.Path]::IsPathRooted($Path)) {
        [System.IO.Path]::GetFullPath($Path)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
    }

$args = @(
    ":engine:backend-gdx:normalizeScene2DSkins",
    "-Pscene2dSkinPath=$resolvedPath"
)

if ($DryRun) {
    $args += "-Pscene2dSkinDryRun=true"
}

& $gradle @args
exit $LASTEXITCODE
