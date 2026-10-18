# PowerShell script for AIDHUNT Trac Windows Setup and USB Deployment
[CmdletBinding()]
param(
    [ValidateSet('Build', 'InstallUsb', 'Test', 'Bootstrap')]
    [string]$Mode = 'Build',

    [string]$DeviceSerial = '',

    [switch]$SkipTests,

    [switch]$AllowProductionOverwrite
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Set-Location $ProjectRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " AIDHUNT Trac - Windows Developer Bootstrap and USB Deployer" -ForegroundColor Cyan
Write-Host " Project Root : $ProjectRoot" -ForegroundColor Gray
Write-Host " Selected Mode: $Mode" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

function Test-Prerequisites {
    Write-Host "`n[1/6] Inspecting Developer Toolchain..." -ForegroundColor Cyan

    $os = [System.Environment]::OSVersion
    Write-Host "  -> Operating System : Windows ($os)" -ForegroundColor Green

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        throw "JDK is missing. Please install OpenJDK 21 and add to PATH or set JAVA_HOME."
    }
    $javaVerLines = cmd.exe /c "java -version 2>&1"
    $firstLine = if ($javaVerLines -is [array]) { $javaVerLines[0] } else { $javaVerLines }
    Write-Host "  -> Java Version     : $firstLine" -ForegroundColor Green

    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) { $sdkPath = $env:ANDROID_SDK_ROOT }
    if (-not $sdkPath) {
        $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path $defaultSdk) {
            $sdkPath = $defaultSdk
            $env:ANDROID_HOME = $sdkPath
            Write-Host "  -> Auto-detected Android SDK at: $sdkPath" -ForegroundColor Yellow
        }
    }

    if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
        throw "Android SDK not found. Set ANDROID_HOME or install Android SDK command-line tools."
    }
    Write-Host "  -> Android SDK      : $sdkPath" -ForegroundColor Green

    $platformTools = Join-Path $sdkPath 'platform-tools'
    if (Test-Path $platformTools) {
        if ($env:PATH -notmatch [regex]::Escape($platformTools)) {
            $env:PATH = "$platformTools;$env:PATH"
        }
    }

    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        $adbVersion = (cmd.exe /c "adb --version 2>&1")[0]
        Write-Host "  -> ADB Platform     : $adbVersion" -ForegroundColor Green
    } else {
        Write-Host "  [!] Warning: adb not found. Required for USB installation." -ForegroundColor Yellow
    }

    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) {
        throw "Gradle wrapper script missing at $gradlew"
    }
    Write-Host "  -> Gradle Wrapper   : Present (gradlew.bat)" -ForegroundColor Green

    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($nodeCmd) {
        $nodeVer = & node -v
        Write-Host "  -> Node.js Runtime  : $nodeVer" -ForegroundColor Green
    } else {
        Write-Host "  -> Node.js Runtime  : Not installed (Optional: needed for backend/Cloudflare tests)" -ForegroundColor DarkGray
    }

    $pyCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pyCmd) {
        $pyVer = & python --version
        Write-Host "  -> Python Runtime   : $pyVer" -ForegroundColor Green
    }
}

function Initialize-Environment {
    Write-Host "`n[2/6] Validating Development Environment and Configuration..." -ForegroundColor Cyan

    $demoGs = Join-Path $ProjectRoot 'app\src\deviceTest\google-services.json'
    $appGs = Join-Path $ProjectRoot 'app\google-services.json'

    if (-not (Test-Path $demoGs)) {
        throw "Required deviceTest configuration missing at $demoGs"
    }

    if (-not (Test-Path $appGs)) {
        & python (Join-Path $ProjectRoot 'scripts/bootstrap_demo.py')
        if ($LASTEXITCODE -ne 0) { throw 'Demo configuration bootstrap failed' }
        Write-Host "  -> Provisioned development google-services.json (demo-aidhunt-v16)" -ForegroundColor Green
    } else {
        $projectId = (Get-Content -Raw $appGs | ConvertFrom-Json).project_info.project_id
        if ($projectId -ne 'demo-aidhunt-v16') { throw 'Refused: this bootstrap helper is for a demo clone, not a business configuration' }
        Write-Host "  -> Demo compile configuration present" -ForegroundColor Green
    }

    $envExample = Join-Path $ProjectRoot '.env.example'
    $envFile = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
        Copy-Item -Path $envExample -Destination $envFile
        Write-Host "  -> Created development .env from .env.example" -ForegroundColor Green
    }
}

