param(
    [string[]]$TestModules = @(
        "film-service"
    ),
    [string[]]$CompileOnlyModules = @(
        "review-service"
    ),
    [string]$MavenCommand
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

function Resolve-MavenCommand {
    param([string]$RequestedCommand)

    if ($RequestedCommand) {
        return $RequestedCommand
    }

    $mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        return $mvnCmd.Source
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return $mvn.Source
    }

    throw "Maven executable not found. Pass -MavenCommand or install Maven in PATH."
}

function Remove-ModuleTarget {
    param([string]$ModuleName)

    $targetPath = Join-Path $RepoRoot "$ModuleName\target"
    if (Test-Path $targetPath) {
        Write-Host ">>> Removing $targetPath"
        Remove-Item -LiteralPath $targetPath -Recurse -Force
    }
}

$Maven = Resolve-MavenCommand -RequestedCommand $MavenCommand

Write-Host ">>> Using Maven: $Maven"

foreach ($module in @($TestModules + $CompileOnlyModules) | Select-Object -Unique) {
    Remove-ModuleTarget -ModuleName $module
}

foreach ($module in $TestModules) {
    Write-Host ">>> Running tests for $module"
    $testFilter = $null
    if ($module -eq "film-service") {
        $testFilter = "FilmControllerSearchIntegrationTest,FilmServiceImplTest"
    }

    if ($testFilter) {
        & $Maven @(
            "-f",
            (Join-Path $RepoRoot "$module\pom.xml"),
            "-Dtest=$testFilter",
            "test"
        )
    } else {
        & $Maven @(
            "-f",
            (Join-Path $RepoRoot "$module\pom.xml"),
            "test"
        )
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test failed for module $module with exit code $LASTEXITCODE"
    }
}

foreach ($module in $CompileOnlyModules) {
    if ($TestModules -contains $module) {
        continue
    }

    Write-Host ">>> Compiling $module"
    & $Maven @(
        "-f",
        (Join-Path $RepoRoot "$module\pom.xml"),
        "-DskipTests",
        "compile"
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Maven compile failed for module $module with exit code $LASTEXITCODE"
    }
}

Write-Host "Done."
