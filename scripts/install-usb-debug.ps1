<#
.SYNOPSIS
    Installs and launches the development/test APK over USB with strict production protection.
#>
[CmdletBinding()]
param(
    [string]$DeviceSerial = '',
    [switch]$SkipTests,
    [switch]$AllowProductionOverwrite
)
$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptDir 'setup-windows.ps1') -Mode InstallUsb -DeviceSerial $DeviceSerial -SkipTests:$SkipTests -AllowProductionOverwrite:$AllowProductionOverwrite