function Invoke-Tests {
    Write-Host "`n[3/6] Running Validation Tests..." -ForegroundColor Cyan

    Write-Host "  -> Executing Gradle Unit Tests (testDebugUnitTest)..." -ForegroundColor Yellow
    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    & $gradlew testDebugUnitTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Unit tests failed with exit code $LASTEXITCODE"
    }
    Write-Host "  -> All Gradle unit tests PASSED" -ForegroundColor Green

    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($nodeCmd) {
        Write-Host "  -> Running backend policy tests..." -ForegroundColor Yellow
        & node functions/entry-deletion-policy.test.js
        if ($LASTEXITCODE -ne 0) { throw "entry-deletion-policy tests failed" }
        & node cloudflare/operations.test.mjs
        if ($LASTEXITCODE -ne 0) { throw "cloudflare operations tests failed" }
        Write-Host "  -> Backend policy and worker tests PASSED" -ForegroundColor Green
    }
}

function Build-Apk {
    Write-Host "`n[4/6] Compiling Isolated Development APK (assembleDeviceTest)..." -ForegroundColor Cyan

    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    & $gradlew :app:assembleDeviceTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $apkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\deviceTest\app-deviceTest.apk'
    if (-not (Test-Path $apkPath)) {
        throw "Expected APK artifact was not generated at $apkPath"
    }

    $hash = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash
    $sizeBytes = (Get-Item $apkPath).Length
    $sizeMb = [math]::Round($sizeBytes / 1MB, 2)

    Write-Host "  -> Build Succeeded!" -ForegroundColor Green
    Write-Host "  -> Artifact  : $apkPath" -ForegroundColor Green
    Write-Host "  -> Size      : $sizeMb MB ($sizeBytes bytes)" -ForegroundColor Green
    Write-Host "  -> SHA-256   : $hash" -ForegroundColor Green
    Write-Host "  -> Package ID: com.aistudio.aidhunttrac.cedlir.devicetest" -ForegroundColor Green
    Write-Host "  -> Variant   : deviceTest (Isolated Emulator / Demo Backend)" -ForegroundColor Green

    return @{
        Path = $apkPath
        Hash = $hash
        PackageId = 'com.aistudio.aidhunttrac.cedlir.devicetest'
    }
}

