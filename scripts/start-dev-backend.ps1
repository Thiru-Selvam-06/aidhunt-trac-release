<#
.SYNOPSIS
    Starts the local Firebase Emulator Suite on loopback ports (19099 Auth, 18180 Firestore, 15001 Functions).
#>
[CmdletBinding()]
param(
    [string]$Project = "demo-aidhunt-v16"
)
$ErrorActionPreference = 'Stop'
if ($Project -ne 'demo-aidhunt-v16') { throw 'Refused: only the disposable demo project is allowed' }
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Set-Location $ProjectRoot

Write-Host "=== Starting Isolated Local Firebase Emulator Suite ($Project) ===" -ForegroundColor Cyan
Write-Host "  -> Auth Emulator      : 127.0.0.1:19099" -ForegroundColor Green
Write-Host "  -> Firestore Emulator : 127.0.0.1:18180" -ForegroundColor Green
Write-Host "  -> Functions Emulator : 127.0.0.1:15001" -ForegroundColor Green

npx --yes firebase-tools@15.33.0 emulators:start --project $Project --only auth,firestore,functions --config firebase.device-test.json
