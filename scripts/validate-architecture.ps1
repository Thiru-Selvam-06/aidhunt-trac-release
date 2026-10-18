param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
& $Python (Join-Path $PSScriptRoot 'validate-architecture.py')
if ($LASTEXITCODE -ne 0) { throw "Architecture validation failed with exit $LASTEXITCODE; inspect .codex/validation-v16/results.json" }
