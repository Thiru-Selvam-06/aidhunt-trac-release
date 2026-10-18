<#
.SYNOPSIS
    Runs Android unit tests, backend Node tests, and Python inventory validations.
#>
[CmdletBinding()]
param(
    [switch]$All
)
$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Set-Location $ProjectRoot

Write-Host "=== Running AIDHUNT Trac Full Test Suites ===" -ForegroundColor Cyan

# 1. Gradle Unit Tests
Write-Host "`n[1/3] Running Android Unit Tests (testDebugUnitTest)..." -ForegroundColor Yellow
$gradlew = Join-Path $ProjectRoot "gradlew.bat"
& $gradlew testDebugUnitTest --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Android unit tests failed with code $LASTEXITCODE" }
Write-Host "-> Android unit tests PASSED" -ForegroundColor Green

# 2. Backend Node Tests
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCmd) {
    Write-Host "`n[2/3] Running Backend Node Policy & Operations Tests..." -ForegroundColor Yellow
    & node functions/entry-deletion-policy.test.js
    if ($LASTEXITCODE -ne 0) { throw "entry-deletion-policy tests failed" }
    & node cloudflare/operations.test.mjs
    if ($LASTEXITCODE -ne 0) { throw "cloudflare operations tests failed" }
    Write-Host "-> Backend tests PASSED" -ForegroundColor Green
} else {
    Write-Host "`n[2/3] Node.js not detected; skipping Node backend tests" -ForegroundColor Gray
}

# 3. Python Tests
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCmd) {
    Write-Host "`n[3/3] Running Python Suite Tests..." -ForegroundColor Yellow
    & python -m unittest discover -s scripts -p "test_*.py"
    if ($LASTEXITCODE -ne 0) { throw "Python unit tests failed" }
    Write-Host "-> Python tests PASSED" -ForegroundColor Green
}

Write-Host "`n=== All Executed Test Suites PASSED ===" -ForegroundColor Green
