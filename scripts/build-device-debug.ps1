<#
.SYNOPSIS
    Builds the isolated development/test APK (com.aistudio.aidhunttrac.cedlir.devicetest).
#>
$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptDir 'setup-windows.ps1') -Mode Build