function Install-UsbDevice([string]$apkPath, [string]$packageId) {
    Write-Host "`n[5/6] Inspecting Connected USB Android Device..." -ForegroundColor Cyan

    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adbCmd) {
        throw "ADB binary not found in PATH or Android SDK platform-tools."
    }

    $rawDevices = (cmd.exe /c "adb devices") | Out-String
    $deviceLines = @($rawDevices.Split("`r`n") | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -and $_ -notmatch '^List of devices'
    })

    if ($deviceLines.Count -eq 0) {
        throw "No Android devices connected over USB. Connect a phone with USB Debugging enabled and retry."
    }

    $targetSerial = $DeviceSerial
    if (-not $targetSerial) {
        $first = ($deviceLines[0] -split '\s+')[0].Trim()
        $targetSerial = $first
    }

    $matchedLine = $deviceLines | Where-Object { $_ -match "^$targetSerial" }
    $parts = $matchedLine -split '\s+'
    $status = if ($parts.Length -gt 1) { $parts[1].Trim() } else { 'unknown' }
    if ($status -eq 'unauthorized') {
        throw "Device $targetSerial is UNAUTHORIZED. Unlock the phone and tap Allow USB debugging."
    }
    if ($status -eq 'offline') {
        throw "Device $targetSerial is OFFLINE. Re-plug USB cable and retry."
    }

    $model = (cmd.exe /c "adb -s $targetSerial shell getprop ro.product.model").Trim()
    $androidVer = (cmd.exe /c "adb -s $targetSerial shell getprop ro.build.version.release").Trim()

    Write-Host "  -> Target Device    : $targetSerial ($model, Android $androidVer)" -ForegroundColor Green

    $prodPackage = 'com.aistudio.aidhunttrac.cedlir'
    $installedPackagesText = (cmd.exe /c "adb -s $targetSerial shell pm list packages aidhunt 2>&1") | Out-String

    if ($installedPackagesText -match "package:$prodPackage") {
        Write-Host "  [i] Notice: Production package '$prodPackage' detected on device." -ForegroundColor Yellow
        Write-Host "      Development package is '$packageId' (coexists safely without overwriting production data)." -ForegroundColor Green
        if ($packageId -eq $prodPackage -and -not $AllowProductionOverwrite) {
            throw "SAFETY BLOCK: Attempting to install directly as '$prodPackage' will overwrite production data! Use -AllowProductionOverwrite to proceed."
        }
    }

    Write-Host "  -> Forwarding loopback ports via ADB reverse (19099, 18180, 15001)..." -ForegroundColor Yellow
    cmd.exe /c "adb -s $targetSerial reverse tcp:19099 tcp:19099" | Out-Null
    cmd.exe /c "adb -s $targetSerial reverse tcp:18180 tcp:18180" | Out-Null
    cmd.exe /c "adb -s $targetSerial reverse tcp:15001 tcp:15001" | Out-Null
    Write-Host "  -> USB port forwarding configured successfully" -ForegroundColor Green

    Write-Host "  -> Installing $apkPath onto $targetSerial..." -ForegroundColor Yellow
    $installOutput = cmd.exe /c "adb -s $targetSerial install -r `"$apkPath`" 2>&1"
    $installText = $installOutput | Out-String
    if ($installText -notmatch 'Success') {
        throw "ADB installation failed: $installText"
    }
    Write-Host "  -> Installation Succeeded!" -ForegroundColor Green

    $verifyPackagesText = (cmd.exe /c "adb -s $targetSerial shell pm list packages $packageId") | Out-String
    if ($verifyPackagesText -notmatch "package:$packageId") {
        throw "Verification failed: Package '$packageId' not found on device after installation."
    }
    Write-Host "  -> Verified package '$packageId' is installed on device" -ForegroundColor Green

    Write-Host "`n[6/6] Launching Development Package..." -ForegroundColor Cyan
    cmd.exe /c "adb -s $targetSerial shell monkey -p $packageId -c android.intent.category.LAUNCHER 1" | Out-Null
    Write-Host "  -> Development app launched on $model!" -ForegroundColor Green
    Write-Host "  -> Database: aidhunt_device_test_v18.db (Isolated Demo Workspace)" -ForegroundColor Green
    Write-Host "  -> Endpoints: 127.0.0.1:18180 (Firestore) / 127.0.0.1:19099 (Auth)" -ForegroundColor Green
}

try {
    Test-Prerequisites
    Initialize-Environment

    if ($Mode -in @('Test', 'Bootstrap') -and -not $SkipTests) {
        Invoke-Tests
    }

    $buildResult = $null
    if ($Mode -in @('Build', 'InstallUsb', 'Bootstrap')) {
        $buildResult = Build-Apk
    }

    if ($Mode -in @('InstallUsb', 'Bootstrap')) {
        if (-not $buildResult) {
            $apk = Join-Path $ProjectRoot 'app\build\outputs\apk\deviceTest\app-deviceTest.apk'
            if (-not (Test-Path $apk)) {
                $buildResult = Build-Apk
            } else {
                $buildResult = @{
                    Path = $apk
                    PackageId = 'com.aistudio.aidhunttrac.cedlir.devicetest'
                }
            }
        }
        Install-UsbDevice -apkPath $buildResult.Path -packageId $buildResult.PackageId
    }

    Write-Host "`n=================================================================" -ForegroundColor Cyan
    Write-Host " Workflow Completed Successfully!" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Cyan
} catch {
    Write-Host "`n[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
